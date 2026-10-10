package app.masroufy.core

/**
 * §82-1 (رد المالك 2026-10-10 — غير المقترح): «صرفت كام في رمضان؟» ⇒ **يسألك عن أول وآخر يوم** (مش من التقويم الهجري) — ونفس الكلام لأي
 * شهر هجري أو مناسبة بالاسم (شعبان · شوال · ذو الحجة · العيد · الحج …). الكارت جوه المحادثة فيه اختيار يومين، وبعدهم الإجابة من **نفس** حالة
 * استخدام الصرف/الدخل بالمدى ده ([AssistPeriodKind.BETWEEN]). من غير الاسم في الكلام ما فيش سؤال (الفترة اللي ما اتفهمتش بتفضل «مش فاهم»).
 */
private class SpokenOccasion(val arabic: String, val english: String, vararg spoken: String) {
    val phrases: List<List<String>> = spoken.map { assistTokens(assistNormalize(it)) }.filter { it.isNotEmpty() }
}

// العبارة الأطول الأول («عيد الفطر» قبل «العيد»). «صفر» لوحدها = رقم ⇒ «شهر صفر» بس. «عيد» لوحدها اسم شخص ⇒ «العيد».
private val OCCASIONS = listOf(
    SpokenOccasion("عيد الفطر", "Eid al-Fitr", "عيد الفطر", "العيد الصغير", "eid al fitr", "eid alfitr"),
    SpokenOccasion("عيد الأضحى", "Eid al-Adha", "عيد الأضحى", "عيد الاضحي", "العيد الكبير", "eid al adha", "eid aladha"),
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
    SpokenOccasion("العيد", "Eid", "العيد", "eid"),
)

/** كل كلمات أسامي المناسبات (بعد التوحيد) — كلمة منهم بعد «في» مش «موضوع مش معروف» لما المناسبة نفسها اتفهمت. */
private val OCCASION_WORDS: Set<String> = OCCASIONS.flatMap { o -> o.phrases.flatten() }.toSet()

/** الأسئلة اللي «من أول يوم لآخر يوم» بيجاوبها بنفس حالة الاستخدام: الصرف كله · تصنيف · محل · شخص · الدخل. */
val PERIOD_ASK_INTENTS: Set<AssistIntent> = setOf(
    AssistIntent.SPEND_TOTAL, AssistIntent.SPEND_CATEGORY, AssistIntent.SPEND_MERCHANT, AssistIntent.SPEND_PERSON, AssistIntent.INCOME,
)

/** اسم المناسبة اللي في الكلام (بلغة العرض) — أو null. كلمة جوه اسم معروف (شخص اسمه «رجب») ما بتتحسبش. */
fun occasionIn(s: AssistSignals): String? {
    val hit = OCCASIONS.firstOrNull { o -> o.phrases.any { p -> phraseOutsideEntities(s, p) } } ?: return null
    return if (Texts.language == Language.EN) hit.english else hit.arabic
}

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
