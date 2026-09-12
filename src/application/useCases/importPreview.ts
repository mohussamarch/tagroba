import { merchantIndex } from '../../domain/merchantIndex'
import { parseCsv } from '../../infrastructure/import/csvReader'
import { parseRows } from '../../infrastructure/import/schemas'
import type { ParsedRow } from '../../infrastructure/import/schemas'
import {
  buildDedupeIndex,
  classifyCandidate,
  hashContent,
  importFingerprint,
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
    merchantsByNormalizedName: merchantIndex(merchants),
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
    ...(row.statedBalanceMinor !== undefined ? { statedBalanceMinor: row.statedBalanceMinor } : {}),
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

  /*
   * **سجل واحد لكل عملية، مش لكل سجل مصدر.** العملية اللي اتسجلت من
   * مصدرين (كشف مستورد مرتين، أو رسالة + كشف) ليها أكتر من `sourceRecord`،
   * وكانت بتتعد أكتر من مرة في فهرس التكرار ⇒ تبتلع أكتر من صف وارد
   * وتخلي عمليات حقيقية تتحسب «مكرر» (اتشاف على بيانات المالك 2026-09-12:
   * 835 «مكرر» مقابل 768 عملية موجودة).
   */
  const existing: ExistingRecord[] = []
  const seenTransactions = new Set<string>()
  for (const record of records) {
    if (!record.transactionId) continue
    if (seenTransactions.has(record.transactionId)) continue
    const txn = byId.get(record.transactionId)
    if (!txn) continue
    seenTransactions.add(record.transactionId)
    existing.push({
      smsSource: record.sourceReference?.startsWith("SMS:") ?? false,
      accountIdentity: record.accountIdentity,
      sourceReference: record.sourceReference,
      date: txn.occurredAt,
      amountMinor: txn.amountMinor,
      // الاتجاه الملاحظ حقيقة بنكية محفوظة، لا يُستنتج من النوع الاقتصادي
      direction: txn.observedDirection,
      merchantName: txn.rawMerchantName ?? '',
      rowIndex: record.originalRowIndex,
      ...(txn.statedBalanceMinor !== undefined ? { statedBalanceMinor: txn.statedBalanceMinor } : {}),
      transactionId: record.transactionId,
    })
  }
  return existing
}

export async function runPreview(
  deps: ImportStatementDeps,
  request: ImportRequest,
): Promise<ImportPreview> {
  const fileHash = importFingerprint(request.content, request.accountIdentity)

  // ─── الدرجة ١: بصمة ملف سبق استيراده ───
  let previousBatch = await deps.batches.findByFileHash(fileHash)
  if (!previousBatch) {
    const legacy = await deps.batches.findByFileHash(hashContent(request.content))
    if (legacy) {
      const records = await deps.sources.listByBatch(legacy.id)
      if (records.length && records.every(record => record.accountIdentity === request.accountIdentity)) previousBatch = legacy
    }
  }

  /*
   * الصفوف الجاهزة (مسار الـPDF) بتتخطى قارئ الـCSV وبس — كل اللي بعد
   * كده واحد: منع التكرار والتصنيف والحفظ على مرحلتين. خط موازي للـPDF
   * كان معناه قاعدتين لمنع التكرار، وواحدة هتتأخر عن التانية حتمًا.
   */
  const outcome = request.parsedRows
    ? { schema: request.schema ?? 'alrajhi_pdf', rows: request.parsedRows, errors: [] }
    : parseRows(parseCsv(request.content), request.schema)

  const index = buildDedupeIndex(await loadExisting(deps, request.accountIdentity))
  const catDeps = await buildCategorizeDeps(deps)

  const lines: ImportPreviewLine[] = []
  // الدفعة تُفحص ضد نفسها أيضًا: repeated-import.csv فيه سطران بنفس المرجع
  const seenInBatch = new Map<string, number>()
  /* كل سجل موجود يبتلع صفًا واردًا واحدًا بس — من غير العدّاد ده، كشف فيه
     عمليتين بنفس اليوم والمبلغ ورصيد نهاية اليوم كان بيتحسب مكرر مرتين
     وتضيع عملية حقيقية (اتشاف على بيانات المالك 2026-09-12). */
  const balanceUsed = new Map<string, number>()

  for (const row of outcome.rows) {
    const candidate = { ...toCandidate(row, request.accountIdentity), smsSource: request.sourceType === "sms" }
    let verdict = classifyCandidate(candidate, index, balanceUsed)
    if (verdict.matchedBalanceKey) {
      const key = verdict.matchedBalanceKey
      balanceUsed.set(key, (balanceUsed.get(key) ?? 0) + 1)
    }

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
