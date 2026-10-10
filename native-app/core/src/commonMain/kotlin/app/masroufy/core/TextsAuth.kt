package app.masroufy.core

/**
 * رسايل تسجيل الدخول باللغتين — العربي **نفس نص التطبيق الحالي** (`FirebaseAuthAdapter.ts`) حرف بحرف.
 * ملف لوحده عشان حد الـ300 سطر؛ بيتدمج في `Texts.kt`.
 */
internal val EGYPTIAN_AUTH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.AUTH_INVALID_EMAIL to "الإيميل مش مكتوب صح",
    TextKey.AUTH_USER_DISABLED to "الحساب ده متوقف",
    TextKey.AUTH_USER_NOT_FOUND to "مفيش حساب بالإيميل ده",
    TextKey.AUTH_WRONG_PASSWORD to "كلمة السر غلط",
    TextKey.AUTH_INVALID_CREDENTIAL to "الإيميل أو كلمة السر غلط",
    TextKey.AUTH_EMAIL_IN_USE to "الإيميل ده مسجّل قبل كده — جرّب تسجيل الدخول",
    TextKey.AUTH_WEAK_PASSWORD to "كلمة السر قصيرة — لازم 6 حروف على الأقل",
    TextKey.AUTH_MISSING_PASSWORD to "اكتب كلمة السر",
    TextKey.AUTH_TOO_MANY_REQUESTS to "محاولات كتير. استنى شوية وجرّب تاني",
    TextKey.AUTH_NETWORK to "مفيش إنترنت. اتأكد من الاتصال وجرّب تاني",
    TextKey.AUTH_NOT_ALLOWED to "طريقة الدخول دي مش مفعّلة في المشروع",
    TextKey.AUTH_UNEXPECTED to "حصل خطأ مش متوقع ({0})",
    TextKey.AUTH_GOOGLE_PENDING to "دخول جوجل لسه قيد التجهيز في التطبيق الجديد. الدخول بالإيميل وكلمة السر متاح.",
)

internal val ENGLISH_AUTH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.AUTH_INVALID_EMAIL to "The email address isn't valid",
    TextKey.AUTH_USER_DISABLED to "This account is disabled",
    TextKey.AUTH_USER_NOT_FOUND to "There's no account with this email",
    TextKey.AUTH_WRONG_PASSWORD to "Wrong password",
    TextKey.AUTH_INVALID_CREDENTIAL to "Wrong email or password",
    TextKey.AUTH_EMAIL_IN_USE to "This email is already registered — try signing in",
    TextKey.AUTH_WEAK_PASSWORD to "The password is too short — at least 6 characters",
    TextKey.AUTH_MISSING_PASSWORD to "Enter your password",
    TextKey.AUTH_TOO_MANY_REQUESTS to "Too many attempts. Wait a little and try again",
    TextKey.AUTH_NETWORK to "No internet connection. Check it and try again",
    TextKey.AUTH_NOT_ALLOWED to "This sign-in method isn't enabled for the project",
    TextKey.AUTH_UNEXPECTED to "Something unexpected went wrong ({0})",
    TextKey.AUTH_GOOGLE_PENDING to "Google sign-in isn't ready in the new app yet. Email and password sign-in works.",
)

/** رسائل الدخول بالفصحى المختصرة (OVERRIDES §66). */
internal val MSA_AUTH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.AUTH_INVALID_EMAIL to "البريد الإلكتروني غير صحيح",
    TextKey.AUTH_USER_DISABLED to "هذا الحساب موقوف",
    TextKey.AUTH_USER_NOT_FOUND to "لا حساب بهذا البريد",
    TextKey.AUTH_WRONG_PASSWORD to "كلمة المرور خاطئة",
    TextKey.AUTH_INVALID_CREDENTIAL to "البريد أو كلمة المرور خاطئة",
    TextKey.AUTH_EMAIL_IN_USE to "البريد مسجّل سابقًا — جرّب تسجيل الدخول",
    TextKey.AUTH_WEAK_PASSWORD to "كلمة المرور قصيرة — 6 أحرف على الأقل",
    TextKey.AUTH_MISSING_PASSWORD to "أدخل كلمة المرور",
    TextKey.AUTH_TOO_MANY_REQUESTS to "محاولات كثيرة. انتظر قليلًا ثم أعد المحاولة",
    TextKey.AUTH_NETWORK to "لا اتصال بالإنترنت. تحقق من الاتصال وأعد المحاولة",
    TextKey.AUTH_NOT_ALLOWED to "طريقة الدخول هذه غير مفعّلة في المشروع",
    TextKey.AUTH_UNEXPECTED to "حدث خطأ غير متوقع ({0})",
    TextKey.AUTH_GOOGLE_PENDING to "الدخول بحساب Google قيد التجهيز في التطبيق الجديد. الدخول بالبريد وكلمة المرور متاح.",
)
