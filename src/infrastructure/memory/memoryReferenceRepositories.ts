import type {
  ClassificationRule,
  Category,
  Id,
  Merchant,
  Obligation,
  PersonAllocation,
  Settlement,
} from '../../domain/entities/types'
import { normalizeText } from '../../domain/normalize'
import type {
  AllocationRepository,
  CategoryRepository,
  MerchantRepository,
  ObligationRepository,
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
  snapshot(){return clone(this.items)}
  restore(state:Map<Id,Category>){this.items=clone(state)}
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
  snapshot(){return clone(this.items)}
  restore(state:Map<Id,Merchant>){this.items=clone(state)}
  constructor(seed: readonly Merchant[] = []) {
    for (const m of seed) this.items.set(m.id, clone(m))
  }
  async listAll(): Promise<Merchant[]> {
    return [...this.items.values()].map(clone)
  }
  async findByNormalizedName(normalizedName: string): Promise<Merchant | null> {
    const key = normalizeText(normalizedName)
    const all=[...this.items.values()]
    const found = all.find(m=>m.normalizedName===key) ?? all.find(m=>m.aliases?.includes(key))
    return found ? clone(found) : null
  }
  async saveMany(merchants: readonly Merchant[]): Promise<void> {
    for (const m of merchants) this.items.set(m.id, clone(m))
  }
}

/**
 * ⚠️ كان هنا تعارض حقيقي بين التنفيذين، كشفه اختبار إدارة القواعد:
 * نسخة الذاكرة كانت **بتستبدل القايمة كلها** ونسخة Firestore
 * **بتحدّث بالمعرّف**. يعني حفظ قاعدة واحدة كان بيمسح الباقي في
 * الاختبار وما بيمسحهمش في التشغيل — أسوأ نوع اختلاف: بيخفي الخلل
 * لحد ما يظهر عند المستخدم.
 *
 * العقد المعتمد هو **التحديث بالمعرّف** (upsert)، زي كل المستودعات.
 */
export class MemoryRuleRepository implements RuleRepository {
  private items = new Map<Id, ClassificationRule>()
  snapshot(){return clone(this.items)}
  restore(state:Map<Id,ClassificationRule>){this.items=clone(state)}
  constructor(seed: readonly ClassificationRule[] = []) {
    for (const r of seed) this.items.set(r.id, clone(r))
  }
  async listAll(): Promise<ClassificationRule[]> {
    // نفس ترتيب Firestore: الأولوية الأصغر أولًا
    return [...this.items.values()].map(clone).sort((a, b) => a.priority - b.priority)
  }
  async saveMany(rules: readonly ClassificationRule[]): Promise<void> {
    for (const r of rules) this.items.set(r.id, clone(r))
  }
  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }
}

export class MemoryObligationRepository implements ObligationRepository {
  private items = new Map<Id, Obligation>()
  snapshot(){return clone(this.items)}
  restore(state:Map<Id,Obligation>){this.items=clone(state)}
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
  snapshot(){return clone(this.items)}
  restore(state:Map<Id,Settlement>){this.items=clone(state)}
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
  snapshot(){return clone(this.items)}
  restore(state:Map<Id,PersonAllocation>){this.items=clone(state)}
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
