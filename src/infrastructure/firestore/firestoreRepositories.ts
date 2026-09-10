import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit as fbLimit,
  orderBy,
  query,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore'
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
  UnitOfWork,
} from '../../application/ports/repositories'

/**
 * تنفيذ المستودعات على Firestore — كل بيانات المستخدم تحت users/{uid}.
 *
 * قيود ARCHITECTURE.md §5.6 مطبَّقة هنا:
 * - **كل استعلام محدود بفترة أو بمعرّفات.** لا توجد قراءة لكل العمليات
 * - القراءة تستفيد من الكاش المحلي المفعّل في firebase.ts
 *
 * قيد OVERRIDES §2: **لا تُخزَّن أرقام حسابات كاملة.**
 * `sanitizeAccountIdentity` يقص أي رقم طويل لآخر أربعة قبل الكتابة.
 */

/** حد Firestore: 500 عملية في الدفعة الواحدة. */
const BATCH_LIMIT = 500

/**
 * يحمي من تسريب رقم حساب كامل إلى فايربيز.
 * أي تتابع من 5 أرقام فأكثر يُقص إلى آخر أربعة مسبوقة بنجوم.
 */
export function sanitizeAccountNumbers(text: string): string {
  return text.replace(/\d{5,}/g, (match) => `****${match.slice(-4)}`)
}

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

export class FirestoreTransactionRepository implements TransactionRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private col() {
    return collection(this.db, userPath(this.uid, 'transactions'))
  }

  /**
   * عمليات فترة واحدة.
   *
   * ⚠️ **لا يوجد orderBy على sourceOrder هنا عمدًا.**
   * الترتيب بحقلين مع مدى (range) يفرض على Firestore **فهرسًا مركّبًا**
   * (composite index) لا يُنشأ تلقائيًا، فيفشل الاستعلام على أي مشروع
   * جديد حتى يُنشر الفهرس يدويًا — وهو عبء إعداد بلا فائدة هنا.
   *
   * المدى وحده يكفيه الفهرس أحادي الحقل الذي ينشئه Firestore تلقائيًا،
   * والترتيب داخل الفترة يتم في الذاكرة: الفترة الواحدة مئات العمليات
   * لا آلاف، وقيد «كل استعلام محدود بفترة» (§5.6) هو ما يضمن ذلك.
   */
  async listByDateRange(fromIso: string, toIso: string): Promise<Transaction[]> {
    const q = query(
      this.col(),
      where('occurredAt', '>=', fromIso),
      where('occurredAt', '<=', toIso),
    )
    const snap = await getDocs(q)
    return snap.docs
      .map((d) => d.data() as Transaction)
      .sort((a, b) =>
        a.occurredAt === b.occurredAt
          ? a.sourceOrder - b.sourceOrder
          : a.occurredAt < b.occurredAt
            ? -1
            : 1,
      )
  }

  async listByBatch(): Promise<Transaction[]> {
    // العلاقة بالدفعة عبر SourceRecord — batchId ليس ملكية مطلقة (spec/03)
    return []
  }

  async findByIds(ids: readonly Id[]): Promise<Transaction[]> {
    if (ids.length === 0) return []
    const out: Transaction[] = []
    // Firestore يحد استعلام `in` بـ 30 قيمة
    for (let i = 0; i < ids.length; i += 30) {
      const chunk = ids.slice(i, i + 30)
      const snap = await getDocs(query(this.col(), where('id', 'in', chunk)))
      out.push(...snap.docs.map((d) => d.data() as Transaction))
    }
    return out
  }

  async saveMany(transactions: readonly Transaction[]): Promise<void> {
    for (let i = 0; i < transactions.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const t of transactions.slice(i, i + BATCH_LIMIT)) {
        batch.set(doc(this.db, userPath(this.uid, 'transactions', t.id)), sanitize(t))
      }
      await batch.commit()
    }
  }

  async update(id: Id, patch: Partial<Transaction>): Promise<void> {
    await updateDoc(doc(this.db, userPath(this.uid, 'transactions', id)), sanitize(patch))
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (let i = 0; i < ids.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const id of ids.slice(i, i + BATCH_LIMIT)) {
        batch.delete(doc(this.db, userPath(this.uid, 'transactions', id)))
      }
      await batch.commit()
    }
  }
}

/**
 * المعرّفات والروابط **ليست نصًا حرًا** ولا تحمل رقم حساب — تُحفظ كما هي.
 * قصّها كان يفصل حقل `id` عن مسار الوثيقة (~30% من المعرّفات المولَّدة فيها
 * 5 أرقام متتالية) فيفشل التعديل، ويكسر روابط `merch-00001`
 * (tests/acceptance/firestoreIdIntegrity.test.ts).
 */
function isIdentifierField(key: string): boolean {
  return key === 'id' || key.endsWith('Id')
}

/** يقص أرقام الحسابات الكاملة من الحقول النصية الحرة قبل الكتابة. */
function sanitize<T extends object>(value: T): T {
  const out: Record<string, unknown> = {}
  for (const [key, v] of Object.entries(value)) {
    if (v === undefined) continue // Firestore يرفض undefined
    out[key] = typeof v === 'string' && !isIdentifierField(key) ? sanitizeAccountNumbers(v) : v
  }
  return out as T
}

export class FirestoreSourceRecordRepository implements SourceRecordRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private col() {
    return collection(this.db, userPath(this.uid, 'sourceRecords'))
  }

  async listByBatch(batchId: Id): Promise<SourceRecord[]> {
    const snap = await getDocs(query(this.col(), where('batchId', '==', batchId)))
    return snap.docs.map((d) => d.data() as SourceRecord)
  }

  async listByAccountIdentity(accountIdentity: string): Promise<SourceRecord[]> {
    const snap = await getDocs(query(this.col(), where('accountIdentity', '==', accountIdentity)))
    return snap.docs.map((d) => d.data() as SourceRecord)
  }

  async listByTransactionIds(ids: readonly Id[]): Promise<SourceRecord[]> {
    if (ids.length === 0) return []
    const out: SourceRecord[] = []
    for (let i = 0; i < ids.length; i += 30) {
      const snap = await getDocs(
        query(this.col(), where('transactionId', 'in', ids.slice(i, i + 30))),
      )
      out.push(...snap.docs.map((d) => d.data() as SourceRecord))
    }
    return out
  }

  async saveMany(records: readonly SourceRecord[]): Promise<void> {
    for (let i = 0; i < records.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const r of records.slice(i, i + BATCH_LIMIT)) {
        batch.set(doc(this.db, userPath(this.uid, 'sourceRecords', r.id)), sanitize(r))
      }
      await batch.commit()
    }
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (let i = 0; i < ids.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const id of ids.slice(i, i + BATCH_LIMIT)) {
        batch.delete(doc(this.db, userPath(this.uid, 'sourceRecords', id)))
      }
      await batch.commit()
    }
  }
}

export class FirestoreImportBatchRepository implements ImportBatchRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private col() {
    return collection(this.db, userPath(this.uid, 'importBatches'))
  }

  async findById(id: Id): Promise<ImportBatch | null> {
    const snap = await getDoc(doc(this.db, userPath(this.uid, 'importBatches', id)))
    return snap.exists() ? (snap.data() as ImportBatch) : null
  }

  /**
   * الدرجة ١ من منع التكرار: هل هذا الملف بعينه اعتُمد قبل ذلك؟
   *
   * ⚠️ الترشيح على `state` يتم **في الذاكرة لا في الاستعلام**: شرطا مساواة
   * على حقلين مختلفين يفرضان فهرسًا مركّبًا، وبصمة الملف انتقائية أصلًا
   * (صفر أو مستند أو مستندان)، فالفرق في التكلفة معدوم والمكسب إعداد أبسط.
   *
   * `committed` فقط: الدفعة المعلّقة (staged) **غير معتمدة** ولا تُحسب
   * استيرادًا سابقًا — ARCHITECTURE.md §11.1.
   */
  async findByFileHash(fileHash: string): Promise<ImportBatch | null> {
    const snap = await getDocs(query(this.col(), where('fileHash', '==', fileHash)))
    const committed = snap.docs
      .map((d) => d.data() as ImportBatch)
      .filter((b) => b.state === 'committed')
    return committed[0] ?? null
  }

  async listRecent(count: number): Promise<ImportBatch[]> {
    const snap = await getDocs(
      query(this.col(), orderBy('importedAt', 'desc'), fbLimit(count)),
    )
    return snap.docs.map((d) => d.data() as ImportBatch)
  }

  async save(batch: ImportBatch): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'importBatches', batch.id)), sanitize(batch))
  }

  async updateState(id: Id, state: ImportBatch['state']): Promise<void> {
    await updateDoc(doc(this.db, userPath(this.uid, 'importBatches', id)), { state })
  }
}

/**
 * وحدة عمل على Firestore.
 *
 * ⚠️ **قيد موثَّق:** Firestore transaction لا تسمح بقراءات بعد الكتابة
 * ولا تتجاوز 500 عملية، وحالة الاستخدام هنا تكتب مئات المستندات.
 * لذلك التنفيذ الحالي **يمرّر العمل كما هو** ويعتمد على writeBatch
 * داخل كل مستودع، وهي ذرّية **لكل دفعة 500 لا للعملية كلها**.
 *
 * الأثر: استيراد أكبر من 500 مستند قد ينقطع بين دفعتين.
 * هذا **ليس مستوفيًا** لشرط spec/06 «صفر أو كامل الدفعة» على الملفات الكبيرة،
 * ويحتاج علمًا صريحًا: الحل استئناف بحالة staged مسجَّلة قبل الكتابة،
 * وهو عمل المرحلة الرابعة (مطابقة الرصيد والتصدير).
 * الشرط **مستوفى بالكامل** في مستودعات الذاكرة، وعليها تُختبر حالات القبول.
 */
export class FirestoreUnitOfWork implements UnitOfWork {
  async run<T>(work: () => Promise<T>): Promise<T> {
    return work()
  }
}

export { deleteDoc }
