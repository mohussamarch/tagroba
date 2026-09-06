import { categorize, prepareRules, type CategorizeDeps } from '../../domain/categorize'
import { normalizeText } from '../../domain/normalize'
import type { Id, Transaction } from '../../domain/entities/types'
import type {
  CategoryRepository,
  Clock,
  MerchantRepository,
  RuleRepository,
  TransactionRepository,
  UnitOfWork,
} from '../ports/repositories'

/**
 * CategorizeTransactions — تطبيق ترتيب أولوية التصنيف على مجموعة عمليات.
 *
 * القيد الأهم (spec/05): **لا تستبدل المؤكد عند إعادة الاستيراد.**
 * أي عملية عليها categoryConfirmed = true تُترك كما هي، ويُسجَّل ذلك صراحة.
 */

export interface CategorizationChange {
  transactionId: Id
  fromCategoryId: Id | undefined
  toCategoryId: Id | undefined
  reason: string
  source: string
}

export interface CategorizationReport {
  changed: CategorizationChange[]
  /** عمليات تُركت لأن المستخدم أكّدها — لا يُكتب فوقها. */
  skippedConfirmed: Id[]
  /** عمليات لم يطابقها شيء وتحتاج مراجعة. */
  stillNeedsReview: Id[]
}

export interface CategorizeTransactionsDeps {
  txns: TransactionRepository
  merchants: MerchantRepository
  categories: CategoryRepository
  rules: RuleRepository
  uow: UnitOfWork
  clock: Clock
}

async function loadDeps(deps: CategorizeTransactionsDeps): Promise<CategorizeDeps> {
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

export function makeCategorizeTransactions(deps: CategorizeTransactionsDeps) {
  /** يحسب ما سيتغيّر بلا كتابة — لعرض الأثر قبل التطبيق. */
  async function plan(transactions: readonly Transaction[]): Promise<CategorizationReport> {
    const catDeps = await loadDeps(deps)
    const changed: CategorizationChange[] = []
    const skippedConfirmed: Id[] = []
    const stillNeedsReview: Id[] = []

    for (const txn of transactions) {
      // ─── الأولوية ١: المؤكد لا يُمس ───
      if (txn.categoryConfirmed) {
        skippedConfirmed.push(txn.id)
        continue
      }

      const input: Parameters<typeof categorize>[0] = { currentConfirmed: false }
      if (txn.categoryId) input.currentCategoryId = txn.categoryId
      if (txn.rawMerchantName) input.merchantName = txn.rawMerchantName
      if (txn.rawDescription) input.description = txn.rawDescription
      if (txn.sourceCategory) input.sourceCategory = txn.sourceCategory

      const result = categorize(input, catDeps)

      if (result.categoryId === undefined) {
        stillNeedsReview.push(txn.id)
        continue
      }
      if (result.categoryId !== txn.categoryId) {
        changed.push({
          transactionId: txn.id,
          fromCategoryId: txn.categoryId,
          toCategoryId: result.categoryId,
          reason: result.reason,
          source: result.source,
        })
      }
    }

    return { changed, skippedConfirmed, stillNeedsReview }
  }

  /** يطبّق التصنيف ذريًا. لا يلمس categoryConfirmed. */
  async function apply(transactions: readonly Transaction[]): Promise<CategorizationReport> {
    const report = await plan(transactions)
    if (report.changed.length === 0) return report

    const now = deps.clock.nowIso()
    return deps.uow.run(async () => {
      for (const change of report.changed) {
        const patch: Partial<Transaction> = {
          categoryId: change.toCategoryId,
          reviewState: 'suggested', // مقترح لا مؤكد — spec/04
          updatedAt: now,
        }
        await deps.txns.update(change.transactionId, patch)
      }
      return report
    })
  }

  /**
   * تأكيد المستخدم لتصنيف عملية — يرفع الحماية ضد الكتابة الآلية.
   * spec/05: «تأكيد المستخدم يحمي اختياره من الكتابة الآلية اللاحقة».
   */
  async function confirm(transactionId: Id, categoryId: Id): Promise<void> {
    await deps.txns.update(transactionId, {
      categoryId,
      categoryConfirmed: true,
      reviewState: 'confirmed',
      updatedAt: deps.clock.nowIso(),
    })
  }

  return { plan, apply, confirm }
}
