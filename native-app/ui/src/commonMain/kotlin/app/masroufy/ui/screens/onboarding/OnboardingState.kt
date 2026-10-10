package app.masroufy.ui.screens.onboarding

import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import app.masroufy.usecase.OnboardingInput

/**
 * رحلة «أول دقيقة» بعد الدخول (`Onboarding` — §63) كحالة **نقية** (بتتختبر على JVM). الترحيب والدخول قبلها في `SignInFlow` (هيكل التطبيق)،
 * فالكروت هنا: الشكل ⇒ البلد ⇒ يوم الراتب ⇒ مصدر العمليات ⇒ جاهز. شريط التقدم ٦ أجزاء زي النموذج والدخول أول جزء خلصان.
 * مفيش ولا رقم بيتحسب هنا: يوم الراتب اختيار، والحفظ كله في `OnboardAccount.finish` (التأكد قبل أي كتابة وتسجيل الانتهاء آخر حاجة).
 */
enum class OnbStep(val position: Int) { LOOK(2), COUNTRY(3), PAYDAY(4), SOURCE(5), READY(6) }

const val ONB_SEGMENTS = 6

/** مصدر العمليات (الخطوة ٥) — ما بيتخزنش: بيحدد بس الشاشة اللي بعد «ادخل التطبيق» (رسايل البنك ⇒ شاشتها والإذن هناك). */
enum class OnbSource { SMS, FILE, LATER }

/** الرد الأخضر تحت الكارت بعد كل إجابة (عنوان + سطر). */
enum class OnbReply { LOOK, COUNTRY_SA, COUNTRY_EG, DAY, DAY_LATER, SMS, FILE, LATER }

data class OnbAnswers(
    val look: Int? = null,
    val countryCode: String? = null,
    /** يوم اختاره بنفسه — null مع [dayLater] = «ليس ثابتًا». */
    val day: Int? = null,
    val dayLater: Boolean = false,
    val source: OnbSource? = null,
)

data class OnbState(val step: OnbStep, val answers: OnbAnswers = OnbAnswers(), val reply: OnbReply? = null)

/** الأجزاء المليانة: اللي خلص قبل الكارت ده + الكارت نفسه لو اتجاوب (و«جاهز» = كله). */
fun filledSegments(s: OnbState): Int = if (s.step == OnbStep.READY) ONB_SEGMENTS else s.step.position - 1 + if (s.reply != null) 1 else 0

/** الرجوع: من يوم الراتب ومصدر العمليات بس (النموذج: بعد الحساب ما يتعمل مفيش رجوع للدخول، والبلد ما بيرجعش). */
fun canBack(s: OnbState): Boolean = s.step == OnbStep.PAYDAY || s.step == OnbStep.SOURCE

/** التخطي في كله ما عدا الدخول والبلد (ملف الهاندأوف §7) — ومش وقت ما الرد ظاهر. */
fun canSkip(s: OnbState): Boolean = s.reply == null && (s.step == OnbStep.LOOK || s.step == OnbStep.PAYDAY || s.step == OnbStep.SOURCE)

fun next(s: OnbState): OnbState = OnbState(OnbStep.entries[(s.step.ordinal + 1).coerceAtMost(OnbStep.entries.size - 1)], s.answers)

fun back(s: OnbState): OnbState = OnbState(OnbStep.entries[(s.step.ordinal - 1).coerceAtLeast(0)], s.answers)

/** «تخطَّ»: الشكل ⇒ الكارت اللي بعده على طول · يوم الراتب ⇒ «ليس ثابتًا» · المصدر ⇒ «لاحقًا». */
fun skip(s: OnbState): OnbState = when (s.step) {
    OnbStep.LOOK -> next(s)
    OnbStep.PAYDAY -> answer(s, s.answers.copy(day = null, dayLater = true), OnbReply.DAY_LATER)
    OnbStep.SOURCE -> answer(s, s.answers.copy(source = OnbSource.LATER), OnbReply.LATER)
    else -> s
}

fun answer(s: OnbState, answers: OnbAnswers, reply: OnbReply): OnbState = s.copy(answers = answers, reply = reply)

fun pickLook(s: OnbState, look: Int) = answer(s, s.answers.copy(look = look), OnbReply.LOOK)

fun pickCountry(s: OnbState, code: String) =
    answer(s, s.answers.copy(countryCode = code), if (code.equals("EG", true)) OnbReply.COUNTRY_EG else OnbReply.COUNTRY_SA)

fun pickDay(s: OnbState, day: Int) = answer(s, s.answers.copy(day = day, dayLater = false), OnbReply.DAY)

fun pickSource(s: OnbState, source: OnbSource) = answer(
    s, s.answers.copy(source = source),
    when (source) {
        OnbSource.SMS -> OnbReply.SMS
        OnbSource.FILE -> OnbReply.FILE
        OnbSource.LATER -> OnbReply.LATER
    },
)

/** عنوان الرد وسطره — [payday] = اليوم اللي هيفضل لو قال «ليس ثابتًا» (المحفوظ في ملفه، مش «أول الشهر»). */
fun replyText(r: OnbReply, answers: OnbAnswers, payday: Int): Pair<String, String> = when (r) {
    OnbReply.LOOK -> t(TextKey.ONB_R_LOOK_T) to t(TextKey.ONB_R_LOOK_L)
    OnbReply.COUNTRY_SA -> t(TextKey.ONB_OK) to t(TextKey.ONB_R_SA_L)
    OnbReply.COUNTRY_EG -> t(TextKey.ONB_OK) to t(TextKey.ONB_R_EG_L)
    OnbReply.DAY -> t(TextKey.ONB_R_DAY_T) to t(TextKey.ONB_R_DAY_L, sentenceNumber(answers.day ?: payday))
    OnbReply.DAY_LATER -> t(TextKey.ONB_R_LATER_T) to t(TextKey.ONB_R_DAY_LATER_L, sentenceNumber(payday))
    OnbReply.SMS -> t(TextKey.ONB_OK) to t(TextKey.ONB_R_SMS_L)
    OnbReply.FILE -> t(TextKey.ONB_OK) to t(TextKey.ONB_R_FILE_L)
    OnbReply.LATER -> t(TextKey.ONB_OK) to t(TextKey.ONB_R_SRC_LATER_L)
}

fun nextLabel(s: OnbState): String = t(if (s.step == OnbStep.SOURCE) TextKey.ONB_DONE_NEXT else TextKey.ONB_NEXT)

fun stepTitle(step: OnbStep): TextKey = when (step) {
    OnbStep.LOOK -> TextKey.ONB_LOOK_TITLE
    OnbStep.COUNTRY -> TextKey.ONB_COUNTRY_TITLE
    OnbStep.PAYDAY -> TextKey.ONB_PAY_TITLE
    OnbStep.SOURCE -> TextKey.ONB_SRC_TITLE
    OnbStep.READY -> TextKey.ONB_READY_TITLE
}

fun stepSub(step: OnbStep): String = when (step) {
    OnbStep.LOOK -> t(TextKey.ONB_LOOK_SUB)
    OnbStep.COUNTRY -> t(TextKey.ONB_COUNTRY_SUB)
    OnbStep.PAYDAY -> t(TextKey.ONB_PAY_SUB)
    OnbStep.SOURCE -> t(TextKey.ONB_SRC_SUB)
    OnbStep.READY -> t(TextKey.ONB_READY_SUB, sentenceNumber(10))
}

/** سطر اختيار (البلد أو المصدر): [off] = مقفول وسببه في [sub] (النموذج: رسايل البنك على الآيفون). */
data class OnbOption(val id: String, val label: String, val sub: String, val mark: String?, val off: Boolean)

/**
 * البلدين (السعودية ومصر): السعودية موجودة دايمًا (المساحة الافتراضية). مصر بتنفع لو مساحتها مفتوحة في الحساب أو إنشاء بلد متاح
 * ([canCreate] — نقطة ربط `SpacesAdmin`)، وإلا مقفولة بسببها بدل ما نختارها ونفشل بعدين.
 */
fun countryOptions(open: List<Space>, canCreate: Boolean): List<OnbOption> = listOf(
    OnbOption("SA", t(TextKey.COUNTRY_SA), t(TextKey.ONB_CUR_SAR), currencySymbol(Currency.SAR), off = false),
    open.any { it.countryCode.equals("EG", true) }.let { has ->
        val on = has || canCreate
        OnbOption("EG", t(TextKey.COUNTRY_EG), if (on) t(TextKey.ONB_CUR_EGP) else t(TextKey.ONB_COUNTRY_NOT_YET), currencySymbol(Currency.EGP), off = !on)
    },
)

/** مصادر العمليات: رسايل البنك على أندرويد بس ([smsReadable] = false على الآيفون ⇒ مقفولة بسببها — حالة «غير متاح» في النموذج). */
fun sourceOptions(smsReadable: Boolean): List<OnbOption> = listOf(
    OnbOption(OnbSource.SMS.name, t(TextKey.ONB_SRC_SMS), t(if (smsReadable) TextKey.ONB_SRC_SMS_SUB else TextKey.ONB_SRC_SMS_IOS), null, off = !smsReadable),
    OnbOption(OnbSource.FILE.name, t(TextKey.ONB_SRC_FILE), t(TextKey.ONB_SRC_FILE_SUB), null, off = false),
    OnbOption(OnbSource.LATER.name, t(TextKey.ONB_SRC_LATER), t(TextKey.ONB_SRC_LATER_SUB), null, off = false),
)

/** اللي بيروح لـ`OnboardAccount.finish`: الملف بيوم الراتب المختار (أو زي ما هو لو «ليس ثابتًا»/اتخطى) — من غير كاش ولا ديون (مش في رحلة §63). */
fun finishInput(profile: UserProfile, answers: OnbAnswers): OnboardingInput =
    OnboardingInput(profile.copy(payday = answers.day ?: profile.payday), cashMinor = null, debts = emptyList())

/** البلد اللي هنتبدل لها بعد الحفظ: مصر اختارها ومساحتها مفتوحة ومش هي الشغالة. null = نفضل مكاننا. */
fun spaceToOpen(answers: OnbAnswers, open: List<Space>, activeId: String): String? {
    val code = answers.countryCode ?: return null
    return open.firstOrNull { it.countryCode.equals(code, true) }?.id?.takeIf { it != activeId }
}
