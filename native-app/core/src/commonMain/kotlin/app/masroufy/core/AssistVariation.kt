package app.masroufy.core

/**
 * الردود المتنوعة (§78-٦ «ردود متنوعة حسب الوقت واللي حصل») — **قواعد ثابتة من غير عشوائية** (التصميم `replyVariation`):
 * - اللي بيتنوّع **الافتتاحيات والكلام العادي بس**. كل نوع إجابة ليه جملة واحدة ثابتة، وأرقامها من `formatMoney` زي الشاشات.
 *   «غير متاح» عمرها ما بتتنوّع، و«تقريبي» بتظهر بس لو المصدر قال كده.
 * - اللغة من **بلد المساحة الشغالة** (§66): مصر ⇒ مصري، السعودية وأي بلد ⇒ فصحى، والإنجليزي جدوله. لهجة المستخدم ما بتغيّرش الرد.
 * - الاختيار: الصيغ اللي بتنطبق بترتيبها ⇒ نشيل اللي اتقالت آخر مرة في نفس الخانة (لو فيه غيرها) ⇒ رقم = (يوم + عدد ردود المحادثة)
 *   باقي القسمة على العدد. نفس الصيغة عمرها ما بتتقال مرتين ورا بعض.
 */
data class Variant(val key: String, val text: TextKey)

fun pickVariant(variants: List<Variant>, previousKey: String?, dayNumber: Int, botCount: Int): Variant? {
    if (variants.isEmpty()) return null
    val pool = if (variants.size > 1) variants.filter { it.key != previousKey } else variants
    if (pool.isEmpty()) return null
    return pool[(dayNumber + botCount).mod(pool.size)]
}

/** رقم اليوم (للاختيار) من تاريخ البلد. */
fun variantDay(today: IsoDate): Int = toDayNumber(parseIsoDate(today))

/** خانة التحية أول الشات: من ٤ الفجر لـ١٢ الضهر «صباح الخير»، وغير كده «مساء الخير» (قاعدة النموذج). */
fun isMorning(hour: Int): Boolean = hour in 4..11

/** «تصبح على خير» من ١٢ بالليل لـ٤ الفجر. */
fun isLateNight(hour: Int): Boolean = hour in 0..3

object AssistVariants {
    val INTRO_DEFAULT = listOf(
        Variant("INTRO_1", TextKey.ASSIST_INTRO_1),
        Variant("INTRO_2", TextKey.ASSIST_INTRO_2),
        Variant("INTRO_3", TextKey.ASSIST_INTRO_3),
    )
    val RECORDED = listOf(
        Variant("REC_1", TextKey.ASSIST_RECORDED_1),
        Variant("REC_2", TextKey.ASSIST_RECORDED_2),
        Variant("REC_3", TextKey.ASSIST_RECORDED_3),
    )
    val THANKS = listOf(Variant("THX_1", TextKey.ASSIST_THANKS_1), Variant("THX_2", TextKey.ASSIST_THANKS_2), Variant("THX_3", TextKey.ASSIST_THANKS_3))
    val OFFER = listOf(Variant("OFFER_1", TextKey.ASSIST_OFFER_1), Variant("OFFER_2", TextKey.ASSIST_OFFER_2))
    val HOW_ARE_YOU = listOf(Variant("HAY_1", TextKey.ASSIST_HOW_ARE_YOU_1), Variant("HAY_2", TextKey.ASSIST_HOW_ARE_YOU_2))
    val BYE = listOf(Variant("BYE_1", TextKey.ASSIST_BYE_1), Variant("BYE_2", TextKey.ASSIST_BYE_2))
    val PRAISE = listOf(Variant("PRAISE_1", TextKey.ASSIST_PRAISE_1), Variant("PRAISE_2", TextKey.ASSIST_PRAISE_2))
    val UNKNOWN = listOf(Variant("U1", TextKey.ASSIST_UNKNOWN_1), Variant("U2", TextKey.ASSIST_UNKNOWN_2))
}
