/**
 * الأنواع الاقتصادية — جدول spec/02-accounting.md حرفيًا.
 *
 * القاعدة الحاكمة: **وارد الحساب لا يحدد وحده النوع الاقتصادي.**
 * اتجاه السيولة (وارد/صادر) حقيقة بنكية؛ النوع الاقتصادي قرار محاسبي.
 * التصنيف الآلي يقترح، وتأكيد المستخدم يحمي اختياره من الكتابة الآلية اللاحقة.
 */

export type EconomicKind =
  // وارد — يزيد الدخل
  | 'salary' // مرتب
  | 'bonus' // بونص
  | 'commission' // عمولة
  | 'overtime' // أوفر تايم
  | 'freelance' // فري لانس
  | 'personal_sale' // بيع شخصي
  // وارد — لا يزيد الدخل
  | 'loan_received' // قرض مستلم → دين عليك يزيد
  | 'debt_collected' // تحصيل دين لك → دين لك يقل
  | 'custody_received' // أمانة مستلمة → أمانة للآخر تزيد
  // صادر — مصروف
  | 'purchase' // شراء / فاتورة
  | 'support_gift' // دعم / هدية إلى مصر
  | 'fee' // رسوم (مصروف مستقل مرتبط بحركة)
  // صادر — ليس مصروفًا
  | 'loan_granted' // قرض ممنوح → دين لك يزيد
  | 'debt_repaid' // سداد دين عليك → دين عليك يقل
  | 'custody_returned' // رد أمانة → الأمانة للآخر تقل
  // داخلي
  | 'internal_transfer' // بين محافظي / سحب إلى محفظة كاش
  // استثمار
  | 'asset_buy' // شراء أصل استثماري
  | 'asset_sell' // بيع أصل استثماري
  // غير محدد بعد
  | 'unclassified'

/** اتجاه السيولة كما هو في الكشف — حقيقة بنكية لا تُستنتج. */
export type LiquidityDirection = 'in' | 'out' | 'internal'

/** أثر النوع على حساب الشخص. */
export type PersonEffect =
  | 'none'
  | 'receivable_up' // لك عنده يزيد
  | 'receivable_down' // لك عنده يقل
  | 'payable_loan_up' // له عندك (قرض) يزيد
  | 'payable_loan_down'
  | 'payable_custody_up' // له عندك (أمانة) يزيد
  | 'payable_custody_down'
  | 'beneficiary_info' // معلومة مستفيد فقط، بلا التزام

export interface EconomicKindRule {
  readonly kind: EconomicKind
  readonly label: string
  readonly liquidity: LiquidityDirection
  /** هل يزيد الدخل الاقتصادي للفترة؟ */
  readonly countsAsIncome: boolean
  /** هل يزيد المصروف الشخصي؟ (بنصيب المستخدم فقط بعد فصل المستحق على الآخرين) */
  readonly countsAsPersonalExpense: boolean
  readonly personEffect: PersonEffect
}

const RULES: Record<EconomicKind, EconomicKindRule> = {
  salary: { kind: 'salary', label: 'مرتب', liquidity: 'in', countsAsIncome: true, countsAsPersonalExpense: false, personEffect: 'none' },
  bonus: { kind: 'bonus', label: 'بونص', liquidity: 'in', countsAsIncome: true, countsAsPersonalExpense: false, personEffect: 'none' },
  commission: { kind: 'commission', label: 'عمولة', liquidity: 'in', countsAsIncome: true, countsAsPersonalExpense: false, personEffect: 'none' },
  overtime: { kind: 'overtime', label: 'أوفر تايم', liquidity: 'in', countsAsIncome: true, countsAsPersonalExpense: false, personEffect: 'none' },
  freelance: { kind: 'freelance', label: 'فري لانس', liquidity: 'in', countsAsIncome: true, countsAsPersonalExpense: false, personEffect: 'none' },
  personal_sale: { kind: 'personal_sale', label: 'بيع شخصي', liquidity: 'in', countsAsIncome: true, countsAsPersonalExpense: false, personEffect: 'none' },

  loan_received: { kind: 'loan_received', label: 'قرض مستلم', liquidity: 'in', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'payable_loan_up' },
  debt_collected: { kind: 'debt_collected', label: 'تحصيل دين لك', liquidity: 'in', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'receivable_down' },
  custody_received: { kind: 'custody_received', label: 'أمانة مستلمة', liquidity: 'in', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'payable_custody_up' },

  purchase: { kind: 'purchase', label: 'شراء / فاتورة', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: true, personEffect: 'receivable_up' },
  support_gift: { kind: 'support_gift', label: 'دعم / هدية', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: true, personEffect: 'beneficiary_info' },
  fee: { kind: 'fee', label: 'رسوم', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: true, personEffect: 'none' },

  loan_granted: { kind: 'loan_granted', label: 'قرض ممنوح', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'receivable_up' },
  debt_repaid: { kind: 'debt_repaid', label: 'سداد دين عليك', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'payable_loan_down' },
  custody_returned: { kind: 'custody_returned', label: 'رد أمانة', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'payable_custody_down' },

  internal_transfer: { kind: 'internal_transfer', label: 'تحويل داخلي', liquidity: 'internal', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'none' },

  asset_buy: { kind: 'asset_buy', label: 'شراء أصل', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'none' },
  // بيع الأصل: لا دخل معيشة من كامل الحصيلة — الربح المحقق فقط، ويُحسب في الاستثمار
  asset_sell: { kind: 'asset_sell', label: 'بيع أصل', liquidity: 'in', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'none' },

  unclassified: { kind: 'unclassified', label: 'غير محدد', liquidity: 'out', countsAsIncome: false, countsAsPersonalExpense: false, personEffect: 'none' },
}

export function ruleFor(kind: EconomicKind): EconomicKindRule {
  const rule = RULES[kind]
  if (!rule) throw new Error(`نوع اقتصادي غير معروف: ${kind}`)
  return rule
}

export const ALL_ECONOMIC_KINDS = Object.keys(RULES) as EconomicKind[]

export const countsAsIncome = (kind: EconomicKind): boolean => ruleFor(kind).countsAsIncome
export const countsAsPersonalExpense = (kind: EconomicKind): boolean =>
  ruleFor(kind).countsAsPersonalExpense
export const liquidityOf = (kind: EconomicKind): LiquidityDirection => ruleFor(kind).liquidity
export const personEffectOf = (kind: EconomicKind): PersonEffect => ruleFor(kind).personEffect

/**
 * هل اتجاه السيولة المُلاحَظ في الكشف متسق مع النوع الاقتصادي المقترح؟
 * يُستخدم للتحذير عند المراجعة، لا لرفض تلقائي —
 * لأن الكشف يعرف الاتجاه فقط ولا يعرف النية.
 */
export function isConsistentWithObservedDirection(
  kind: EconomicKind,
  observed: 'in' | 'out',
): boolean {
  const expected = liquidityOf(kind)
  if (expected === 'internal') return true // التحويل الداخلي له طرفان
  return expected === observed
}
