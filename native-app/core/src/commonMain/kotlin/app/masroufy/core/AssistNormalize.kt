package app.masroufy.core

/**
 * توحيد كلام المساعد قبل أي فهم (OVERRIDES §78 + مطابقة النموذج التفاعلي على فرع التصميم): من غير تشكيل ولا تطويل ·
 * أ/إ/آ/ٱ ⇒ ا · ة ⇒ ه · ى ⇒ ي · الأرقام العربي والفارسي ⇒ 0-9 (وفاصلة الكسر ٫ ⇒ «.» وفاصل الآلاف ٬ ⇒ «,») · اللاتيني صغير ·
 * الحرف المتكرر ٣ مرات أو أكتر بيتلم (كاااام ⇒ كام). **مش `normalizeText`** (ده للمطابقة مع الكشوف ومبيكبّرش اللاتيني بس): المساعد
 * محتاج النقطة والفاصلة جوه الأرقام وعلامة السؤال.
 */
fun assistNormalize(raw: String): String {
    val out = StringBuilder(raw.length)
    for (c in raw) {
        val code = c.code
        when {
            code in 0x064B..0x065F || code == 0x0670 || code in 0x0610..0x061A || code in 0x06D6..0x06ED || code == 0x0640 -> Unit
            code in 0x200B..0x200F || code in 0x202A..0x202E || code in 0x2066..0x2069 || code == 0xFEFF -> Unit
            c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ' -> out.append('ا')
            c == 'ة' -> out.append('ه')
            c == 'ؤ' -> out.append('و')
            c == 'ئ' -> out.append('ي')
            // رمز الريال (القديم والجديد) ⇒ كلمة «ريال»
            code == 0xFDFC || code == 0x20C1 -> out.append(" ريال ")
            // رموز العملات اللي مش حروف ⇒ كلمتها (عشان ما تضيعش) · «٪» العربي ⇒ «%»
            c == '€' -> out.append(" يورو ")
            c == '£' -> out.append(" استرليني ")
            code == 0x066A -> out.append('%')
            c == 'ى' -> out.append('ي')
            code in 0x0660..0x0669 -> out.append('0' + (code - 0x0660))
            code in 0x06F0..0x06F9 -> out.append('0' + (code - 0x06F0))
            code == 0x066B -> out.append('.')
            code == 0x066C || c == '،' -> out.append(',')
            c == '؟' -> out.append('?')
            c in 'A'..'Z' -> out.append(c.lowercaseChar())
            c.isLetterOrDigit() || c == '.' || c == ',' || c == '?' || c == '%' || c == '$' -> out.append(c)
            else -> out.append(' ')
        }
    }
    // الهمزة في آخر الكلمة بتتشال (اختيار Claude — زيادة على قايمة التصميم): المصري والخليجي بيكتبوها من غيرها («الكهربا» · «الغدا» · «العشا»)
    return squeezeRepeats(out.toString()).split(' ').filter { it.isNotEmpty() }
        .map { w -> if (w.length > 2 && w.endsWith('ء')) w.dropLast(1) else w }
        .joinToString(" ")
}

/** حرف عربي متكرر ٣ مرات أو أكتر ورا بعض ⇒ مرة واحدة (التمطيط في الكتابة: «شكراااا» · «كااام»). الأرقام زي ما هي. */
private fun squeezeRepeats(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        var j = i
        while (j < text.length && text[j] == c) j++
        val run = j - i
        if (run >= 3 && c.code in 0x0621..0x064A) out.append(c) else repeat(run) { out.append(c) }
        i = j
    }
    return out.toString()
}

/**
 * الكلمات بعد التوحيد — النقطة والفاصلة وعلامة السؤال بتتشال من أطراف الكلمة (النقطة والفاصلة **جوه** الرقم بتفضل: «1,500.50»).
 * «%» و«$» بيفضلوا (نسبة · دولار).
 */
fun assistTokens(normalized: String): List<String> = normalized.split(' ').mapNotNull { t ->
    val trimmed = t.trim('.', ',', '?')
    trimmed.ifEmpty { null }
}

/**
 * أدوات بتلزق في أول الكلمة: «و» «ف» «ب» «ل» «ك» «ال» و«لل» و«عال» (= على الـ — مصري وخليجي). الكلمة بتتقارن بنفسها وبكل شكل
 * بعد شيل أداة (لو فضل حرفين على الأقل) — فـ«بالراجحي» = «راجحي»، و«بقاله» بتفضل «بقاله» (الشكل الأصلي من ضمن الاحتمالات).
 */
private val CLITICS = listOf("وبال", "وال", "بال", "فال", "كال", "عال", "لل", "ال", "و", "ف", "ب", "ل", "ك")

fun cliticForms(token: String): List<String> {
    val forms = LinkedHashSet<String>()
    forms += token
    for (p in CLITICS) if (token.startsWith(p) && token.length - p.length >= 2) {
        val rest = token.substring(p.length)
        forms += rest
        if (rest.startsWith("ال") && rest.length - 2 >= 2) forms += rest.substring(2)
    }
    return forms.toList()
}

/** مسافة التعديل (حذف · إضافة · تبديل · قلب حرفين جنب بعض) — للأخطاء الإملائية في الكلمات الطويلة بس. */
internal fun editDistance(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
        d[i][j] = v
    }
    return d[a.length][b.length]
}

/**
 * أقصر جذر بيتسمح فيه بغلطة إملائية واحدة — الأقصر منه لازم يتكتب صح. **٦ مش ٥ زي التصميم (سبب مكتوب):** الجذر هنا بيطابق أول الكلمة
 * (أي لاحقة بعده مقبولة)، فالغلطة على جذر من ٥ حروف كانت بتلم كلمات تانية: «بقاله» ⇒ «بقالي» · «ميرسي» ⇒ «ميراث» · «اصرف» ⇒ «ساصرف»
 * (اتمسكوا في اختبار الكتالوج).
 */
const val ASSIST_FUZZY_MIN_STEM = 6

/**
 * الكلمة بتبدأ بالجذر (بأي أداة قبلها)، أو — لو الجذر عربي وطويل — بتبدأ بشكل قريب منه بغلطة واحدة (تبديل · زيادة · نقص في النص ·
 * قلب حرفين). **النقص من آخر الجذر بس مش غلطة** («ديون» مش «ديوني» · «اخبار» مش «اخبارك» — كلمة تانية). الإنجليزي من غير غلطات.
 */
fun tokenHasStem(token: String, stem: String, fuzzy: Boolean = true): Boolean {
    val forms = cliticForms(token)
    if (forms.any { it.startsWith(stem) }) return true
    if (!fuzzy || stem.length < ASSIST_FUZZY_MIN_STEM || stem[0] !in 'ء'..'ي') return false
    val target = loose(stem)
    return forms.any { f ->
        f.length >= stem.length - 1 && (stem.length - 1..stem.length + 1).any { n ->
            n <= f.length && !stem.startsWith(f.substring(0, n)) && editDistance(loose(f.substring(0, n)), target) <= 1
        }
    }
}

/** الشكل «المرن» للمقارنة التقريبية بس (التصميم): ذ⇒ز · ث⇒س · ظ⇒ض. */
fun loose(word: String): String = word.replace('ذ', 'ز').replace('ث', 'س').replace('ظ', 'ض')

/** الكلمة نفسها (بأي أداة قبلها) — من غير لواحق. */
fun tokenIsWord(token: String, word: String): Boolean = cliticForms(token).any { it == word }
