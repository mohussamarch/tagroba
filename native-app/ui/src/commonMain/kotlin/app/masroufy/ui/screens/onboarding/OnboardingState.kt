package app.masroufy.ui.screens.onboarding

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.core.bankByKey
import app.masroufy.core.bankLabel
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.AddWalletInput
import app.masroufy.usecase.OnboardingInput

/**
 * رحلة «أول دقيقة» بعد الدخول (`Onboarding` — §63) كحالة **نقية** (بتتختبر على JVM). الترحيب والدخول قبلها في `SignInFlow` (هيكل التطبيق)،
 * فالكروت هنا: الشكل ⇒ البلد ⇒ [الكاش ⇒ البنك] ⇒ يوم الراتب ⇒ مصدر العمليات ⇒ جاهز. شريط التقدم: الدخول أول جزء خلصان + جزء لكل كارت.
 * **كارتين الفلوس** (§81 — المالك: «الاول يسأله معاك كام كاش وبعدين يسأله عندك حساب بنكي؟ ويكون في اضافة او تخطي») للحساب الجديد
 * بس ([OnbState.fresh]) وبعد البلد (العملة والبنوك بتاعة البلد): لو اختار بلد غير بلد الحساب ([OnbState.home]) الكارتين بيستخبوا.
 * مفيش ولا رقم بيتحسب هنا: المبالغ اللي اتكتبت بتروح زي ما هي لـ`OnboardAccount.finish` (التأكد قبل أي كتابة وتسجيل الانتهاء آخر حاجة).
 */
enum class OnbStep { LOOK, COUNTRY, CASH, BANK, PAYDAY, SOURCE, READY }

/** عدد الأجزاء من غير كارتين الفلوس (الحساب القديم · بلد تانية) — الدخول + 5 كروت. */
const val ONB_SEGMENTS = 6

/** مصدر العمليات (الخطوة 5) — ما بيتخزنش: بيحدد بس الشاشة اللي بعد «ادخل التطبيق» (رسايل البنك ⇒ شاشتها والإذن هناك). */
enum class OnbSource { SMS, FILE, LATER }

/** الرد الأخضر تحت الكارت بعد كل إجابة (عنوان + سطر). */
enum class OnbReply { LOOK, COUNTRY_SA, COUNTRY_EG, DAY, DAY_LATER, SMS, FILE, LATER, CASH, CASH_SKIP, BANK, BANK_SKIP }

/** البنك من «عندك حساب بنكي؟»: مفتاحه في الدليل · آخر 4 (أو null) · رصيده دلوقتي بالهللة (null = ما كتبش ⇒ صفر من النهارده). */
data class OnbBank(val bankKey: String, val last4: String?, val openingMinor: Halalas?)

data class OnbAnswers(
    val look: Int? = null,
    val countryCode: String? = null,
    /** يوم اختاره بنفسه — null مع [dayLater] = «ليس ثابتًا». */
    val day: Int? = null,
    val dayLater: Boolean = false,
    val source: OnbSource? = null,
    /** «معاك كام كاش؟» — null = اتخطى (أو لسه). */
    val cashMinor: Halalas? = null,
    /** «عندك حساب بنكي؟» — null = اتخطى (أو لسه). */
    val bank: OnbBank? = null,
)

/** [fresh] = حساب جديد خالص (`OnboardingStart.freshAccount`) · [home] = بلد الحساب اللي الفلوس هتتكتب فيه. */
data class OnbState(
    val step: OnbStep,
    val answers: OnbAnswers = OnbAnswers(),
    val reply: OnbReply? = null,
    val fresh: Boolean = false,
    val home: String? = null,
)

/** كارتين الفلوس ظاهرين؟ حساب جديد + البلد المختارة (لو اختار) هي بلد الحساب. */
fun money(s: OnbState): Boolean = s.fresh && (s.answers.countryCode == null || s.home == null || s.answers.countryCode.equals(s.home, true))

/** الكروت بالترتيب للحالة دي. */
fun steps(s: OnbState): List<OnbStep> = if (money(s)) OnbStep.entries else OnbStep.entries.filter { it != OnbStep.CASH && it != OnbStep.BANK }

/** كل الأجزاء (الدخول + الكروت) ورقم الكارت ده فيهم. */
fun segmentCount(s: OnbState): Int = steps(s).size + 1

fun stepNumber(s: OnbState): Int = steps(s).indexOf(s.step).coerceAtLeast(0) + 2

/** الأجزاء المليانة: اللي خلص قبل الكارت ده + الكارت نفسه لو اتجاوب (و«جاهز» = كله). */
fun filledSegments(s: OnbState): Int = if (s.step == OnbStep.READY) segmentCount(s) else stepNumber(s) - 1 + if (s.reply != null) 1 else 0

/** الرجوع: من البنك ويوم الراتب ومصدر العمليات بس (النموذج: بعد الحساب ما يتعمل مفيش رجوع للدخول، والبلد ما بيرجعش). */
fun canBack(s: OnbState): Boolean = s.step == OnbStep.BANK || s.step == OnbStep.PAYDAY || s.step == OnbStep.SOURCE

/** التخطي في كله ما عدا الدخول والبلد (ملف الهاندأوف §7) — ومش وقت ما الرد ظاهر. */
fun canSkip(s: OnbState): Boolean = s.reply == null && s.step != OnbStep.COUNTRY && s.step != OnbStep.READY

private fun move(s: OnbState, by: Int): OnbState {
    val list = steps(s)
    val at = list.indexOf(s.step).coerceAtLeast(0)
    return s.copy(step = list[(at + by).coerceIn(0, list.size - 1)], reply = null)
}

fun next(s: OnbState): OnbState = move(s, 1)

fun back(s: OnbState): OnbState = move(s, -1)

/** «تخطَّ»/«تخطي»: الشكل ⇒ الكارت اللي بعده على طول · الكاش والبنك ⇒ من غير · يوم الراتب ⇒ «ليس ثابتًا» · المصدر ⇒ «لاحقًا». */
fun skip(s: OnbState): OnbState = when (s.step) {
    OnbStep.LOOK -> next(s)
    OnbStep.CASH -> answer(s, s.answers.copy(cashMinor = null), OnbReply.CASH_SKIP)
    OnbStep.BANK -> answer(s, s.answers.copy(bank = null), OnbReply.BANK_SKIP)
    OnbStep.PAYDAY -> answer(s, s.answers.copy(day = null, dayLater = true), OnbReply.DAY_LATER)
    OnbStep.SOURCE -> answer(s, s.answers.copy(source = OnbSource.LATER), OnbReply.LATER)
    else -> s
}

fun answer(s: OnbState, answers: OnbAnswers, reply: OnbReply): OnbState = s.copy(answers = answers, reply = reply)

fun pickLook(s: OnbState, look: Int) = answer(s, s.answers.copy(look = look), OnbReply.LOOK)

fun pickCountry(s: OnbState, code: String) =
    answer(s, s.answers.copy(countryCode = code), if (code.equals("EG", true)) OnbReply.COUNTRY_EG else OnbReply.COUNTRY_SA)

/** «إضافة» في «معاك كام كاش؟» — [minor] بالهللة (من الخانة زي ما اتكتبت؛ صفر مسموح). */
fun addCash(s: OnbState, minor: Halalas) = answer(s, s.answers.copy(cashMinor = minor), OnbReply.CASH)

/** «إضافة» في «عندك حساب بنكي؟» بعد اختيار البنك من الدليل. */
fun addBank(s: OnbState, bank: OnbBank) = answer(s, s.answers.copy(bank = bank), OnbReply.BANK)

fun pickDay(s: OnbState, day: Int) = answer(s, s.answers.copy(day = day, dayLater = false), OnbReply.DAY)

fun pickSource(s: OnbState, source: OnbSource) = answer(
    s, s.answers.copy(source = source),
    when (source) {
        OnbSource.SMS -> OnbReply.SMS
        OnbSource.FILE -> OnbReply.FILE
        OnbSource.LATER -> OnbReply.LATER
    },
)

/** اسم البنك المختار + آخر 4 («مصرف الراجحي •••• 1234»). */
fun bankLine(b: OnbBank): String = (bankByKey(b.bankKey)?.let(::bankLabel) ?: b.bankKey) + (b.last4?.let { " •••• $it" } ?: "")

/** عنوان الرد وسطره — [payday] = اليوم اللي هيفضل لو قال «ليس ثابتًا» (المحفوظ في ملفه، مش «أول الشهر») · [currency] لمبلغ الكاش. */
fun replyText(r: OnbReply, answers: OnbAnswers, payday: Int, currency: Currency = Currency.SAR): Pair<String, String> = when (r) {
    OnbReply.LOOK -> t(UiKey.ONB_R_LOOK_T) to t(UiKey.ONB_R_LOOK_L)
    OnbReply.COUNTRY_SA -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_SA_L)
    OnbReply.COUNTRY_EG -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_EG_L)
    OnbReply.CASH -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_CASH_L, amountLabel(answers.cashMinor ?: 0, currency))
    OnbReply.CASH_SKIP -> t(UiKey.ONB_R_LATER_T) to t(UiKey.ONB_R_CASH_SKIP_L)
    OnbReply.BANK -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_BANK_L, answers.bank?.let(::bankLine).orEmpty())
    OnbReply.BANK_SKIP -> t(UiKey.ONB_R_LATER_T) to t(UiKey.ONB_R_BANK_SKIP_L)
    OnbReply.DAY -> t(UiKey.ONB_R_DAY_T) to t(UiKey.ONB_R_DAY_L, sentenceNumber(answers.day ?: payday))
    OnbReply.DAY_LATER -> t(UiKey.ONB_R_LATER_T) to t(UiKey.ONB_R_DAY_LATER_L, sentenceNumber(payday))
    OnbReply.SMS -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_SMS_L)
    OnbReply.FILE -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_FILE_L)
    OnbReply.LATER -> t(UiKey.ONB_OK) to t(UiKey.ONB_R_SRC_LATER_L)
}

fun nextLabel(s: OnbState): String = t(if (s.step == OnbStep.SOURCE) UiKey.ONB_DONE_NEXT else UiKey.ONB_NEXT)

fun stepTitle(step: OnbStep): TextRef = when (step) {
    OnbStep.LOOK -> UiKey.ONB_LOOK_TITLE
    OnbStep.COUNTRY -> UiKey.ONB_COUNTRY_TITLE
    OnbStep.CASH -> UiKey.ONB_CASH_TITLE
    OnbStep.BANK -> UiKey.ONB_BANK_TITLE
    OnbStep.PAYDAY -> UiKey.ONB_PAY_TITLE
    OnbStep.SOURCE -> UiKey.ONB_SRC_TITLE
    OnbStep.READY -> UiKey.ONB_READY_TITLE
}

fun stepSub(step: OnbStep): String = when (step) {
    OnbStep.LOOK -> t(UiKey.ONB_LOOK_SUB)
    OnbStep.COUNTRY -> t(UiKey.ONB_COUNTRY_SUB)
    OnbStep.CASH -> t(UiKey.ONB_CASH_SUB)
    OnbStep.BANK -> t(UiKey.ONB_BANK_SUB)
    OnbStep.PAYDAY -> t(UiKey.ONB_PAY_SUB)
    OnbStep.SOURCE -> t(UiKey.ONB_SRC_SUB)
    OnbStep.READY -> t(UiKey.ONB_READY_SUB, sentenceNumber(10))
}

/** سطر اختيار (البلد أو المصدر): [off] = مقفول وسببه في [sub] (النموذج: رسايل البنك على الآيفون). */
data class OnbOption(val id: String, val label: String, val sub: String, val mark: String?, val off: Boolean)

/**
 * البلدين (السعودية ومصر): السعودية موجودة دايمًا (المساحة الافتراضية). مصر بتنفع لو مساحتها مفتوحة في الحساب أو إنشاء بلد متاح
 * ([canCreate] — نقطة ربط `SpacesAdmin`)، وإلا مقفولة بسببها بدل ما نختارها ونفشل بعدين.
 */
fun countryOptions(open: List<Space>, canCreate: Boolean): List<OnbOption> = listOf(
    OnbOption("SA", t(TextKey.COUNTRY_SA), t(UiKey.ONB_CUR_SAR), currencySymbol(Currency.SAR), off = false),
    open.any { it.countryCode.equals("EG", true) }.let { has ->
        val on = has || canCreate
        OnbOption("EG", t(TextKey.COUNTRY_EG), if (on) t(UiKey.ONB_CUR_EGP) else t(UiKey.ONB_COUNTRY_NOT_YET), currencySymbol(Currency.EGP), off = !on)
    },
)

/** مصادر العمليات: رسايل البنك على أندرويد بس ([smsReadable] = false على الآيفون ⇒ مقفولة بسببها — حالة «غير متاح» في النموذج). */
fun sourceOptions(smsReadable: Boolean): List<OnbOption> = listOf(
    OnbOption(OnbSource.SMS.name, t(UiKey.ONB_SRC_SMS), t(if (smsReadable) UiKey.ONB_SRC_SMS_SUB else UiKey.ONB_SRC_SMS_IOS), null, off = !smsReadable),
    OnbOption(OnbSource.FILE.name, t(UiKey.ONB_SRC_FILE), t(UiKey.ONB_SRC_FILE_SUB), null, off = false),
    OnbOption(OnbSource.LATER.name, t(UiKey.ONB_SRC_LATER), t(UiKey.ONB_SRC_LATER_SUB), null, off = false),
)

/**
 * اللي بيروح لـ`OnboardAccount.finish`: الملف بيوم الراتب المختار (أو زي ما هو لو «ليس ثابتًا»/اتخطى) + **الكاش والبنك لو الكارتين ظهروا**
 * ([money]) — البنك بنوعه واسمه من الدليل، ورصيده من النهارده (تاريخ الرصيد بيتحط في `ManageWallets`). من غير ديون (مش في الرحلة).
 */
fun finishInput(profile: UserProfile, answers: OnbAnswers, withMoney: Boolean = false): OnboardingInput {
    val bank = answers.bank?.takeIf { withMoney }?.let { b ->
        bankByKey(b.bankKey)?.let { AddWalletInput(it.walletKind, bankLabel(it), b.last4, b.openingMinor, null, it.key) }
    }
    return OnboardingInput(profile.copy(payday = answers.day ?: profile.payday), cashMinor = answers.cashMinor?.takeIf { withMoney }, debts = emptyList(), bank = bank)
}

/** البلد اللي هنتبدل لها بعد الحفظ: مصر اختارها ومساحتها مفتوحة ومش هي الشغالة. null = نفضل مكاننا. */
fun spaceToOpen(answers: OnbAnswers, open: List<Space>, activeId: String): String? {
    val code = answers.countryCode ?: return null
    return open.firstOrNull { it.countryCode.equals(code, true) }?.id?.takeIf { it != activeId }
}
