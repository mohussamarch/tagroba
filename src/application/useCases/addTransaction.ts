import { assertHalalas, type Currency, type Halalas } from '../../domain/money'
import { isValidIsoDate } from '../../domain/period'
import { isConsistentWithObservedDirection, liquidityOf, type EconomicKind } from '../../domain/entities/economicKind'
import type { Id, Transaction } from '../../domain/entities/types'
import type { Clock, IdGenerator, TransactionRepository, WalletRepository } from '../ports/repositories'

/**
 * AddTransaction — إضافة عملية يدويًا.
 *
 * `spec/01`: «الإضافة: **فاتورة/شراء**، حركة أموال، كشف حساب، شخص جديد».
 *
 * ⚠️ الحاجة اكتشفها المالك بالاستعمال: اشترى قهوة بالكاش ولم يجد أين
 * يسجّلها — زر الإضافة كان يفتح الاستيراد وحده. والكاش تحديدًا **لا يصل
 * من أي كشف**، فبدون هذا المسار يبقى صرف الكاش غير مرصود بالكامل.
 *
 * الفرق عن الاستيراد: هنا المستخدم **يعرف** ما فعله، فالنوع الاقتصادي
 * يُحدَّد ويُؤكَّد فورًا — لا يمر بدورة «غير محدد ثم اقتراح ثم تأكيد».
 */

export class AddTransactionError extends Error {}

export interface NewTransactionInput {
  /** موجب دائمًا. الاتجاه من النوع الاقتصادي. */
  amountMinor: Halalas
  currency?: Currency
  occurredAt: string
  walletId: Id
  economicKind: EconomicKind
  merchantName: string
  categoryId?: Id
  note?: string
  isCashTagged?: boolean
  excludedFromBudget?: boolean
}

export interface AddTransactionDeps {
  txns: TransactionRepository
  wallets: WalletRepository
  ids: IdGenerator
  clock: Clock
}

export function makeAddTransaction(deps: AddTransactionDeps) {
  return async function add(input: NewTransactionInput): Promise<Transaction> {
    if (!Number.isInteger(input.amountMinor)) {
      throw new AddTransactionError('المبلغ لازم يكون بالهللة كعدد صحيح')
    }
    if (input.amountMinor <= 0) {
      throw new AddTransactionError('المبلغ لازم يكون أكبر من صفر. الاتجاه بيتحدد من نوع العملية.')
    }
    assertHalalas(input.amountMinor, 'مبلغ العملية')

    if (!isValidIsoDate(input.occurredAt)) {
      throw new AddTransactionError('التاريخ مش صالح')
    }

    const wallet = await deps.wallets.findById(input.walletId)
    if (!wallet) throw new AddTransactionError('اختار محفظة موجودة')

    if (input.economicKind === 'unclassified') {
      throw new AddTransactionError('لازم تحدد نوع العملية — انت عارف اشتريت ولا حوّلت')
    }

    /*
     * الاتجاه الملاحظ يُشتق من النوع هنا — عكس الاستيراد تمامًا.
     * في الاستيراد الكشف يعطي الاتجاه والنوع مجهول؛ هنا المستخدم يعطي
     * النوع والاتجاه نتيجته. `internal_transfer` يُعامل صادرًا من محفظة
     * المصدر، وطرفه الثاني عمل لم يُبنَ بعد (ARCHITECTURE §14).
     */
    const liquidity = liquidityOf(input.economicKind)
    const observedDirection: 'in' | 'out' = liquidity === 'in' ? 'in' : 'out'

    if (!isConsistentWithObservedDirection(input.economicKind, observedDirection)) {
      throw new AddTransactionError('النوع ده ما يتوافقش مع اتجاه الحركة')
    }

    const name = input.merchantName.trim()
    if (name.length > 120) {
      throw new AddTransactionError('اسم المتجر أطول من 120 حرف')
    }
    if ((input.note ?? '').length > 1000) {
      throw new AddTransactionError('الملاحظة أطول من 1000 حرف')
    }

    const now = deps.clock.nowIso()
    const transaction: Transaction = {
      id: deps.ids.next('txn'),
      occurredAt: input.occurredAt,
      datePrecision: 'day',
      // العمليات اليدوية بعد أي عملية مستوردة في نفس اليوم
      sourceOrder: 9_000_000,
      economicKind: input.economicKind,
      // ✅ مؤكَّد فورًا: المستخدم اختاره بنفسه، فلا يُكتب فوقه آليًا (spec/05)
      economicKindConfirmed: true,
      observedDirection,
      amountMinor: input.amountMinor,
      currency: input.currency ?? wallet.currency,
      walletId: wallet.id,
      categoryConfirmed: input.categoryId !== undefined,
      excludedFromBudget: input.excludedFromBudget ?? false,
      reviewState: input.categoryId ? 'confirmed' : 'needs_review',
      // الكاش يُوسم تلقائيًا حين تكون المحفظة كاش — الوسم شارة عرض لا مبلغ
      isCashTagged: input.isCashTagged ?? wallet.kind === 'cash',
      rawMerchantName: name || 'بلا اسم',
      createdAt: now,
      updatedAt: now,
    }

    if (input.categoryId) transaction.categoryId = input.categoryId
    if (input.note?.trim()) transaction.note = input.note.trim()

    await deps.txns.saveMany([transaction])
    return transaction
  }
}
