package app.masroufy.core

/**
 * §82-1 (رد المالك 2026-10-10 — غير المقترح): «صرفت كام في رمضان؟» ⇒ **يسألك عن أول وآخر يوم** (مش من التقويم الهجري) — ونفس الكلام لأي
 * شهر هجري أو مناسبة بالاسم (شعبان · شوال · ذو الحجة · العيد · الحج …). الكارت جوه المحادثة فيه اختيار يومين، وبعدهم الإجابة من **نفس** حالة
 * استخدام الصرف/الدخل بالمدى ده ([AssistPeriodKind.BETWEEN]). من غير الاسم في الكلام ما فيش سؤال (الفترة اللي ما اتفهمتش بتفضل «مش فاهم»).
 */
private class SpokenOccasion(val arabic: String, val english: String, vararg spoken: String) {
    val phrases: List<List<String>> = spoken.map { assistTokens(assistNormalize(it)) }.filter { it.isNotEmpty() }
}

private val EID_FITR = SpokenOccasion("عيد الفطر", "Eid al-Fitr", "عيد الفطر", "العيد الصغير", "eid al fitr", "eid alfitr")
private val EID_ADHA = SpokenOccasion("عيد الأضحى", "Eid al-Adha", "عيد الأضحى", "عيد الاضحي", "العيد الكبير", "eid al adha", "eid aladha")

/** «العيد» لوحده — مش معروف أنهي عيد (§89-2). */
private val ANY_EID = SpokenOccasion("العيد", "Eid", "العيد", "eid")

// العبارة الأطول الأول («عيد الفطر» قبل «العيد»). «صفر» لوحدها = رقم ⇒ «شهر صفر» بس. «عيد» لوحدها اسم شخص ⇒ «العيد».
private val OCCASIONS = listOf(
    EID_FITR,
    EID_ADHA,
    SpokenOccasion("ربيع الأول", "Rabi al-Awwal", "ربيع الأول", "ربيع اول"),
    SpokenOccasion("ربيع الآخر", "Rabi al-Akhir", "ربيع الآخر", "ربيع الثاني", "ربيع التاني"),
    SpokenOccasion("جمادى الأولى", "Jumada al-Ula", "جمادى الأولى", "جماد اول"),
    SpokenOccasion("جمادى الآخرة", "Jumada al-Akhirah", "جمادى الآخرة", "جمادى الثانية"),
    SpokenOccasion("ذو القعدة", "Dhu al-Qadah", "ذو القعدة", "ذي القعدة"),
    SpokenOccasion("ذو الحجة", "Dhu al-Hijjah", "ذو الحجة", "ذي الحجة"),
    SpokenOccasion("المولد النبوي", "the Prophet's birthday", "المولد النبوي", "المولد"),
    SpokenOccasion("صفر", "Safar", "شهر صفر"),
    SpokenOccasion("رمضان", "Ramadan", "رمضان", "ramadan", "ramadhan"),
    SpokenOccasion("شعبان", "Shaban", "شعبان", "shaban"),
    SpokenOccasion("شوال", "Shawwal", "شوال", "shawwal"),
    SpokenOccasion("محرم", "Muharram", "محرم", "muharram"),
    SpokenOccasion("رجب", "Rajab", "رجب", "rajab"),
    SpokenOccasion("عاشوراء", "Ashura", "عاشوراء", "ashura"),
    SpokenOccasion("الحج", "Hajj", "الحج", "hajj"),
    ANY_EID,
)

/** كل كلمات أسامي المناسبات (بعد التوحيد) — كلمة منهم بعد «في» مش «موضوع مش معروف» لما المناسبة نفسها اتفهمت. */
private val OCCASION_WORDS: Set<String> = OCCASIONS.flatMap { o -> o.phrases.flatten() }.toSet()

/** الأسئلة اللي «من أول يوم لآخر يوم» بيجاوبها بنفس حالة الاستخدام: الصرف كله · تصنيف · محل · شخص · الدخل. */
val PERIOD_ASK_INTENTS: Set<AssistIntent> = setOf(
    AssistIntent.SPEND_TOTAL, AssistIntent.SPEND_CATEGORY, AssistIntent.SPEND_MERCHANT, AssistIntent.SPEND_PERSON, AssistIntent.INCOME,
)

/** اسم المناسبة اللي في الكلام (بلغة العرض) — أو null. كلمة جوه اسم معروف (شخص اسمه «رجب») ما بتتحسبش. */
fun occasionIn(s: AssistSignals): String? = hitIn(s)?.let(::shown)

private fun hitIn(s: AssistSignals): SpokenOccasion? = OCCASIONS.firstOrNull { o -> o.phrases.any { p -> phraseOutsideEntities(s, p) } }

private fun shown(o: SpokenOccasion): String = if (Texts.language == Language.EN) o.english else o.arabic

/**
 * §89-2 (المالك 2026-10-11): «العيد» لوحده (من غير «الفطر» ولا «الأضحى») ⇒ **ما بنخمّنش** أنهي عيد. السعودية: سؤال باختيارين ([eidChoices])
 * وبعده نفس كارت اليومين للعيد اللي اتختار · مصر (وأي بلد تاني): كارت اليومين على طول. «عيد الفطر» و«عيد الأضحى» بالاسم زي ما هم.
 */
fun isAnyEid(s: AssistSignals): Boolean = hitIn(s) === ANY_EID

/** السعودية بس بتسأل «أنهي عيد؟» — غيرها كارت اليومين على طول (§89-2). */
fun asksWhichEid(countryCode: String?): Boolean = countryCode == "SA"

/** اختياري «أنهي عيد؟»: المعرّف الثابت ← الاسم بلغة العرض (اللي بيتحط في كارت اليومين). */
fun eidChoices(): List<Pair<String, String>> = listOf(EID_FITR_ID to shown(EID_FITR), EID_ADHA_ID to shown(EID_ADHA))

const val EID_FITR_ID = "eidFitr"
const val EID_ADHA_ID = "eidAdha"

/** الكلمة (بأداتها أو من غيرها) من أسامي المناسبات. */
internal fun isOccasionWord(word: String): Boolean = cliticForms(word).any { it in OCCASION_WORDS }

private fun phraseOutsideEntities(s: AssistSignals, phrase: List<String>): Boolean {
    val t = s.tokens
    if (phrase.isEmpty() || t.size < phrase.size) return false
    for (start in 0..t.size - phrase.size) {
        if (!containsPhrase(t.subList(start, start + phrase.size), phrase)) continue
        if (s.entities.none { e -> (start until start + phrase.size).any { it in e.start until e.end } }) return true
    }
    return false
}

/** المدى اللي المالك اختاره في الكارت: صالح · الأول قبل التاني أو نفسه · مش بعد النهارده. null = تمام، غير كده سبب الرفض. */
fun checkPickedRange(from: IsoDate, to: IsoDate, today: IsoDate): String? = when {
    !isValidIsoDate(from) || !isValidIsoDate(to) -> uiText(AskKey.CHAT_PERIOD_BAD)
    from > to -> uiText(AskKey.CHAT_PERIOD_ORDER)
    to > today -> uiText(AskKey.CHAT_PERIOD_FUTURE)
    else -> null
}
