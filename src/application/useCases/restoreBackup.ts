import { checkBackup, type BackupFile } from './exportBackup'
import { foreignCurrencies, mergeById, mergeTransactions } from '../../domain/mergeBackup'
import type {
  Budget,
  Category,
  CategoryBudget,
  ClassificationRule,
  Merchant,
  Transaction,
  Wallet,
} from '../../domain/entities/types'
import type {
  BudgetRepository,
  CategoryRepository,
  MerchantRepository,
  RuleRepository,
  TransactionRepository,
  UnitOfWork,
  WalletRepository,
} from '../ports/repositories'

/**
 * RestoreBackup — استعادة نسخة احتياطية **بالدمج**.
 *
 * قرار المالك (2026-09-07): الدمج لا الاستبدال ولا الرفض —
 * `OVERRIDES §12`. قواعد الدمج نفسها في `domain/mergeBackup.ts` النقي.
 *
 * الترتيب مقصود: **المعاينة قبل الكتابة، دايمًا**. المستخدم بيشوف
 * هيتضاف كام وهيتخطى كام و**ليه** قبل ما يتكتب حرف واحد.
 * `spec/05`: «لا استبدال صامت».
 */

export class RestoreError extends Error {}

export interface RestorePlan {
  file: BackupFile
  /** كل مجموعة: هيتضاف كام، وهيتخطى كام، وسبب التخطي. */
  lines: {
    key: string
    label: string
    incoming: number
    toAdd: number
    skipped: number
    note: string | null
  }[]
  /** تحذيرات لازم تتعرض قبل التأكيد — مش بتمنع، بتخلي القرار مبنيًا على علم. */
  warnings: string[]
  totalToAdd: number
}

export interface RestoreOutcome {
  added: Record<string, number>
  totalAdded: number
}

export interface RestoreDeps {
  txns: TransactionRepository
  wallets: WalletRepository
  categories: CategoryRepository
  rules: RuleRepository
  merchants: MerchantRepository
  budgets: BudgetRepository
  uow: UnitOfWork
}

const LABELS: Record<string, string> = {
  wallets: 'المحافظ',
  categories: 'التصنيفات',
  rules: 'القواعد',
  merchants: 'التجار',
  transactions: 'العمليات',
  budgets: 'الميزانيات',
  categoryBudgets: 'سقوف التصنيفات',
}

export function makeRestoreBackup(deps: RestoreDeps) {
  /**
   * يقرأ الموجود حاليًا — أساس المقارنة.
   *
   * العمليات بتتقرا **في نافذة تواريخ النسخة بس** لا كلها:
   * عملية برّه النافذة دي مستحيل تكون تكرارًا لحاجة جواها، والقراءة
   * الكاملة كانت هتبقى استعلامًا بلا حد (ARCHITECTURE §5.6) —
   * وكمان `TransactionRepository` مالهاش `listAll` أصلًا بسبب قاعدة
   * «لا فهارس مركّبة» (§12).
   */
  async function readExisting(incoming: readonly Transaction[]) {
    const [wallets, categories, rules, merchants] = await Promise.all([
      deps.wallets.listAll(),
      deps.categories.listAll(),
      deps.rules.listAll(),
      deps.merchants.listAll(),
    ])

    let transactions: Transaction[] = []
    if (incoming.length > 0) {
      const dates = incoming.map((t) => t.occurredAt).sort()
      transactions = await deps.txns.listByDateRange(dates[0], dates[dates.length - 1])
    }

    return { wallets, categories, rules, merchants, transactions }
  }

  /**
   * يبني خطة الاستعادة **بلا أي كتابة**.
   * الفشل في التحقق بيرمي بسببه، فالنسخة الناقصة ما بتوصلش للكتابة أصلًا.
   */
  async function plan(raw: string): Promise<RestorePlan> {
    const check = checkBackup(raw)
    if (!check.valid || !check.file) {
      throw new RestoreError(`النسخة دي مش سليمة:\n• ${check.problems.join('\n• ')}`)
    }

    const file = check.file
    const existing = await readExisting(file.data.transactions ?? [])
    const warnings: string[] = []

    const currencies = foreignCurrencies(file.data.transactions ?? [], existing.transactions)
    if (currencies.length > 0) {
      warnings.push(
        `النسخة فيها عمليات بعملة (${currencies.join('، ')}) مش موجودة عندك. ` +
          `المبالغ **مش هتتحوّل** — كل عملية بتفضل بعملتها.`,
      )
    }

    const txnMerge = mergeTransactions(file.data.transactions ?? [], existing.transactions)
    const lines: RestorePlan['lines'] = [
      {
        key: 'transactions',
        label: LABELS.transactions,
        incoming: (file.data.transactions ?? []).length,
        toAdd: txnMerge.addedCount,
        skipped: txnMerge.skippedSameId + txnMerge.skippedSameContent,
        note:
          txnMerge.skippedSameContent > 0
            ? `${txnMerge.skippedSameContent} منهم بمعرّف مختلف بس نفس التاريخ والمبلغ — ` +
              `اتخطوا عشان المصروف ما يتضاعفش`
            : null,
      },
    ]

    for (const [key, incoming, current] of [
      ['wallets', file.data.wallets ?? [], existing.wallets],
      ['categories', file.data.categories ?? [], existing.categories],
      ['rules', file.data.rules ?? [], existing.rules],
      ['merchants', file.data.merchants ?? [], existing.merchants],
    ] as const) {
      const merged = mergeById(incoming as { id: string }[], current as { id: string }[])
      lines.push({
        key,
        label: LABELS[key],
        incoming: incoming.length,
        toAdd: merged.addedCount,
        skipped: merged.skippedSameId,
        note: null,
      })
    }

    const totalToAdd = lines.reduce((sum, l) => sum + l.toAdd, 0)
    if (totalToAdd === 0) {
      warnings.push('مفيش حاجة جديدة في النسخة دي — كل اللي فيها موجود عندك بالفعل.')
    }

    return { file, lines, warnings, totalToAdd }
  }

  /**
   * ينفّذ الخطة. **بيعيد حساب الدمج على الموجود دلوقتي** لا على وقت
   * المعاينة: لو التطبيق اتفتح على جهازين، الموجود ممكن يكون اتغيّر
   * بين المعاينة والتأكيد، والكتابة على خطة قديمة كانت هتكرّر عمليات.
   */
  async function apply(file: BackupFile): Promise<RestoreOutcome> {
    const existing = await readExisting(file.data.transactions ?? [])
    const added: Record<string, number> = {}

    return deps.uow.run(async () => {
      const wallets = mergeById<Wallet>(file.data.wallets ?? [], existing.wallets)
      for (const w of wallets.toAdd) await deps.wallets.save(w)
      added.wallets = wallets.addedCount

      const categories = mergeById<Category>(file.data.categories ?? [], existing.categories)
      // المستودع بيحفظ واحدًا واحدًا — مفيش saveMany للتصنيفات
      for (const c of categories.toAdd) await deps.categories.save(c)
      added.categories = categories.addedCount

      const rules = mergeById<ClassificationRule>(file.data.rules ?? [], existing.rules)
      if (rules.toAdd.length > 0) await deps.rules.saveMany(rules.toAdd)
      added.rules = rules.addedCount

      const merchants = mergeById<Merchant>(file.data.merchants ?? [], existing.merchants)
      if (merchants.toAdd.length > 0) await deps.merchants.saveMany(merchants.toAdd)
      added.merchants = merchants.addedCount

      /*
       * العمليات آخر حاجة عن قصد: لو حاجة وقعت قبلها، بنبقى ضايفين
       * مراجع بلا عمليات — ناقص لكنه غير ضار. العكس (عمليات بلا
       * تصنيفاتها) كان بيدي عمليات معلّقة على تصنيف مش موجود.
       */
      const txns = mergeTransactions(file.data.transactions ?? [], existing.transactions)
      if (txns.toAdd.length > 0) await deps.txns.saveMany(txns.toAdd)
      added.transactions = txns.addedCount

      const budgets = mergeById<Budget>(file.data.budgets ?? [], [])
      for (const b of budgets.toAdd) {
        // الميزانية الموجودة لفترة ما بتتدهسش
        if (!(await deps.budgets.findByPeriod(b.periodKey))) await deps.budgets.save(b)
      }
      added.budgets = budgets.addedCount

      const lines = mergeById<CategoryBudget>(file.data.categoryBudgets ?? [], [])
      for (const line of lines.toAdd) await deps.budgets.saveCategoryBudget(line)
      added.categoryBudgets = lines.addedCount

      return { added, totalAdded: Object.values(added).reduce((a, b) => a + b, 0) }
    })
  }

  return { plan, apply }
}

export type { Transaction }
