package app.masroufy.core

/**
 * المناسبات العامة في التقويم (OVERRIDES §65) — **بداية رمضان · عيد الفطر · عيد الأضحى** من تقويم أم القرى (`Hijri.kt` — نفس
 * كود الزكاة، من غير مكتبة جديدة): 1 رمضان · 1 شوال · 10 ذو الحجة.
 * ⚠️ الميعاد الرسمي بيتعلن **بالرؤية** (في السعودية ومصر) وممكن يفرق يوم عن الحساب ⇒ السطر بيتعلّم «تقريبي» (`approximate`)
 * ومصدره مكتوب (اختيار Claude — المالك يقدر يغيّره).
 * **بداية الدراسة مش مناسبة عامة** (رد المالك §65): المستخدم بيحطها بإيده كحدث نوعه «دخول مدرسة» (`LifeEventKind.SCHOOL`)،
 * وبتظهر في التقويم سطر حدث عادي — مفيش جدول رسمي ولا تخمين.
 */
enum class PublicOccasionKind(val wire: String, val hijriMonth: Int, val hijriDay: Int, val labelKey: TextKey) {
    RAMADAN_START("ramadan_start", 9, 1, TextKey.PUBLIC_RAMADAN_START),
    EID_AL_FITR("eid_al_fitr", 10, 1, TextKey.PUBLIC_EID_AL_FITR),
    EID_AL_ADHA("eid_al_adha", 12, 10, TextKey.PUBLIC_EID_AL_ADHA),
    ;

    val label: String get() = uiText(labelKey)
}

/** مناسبة عامة في يوم. المعرّف ثابت (النوع + السنة الهجري) ⇒ الحجز عليها بيفضل لنفس المناسبة. */
data class PublicOccasion(val kind: PublicOccasionKind, val date: IsoDate, val hijriYear: Int) {
    val id: Id get() = "${kind.wire}-$hijriYear"
}

/** البلاد اللي المناسبات دي بتتحسب ليها (السعودية ومصر — البلاد اللي التطبيق بيدعمها، §40). */
val PUBLIC_OCCASION_COUNTRIES = setOf("SA", "EG")

/** المصدر اللي بيتكتب جنب السطر. */
fun publicOccasionSourceNote(): String = uiText(TextKey.PUBLIC_OCCASION_SOURCE)

/**
 * المناسبات العامة من [from] لحد [to] لبلد [countryCode]. بلد مش مدعومة ⇒ فاضية (مش تخمين).
 * بيمشي يوم يوم ويسأل `hijriOf` — كام مية نداء للسنة، ومن غير ما نحتاج تحويل عكسي جديد على كل جهاز.
 * يوم برا مدى تقويم أم القرى (الجافا بيرفضه) بيتساب.
 */
fun publicOccasions(from: IsoDate, to: IsoDate, countryCode: String?): List<PublicOccasion> {
    if (countryCode?.uppercase() !in PUBLIC_OCCASION_COUNTRIES) return emptyList()
    val out = mutableListOf<PublicOccasion>()
    val last = toDayNumber(parseIsoDate(to))
    var day = toDayNumber(parseIsoDate(from))
    while (day <= last) {
        val date = dayNumberToIso(day)
        val h = runCatching { hijriOf(date) }.getOrNull()
        if (h != null) {
            PublicOccasionKind.entries.firstOrNull { it.hijriMonth == h.month && it.hijriDay == h.day }?.let { out += PublicOccasion(it, date, h.year) }
        }
        day++
    }
    return out
}
