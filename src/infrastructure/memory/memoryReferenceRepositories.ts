import type {
  ClassificationRule,
  Category,
  Id,
  Merchant,
  Obligation,
  Person,
  PersonAllocation,
  Settlement,
} from '../../domain/entities/types'
import { normalizeText } from '../../domain/normalize'
import type {
  AllocationRepository,
  CategoryRepository,
  MerchantRepository,
  ObligationRepository,
  PersonRepository,
  RuleRepository,
  SettlementRepository,
} from '../../application/ports/repositories'

/**
 * مستودعات المراجع والأشخاص في الذاكرة.
 * مفصولة عن memoryRepositories.ts لأن حد الملف 300 سطر (CLAUDE.md #7).
 */

const clone = <T>(value: T): T => structuredClone(value)
export class MemoryCategoryRepository implements CategoryRepository {
  private items = new Map<Id, Category>()
  constructor(seed: readonly Category[] = []) {
    for (const c of seed) this.items.set(c.id, clone(c))
  }
  async listAll(): Promise<Category[]> {
    return [...this.items.values()].map(clone)
  }
  async save(category: Category): Promise<void> {
    this.items.set(category.id, clone(category))
  }
}

export class MemoryMerchantRepository implements MerchantRepository {
  private items = new Map<Id, Merchant>()
  constructor(seed: readonly Merchant[] = []) {
    for (const m of seed) this.items.set(m.id, clone(m))
  }
  async listAll(): Promise<Merchant[]> {
    return [...this.items.values()].map(clone)
  }
  async findByNormalizedName(normalizedName: string): Promise<Merchant | null> {
    const key = normalizeText(normalizedName)
    const found = [...this.items.values()].find((m) => m.normalizedName === key)
    return found ? clone(found) : null
  }
  async saveMany(merchants: readonly Merchant[]): Promise<void> {
    for (const m of merchants) this.items.set(m.id, clone(m))
  }
}

export class MemoryRuleRepository implements RuleRepository {
  private items: ClassificationRule[] = []
  constructor(seed: readonly ClassificationRule[] = []) {
    this.items = seed.map(clone)
  }
  async listAll(): Promise<ClassificationRule[]> {
    return this.items.map(clone)
  }
  async saveMany(rules: readonly ClassificationRule[]): Promise<void> {
    this.items = rules.map(clone)
  }
}

export class MemoryPersonRepository implements PersonRepository {
  private items = new Map<Id, Person>()
  constructor(seed: readonly Person[] = []) {
    for (const p of seed) this.items.set(p.id, clone(p))
  }
  async listAll(): Promise<Person[]> {
    return [...this.items.values()].map(clone)
  }
  async save(person: Person): Promise<void> {
    this.items.set(person.id, clone(person))
  }
}

export class MemoryObligationRepository implements ObligationRepository {
  private items = new Map<Id, Obligation>()
  async listByPerson(personId: Id): Promise<Obligation[]> {
    return [...this.items.values()].filter((o) => o.personId === personId).map(clone)
  }
  async listByTransactionIds(ids: readonly Id[]): Promise<Obligation[]> {
    const wanted = new Set(ids)
    return [...this.items.values()].filter((o) => wanted.has(o.originTransactionId)).map(clone)
  }
  async saveMany(obligations: readonly Obligation[]): Promise<void> {
    for (const o of obligations) this.items.set(o.id, clone(o))
  }
  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }
  all(): Obligation[] {
    return [...this.items.values()].map(clone)
  }
}

export class MemorySettlementRepository implements SettlementRepository {
  private items = new Map<Id, Settlement>()
  async listByObligations(obligationIds: readonly Id[]): Promise<Settlement[]> {
    const wanted = new Set(obligationIds)
    return [...this.items.values()].filter((s) => wanted.has(s.obligationId)).map(clone)
  }
  async listByTransactionIds(ids: readonly Id[]): Promise<Settlement[]> {
    const wanted = new Set(ids)
    return [...this.items.values()].filter((s) => wanted.has(s.transactionId)).map(clone)
  }
  async saveMany(settlements: readonly Settlement[]): Promise<void> {
    for (const s of settlements) this.items.set(s.id, clone(s))
  }
  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }
  all(): Settlement[] {
    return [...this.items.values()].map(clone)
  }
}

export class MemoryAllocationRepository implements AllocationRepository {
  private items = new Map<Id, PersonAllocation>()
  async listByTransactionIds(ids: readonly Id[]): Promise<PersonAllocation[]> {
    const wanted = new Set(ids)
    return [...this.items.values()].filter((a) => wanted.has(a.transactionId)).map(clone)
  }
  async saveMany(allocations: readonly PersonAllocation[]): Promise<void> {
    for (const a of allocations) this.items.set(a.id, clone(a))
  }
  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }
  all(): PersonAllocation[] {
    return [...this.items.values()].map(clone)
  }
}

