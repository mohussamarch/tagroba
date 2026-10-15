package app.masroufy.core

/**
 * **الاسم كامل** للطرف التاني في التحويل (قايمة المالك §6.5 بند 8 — «الأسامي بتظهر أول كام حرف بس»).
 *
 * المفتاح ([TransferPartyRef.key]) بيفضل **زي ما هو بالظبط** (القرارات المحفوظة في فايربيز متسجّلة بيه — لو اتغيّر، المالك
 * هيتسأل تاني عن أطراف قرّر فيها)، والإصلاح هنا في **الاسم المعروض** بس ([TransferPartyRef.label]):
 * - كشف الراجحي القديم بحروف العرض المقلوبة: الاسم فيه «-» أو «.» («آل-سالم» · «م. سالم») كان بيتقلب **حتة حتة** فبيطلع أوله بس
 *   («عبدالله آل» من «عبدالله آل-سالم») — دلوقتي الجملة كلها بتتقلب مرة واحدة ([logicalArabicJoined]) والاسم بيتمد لورا لحد أوله.
 * - محفظة مصر «المسجل باسم محمد أ. السيد على رقم …»: كان بيقف عند أول نقطة («محمد أ») — دلوقتي النقطة بعد حرف لوحده (اختصار)
 *   مش آخر الاسم، **بس** لو «على رقم» جاية بعده في نفس السطر (من غير كده ممكن ياخد جملة الرصيد اللي بعده — ما بنخمّنش).
 * الاسم الأطول بيتقبل **بس** لو فيه حروف الاسم القديم كلها (توسيع، عمره ما بيبقى اسم تاني).
 *
 * **البنك نفسه قص الاسم** ([NameCut]): نجوم/نقط حجب («عبدالله محم****») · «…» أو «..» في الآخر · كشيدة في الآخر («عبدالـ») ·
 * اسم QNB مالي خانته كلها (12 حرف). الشاشة بتعرضه زي ما جه ومعاه «الاسم ناقص من رسالة البنك»، والمالك يكمّله مرة واحدة بربطه بشخص.
 */
enum class NameCut {
    /** رسالة البنك جابت الاسم ناقص. */
    SMS,

    /** كشف البنك (الملف) جاب الاسم ناقص. */
    STATEMENT,
}

private const val PRESENTATION = "\uFB50-\uFDFF\uFE70-\uFEFF"

/** حروف العرض + الفواصل المحايدة جواها (مسافة · «-» · «.» · «'») ⇒ جملة واحدة بتتقلب مرة واحدة. */
private val JOINED_RUN = Regex("[$PRESENTATION]+(?:[ .'\\-]+[$PRESENTATION]+)*")

/**
 * زي [logicalArabic] بس الجملة المقلوبة بتتقلب **كلها** حتى لو جواها «-» أو «.» (قاعدة الاتجاه: العلامة المحايدة بين حرفين
 * عربي بتمشي معاهم). الأرقام بتفصل (ترتيبها جوه الجملة من الشمال لليمين). للاسم المعروض بس — المفتاح من [logicalArabic].
 */
internal fun logicalArabicJoined(text: String): String =
    if (!JOINED_RUN.containsMatchIn(text)) text else JOINED_RUN.replace(text) { it.value.reversed() }

/** حرف اسم: عربي (من غير الأرقام العربي) · حروف العرض · لاتيني · مسافة. */
private fun isNameChar(c: Char): Boolean =
    c == ' ' || c in 'A'..'Z' || c in 'a'..'z' || (c in '\u0600'..'\u06FF' && c !in '\u0660'..'\u0669') ||
        c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'

/** أقصى طول للاسم المعروض (المفتاح بياخد أول 60 زي ما كان). */
internal const val PARTY_LABEL_MAX = 120

/**
 * الاسم قبل «W-/FRACCT/» في كشف الراجحي: من أول الاسم القديم ([start]) بنرجع لورا على حروف الاسم والمسافات، ونعدّي «-» و«.»
 * و«'» **بس** لو قبلها حرف اسم مش مسافة (مش رقم ولا «:» ولا «|»). بيرجّع null لو مفيش حاجة زيادة.
 */
internal fun extendNameBackward(text: String, start: Int, end: Int): String? {
    var i = start
    while (i > 0 && end - i < PARTY_LABEL_MAX) {
        val c = text[i - 1]
        when {
            isNameChar(c) -> i--
            c in ".'-" && i >= 2 && isNameChar(text[i - 2]) && text[i - 2] != ' ' -> i--
            else -> break
        }
    }
    if (i == start) return null
    return JsText.trim(text.substring(i, end).trimStart('.', '\'', '-', ' '))
}

private const val H = "[ \\t]"
private val EG_REGISTERED_FULL = Regex("المسجل${H}*ب[إا]سم$H*([^\\n]+?)$H*على${H}*رقم")
private val INITIAL_DOT = Regex("(?:^|$H)[A-Za-z\\u0600-\\u06FF]\\.")

/** «المسجل باسم <اسم فيه حرف واختصار بنقطة> على رقم»: الاسم لحد «على رقم» — لو فيه نقطة بعد حرف لوحده بس (مش آخر جملة). */
internal fun egyptRegisteredFullName(text: String): String? {
    val name = EG_REGISTERED_FULL.find(text)?.groupValues?.get(1)?.let(JsText::trim) ?: return null
    if (!INITIAL_DOT.containsMatchIn(name)) return null
    // كل نقطة لازم تبقى بعد حرف لوحده — غير كده النقطة آخر جملة والاسم مش واضح
    val dots = name.count { it == '.' }
    return name.takeIf { dots == INITIAL_DOT.findAll(name).count() }
}

/** الأطول بيتقبل لو فيه حروف الاسم القديم كلها (توسيع بس). */
internal fun fullerLabel(legacy: String, candidate: String?): String {
    val full = candidate?.let(JsText::trim)?.takeIf { it.isNotEmpty() } ?: return legacy
    val old = normalizeCompact(legacy)
    val new = normalizeCompact(full)
    return if (new.length > old.length && new.contains(old)) full.take(PARTY_LABEL_MAX) else legacy
}

/** نجوم/نقط حجب · «…» أو «..» في الآخر · كشيدة في آخر الاسم. */
private val CUT_MARKS = Regex("[*•]{2,}|…\\s*$|\\.{2,}\\s*$|ـ\\s*$")

/** البنك قص الاسم أو حجب جزء منه (مش تخمين — علامة مكتوبة في الاسم نفسه). */
fun looksCutByBank(label: String): Boolean = CUT_MARKS.containsMatchIn(label)

private val TRAILING_MARKS = Regex("[\\s*•.…ـ]+$")

/** اسم الشخص الجديد من اسم ناقص: من غير علامات القص في الآخر («عبدالله محم****» ⇒ «عبدالله محم») — المالك يكمّله. */
fun personNameFrom(label: String): String = JsText.trim(label.replace(TRAILING_MARKS, "")).ifEmpty { label }

/** الاسم المعروض الأطول + علامة القص لو البنك قصّه. [qnbFull] = اسم QNB مالي خانته كلها. */
internal fun withFullName(ref: TransferPartyRef, candidate: String?, source: NameCut, qnbFull: Boolean = false): TransferPartyRef {
    val label = if (ref.label.startsWith("••••")) ref.label else fullerLabel(ref.label, candidate)
    val cut = if (qnbFull || (!ref.label.startsWith("••••") && looksCutByBank(label))) source else null
    return if (label == ref.label && cut == ref.cut) ref else ref.copy(label = label, cut = cut)
}

/** الاسم المقترح للشخص الجديد: المقصوص من غير علامات القص (المالك يكمّله) · غير كده زي ما جه بالظبط («JR.» بتفضل بنقطتها). */
fun suggestedPersonName(ref: TransferPartyRef): String = if (ref.cut != null) personNameFrom(ref.label) else ref.label
