import { suggestEconomicKind, isBulkConfirmable, type KindSuggestion } from '../../domain/suggestEconomicKind'
import { isConsistentWithObservedDirection, type EconomicKind } from '../../domain/entities/economicKind'
import type { Id, Transaction } from '../../domain/entities/types'
import type {
  CategoryRepository,
  Clock,
  TransactionRepository,
  UnitOfWork,
} from '../ports/repositories'

/**
 * SetEconomicKind — تحديد النوع الاقتصادي للعمليات.
 *
 * spec/02: «التصنيف الآلي يقترح، و**تأكيد المستخدم** يحمي اختياره من
 *           الكتابة الآلية اللاحقة.»
 *
 * لذلك لا توجد هنا دالة «طبّق الاقتراحات تلقائيًا». الاقتراح يُعرض،
 * والمستخدم يوافق — إما فرديًا أو جماعيًا على مجموعة يراها.
 */

export interface SuggestionLine {
  transaction: Transaction
  suggestion: KindSuggestion
}

export interface SuggestionSummary {
  /** اقتراحات قاطعة قابلة للتأكيد الجماعي. */
  confirmable: SuggestionLine[]
  /** اقتراحات مرجّحة تحتاج نظرة قبل التأكيد. */
  needsLook: SuggestionLine[]
  /** غامضة: بلا اقتراح، قرار فردي مطلوب. */
  ambiguous: SuggestionLine[]
  /** محدَّدة بالفعل ولا تُمس. */
  alreadySet: number
}

export interface SetEconomicKindDeps {
  txns: TransactionRepository
  /** لترجمة categoryId إلى اسم يفهمه محرك الاقتراح. */
  categories: CategoryRepository
  uow: UnitOfWork
  clock: Clock
}

export class EconomicKindError extends Error {}

export function makeSetEconomicKind(deps: SetEconomicKindDeps) {
  /** يبني الاقتراحات مجمّعة حسب قوتها. **لا يكتب شيئًا.** */
  async function summarize(transactions: readonly Transaction[]): Promise<SuggestionSummary> {
    const categoryNameById = new Map((await deps.categories.listAll()).map((c) => [c.id, c.name]))

    const confirmable: SuggestionLine[] = []
    const needsLook: SuggestionLine[] = []
    const ambiguous: SuggestionLine[] = []
    let alreadySet = 0

    for (const transaction of transactions) {
      if (transaction.economicKindConfirmed) {
        alreadySet++
        continue
      }

      const suggestion = suggestEconomicKind({
        direction: transaction.observedDirection,
        ...(transaction.sourceCategory !== undefined
          ? { sourceCategory: transaction.sourceCategory }
          : {}),
        // ما يعرفه التطبيق عن التصنيف دليل أيضًا، لا يُهدر
        ...(transaction.categoryId && categoryNameById.has(transaction.categoryId)
          ? { categoryName: categoryNameById.get(transaction.categoryId)! }
          : {}),
        ...(transaction.sourceOperationType !== undefined
          ? { sourceOperationType: transaction.sourceOperationType }
          : {}),
        ...(transaction.rawMerchantName !== undefined
          ? { merchantName: transaction.rawMerchantName }
          : {}),
        ...(transaction.rawDescription !== undefined
          ? { description: transaction.rawDescription }
          : {}),
      })

      const line = { transaction, suggestion }
      if (isBulkConfirmable(suggestion)) confirmable.push(line)
      else if (suggestion.kind !== null) needsLook.push(line)
      else ambiguous.push(line)
    }

    return { confirmable, needsLook, ambiguous, alreadySet }
  }

  /**
   * يحدّد نوع عملية واحدة بقرار صريح من المستخدم.
   *
   * يتحقق من اتساق النوع مع اتجاه السيولة الملاحظ، ويرفض التناقض الصريح:
   * لا يجوز وسم حركة صادرة كـ«مرتب» مثلًا.
   */
  async function setOne(transactionId: Id, kind: EconomicKind): Promise<void> {
    const [transaction] = await deps.txns.findByIds([transactionId])
    if (!transaction) throw new EconomicKindError(`عملية غير موجودة: ${transactionId}`)

    if (!isConsistentWithObservedDirection(kind, transaction.observedDirection)) {
      const observed = transaction.observedDirection === 'in' ? 'وارد' : 'صادر'
      throw new EconomicKindError(
        `النوع ده ما يتوافقش مع اتجاه الحركة. الكشف بيقول العملية دي «${observed}».`,
      )
    }

    await deps.txns.update(transactionId, {
      economicKind: kind,
      economicKindConfirmed: true,
      updatedAt: deps.clock.nowIso(),
    })
  }

  /**
   * تأكيد جماعي لاقتراحات قاطعة **اختارها المستخدم صراحةً**.
   *
   * `lineIds` تأتي من الشاشة بعد أن يرى المستخدم القائمة ويوافق.
   * لا تُستدعى تلقائيًا في أي مكان.
   */
  async function confirmBulk(
    transactions: readonly Transaction[],
    selectedIds: readonly Id[],
  ): Promise<{ applied: number; skipped: number }> {
    const wanted = new Set(selectedIds)
    const summary = await summarize(transactions)

    // التأكيد الجماعي مقصور على القاطع — الغامض لا يُؤكَّد جماعيًا أبدًا
    const eligible = summary.confirmable.filter((l) => wanted.has(l.transaction.id))
    const skipped = wanted.size - eligible.length

    if (eligible.length === 0) return { applied: 0, skipped }

    const now = deps.clock.nowIso()
    return deps.uow.run(async () => {
      for (const line of eligible) {
        await deps.txns.update(line.transaction.id, {
          economicKind: line.suggestion.kind!,
          economicKindConfirmed: true,
          updatedAt: now,
        })
      }
      return { applied: eligible.length, skipped }
    })
  }

  return { summarize, setOne, confirmBulk }
}
