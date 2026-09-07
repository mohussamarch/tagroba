import type { Currency, Halalas } from '../money'
import type { EconomicKind } from './economicKind'

/**
 * الكيانات — أنواع TypeScript خالصة، بلا سلوك وبلا اعتماد خارجي.
 * تقابل جداول spec/03-data-model.md كنموذج منطقي (OVERRIDES §2).
 */

export type Id = string

/** تاريخ ISO بصيغة YYYY-MM-DD. منطقة العرض Asia/Riyadh (spec/02). */
export type IsoDate = string

/** دقة التاريخ: بعض المصادر تعطي اليوم فقط بلا ساعة. */
export type DatePrecision = 'day' | 'minute'

export type WalletKind = 'bank' | 'cash' | 'own_abroad' | 'digital_wallet'

export interface Wallet {
  id: Id
  name: string
  currency: Currency
  kind: WalletKind
  openingBalanceMinor: Halalas
  openingAt: IsoDate
  /**
   * آخر أربعة أرقام فقط. **ممنوع تخزين رقم حساب كامل** —
   * OVERRIDES §2 و CLAUDE.md #11.
   */
  accountLast4?: string
}

export interface Category {
  id: Id
  parentId: Id | null
  name: string
  iconKey: string
  lightColor: string
  darkColor: string
  active: boolean
  order: number
}

export interface Merchant {
  id: Id
  displayName: string
  normalizedName: string
  logoAsset?: string
  logoSource?: string
  /** التصنيف المؤكد من المستخدم — يعلو على القواعد (spec/05). */
  verifiedCategoryId?: Id
}

export interface MerchantAlias {
  merchantId: Id
  normalizedAlias: string
}

export type RuleMatchMode = 'contains' | 'startsWith' | 'exact'

export interface ClassificationRule {
  id: Id
  /** الأصغر يُطبَّق أولًا. */
  priority: number
  matchText: string
  matchMode: RuleMatchMode
  categoryId: Id
  enabled: boolean
}

/** حالة المراجعة المعروضة للمستخدم (spec/04 و spec/05). */
export type ReviewState = 'confirmed' | 'suggested' | 'needs_review'

export interface Tag {
  id: Id
  normalizedName: string
  displayName: string
}

export interface Transaction {
  id: Id
  occurredAt: IsoDate
  datePrecision: DatePrecision
  /** الوقت الأصلي من المصدر إن وُجد — يُحفظ ولا يُختلق. */
  sourceTime?: string
  /** ترتيب السطر داخل يومه في المصدر — يحفظ ترتيب الكشف عند تساوي التاريخ. */
  sourceOrder: number

  economicKind: EconomicKind
  /** هل المستخدم أكّد النوع الاقتصادي بنفسه؟ لو نعم لا يُكتب فوقه آليًا. */
  economicKindConfirmed: boolean

  /**
   * اتجاه السيولة كما لوحظ في المصدر — **حقيقة بنكية لا رأي**.
   * منفصل عن economicKind عمدًا: spec/02 «وارد الحساب لا يحدد وحده
   * النوع الاقتصادي». يبقى ثابتًا مهما تغيّر النوع الاقتصادي لاحقًا،
   * وهو ما تعتمد عليه مطابقة التكرار عند إعادة الاستيراد.
   */
  observedDirection: 'in' | 'out'

  /** المبلغ **موجب دائمًا**. الاتجاه في WalletPosting (spec/03). */
  amountMinor: Halalas
  currency: Currency

  merchantId?: Id
  /** تصنيف نهائي واحد، قد يكون فرعًا. */
  categoryId?: Id
  categoryConfirmed: boolean

  note?: string
  excludedFromBudget: boolean
  reviewState: ReviewState
  isCashTagged: boolean

  /**
   * المحفظة التي وقعت عليها الحركة.
   *
   * اختياري لأن عمليات استُوردت قبل إضافة المحافظ لا تحمله؛ تلك
   * تُعرض «غير منسوبة لمحفظة» ولا تدخل مطابقة رصيد أي محفظة،
   * بدل أن تُنسب لواحدة بالتخمين.
   */
  walletId?: Id

  /**
   * الرصيد الذي أعلنه المصدر **بعد** هذه الحركة، إن وُجد في الكشف.
   *
   * هذا هو ما تُقارَن به السلسلة المحسوبة في مطابقة الرصيد
   * (OVERRIDES §7-ب). بدون تخزينه تصير المطابقة داخل التطبيق مستحيلة،
   * ويبقى معيار القبول مثبتًا في الاختبارات وحدها لا في المنتج.
   */
  statedBalanceMinor?: Halalas

  /** النص الأصلي من الكشف — يُحفظ للمراجعة ولا يُقص (spec/05). */
  rawDescription?: string
  rawMerchantName?: string
  /** تصنيف المصدر كما جاء في CSV — دليل لا حكم. */
  sourceCategory?: string
  sourceOperationType?: string

  createdAt: string
  updatedAt: string
}

/** طرف واحد من حركة على محفظة. التحويل الداخلي له طرفان. */
export interface WalletPosting {
  id: Id
  transactionId: Id
  walletId: Id
  /** موجب = وارد، سالب = صادر. الإشارة هنا فقط، لا تُكرَّر في مكان آخر. */
  signedAmountMinor: Halalas
  currency: Currency
  bookingAt: IsoDate
}

export interface Person {
  id: Id
  name: string
  archived: boolean
}

export type ObligationKind = 'receivable' | 'loan_payable' | 'custody_payable'

export interface Obligation {
  id: Id
  personId: Id
  originTransactionId: Id
  kind: ObligationKind
  originalMinor: Halalas
  currency: Currency
}

export interface Settlement {
  id: Id
  transactionId: Id
  obligationId: Id
  amountMinor: Halalas
}

export type AllocationKind = 'receivable' | 'gift'

export interface PersonAllocation {
  id: Id
  transactionId: Id
  personId: Id
  allocationKind: AllocationKind
  amountMinor: Halalas
  currency: Currency
}

export type ImportSourceType = 'csv_preview' | 'csv_legacy' | 'pdf_alrajhi' | 'sms'
export type ImportBatchState = 'staged' | 'committed' | 'reverted'

export interface ImportBatch {
  id: Id
  sourceType: ImportSourceType
  fileHash: string
  fileName: string
  importedAt: string
  state: ImportBatchState
  counts: {
    total: number
    imported: number
    duplicates: number
    similar: number
    conflicts: number
    invalid: number
  }
}

/** حالة مطابقة سطر المصدر — خمس درجات spec/05. */
export type MatchingState =
  | 'new' // جديد
  | 'duplicate' // مكرر مؤكد
  | 'similar' // متشابه، يحتاج قرارًا
  | 'conflict' // نفس المرجع بتفاصيل مختلفة
  | 'invalid' // صف غير صالح

export interface SourceRecord {
  id: Id
  batchId: Id
  /** هوية الحساب/المصدر — نطاق تفرّد المرجع البنكي. */
  accountIdentity: string
  sourceReference: string | null
  sourceHash: string
  originalRowIndex: number
  /** النص الأصلي للصف كما ورد. */
  rawLine: string
  transactionId: Id | null
  matchingState: MatchingState
  /** سبب الحالة بلغة المستخدم — لا حالة بلا تفسير (spec/04). */
  reason: string
}

/**
 * ميزانية فترة — spec/03: «حدود صريحة».
 * لا تُنشأ تلقائيًا من متوسط؛ المستخدم يحددها (spec/01).
 */
export interface Budget {
  /** مفتاح الفترة نفسه: "2026-09". فترة واحدة = ميزانية واحدة. */
  id: Id
  periodKey: string
  periodStart: IsoDate
  periodEnd: IsoDate
  /** السقف الإجمالي، أو null لو المستخدم حدد سقوف تصنيفات فقط. */
  totalLimitMinor: Halalas | null
  /** عتبة التنبيه بالمئة (80 = ٨٠٪)، أو null فلا تنبيه (spec/06). */
  thresholdPercent: number | null
  createdAt: string
  updatedAt: string
}

export interface CategoryBudget {
  id: Id
  budgetId: Id
  categoryId: Id
  limitMinor: Halalas
  notifyEnabled: boolean
  thresholdPercent: number | null
}
