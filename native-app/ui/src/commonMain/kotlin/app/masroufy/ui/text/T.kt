package app.masroufy.ui.text

import app.masroufy.core.TextRef
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.core.formatMoney
import app.masroufy.core.isUiText
import app.masroufy.core.uiText

/**
 * **كل نص في الشاشات من هنا** — خرائط النصوص في `core` (`TextRef` + الفصحى · المصري · الإنجليزي — OVERRIDES §40 · §66).
 * ممنوع نص عربي أو إنجليزي مكتوب جوه شاشة. مفتاح جديد: في `TextKeys.kt` + الجداول التلاتة (`TextsShell*.kt` أو ملف المنطقة)،
 * و`TextsTest`/`ArabicVariantsTest` بيتأكدوا إن ليه نص في الثلاثة ومفيش مصري في الفصحى.
 */
fun t(key: TextRef, vararg args: String): String = uiText(key, *args)

/**
 * رسالة فشل للمستخدم: رسالة الاستثناء **لو** هي نص من الجداول (تحقق حالة الاستخدام — `isUiText`)، وإلا [fallback].
 * رسائل فايرستور والشبكة (إنجليزي تقني) والرسائل الداخلية ما تطلعش على الشاشة أبدًا.
 */
fun failureText(error: Throwable, fallback: TextRef): String = error.message?.takeIf { isUiText(it) } ?: t(fallback)

/** مبلغ جوه جملة (بعملته) — `formatMoney` من `core` (المكان الوحيد اللي الهللة بتبقى ريال فيه). */
fun money(minor: Halalas, currency: Currency): String = formatMoney(minor, currency)

/** مبلغ من غير عملة («6,450.00»). */
fun amount(minor: Halalas, currency: Currency): String = formatMoney(minor, currency, showCurrency = false)

/** الشرطة اللي في أول المبلغ السالب ⇒ علامة الطرح الحقيقية «−» (U+2212 — DESIGN-SYSTEM «الخط والأرقام»). عرض بس. */
fun trueMinus(text: String): String = if (text.startsWith("-")) "−" + text.substring(1) else text
