import { parseCsv } from '../../infrastructure/import/csvReader'
import { parseRows } from '../../infrastructure/import/schemas'
import type { ParsedRow } from '../../infrastructure/import/schemas'
import {
  buildDedupeIndex,
  classifyCandidate,
  hashContent,
  type DedupeCandidate,
  type ExistingRecord,
} from '../../domain/dedupe'
import { categorize, prepareRules, type CategorizeDeps } from '../../domain/categorize'
import { normalizeText } from '../../domain/normalize'
import { addMoney } from '../../domain/money'
import type { Id } from '../../domain/entities/types'
import type {
  ImportPreview,
  ImportPreviewLine,
  ImportRequest,
  ImportStatementDeps,
} from './importTypes'

/**
 * مرحلة المعاينة من ImportStatement — استخراج ومراجعة **بلا أي كتابة**.
 *
 * مفصولة عن الالتزام في ملف مستقل لأن حد الملف 300 سطر (CLAUDE.md #7)،
 * ولأن الفصل نفسه هو المطلوب في spec/04: «مراجعة → ملخص الأثر → تأكيد».
 * آمن استدعاؤها مرارًا: لا تلمس التخزين إطلاقًا.
 */

export async function buildCategorizeDeps(deps: ImportStatementDeps): Promise<CategorizeDeps> {
  const [merchants, categories, rules] = await Promise.all([
    deps.merchants.listAll(),
    deps.categories.listAll(),
    deps.rules.listAll(),
  ])
  return {
    merchantsByNormalizedName: new Map(merchants.map((m) => [m.normalizedName, m])),
    categoryIdByName: new Map(categories.map((c) => [normalizeText(c.name), c.id])),
    rules: prepareRules(rules),
  }
}

function toCandidate(row: ParsedRow, accountIdentity: string): DedupeCandidate {
  return {
    accountIdentity,
    sourceReference: row.reference,
    date: row.date,
    amountMinor: row.amountMinor,
    direction: row.direction,
    merchantName: row.merchantName,
    rowIndex: row.lineNumber,
  }
}

/** يبني فهرس ما هو موجود مسبقًا لهذا الحساب. */
async function loadExisting(
  deps: ImportStatementDeps,
  accountIdentity: string,
): Promise<ExistingRecord[]> {
  const records = await deps.sources.listByAccountIdentity(accountIdentity)
  const txnIds = records.map((r) => r.transactionId).filter((id): id is Id => id !== null)
  const txns = await deps.txns.findByIds(txnIds)
  const byId = new Map(txns.map((t) => [t.id, t]))

  const existing: ExistingRecord[] = []
  for (const record of records) {
    if (!record.transactionId) continue
    const txn = byId.get(record.transactionId)
    if (!txn) continue
    existing.push({
      accountIdentity: record.accountIdentity,
      sourceReference: record.sourceReference,
      date: txn.occurredAt,
      amountMinor: txn.amountMinor,
      // الاتجاه الملاحظ حقيقة بنكية محفوظة، لا يُستنتج من النوع الاقتصادي
      direction: txn.observedDirection,
      merchantName: txn.rawMerchantName ?? '',
      rowIndex: record.originalRowIndex,
      transactionId: record.transactionId,
    })
  }
  return existing
}

export async function runPreview(
  deps: ImportStatementDeps,
  request: ImportRequest,
): Promise<ImportPreview> {
  const fileHash = hashContent(request.content)

  // ─── الدرجة ١: بصمة ملف سبق استيراده ───
  const previousBatch = await deps.batches.findByFileHash(fileHash)

  const doc = parseCsv(request.content)
  const outcome = parseRows(doc, request.schema)

  const index = buildDedupeIndex(await loadExisting(deps, request.accountIdentity))
  const catDeps = await buildCategorizeDeps(deps)

  const lines: ImportPreviewLine[] = []
  // الدفعة تُفحص ضد نفسها أيضًا: repeated-import.csv فيه سطران بنفس المرجع
  const seenInBatch = new Map<string, number>()

  for (const row of outcome.rows) {
    const candidate = toCandidate(row, request.accountIdentity)
    let verdict = classifyCandidate(candidate, index)

    const ref = candidate.sourceReference?.trim()
    if (verdict.state === 'new' && ref) {
      const key = `${request.accountIdentity}|${ref}`
      const priorLine = seenInBatch.get(key)
      if (priorLine !== undefined) {
        verdict = {
          state: 'duplicate',
          reason: `نفس المرجع «${ref}» اتكرر في الملف ده نفسه (صف ${priorLine})`,
        }
      } else {
        seenInBatch.set(key, row.lineNumber)
      }
    }

    const categorization = categorize(
      {
        currentConfirmed: false,
        merchantName: row.merchantName,
        description: row.description,
        sourceCategory: row.sourceCategory,
      },
      catDeps,
    )

    const line: ImportPreviewLine = {
      row,
      state: verdict.state,
      reason: verdict.reason,
      categoryReason: categorization.reason,
      // «أضف الجديد فقط» — fixtures/README. المتشابه والتعارض يحتاجان قرارًا
      selectedByDefault: verdict.state === 'new',
    }
    if (verdict.matchedTransactionId) line.matchedTransactionId = verdict.matchedTransactionId
    if (categorization.categoryId) line.categoryId = categorization.categoryId
    lines.push(line)
  }

  const counts = {
    total: outcome.rows.length + outcome.errors.length,
    newCount: lines.filter((l) => l.state === 'new').length,
    duplicates: lines.filter((l) => l.state === 'duplicate').length,
    similar: lines.filter((l) => l.state === 'similar').length,
    conflicts: lines.filter((l) => l.state === 'conflict').length,
    invalid: outcome.errors.length,
  }

  let walletDelta = 0
  let expense = 0
  let income = 0
  for (const line of lines.filter((l) => l.selectedByDefault)) {
    if (line.row.direction === 'in') {
      walletDelta = addMoney(walletDelta, line.row.amountMinor)
      income = addMoney(income, line.row.amountMinor)
    } else {
      walletDelta = addMoney(walletDelta, -line.row.amountMinor)
      expense = addMoney(expense, line.row.amountMinor)
    }
  }

  return {
    fileName: request.fileName,
    fileHash,
    schema: outcome.schema,
    accountIdentity: request.accountIdentity,
    previousBatch,
    lines,
    errors: outcome.errors,
    counts,
    impact: { walletDeltaMinor: walletDelta, expenseMinor: expense, incomeMinor: income },
  }
}
