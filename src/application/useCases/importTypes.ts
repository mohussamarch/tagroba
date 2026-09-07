import type { ParsedRow, RowError, SchemaId } from '../../infrastructure/import/schemas'
import type { Halalas } from '../../domain/money'
import type { Id, ImportBatch, ImportSourceType, MatchingState } from '../../domain/entities/types'
import type {
  CategoryRepository,
  Clock,
  IdGenerator,
  ImportBatchRepository,
  MerchantRepository,
  RuleRepository,
  SourceRecordRepository,
  TransactionRepository,
  UnitOfWork,
} from '../ports/repositories'

/** أنواع ImportStatement المشتركة بين مرحلتي المعاينة والالتزام. */

export interface ImportPreviewLine {
  row: ParsedRow
  state: MatchingState
  /** سبب الحالة بلغة المستخدم — لا حالة بلا تفسير (spec/04). */
  reason: string
  matchedTransactionId?: string
  categoryId?: Id
  categoryReason: string
  /** مختار للاستيراد افتراضيًا؟ الجديد نعم، وما عداه يحتاج قرارًا. */
  selectedByDefault: boolean
}

export interface ImportPreview {
  fileName: string
  fileHash: string
  schema: SchemaId
  accountIdentity: string
  /** الدرجة ١: نفس الملف سبق استيراده — تُعرض الدفعة السابقة بدل الإضافة. */
  previousBatch: ImportBatch | null
  lines: ImportPreviewLine[]
  errors: RowError[]
  counts: {
    total: number
    newCount: number
    duplicates: number
    similar: number
    conflicts: number
    invalid: number
  }
  /** مجموع أثر المحدد افتراضيًا على المحفظة والدخل والمصروف — spec/05. */
  impact: {
    walletDeltaMinor: Halalas
    expenseMinor: Halalas
    incomeMinor: Halalas
  }
}

export interface ImportStatementDeps {
  txns: TransactionRepository
  sources: SourceRecordRepository
  batches: ImportBatchRepository
  merchants: MerchantRepository
  categories: CategoryRepository
  rules: RuleRepository
  uow: UnitOfWork
  ids: IdGenerator
  clock: Clock
}

export interface ImportRequest {
  fileName: string
  content: string
  /** هوية الحساب/المصدر — نطاق تفرّد المرجع البنكي (spec/03). */
  accountIdentity: string
  sourceType: ImportSourceType
  /** المحفظة التي يخص هذا الكشف — تربط العمليات بها لمطابقة الرصيد. */
  walletId?: Id
  schema?: SchemaId
  /**
   * صفوف محلَّلة جاهزة — مسار الـPDF.
   * لما تتبعت، التحليل بيتخطى وبقية الخط (منع التكرار والتصنيف
   * والحفظ على مرحلتين) بتشتغل زي ما هي بالظبط.
   */
  parsedRows?: ParsedRow[]
}
