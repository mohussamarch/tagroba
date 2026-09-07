import type {
  Budget,
  CategoryBudget,
  ClassificationRule,
  Category,
  Id,
  ImportBatch,
  Merchant,
  Obligation,
  Person,
  PersonAllocation,
  Settlement,
  SourceRecord,
  Transaction,
  Wallet,
} from '../../domain/entities/types'
import type { Asset, AssetLot, AssetPrice, AssetSale } from '../../domain/entities/assets'
import type { NotificationReceipt } from '../../domain/notifications'

/**
 * واجهات المستودعات (ports) — **تعريفات بلا تنفيذ**.
 *
 * ARCHITECTURE.md §3: application/ يعرف الواجهات فقط.
 * لكل واجهة تنفيذان: infrastructure/firestore/ للتشغيل و
 * infrastructure/memory/ للاختبار (CLAUDE.md #6).
 *
 * قيد Firestore المشتق (ARCHITECTURE.md §5.6): **كل استعلام محدود**.
 * لا توجد هنا دالة listAll() على العمليات — عمدًا.
 */

export interface TransactionRepository {
  listByDateRange(fromIso: string, toIso: string): Promise<Transaction[]>
  listByBatch(batchId: Id): Promise<Transaction[]>
  findByIds(ids: readonly Id[]): Promise<Transaction[]>
  saveMany(transactions: readonly Transaction[]): Promise<void>
  update(id: Id, patch: Partial<Transaction>): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

export interface SourceRecordRepository {
  listByBatch(batchId: Id): Promise<SourceRecord[]>
  /** كل السجلات الخاصة بهوية حساب — أساس فحص التكرار. */
  listByAccountIdentity(accountIdentity: string): Promise<SourceRecord[]>
  listByTransactionIds(ids: readonly Id[]): Promise<SourceRecord[]>
  saveMany(records: readonly SourceRecord[]): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

export interface ImportBatchRepository {
  findById(id: Id): Promise<ImportBatch | null>
  /** الدرجة ١ من منع التكرار: بصمة ملف سبق استيراده. */
  findByFileHash(fileHash: string): Promise<ImportBatch | null>
  listRecent(limit: number): Promise<ImportBatch[]>
  save(batch: ImportBatch): Promise<void>
  updateState(id: Id, state: ImportBatch['state']): Promise<void>
}

export interface WalletRepository {
  listAll(): Promise<Wallet[]>
  findById(id: Id): Promise<Wallet | null>
  save(wallet: Wallet): Promise<void>
}

export interface CategoryRepository {
  listAll(): Promise<Category[]>
  save(category: Category): Promise<void>
}

export interface MerchantRepository {
  listAll(): Promise<Merchant[]>
  findByNormalizedName(normalizedName: string): Promise<Merchant | null>
  saveMany(merchants: readonly Merchant[]): Promise<void>
}

export interface RuleRepository {
  listAll(): Promise<ClassificationRule[]>
  saveMany(rules: readonly ClassificationRule[]): Promise<void>
}

export interface PersonRepository {
  listAll(): Promise<Person[]>
  save(person: Person): Promise<void>
}

export interface ObligationRepository {
  listByPerson(personId: Id): Promise<Obligation[]>
  listByTransactionIds(ids: readonly Id[]): Promise<Obligation[]>
  saveMany(obligations: readonly Obligation[]): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

export interface SettlementRepository {
  listByObligations(obligationIds: readonly Id[]): Promise<Settlement[]>
  listByTransactionIds(ids: readonly Id[]): Promise<Settlement[]>
  saveMany(settlements: readonly Settlement[]): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

export interface AllocationRepository {
  listByTransactionIds(ids: readonly Id[]): Promise<PersonAllocation[]>
  saveMany(allocations: readonly PersonAllocation[]): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

/**
 * وحدة العمل الذرية — spec/03: «تغيير العملية وتخصيص الشخص وتسويته
 * وتحديث حالة الاستيراد عملية ذرية واحدة».
 * OVERRIDES §2: ما يقابلها هو Firestore transaction / batched write.
 *
 * spec/06: «انقطاع أثناء حفظ دفعة ⇒ صفر أو كامل الدفعة، لا نصفها».
 */
export interface UnitOfWork {
  run<T>(work: () => Promise<T>): Promise<T>
}

/** يولّد معرّفات ثابتة. يُمرَّر كاعتماد ليكون الاختبار حتميًا. */
export interface IdGenerator {
  next(prefix: string): Id
}

/** ساعة تُمرَّر كاعتماد — لا Date.now() داخل المنطق. */
export interface Clock {
  nowIso(): string
}

/**
 * الميزانيات — spec/03.
 * الاستعلام بمفتاح الفترة: حد واحد لكل فترة، والقراءة محدودة (§5.6).
 */
export interface BudgetRepository {
  findByPeriod(periodKey: string): Promise<Budget | null>
  save(budget: Budget): Promise<void>
  /** حذف السقف الإجمالي وسقوف تصنيفاته معًا. */
  remove(periodKey: string): Promise<void>
  listCategoryBudgets(budgetId: Id): Promise<CategoryBudget[]>
  saveCategoryBudget(line: CategoryBudget): Promise<void>
  removeCategoryBudget(id: Id): Promise<void>
}

/**
 * الاستثمار — `spec/01`. الأصول ودفعات الشراء وعمليات البيع والأسعار.
 *
 * الاستعلام كله **بحقل واحد** (`assetId`) أو بالمجموعة كاملة،
 * فلا فهرس مركّب (ARCHITECTURE §12). الترتيب الزمني في الذاكرة.
 */
export interface AssetRepository {
  listAll(): Promise<Asset[]>
  save(asset: Asset): Promise<void>
}

export interface AssetLotRepository {
  listByAsset(assetId: Id): Promise<AssetLot[]>
  listAll(): Promise<AssetLot[]>
  saveMany(lots: readonly AssetLot[]): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

export interface AssetSaleRepository {
  listByAsset(assetId: Id): Promise<AssetSale[]>
  listAll(): Promise<AssetSale[]>
  saveMany(sales: readonly AssetSale[]): Promise<void>
  deleteMany(ids: readonly Id[]): Promise<void>
}

/** سعر واحد محفوظ لكل أصل — الأحدث. التاريخ محفوظ معه دائمًا. */
export interface AssetPriceRepository {
  listAll(): Promise<AssetPrice[]>
  save(price: AssetPrice): Promise<void>
}

/**
 * إيصالات التنبيه — `spec/03`: «لمنع تكرار التنبيه».
 * المفتاح هو `eventKey` نفسه، فالكتابة المكررة بتدهس نفسها بلا ضرر.
 */
export interface NotificationReceiptRepository {
  listAll(): Promise<NotificationReceipt[]>
  saveMany(receipts: readonly NotificationReceipt[]): Promise<void>
  deleteMany(eventKeys: readonly string[]): Promise<void>
}
