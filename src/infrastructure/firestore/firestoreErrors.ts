/**
 * ترجمة أخطاء فايرستور إلى رسائل يفهمها المستخدم.
 *
 * ⚠️ **الفجوة التي سدّها هذا الملف:** أخطاء تسجيل الدخول كانت مترجمة،
 * وأخطاء الاستيراد تذكر رقم السطر وسببه — لكن أي فشل في القراءة أو
 * الكتابة على فايرستور كان يصل للمستخدم **بنصه الإنجليزي التقني**:
 * «Missing or insufficient permissions» أو «client is offline».
 *
 * القاعدة: كل خطأ يظهر للمستخدم يقول **ما حدث وما يفعله**،
 * لا رمزًا ولا جملة من مكتبة.
 */

export interface UserFacingError {
  /** ما حدث، بلغة المستخدم. */
  message: string
  /** ما يفعله. null لو مفيش إجراء واضح. */
  action: string | null
  /** هل تُجدي إعادة المحاولة؟ */
  retryable: boolean
  /** الرمز الأصلي — يُعرض مطويًا لا في الواجهة الأساسية. */
  code: string
}

const FIRESTORE_MESSAGES: Record<
  string,
  { message: string; action: string | null; retryable: boolean }
> = {
  'permission-denied': {
    message: 'الصلاحية مرفوضة. ده معناه إن جلستك انتهت أو إن القواعد بترفض العملية دي.',
    action: 'سجّل خروج وادخل تاني.',
    retryable: false,
  },
  unauthenticated: {
    message: 'جلستك انتهت.',
    action: 'سجّل دخولك تاني.',
    retryable: false,
  },
  unavailable: {
    message: 'مفيش اتصال بفايربيز دلوقتي.',
    action: 'اتأكد من الإنترنت. اللي اتحفظ على جهازك بيفضل موجود، وهيتزامن أول ما الاتصال يرجع.',
    retryable: true,
  },
  'deadline-exceeded': {
    message: 'الاتصال أخد وقت طويل وانقطع.',
    action: 'جرّب تاني. لو الشبكة بطيئة، استنى شوية.',
    retryable: true,
  },
  'resource-exhausted': {
    message: 'وصلنا الحد اليومي المجاني لفايربيز (٥٠ ألف قراءة و٢٠ ألف كتابة).',
    action: 'الحد بيترجع بكرة. لو ده بيتكرر، يبقى فيه استعلام بيقرا أكتر من اللازم.',
    retryable: false,
  },
  'failed-precondition': {
    message: 'الاستعلام محتاج فهرس مش موجود في قاعدة البيانات.',
    action: 'دي مشكلة في الكود مش عندك. ابعتلي الرسالة كاملة.',
    retryable: false,
  },
  'not-found': {
    message: 'الحاجة اللي بتدوّر عليها مش موجودة. يمكن اتمسحت من جهاز تاني.',
    action: 'اعمل تحديث للصفحة.',
    retryable: true,
  },
  'already-exists': {
    message: 'فيه حاجة بنفس المعرّف موجودة قبل كده.',
    action: null,
    retryable: false,
  },
  cancelled: {
    message: 'العملية اتلغت قبل ما تخلص.',
    action: 'جرّب تاني.',
    retryable: true,
  },
  'invalid-argument': {
    message: 'فيه بيانات شكلها غلط اترفضت من قاعدة البيانات.',
    action: 'دي مشكلة في الكود مش عندك. ابعتلي الرسالة كاملة.',
    retryable: false,
  },
  aborted: {
    message: 'العملية اتوقفت عشان حاجة تانية كانت بتعدّل نفس البيانات.',
    action: 'جرّب تاني.',
    retryable: true,
  },
  internal: {
    message: 'خطأ من ناحية فايربيز نفسه.',
    action: 'جرّب تاني بعد شوية.',
    retryable: true,
  },
}

/** رسائل المتصفح الشائعة التي لا تحمل رمزًا. */
function fromPlainMessage(text: string): UserFacingError | null {
  const lower = text.toLowerCase()

  if (lower.includes('offline') || lower.includes('network') || lower.includes('failed to fetch')) {
    return {
      message: 'مفيش اتصال بالإنترنت.',
      action: 'اللي اتحفظ على جهازك موجود. وصّل الإنترنت وهيتزامن لوحده.',
      retryable: true,
      code: 'network',
    }
  }

  if (lower.includes('quota') || lower.includes('storage')) {
    return {
      message: 'مساحة التخزين على جهازك ممتلئة، فمقدرناش نحفظ نسخة محلية.',
      action: 'فضّي مساحة من المتصفح أو الجهاز.',
      retryable: true,
      code: 'storage',
    }
  }

  return null
}

/**
 * يحوّل أي خطأ إلى رسالة للمستخدم.
 *
 * الأخطاء التي كُتبت رسائلها بالعربية أصلًا (`AuthError`، أخطاء المجال،
 * أخطاء الاستيراد) **تمر كما هي** — لا تُترجم مرتين ولا يُعاد صياغتها.
 */
export function toUserFacingError(cause: unknown): UserFacingError {
  if (typeof cause === 'object' && cause !== null && 'code' in cause) {
    const raw = String((cause as { code: unknown }).code)
    // فايرستور يرسل "firestore/permission-denied" أو "permission-denied"
    const code = raw.includes('/') ? raw.slice(raw.indexOf('/') + 1) : raw
    const known = FIRESTORE_MESSAGES[code]
    if (known) return { ...known, code: raw }
  }

  const text = cause instanceof Error ? cause.message : String(cause)

  // الرسائل العربية مكتوبة أصلًا للمستخدم — تُعرض كما هي
  if (/[؀-ۿ]/.test(text)) {
    return { message: text, action: null, retryable: false, code: 'app' }
  }

  const plain = fromPlainMessage(text)
  if (plain) return plain

  return {
    message: 'حصل خطأ مش متوقع.',
    action: 'جرّب تاني. لو فضل، ابعتلي التفاصيل اللي تحت.',
    retryable: true,
    code: text.slice(0, 200),
  }
}
