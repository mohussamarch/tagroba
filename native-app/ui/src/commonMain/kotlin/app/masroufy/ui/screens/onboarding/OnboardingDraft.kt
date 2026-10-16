package app.masroufy.ui.screens.onboarding

import androidx.compose.runtime.saveable.listSaver

/** الكارت اللي كان عليه يفضل لو الشاشة اتبنت من جديد (لفّ الجوال · رجوع من الخلفية) — «لو قفل في النص بيرجع لنفس الكارت». */
internal val OnbStateSaver = listSaver<OnbState, Any?>(
    save = { st ->
        val a = st.answers
        listOf(st.step.name, null, a.countryCode, a.day, a.dayLater, a.source?.name, st.reply?.name, st.fresh, st.home, a.cashMinor,
            a.bank?.bankKey, a.bank?.last4, a.bank?.openingMinor, st.otherCountryMoney, a.name, a.gender)
    },
    restore = { v ->
        OnbState(
            restoreStep(v[0]),
            OnbAnswers(
                v[2] as String?, v[3] as Int?, v[4] as Boolean, restoreSource(v[5]), v[9] as Long?,
                (v[10] as String?)?.let { OnbBank(it, v[11] as String?, v[12] as Long?) },
                name = v.getOrNull(14) as String?, gender = v.getOrNull(15) as String?,
            ),
            restoreReply(v[6]),
            fresh = v[7] as Boolean,
            home = v[8] as String?,
            otherCountryMoney = v.getOrNull(13) as Boolean? ?: false,
        )
    },
)

/**
 * r041b P2 (فحص المحاكي r041): زرار رجوع الجهاز جوه «أول تشغيل» كان بيشيل الرحلة ويفتح رئيسية نص مبنية (من غير اسم ولا كارت رصيد)،
 * والفتح التاني كان بيبدأ من الأول والإجابات كلها بتضيع. دلوقتي: الرجوع = الكارت اللي قبله ([systemBack])، وفي أول كارت التطبيق
 * بيتقفل (عمره ما يفتح الرئيسية)؛ والإجابات بتتكتب على الجهاز ([OnbDraftStore]) بعد كل تغيير ⇒ الفتح الجاي بيكمّل من نفس الكارت.
 * المسودة بتتمسح أول ما الرحلة تخلص. **مفيش فلوس بتتحسب هنا** — المبالغ اللي اتكتبت بالهللة زي ما هي.
 */
interface OnbDraftStore {
    /** آخر مسودة للحساب ده على الجهاز ده (null = مفيش). */
    fun read(): String?

    /** null = امسح (الرحلة خلصت). */
    fun write(value: String?)
}

/** رجوع الجهاز: الكارت اللي قبله (حتى «الكاش» و«جاهز» اللي مالهمش سهم رجوع فوق) · أول كارت ⇒ null (سيب الجهاز يقفل التطبيق). */
fun systemBack(s: OnbState): OnbState? = if (steps(s).indexOf(s.step) <= 0) null else back(s)

/** الحالة لسه زي ما اتولدت؟ (أول كارت من غير أي إجابة) ⇒ المسودة تقدر تحل محلها. حالة رجعت من الجهاز (لفّ الجوال) ⇒ هي اللي تكسب. */
fun pristine(s: OnbState): Boolean = s.step == OnbStep.NAME && s.reply == null && s.answers == OnbAnswers()

/** الحالة اللي الشاشة تبدأ بيها: المسودة لو الحالة الحالية لسه فاضية، وإلا الحالية. */
fun resumeFrom(current: OnbState, draft: OnbState?): OnbState = if (draft != null && pristine(current)) draft else current

private const val SEP = '\u001F'
private const val DRAFT_VERSION = "d1"

/** الحالة ⇒ سطر واحد (فاصل الوحدة بين الخانات؛ لو اتكتب في الاسم بيتحول مسافة). */
fun encodeDraft(s: OnbState): String {
    val a = s.answers
    fun str(v: String?) = v.orEmpty().replace(SEP, ' ')
    fun flag(v: Boolean) = if (v) "1" else "0"
    return listOf(
        DRAFT_VERSION, s.step.name, s.reply?.name.orEmpty(), flag(s.fresh), str(s.home), flag(s.otherCountryMoney),
        str(a.countryCode), a.day?.toString().orEmpty(), flag(a.dayLater), a.source?.name.orEmpty(), a.cashMinor?.toString().orEmpty(),
        str(a.bank?.bankKey), str(a.bank?.last4), a.bank?.openingMinor?.toString().orEmpty(), str(a.name), str(a.gender),
    ).joinToString(SEP.toString())
}

/** السطر ⇒ الحالة. أي حاجة مش مفهومة (نسخة تانية · ملف بايظ) ⇒ null (الرحلة تبدأ من أولها بدل ما توقع). */
fun decodeDraft(raw: String?): OnbState? {
    val v = raw?.split(SEP) ?: return null
    if (v.size != 16 || v[0] != DRAFT_VERSION) return null
    fun opt(i: Int) = v[i].takeIf { it.isNotEmpty() }
    return runCatching {
        OnbState(
            step = OnbStep.valueOf(v[1]),
            answers = OnbAnswers(
                countryCode = opt(6), day = opt(7)?.toInt(), dayLater = v[8] == "1",
                source = opt(9)?.let { OnbSource.valueOf(it) }, cashMinor = opt(10)?.toLong(),
                bank = opt(11)?.let { OnbBank(it, opt(12), opt(13)?.toLong()) },
                name = opt(14), gender = opt(15),
            ),
            reply = opt(2)?.let { OnbReply.valueOf(it) },
            fresh = v[3] == "1",
            home = opt(4),
            otherCountryMoney = v[5] == "1",
        )
    }.getOrNull()
}
