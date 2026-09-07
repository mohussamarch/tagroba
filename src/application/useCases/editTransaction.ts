import { normalizeText } from '../../domain/normalize'
import type { Id, Tag, Transaction, TransactionTag } from '../../domain/entities/types'
import type {
  CategoryRepository,
  Clock,
  IdGenerator,
  TagRepository,
  TransactionRepository,
  TransactionTagRepository,
  UnitOfWork,
} from '../ports/repositories'

/**
 * EditTransaction — تعديل تفاصيل عملية موجودة.
 *
 * `spec/04`: صفحة العملية بتسمح بتعديل **التصنيف والملاحظة ووسم الكاش
 * والوسوم**. اللي **ما بيتعدلش أبدًا** هنا: المبلغ والتاريخ واتجاه الحركة —
 * دول حقائق من الكشف لا آراء (`spec/02`: «وارد الحساب… حقيقة بنكية»).
 * تعديلهم كان هيكسر مطابقة الرصيد ومنع التكرار مع إعادة الاستيراد.
 *
 * أي تعديل من المستخدم **بيتحسب تأكيدًا**: `categoryConfirmed` بتبقى true
 * فالتصنيف الآلي ما يكتبش فوقه بعدين (`spec/05`).
 */

export class EditTransactionError extends Error {}

/** حد الملاحظة — معلن ومتحقَّق منه، زي باقي الحدود في المشروع. */
export const MAX_NOTE = 1000
export const MAX_TAG_NAME = 40

export interface EditTransactionDeps {
  txns: TransactionRepository
  categories: CategoryRepository
  tags: TagRepository
  transactionTags: TransactionTagRepository
  uow: UnitOfWork
  ids: IdGenerator
  clock: Clock
}

export interface TransactionDetail {
  transaction: Transaction
  tags: Tag[]
}

export function makeEditTransaction(deps: EditTransactionDeps) {
  async function load(transactionId: Id): Promise<TransactionDetail> {
    const [transaction] = await deps.txns.findByIds([transactionId])
    if (!transaction) throw new EditTransactionError('العملية دي مش موجودة')

    const links = await deps.transactionTags.listByTransactionIds([transactionId])
    const all = await deps.tags.listAll()
    const byId = new Map(all.map((t) => [t.id, t]))

    return {
      transaction,
      tags: links.map((l) => byId.get(l.tagId)).filter((t): t is Tag => !!t),
    }
  }

  /**
   * يغيّر التصنيف. `null` بيشيله.
   * التغيير اليدوي بيتأكّد فورًا فالقواعد ما تكتبش فوقه (`spec/05`).
   */
  async function setCategory(transactionId: Id, categoryId: Id | null): Promise<void> {
    const [transaction] = await deps.txns.findByIds([transactionId])
    if (!transaction) throw new EditTransactionError('العملية دي مش موجودة')

    if (categoryId !== null) {
      const categories = await deps.categories.listAll()
      if (!categories.some((c) => c.id === categoryId)) {
        throw new EditTransactionError('التصنيف ده مش موجود')
      }
    }

    await deps.txns.update(transactionId, {
      categoryId: categoryId ?? undefined,
      // اختيار المستخدم قرار مؤكد، وشيل التصنيف قرار كمان
      categoryConfirmed: categoryId !== null,
      reviewState: categoryId !== null ? 'confirmed' : 'needs_review',
      updatedAt: deps.clock.nowIso(),
    })
  }

  /** يكتب ملاحظة أو يشيلها. الفاضي بيشيلها بدل ما يحفظ نصًا فاضيًا. */
  async function setNote(transactionId: Id, note: string): Promise<void> {
    const trimmed = note.trim()
    if (trimmed.length > MAX_NOTE) {
      throw new EditTransactionError(`الملاحظة أطول من ${MAX_NOTE} حرف`)
    }
    await deps.txns.update(transactionId, {
      note: trimmed || undefined,
      updatedAt: deps.clock.nowIso(),
    })
  }

  /**
   * وسم الكاش — `spec/01`: «وسم كاش».
   * ⚠️ ده **مش** بيغيّر المحفظة ولا المبلغ: بيعلّم إن العملية اتدفعت
   * كاش عشان تظهر في تقاريرها، والخصم من المحفظة زي ما هو.
   */
  async function setCashTag(transactionId: Id, isCashTagged: boolean): Promise<void> {
    await deps.txns.update(transactionId, { isCashTagged, updatedAt: deps.clock.nowIso() })
  }

  /** يستبعد العملية من الميزانية أو يرجّعها. الاستبعاد **ما بيلغيش** خصم المحفظة. */
  async function setExcludedFromBudget(transactionId: Id, excluded: boolean): Promise<void> {
    await deps.txns.update(transactionId, {
      excludedFromBudget: excluded,
      updatedAt: deps.clock.nowIso(),
    })
  }

  /**
   * يضيف وسمًا للعملية، وينشئه لو مش موجود.
   *
   * التطبيع (normalize) بيمنع «مطاعم» و«  مطاعم » إنهم يبقوا وسمين.
   * الوسم المكرر على نفس العملية **ما بيتضافش تاني**: هيضاعف الجمع
   * في أي تقرير بيربط الجدولين (`spec/02`).
   */
  async function addTag(transactionId: Id, displayName: string): Promise<Tag> {
    const name = displayName.trim()
    if (!name) throw new EditTransactionError('اكتب اسم الوسم')
    if (name.length > MAX_TAG_NAME) {
      throw new EditTransactionError(`اسم الوسم أطول من ${MAX_TAG_NAME} حرف`)
    }

    const [transaction] = await deps.txns.findByIds([transactionId])
    if (!transaction) throw new EditTransactionError('العملية دي مش موجودة')

    const normalized = normalizeText(name)

    return deps.uow.run(async () => {
      const all = await deps.tags.listAll()
      let tag = all.find((t) => t.normalizedName === normalized)

      if (!tag) {
        tag = { id: deps.ids.next('tag'), normalizedName: normalized, displayName: name }
        await deps.tags.save(tag)
      }

      const links = await deps.transactionTags.listByTransactionIds([transactionId])
      if (links.some((l) => l.tagId === tag!.id)) return tag!

      await deps.transactionTags.saveMany([
        { id: deps.ids.next('ttag'), transactionId, tagId: tag.id },
      ])
      return tag
    })
  }

  /** يشيل وسمًا من عملية. الوسم نفسه بيفضل موجود لباقي العمليات. */
  async function removeTag(transactionId: Id, tagId: Id): Promise<void> {
    const links = await deps.transactionTags.listByTransactionIds([transactionId])
    const target = links.filter((l) => l.tagId === tagId).map((l) => l.id)
    if (target.length > 0) await deps.transactionTags.deleteMany(target)
  }

  /** كل الوسوم المتاحة — للاقتراح في الواجهة. */
  async function listTags(): Promise<Tag[]> {
    return (await deps.tags.listAll()).sort((a, b) =>
      a.displayName.localeCompare(b.displayName, 'ar'),
    )
  }

  /** الوسوم لمجموعة عمليات — استعلام واحد لا واحد لكل عملية. */
  async function tagsFor(transactionIds: readonly Id[]): Promise<Map<Id, Tag[]>> {
    const out = new Map<Id, Tag[]>()
    if (transactionIds.length === 0) return out

    const [links, all] = await Promise.all([
      deps.transactionTags.listByTransactionIds(transactionIds),
      deps.tags.listAll(),
    ])
    const byId = new Map(all.map((t) => [t.id, t]))

    for (const link of links) {
      const tag = byId.get(link.tagId)
      if (!tag) continue
      const list = out.get(link.transactionId)
      if (list) list.push(tag)
      else out.set(link.transactionId, [tag])
    }
    return out
  }

  return {
    load,
    setCategory,
    setNote,
    setCashTag,
    setExcludedFromBudget,
    addTag,
    removeTag,
    listTags,
    tagsFor,
  }
}

export type { TransactionTag }
