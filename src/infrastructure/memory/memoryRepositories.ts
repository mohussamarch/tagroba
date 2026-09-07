import type {
  Id,
  ImportBatch,
  SourceRecord,
  Transaction,
} from '../../domain/entities/types'
import type {
  ImportBatchRepository,
  SourceRecordRepository,
  TransactionRepository,
} from '../../application/ports/repositories'

/**
 * مستودعات في الذاكرة — للاختبار من fixtures (CLAUDE.md #6 و #15).
 *
 * تُستخدم لتشغيل كل حالات spec/06 ومعايير OVERRIDES §7
 * **بدون فايربيز إطلاقًا**، وهو ما يجعل معيار القبول قابلًا للتحقق أصلًا.
 *
 * النسخ عند القراءة والكتابة مقصود: يمنع تعديل الحالة من خارج المستودع،
 * فيتصرف كقاعدة بيانات حقيقية لا كمصفوفة مشتركة.
 */

const clone = <T>(value: T): T => structuredClone(value)

/**
 * مستودع يقدر يرجّع حالته لِما كانت عليه.
 * هو ما يجعل MemoryUnitOfWork ذرّية فعليًا لا اسمًا،
 * ويثبت حالة spec/06: «انقطاع أثناء حفظ دفعة ⇒ صفر أو كامل الدفعة».
 */
export interface Snapshotable<S = unknown> {
  snapshot(): S
  restore(state: S): void
}

/** أساس مشترك لمستودعات الذاكرة المفتاحية. */
abstract class KeyedStore<T> implements Snapshotable<Map<Id, T>> {
  protected items = new Map<Id, T>()

  snapshot(): Map<Id, T> {
    // نسخة عميقة: التراجع لازم يرجّع محتوى المستندات لا مفاتيحها فقط
    return new Map([...this.items].map(([k, v]) => [k, clone(v)]))
  }

  restore(state: Map<Id, T>): void {
    this.items = new Map([...state].map(([k, v]) => [k, clone(v)]))
  }
}

export class MemoryTransactionRepository extends KeyedStore<Transaction> implements TransactionRepository {

  async listByDateRange(fromIso: string, toIso: string): Promise<Transaction[]> {
    return [...this.items.values()]
      .filter((t) => t.occurredAt >= fromIso && t.occurredAt <= toIso)
      .sort((a, b) =>
        a.occurredAt === b.occurredAt
          ? a.sourceOrder - b.sourceOrder
          : a.occurredAt < b.occurredAt
            ? -1
            : 1,
      )
      .map(clone)
  }

  async listByBatch(): Promise<Transaction[]> {
    return [] // العلاقة بالدفعة عبر SourceRecord — ليست ملكية مطلقة (spec/03)
  }

  async findByIds(ids: readonly Id[]): Promise<Transaction[]> {
    return ids.map((id) => this.items.get(id)).filter((t): t is Transaction => !!t).map(clone)
  }

  async saveMany(transactions: readonly Transaction[]): Promise<void> {
    for (const t of transactions) this.items.set(t.id, clone(t))
  }

  async update(id: Id, patch: Partial<Transaction>): Promise<void> {
    const existing = this.items.get(id)
    if (!existing) throw new Error(`عملية غير موجودة: ${id}`)
    this.items.set(id, { ...existing, ...clone(patch), id })
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }

  /** للاختبار فقط — عدد ما هو مخزَّن فعلًا. */
  size(): number {
    return this.items.size
  }

  all(): Transaction[] {
    return [...this.items.values()].map(clone)
  }
}

export class MemorySourceRecordRepository extends KeyedStore<SourceRecord> implements SourceRecordRepository {

  async listByBatch(batchId: Id): Promise<SourceRecord[]> {
    return [...this.items.values()].filter((r) => r.batchId === batchId).map(clone)
  }

  async listByAccountIdentity(accountIdentity: string): Promise<SourceRecord[]> {
    return [...this.items.values()]
      .filter((r) => r.accountIdentity === accountIdentity)
      .map(clone)
  }

  async listByTransactionIds(ids: readonly Id[]): Promise<SourceRecord[]> {
    const wanted = new Set(ids)
    return [...this.items.values()]
      .filter((r) => r.transactionId !== null && wanted.has(r.transactionId))
      .map(clone)
  }

  async saveMany(records: readonly SourceRecord[]): Promise<void> {
    for (const r of records) this.items.set(r.id, clone(r))
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }

  all(): SourceRecord[] {
    return [...this.items.values()].map(clone)
  }
}

export class MemoryImportBatchRepository extends KeyedStore<ImportBatch> implements ImportBatchRepository {

  async findById(id: Id): Promise<ImportBatch | null> {
    const found = this.items.get(id)
    return found ? clone(found) : null
  }

  async findByFileHash(fileHash: string): Promise<ImportBatch | null> {
    const found = [...this.items.values()].find(
      (b) => b.fileHash === fileHash && b.state === 'committed',
    )
    return found ? clone(found) : null
  }

  async listRecent(limit: number): Promise<ImportBatch[]> {
    return [...this.items.values()]
      .sort((a, b) => (a.importedAt < b.importedAt ? 1 : -1))
      .slice(0, limit)
      .map(clone)
  }

  async save(batch: ImportBatch): Promise<void> {
    this.items.set(batch.id, clone(batch))
  }

  async updateState(id: Id, state: ImportBatch['state']): Promise<void> {
    const existing = this.items.get(id)
    if (!existing) throw new Error(`دفعة غير موجودة: ${id}`)
    this.items.set(id, { ...existing, state })
  }

  all(): ImportBatch[] {
    return [...this.items.values()].map(clone)
  }
}


export * from './memoryReferenceRepositories'
export * from './memorySupport'
