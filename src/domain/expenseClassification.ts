import { addMoney, type Halalas } from './money'
import { normalizeText } from './normalize'

/**
 * فصل «فلوس خرجت من حياته» عن «فلوس انتقلت» — OVERRIDES §7-أ.
 *
 * هذا أهم فصل في التطبيق كله: ثلثا ما يخرج من الحساب ليس مصروفًا.
 * لو حُسب «المصروف» = 302 ألف، فالرقم مضلّل تمامًا.
 *
 * ⚠️ مصدر هذه القائمة: **اشتقاق من البيانات**، لا افتراض.
 * المجموعة أدناه هي وحدها التي تعطي 201,703.18 بالضبط على
 * files/transactions_full.csv، وهي معيار القبول المعلن.
 *
 * ⚠️ ملاحظة موثّقة: الجرد القديم في files/اقرأني-أولاً.md يذكر 199,256
 * لأنه أغفل «ذهب» (2,448.06). المعتمد هو 201,703.18.
 *
 * ⚠️ تعارض مسجَّل مع spec/02 (ARCHITECTURE.md §9.6):
 * تصنيف «سداد» (17,233.19) يدخل هنا ضمن المصروف الحقيقي، بينما spec/02
 * يقول إن سداد دين عليك ليس مصروفًا. السبب أن هذا العمود **تصنيف إنفاق
 * لا نوع اقتصادي**؛ النوع الاقتصادي يحتاج تأكيد المستخدم لكل عملية.
 * تنقيته لاحقًا ستغيّر هذه المجاميع عن عمد وبعلم المالك.
 */

/** تصنيفات المصدر التي تمثل انتقال مال لا استهلاكًا. */
export const NON_EXPENSE_SOURCE_CATEGORIES: readonly string[] = [
  'تحويلات',
  'محافظ رقمية',
  'تقسيط',
  'استثمار',
  'سحب نقدي',
  'ذهب',
]

const NON_EXPENSE_NORMALIZED = new Set(NON_EXPENSE_SOURCE_CATEGORIES.map(normalizeText))

/** هل تصنيف المصدر ده يمثل حركة ليست مصروفًا؟ */
export function isNonExpenseSourceCategory(category: string | undefined | null): boolean {
  if (!category) return false
  return NON_EXPENSE_NORMALIZED.has(normalizeText(category))
}

export interface ExpenseBreakdown {
  /** إجمالي المدين — كل ما خرج من الحساب. */
  totalDebitMinor: Halalas
  /** إجمالي الدائن — كل ما دخل. */
  totalCreditMinor: Halalas
  /** حركات ليست مصروفًا: تحويلات ومحافظ وتقسيط واستثمار وسحب وذهب. */
  nonExpenseMinor: Halalas
  /** المصروف الحقيقي = المدين − ما ليس مصروفًا. */
  realExpenseMinor: Halalas
  /** تفصيل ما ليس مصروفًا حسب التصنيف — لعرضه منفصلًا لا لإخفائه. */
  nonExpenseByCategory: Record<string, Halalas>
}

export interface ClassifiableRow {
  debitMinor: Halalas
  creditMinor: Halalas
  sourceCategory?: string
}

/**
 * يحسب الفصل على مجموعة صفوف.
 * دالة نقية: نفس المدخل ينتج نفس المخرج بالهللة.
 */
export function computeExpenseBreakdown(
  rows: readonly ClassifiableRow[],
): ExpenseBreakdown {
  let totalDebit = 0
  let totalCredit = 0
  let nonExpense = 0
  const byCategory: Record<string, Halalas> = {}

  for (const row of rows) {
    totalDebit = addMoney(totalDebit, row.debitMinor)
    totalCredit = addMoney(totalCredit, row.creditMinor)

    if (row.debitMinor > 0 && isNonExpenseSourceCategory(row.sourceCategory)) {
      nonExpense = addMoney(nonExpense, row.debitMinor)
      const key = row.sourceCategory!
      byCategory[key] = addMoney(byCategory[key] ?? 0, row.debitMinor)
    }
  }

  return {
    totalDebitMinor: totalDebit,
    totalCreditMinor: totalCredit,
    nonExpenseMinor: nonExpense,
    realExpenseMinor: totalDebit - nonExpense,
    nonExpenseByCategory: byCategory,
  }
}
