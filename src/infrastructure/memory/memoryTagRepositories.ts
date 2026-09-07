import type { Id, Tag, TransactionTag } from '../../domain/entities/types'
import type {
  TagRepository,
  TransactionTagRepository,
} from '../../application/ports/repositories'
import type { Snapshotable } from './memoryRepositories'

const clone = <T>(value: T): T => structuredClone(value)

/** الوسوم في الذاكرة — للاختبار بلا فايربيز (CLAUDE.md #6). */
export class MemoryTagRepository implements TagRepository, Snapshotable<Tag[]> {
  private items = new Map<Id, Tag>()

  constructor(seed: readonly Tag[] = []) {
    for (const t of seed) this.items.set(t.id, clone(t))
  }

  async listAll(): Promise<Tag[]> {
    return [...this.items.values()].map(clone)
  }

  async save(tag: Tag): Promise<void> {
    this.items.set(tag.id, clone(tag))
  }

  async remove(id: Id): Promise<void> {
    this.items.delete(id)
  }

  snapshot(): Tag[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: Tag[]): void {
    this.items = new Map(state.map((t) => [t.id, clone(t)]))
  }
}

export class MemoryTransactionTagRepository
  implements TransactionTagRepository, Snapshotable<TransactionTag[]>
{
  private items = new Map<Id, TransactionTag>()

  constructor(seed: readonly TransactionTag[] = []) {
    for (const l of seed) this.items.set(l.id, clone(l))
  }

  async listByTransactionIds(ids: readonly Id[]): Promise<TransactionTag[]> {
    const wanted = new Set(ids)
    return [...this.items.values()].filter((l) => wanted.has(l.transactionId)).map(clone)
  }

  async listByTag(tagId: Id): Promise<TransactionTag[]> {
    return [...this.items.values()].filter((l) => l.tagId === tagId).map(clone)
  }

  async saveMany(links: readonly TransactionTag[]): Promise<void> {
    for (const l of links) this.items.set(l.id, clone(l))
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }

  snapshot(): TransactionTag[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: TransactionTag[]): void {
    this.items = new Map(state.map((l) => [l.id, clone(l)]))
  }
}
