import {
  collection,
  doc,
  getDocs,
  limit as fbLimit,
  query,
  setDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore'
import type { Category, ClassificationRule, Merchant } from '../../domain/entities/types'
import type {
  CategoryRepository,
  MerchantRepository,
  RuleRepository,
} from '../../application/ports/repositories'
import { normalizeText } from '../../domain/normalize'

/**
 * مستودعات المراجع على Firestore — التصنيفات والقواعد والتجار.
 *
 * تحل قيد ARCHITECTURE.md §10.6: بدونها تُبنى المراجع في الذاكرة كل تشغيل،
 * فيضيع أي تعديل يعمله المستخدم، رغم أن spec/05 ينص على أنها «قابلة للتحرير».
 *
 * ⚠️ **ملاحظة تكلفة (ARCHITECTURE.md §5.6):** قائمة التجار في المرجع الأولي
 * فيها آلاف الأسماء. قراءتها كاملة في كل تشغيل تستهلك من حد الـ50,000 قراءة.
 * لذلك `MerchantRepository.findByNormalizedName` هنا **استعلام محدود بواحد**،
 * و`listAll` مخصص للزرع والإدارة لا للتصنيف اللحظي.
 */

const BATCH_LIMIT = 500

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

/** يزيل الحقول غير المعرّفة — Firestore يرفض undefined. */
function clean<T extends object>(value: T): T {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(value)) if (v !== undefined) out[k] = v
  return out as T
}

export class FirestoreCategoryRepository implements CategoryRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<Category[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'categories')))
    return snap.docs.map((d) => d.data() as Category).sort((a, b) => a.order - b.order)
  }

  async save(category: Category): Promise<void> {
    await setDoc(
      doc(this.db, userPath(this.uid, 'categories', category.id)),
      clean(category),
    )
  }
}

export class FirestoreRuleRepository implements RuleRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<ClassificationRule[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'rules')))
    return snap.docs
      .map((d) => d.data() as ClassificationRule)
      .sort((a, b) => a.priority - b.priority)
  }

  async saveMany(rules: readonly ClassificationRule[]): Promise<void> {
    for (let i = 0; i < rules.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const rule of rules.slice(i, i + BATCH_LIMIT)) {
        batch.set(doc(this.db, userPath(this.uid, 'rules', rule.id)), clean(rule))
      }
      await batch.commit()
    }
  }

  async deleteMany(ids: readonly string[]): Promise<void> {
    for (let i = 0; i < ids.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const id of ids.slice(i, i + BATCH_LIMIT)) {
        batch.delete(doc(this.db, userPath(this.uid, 'rules', id)))
      }
      await batch.commit()
    }
  }
}

export class FirestoreMerchantRepository implements MerchantRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private col() {
    return collection(this.db, userPath(this.uid, 'merchants'))
  }

  /**
   * ⚠️ قراءة كاملة — للزرع والإدارة فقط، لا للتصنيف اللحظي.
   * التصنيف يستعمل findByNormalizedName المحدود.
   */
  async listAll(): Promise<Merchant[]> {
    const snap = await getDocs(this.col())
    return snap.docs.map((d) => d.data() as Merchant)
  }

  async findByNormalizedName(name: string): Promise<Merchant | null> {
    const snap = await getDocs(
      query(this.col(), where('normalizedName', '==', normalizeText(name)), fbLimit(1)),
    )
    return snap.empty ? null : (snap.docs[0].data() as Merchant)
  }

  async saveMany(merchants: readonly Merchant[]): Promise<void> {
    for (let i = 0; i < merchants.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const merchant of merchants.slice(i, i + BATCH_LIMIT)) {
        batch.set(doc(this.db, userPath(this.uid, 'merchants', merchant.id)), clean(merchant))
      }
      await batch.commit()
    }
  }
}
