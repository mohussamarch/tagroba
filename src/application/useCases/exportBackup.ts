import { formatAmount } from '../../domain/formatMoney'
import { buildPeriod, periodForDate, type Period } from '../../domain/period'
import type {
  Budget,
  Category,
  CategoryBudget,
  ClassificationRule,
  ImportBatch,
  Merchant,
  SourceRecord,
  Transaction,
  Wallet,
} from '../../domain/entities/types'
import type {
  BudgetRepository,
  CategoryRepository,
  ImportBatchRepository,
  MerchantRepository,
  RuleRepository,
  SourceRecordRepository,
  TransactionRepository,
  WalletRepository,
} from '../ports/repositories'

/**
 * ExportBackup — التصدير والنسخة الاحتياطية.
 *
 * OVERRIDES §2: «التصدير والنسخ الاحتياطي المحلي **يبقيان مطلبين**
 *                كما في الحزمة، **ولا يُلغيهما وجود السحابة**.»
 *
 * spec/03: «النسخة الاحتياطية لها **schemaVersion** وتحقق سلامة،
 *           وتحفظ المعرفات وعلاقات المصادر والتسويات.»
 */

/** يُرفع عند أي تغيير غير متوافق في شكل النسخة. */
export const BACKUP_SCHEMA_VERSION = 1

export interface BackupFile {
  schemaVersion: number
  app: 'masroufy'
  exportedAt: string
  /** عدد العناصر لكل مجموعة — تحقق سلامة سريع عند الاستعادة. */
  counts: Record<string, number>
  data: {
    wallets: Wallet[]
    categories: Category[]
    rules: ClassificationRule[]
    merchants: Merchant[]
    transactions: Transaction[]
    sourceRecords: SourceRecord[]
    importBatches: ImportBatch[]
    budgets: Budget[]
    categoryBudgets: CategoryBudget[]
  }
}

export interface ExportDeps {
  txns: TransactionRepository
  sources: SourceRecordRepository
  batches: ImportBatchRepository
  wallets: WalletRepository
  categories: CategoryRepository
  rules: RuleRepository
  merchants: MerchantRepository
  budgets: BudgetRepository
}

const MAX_PERIODS = 60

function nextPeriod(period: Period, payday: number): Period {
  const [year, month] = period.key.split('-').map(Number)
  const total = year * 12 + (month - 1) + 1
  return buildPeriod(Math.floor(total / 12), (total % 12) + 1, payday)
}

export function makeExportBackup(deps: ExportDeps) {
  /** يقرأ كل العمليات فترةً فترة — كل استعلام يبقى محدودًا (§5.6). */
  async function readAllTransactions(from: string, to: string, payday: number) {
    const out: Transaction[] = []
    /*
     * ⚠️ **الفترة التي تحوي `from` لا الفترة التي تبدأ في شهره.**
     * الشهر المالي يبدأ يوم الراتب (28)، فيوم 2025-01-01 يقع في فترة
     * ديسمبر لا يناير. بناء الفترة من الشهر مباشرة كان يتخطى كل عملية
     * قبل يوم 28 فيخرج التصدير فارغًا — خلل اكتُشف بفشل اختبار.
     */
    let period = periodForDate(from, payday)
    let guard = 0
    while (period.start <= to && guard < MAX_PERIODS) {
      out.push(...(await deps.txns.listByDateRange(period.start, period.end)))
      guard++
      if (period.end >= to) break
      period = nextPeriod(period, payday)
    }
    // إزالة أي تكرار ناتج عن تداخل حدود الفترات
    return [...new Map(out.map((t) => [t.id, t])).values()]
  }

  /** نسخة احتياطية كاملة بصيغة JSON. */
  async function backup(options: {
    from: string
    to: string
    payday: number
    exportedAt: string
  }): Promise<BackupFile> {
    const transactions = await readAllTransactions(options.from, options.to, options.payday)
    const [wallets, categories, rules, merchants, batches] = await Promise.all([
      deps.wallets.listAll(),
      deps.categories.listAll(),
      deps.rules.listAll(),
      deps.merchants.listAll(),
      deps.batches.listRecent(200),
    ])

    const sourceRecords: SourceRecord[] = []
    for (const batch of batches) sourceRecords.push(...(await deps.sources.listByBatch(batch.id)))

    const budgets: Budget[] = []
    const categoryBudgets: CategoryBudget[] = []
    const periodKeys = new Set(transactions.map((t) => t.occurredAt.slice(0, 7)))
    for (const key of periodKeys) {
      const budget = await deps.budgets.findByPeriod(key)
      if (!budget) continue
      budgets.push(budget)
      categoryBudgets.push(...(await deps.budgets.listCategoryBudgets(budget.id)))
    }

    const data = {
      wallets,
      categories,
      rules,
      merchants,
      transactions,
      sourceRecords,
      importBatches: batches,
      budgets,
      categoryBudgets,
    }

    return {
      schemaVersion: BACKUP_SCHEMA_VERSION,
      app: 'masroufy',
      exportedAt: options.exportedAt,
      counts: Object.fromEntries(
        Object.entries(data).map(([key, value]) => [key, value.length]),
      ),
      data,
    }
  }

  /**
   * تصدير العمليات كـ CSV بمخطط المعاينة — قابل لإعادة الاستيراد.
   *
   * المبالغ تُكتب بالريال نصًا عبر `formatAmount` (المحوِّل الوحيد)،
   * والاقتباس يُهرَّب فلا يكسر الفاصلة اسمًا فيه فاصلة.
   */
  async function exportCsv(options: {
    from: string
    to: string
    payday: number
  }): Promise<string> {
    const transactions = await readAllTransactions(options.from, options.to, options.payday)
    transactions.sort((a, b) =>
      a.occurredAt === b.occurredAt
        ? a.sourceOrder - b.sourceOrder
        : a.occurredAt < b.occurredAt
          ? -1
          : 1,
    )

    const escape = (value: string): string =>
      /[",\n\r]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value

    const lines = ['date,name,amount,type,source,reference']
    for (const t of transactions) {
      lines.push(
        [
          t.occurredAt,
          escape(t.rawMerchantName || t.rawDescription || ''),
          formatAmount(t.amountMinor, t.currency, { grouping: false }),
          t.observedDirection === 'in' ? 'income' : 'expense',
          escape(t.walletId ?? ''),
          escape(t.id),
        ].join(','),
      )
    }
    // BOM ليفتح Excel العربية صحيحة
    return '﻿' + lines.join('\n') + '\n'
  }

  return { backup, exportCsv }
}

/* ───────────────────────── التحقق من النسخة ───────────────────────── */

export interface BackupCheck {
  valid: boolean
  problems: string[]
  file: BackupFile | null
}

/**
 * يتحقق من سلامة نسخة قبل استعادتها.
 *
 * spec/03: «الاستعادة **لا تكرر العمليات أو تخلط العملات**.»
 * فالتحقق يسبق أي كتابة، ويرفض بتفسير بدل أن يكتب نصف نسخة.
 */
export function checkBackup(raw: string): BackupCheck {
  const problems: string[] = []
  let parsed: unknown

  try {
    parsed = JSON.parse(raw)
  } catch {
    return { valid: false, problems: ['الملف مش JSON صالح'], file: null }
  }

  if (typeof parsed !== 'object' || parsed === null) {
    return { valid: false, problems: ['الملف مش على شكل نسخة احتياطية'], file: null }
  }

  const file = parsed as Partial<BackupFile>

  if (file.app !== 'masroufy') problems.push('الملف ده مش نسخة من مصروفي')
  if (typeof file.schemaVersion !== 'number') problems.push('مفيش رقم إصدار للنسخة')
  else if (file.schemaVersion > BACKUP_SCHEMA_VERSION) {
    problems.push(
      `النسخة دي من إصدار أحدث (${file.schemaVersion}) من اللي التطبيق يعرفه ` +
        `(${BACKUP_SCHEMA_VERSION}). حدّث التطبيق الأول.`,
    )
  }
  if (!file.data || typeof file.data !== 'object') problems.push('مفيش بيانات في الملف')

  if (problems.length === 0 && file.counts && file.data) {
    // تحقق السلامة: العدد المعلن يطابق الموجود فعلًا
    for (const [key, declared] of Object.entries(file.counts)) {
      const actual = (file.data as Record<string, unknown[]>)[key]?.length ?? 0
      if (actual !== declared) {
        problems.push(`«${key}»: الملف بيقول ${declared} والموجود ${actual} — الملف ناقص أو متعدّل`)
      }
    }

    // معرّفات مكررة تكسر «الاستعادة لا تكرر العمليات»
    const ids = (file.data.transactions ?? []).map((t) => t.id)
    if (new Set(ids).size !== ids.length) problems.push('فيه عمليات بمعرّفات مكررة في الملف')
  }

  return {
    valid: problems.length === 0,
    problems,
    file: problems.length === 0 ? (file as BackupFile) : null,
  }
}
