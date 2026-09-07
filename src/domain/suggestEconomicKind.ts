import { normalizeText, normalizedContains } from './normalize'
import type { EconomicKind } from './entities/economicKind'

/**
 * اقتراح النوع الاقتصادي — **يقترح ولا يقرر.**
 *
 * spec/02: «وارد الحساب لا يحدد وحده النوع الاقتصادي.
 *           التصنيف الآلي **يقترح**، وتأكيد المستخدم يحمي اختياره.
 *           **لا تصنف تحويلًا غامضًا كراتب افتراضيًا.**»
 *
 * لماذا هذا الملف موجود أصلًا: كشف حقيقي فيه 1912 عملية، وتحديد نوع كل
 * واحدة يدويًا مستحيل عمليًا. الاقتراح يجعل التأكيد الجماعي ممكنًا،
 * **دون** أن يقرر التطبيق نيابة عن المستخدم.
 *
 * الغامض يبقى غامضًا: يُعرض بلا اقتراح، ويحتاج قرارًا فرديًا.
 */

export type SuggestionConfidence =
  /** دليل قاطع من طبيعة الحركة نفسها. */
  | 'high'
  /** مرجّح لكن يحتمل غيره. */
  | 'medium'
  /** لا يكفي الدليل — **لا اقتراح**، قرار فردي مطلوب. */
  | 'ambiguous'

export interface KindSuggestion {
  /** null عند الغموض — لا يُخترع اقتراح لسد فراغ. */
  kind: EconomicKind | null
  confidence: SuggestionConfidence
  /** سبب الاقتراح أو سبب الامتناع، بلغة المستخدم. */
  reason: string
  /** بدائل واردة يُعرضها للمستخدم عند الغموض. */
  alternatives: EconomicKind[]
}

export interface SuggestionInput {
  direction: 'in' | 'out'
  /** تصنيف الإنفاق من الملف — دليل قوي لكنه ليس نوعًا اقتصاديًا. */
  sourceCategory?: string
  /**
   * اسم التصنيف الذي حدّده التطبيق بقواعده أو أكّده المستخدم.
   *
   * يُستعمل حين لا يحمل الملف عمود تصنيف (مخطط المعاينة مثلًا).
   * بدونه كان الاقتراح يتجاهل ما يعرفه التطبيق فعلًا: قاعدة تقول
   * إن STC «اتصالات»، ومع ذلك تُعرض العملية كغامضة.
   */
  categoryName?: string
  /** نوع العملية من الكشف. ⚠️ قد ينزلق لترويسة مجاورة (spec/05). */
  sourceOperationType?: string
  merchantName?: string
  description?: string
}

/* ───────── تصنيفات المصدر التي تعني «انتقال مال» لا استهلاكًا ───────── */

/** سحب نقدي: من البنك إلى محفظة الكاش — spec/02 صريح أنه ليس مصروفًا. */
const CASH_WITHDRAWAL = 'سحب نقدي'
const INVESTMENT = ['استثمار', 'ذهب']
const INSTALLMENT = 'تقسيط'
const TRANSFERS = 'تحويلات'
const DIGITAL_WALLETS = 'محافظ رقمية'
const DEBT_PAYMENT = 'سداد'
const BANK_FEES = 'رسوم بنكية'
const DEPOSIT = 'إيداع'
const REFUND = 'استرداد'

const eq = (a: string | undefined, b: string): boolean =>
  a !== undefined && normalizeText(a) === normalizeText(b)

const oneOf = (a: string | undefined, list: readonly string[]): boolean =>
  list.some((b) => eq(a, b))

const ambiguous = (reason: string, alternatives: EconomicKind[]): KindSuggestion => ({
  kind: null,
  confidence: 'ambiguous',
  reason,
  alternatives,
})

/**
 * يقترح نوعًا اقتصاديًا. **لا يكتب شيئًا ولا يقرر.**
 * دالة نقية: نفس المدخل ينتج نفس المخرج.
 */
export function suggestEconomicKind(input: SuggestionInput): KindSuggestion {
  /*
   * عمود الملف أولًا لأنه أقرب للمصدر، ثم تصنيف التطبيق.
   * الاثنان دليل على **طبيعة الإنفاق** لا على النوع الاقتصادي،
   * لكن معرفة أن العملية «بقالة» تكفي لاقتراح «شراء».
   */
  const cat = input.sourceCategory?.trim() || input.categoryName?.trim() || undefined
  const haystack = `${input.merchantName ?? ''} ${input.description ?? ''}`

  /* ─────────────── الصادر ─────────────── */
  if (input.direction === 'out') {
    if (eq(cat, CASH_WITHDRAWAL)) {
      return {
        kind: 'internal_transfer',
        confidence: 'high',
        reason: 'سحب نقدي: الفلوس اتنقلت من البنك لمحفظة الكاش، مش اتصرفت',
        alternatives: [],
      }
    }

    if (oneOf(cat, INVESTMENT)) {
      return {
        kind: 'asset_buy',
        confidence: 'high',
        reason: `«${cat}»: شراء أصل استثماري، مش استهلاك`,
        alternatives: ['purchase'],
      }
    }

    if (eq(cat, BANK_FEES)) {
      return {
        kind: 'fee',
        confidence: 'high',
        reason: 'رسوم بنكية: مصروف مستقل',
        alternatives: [],
      }
    }

    if (eq(cat, INSTALLMENT)) {
      return {
        kind: 'debt_repaid',
        confidence: 'medium',
        reason:
          'تقسيط: سداد جزء من التزام سابق. ' +
          'لو الشراء الأصلي مسجَّل كمصروف، احتسابه تاني هيبقى عدّ مزدوج.',
        alternatives: ['purchase'],
      }
    }

    if (eq(cat, DEBT_PAYMENT)) {
      // ⚠️ التعارض الموثّق في ARCHITECTURE.md §9.6 — لا يُحسم آليًا
      return ambiguous(
        'سداد: ممكن يكون سداد دين عليك (مش مصروف) وممكن يكون دفع فاتورة ' +
          '(مصروف). الكشف مش بيفرّق، فمحتاج قرارك.',
        ['debt_repaid', 'purchase', 'internal_transfer'],
      )
    }

    if (eq(cat, DIGITAL_WALLETS)) {
      return ambiguous(
        'محفظة رقمية (زي برق): الفلوس خرجت من الراجحي، بس الكشف مش بيقول ' +
          'راحت فين. ممكن تكون محفظتك انت، وممكن تكون دعم أو قرض لحد.',
        ['internal_transfer', 'support_gift', 'loan_granted'],
      )
    }

    if (eq(cat, TRANSFERS)) {
      return ambiguous(
        'تحويل صادر: الكشف بيقول الفلوس خرجت بس مش بيقول ليه. ' +
          'ممكن تحويل لحسابك، دعم، قرض، أو سداد.',
        ['internal_transfer', 'support_gift', 'loan_granted', 'debt_repaid'],
      )
    }

    // برق كخدمة تحويل، لا كنوع مصروف — spec/02
    if (normalizedContains(haystack, 'BARQ')) {
      return ambiguous(
        'برق وسيلة تحويل مش نوع مصروف. لازم تحدد الغرض: حسابك، دعم، قرض، ولا سداد.',
        ['internal_transfer', 'support_gift', 'loan_granted', 'debt_repaid'],
      )
    }

    // الباقي صادر بتصنيف إنفاق معروف ⇒ شراء
    if (cat && cat.trim() !== '' && !eq(cat, 'غير مصنّف')) {
      return {
        kind: 'purchase',
        confidence: 'high',
        reason: `«${cat}»: شراء أو فاتورة`,
        alternatives: ['support_gift', 'loan_granted'],
      }
    }

    return ambiguous(
      'صادر بلا تصنيف واضح. محتاج تحدد ده شراء ولا نقل فلوس.',
      ['purchase', 'internal_transfer', 'loan_granted', 'debt_repaid'],
    )
  }

  /* ─────────────── الوارد — الأخطر ─────────────── */

  /*
   * spec/02: «لا تصنف تحويلًا غامضًا كراتب افتراضيًا.»
   * الوارد **لا يُقترح كدخل أبدًا** من الكشف وحده، لأن التمييز بين
   * راتب وقرض وتحصيل دين وأمانة **غير موجود في البيانات**.
   * الملاحظة الحقيقية: معظم الدخل يصل كتحويلات لا كإيداع راتب.
   */

  if (oneOf(cat, INVESTMENT)) {
    return {
      kind: 'asset_sell',
      confidence: 'medium',
      reason: 'وارد من الاستثمار: حصيلة بيع أصل، والربح المحقق فقط هو المكسب',
      alternatives: ['salary'],
    }
  }

  if (eq(cat, REFUND)) {
    return ambiguous(
      'استرداد: ده رجوع فلوس دفعتها، مش دخل جديد. ' +
        'الأنسب تربطه بالعملية الأصلية بدل ما تعدّه دخلًا.',
      ['personal_sale'],
    )
  }

  if (eq(cat, DEPOSIT) || eq(cat, TRANSFERS) || eq(cat, DIGITAL_WALLETS) || !cat) {
    return ambiguous(
      'وارد: الكشف بيقول الفلوس دخلت بس مش بيقول منين. ' +
        'راتب؟ قرض استلمته؟ تحصيل دين لك؟ أمانة؟ الفرق بينهم كبير في الحساب.',
      ['salary', 'freelance', 'loan_received', 'debt_collected', 'custody_received'],
    )
  }

  return ambiguous(
    `وارد بتصنيف «${cat}»: محتاج تحدد نوعه الاقتصادي.`,
    ['salary', 'freelance', 'personal_sale', 'loan_received', 'debt_collected'],
  )
}

/** هل الاقتراح قوي كفاية ليُعرض للتأكيد الجماعي؟ */
export function isBulkConfirmable(suggestion: KindSuggestion): boolean {
  return suggestion.kind !== null && suggestion.confidence === 'high'
}
