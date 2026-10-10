package app.masroufy.core

/**
 * الكلام بعد التوحيد وكل اللي اتطلّع منه مرة واحدة: سؤال ولا لأ · المبالغ · الفترة · يوم المصروف · الأسامي. القواعد ([understandAssist])
 * بتقرا من هنا بس.
 */
class AssistSignals(val raw: String, val lexicon: AssistLexicon) {
    val normalized: String = assistNormalize(raw)
    val tokens: List<String> = splitPunctuation(assistTokens(normalized))
    val money: AssistMoneyScan = scanMoney(tokens)
    val period: AssistPeriod? = detectAssistPeriod(tokens.joinToString(" "), tokens)
    val dayOffset: Int? = detectSpendDayOffset(raw, tokens)
    val entities: List<AssistEntity> = filterStopNames(findEntities(tokens, lexicon), tokens)

    /** كلمة سؤال: نفسها أو بحرف واحد قبلها («وكام» · «بكم» · «لفين») — **مش بعد «ال»** («الفين» = ألفين مش «فين»). */
    val question: Boolean = '?' in normalized || tokens.any { t -> shortForms(t).any { it in QUESTION_WORDS } } || has(QUESTION_PHRASES)

    /** سؤال عن مكان: «فين/وين/أين/where» لوحدها أو بعد «و» — «وصل لفين» = وصل لحد فين (سؤال كمية، مش مكان). */
    val asksWhere: Boolean = tokens.any { t -> (t in WHERE_WORDS) || (t.startsWith("و") && t.drop(1) in WHERE_WORDS) }

    /** جذر من [stems] في أي كلمة. */
    fun has(v: Vocab): Boolean = v.single.any { s -> tokens.any { if (v.exact) tokenIsWord(it, s) else tokenHasStem(it, s, fuzzy = v.fuzzy) } } ||
        v.phrases.any { containsPhrase(tokens, it) }

    fun hasWord(words: Set<String>): Boolean = tokens.any { t -> cliticForms(t).any { it in words } }

    fun entity(type: AssistEntityType): AssistEntity? = entities.ofType(type).firstOrNull { !it.generic } ?: entities.ofType(type).firstOrNull()

    fun specific(type: AssistEntityType): AssistEntity? = entities.ofType(type).firstOrNull { !it.generic }

    val hasAmount: Boolean get() = money.amounts.isNotEmpty()

    private companion object {
        /** «احمد،ساره» و«قهوه.بنزين» ⇒ كلمتين (النقطة والفاصلة جوه الأرقام بس بتفضل). «ر.س» و«ج.م» زي ما هم. */
        fun splitPunctuation(tokens: List<String>): List<String> = tokens.flatMap { t ->
            if (t == "ر.س" || t == "ج.م" || t.none { it == ',' || it == '.' }) listOf(t)
            else Regex("""(?<=\D)[.,]|[.,](?=\D)""").split(t).filter { it.isNotEmpty() }
        }

        /** أسامي أشخاص هي كمان كلمات عادية («على» = «علي» بعد التوحيد · «عيد» · «كريم» تطبيق التاكسي). */
        val STOP_NAMES = setOf("علي", "عيد", "كريم", "امل", "نور")
        val PERSON_CUES = setOf("مع", "عند", "افتح", "ملف", "صفحه", "وريني", "بين", "وبين", "ل", "و")

        fun filterStopNames(found: List<AssistEntity>, tokens: List<String>): List<AssistEntity> = found.filter { e ->
            if (e.type != AssistEntityType.PERSON || e.end - e.start != 1) return@filter true
            val t = tokens[e.start]
            if (t !in STOP_NAMES) return@filter true
            // «لعلي» · «وعلي» (فيه أداة) أو قبلها «مع/عند/افتح…» أو الكلام كله الاسم ⇒ شخص
            tokens.size == 1 || tokens.getOrNull(e.start - 1)?.let { it in PERSON_CUES } == true
        }
    }
}

/**
 * قايمة كلمات: جذور كلمة واحدة + عبارات. [fuzzy] = يسمح بغلطة إملائية في الجذور الطويلة. [exact] = الكلمة نفسها بس (من غير لواحق) —
 * للكلمات القصيرة اللي بتبدأ بيها كلمات تانية («كم» جوه «كمّل» · «عند» جوه «عندي»).
 */
class Vocab(words: List<String>, val fuzzy: Boolean = true, val exact: Boolean = false) {
    private val norm = words.map { assistTokens(assistNormalize(it)) }.filter { it.isNotEmpty() }
    val single: List<String> = norm.filter { it.size == 1 }.map { it[0] }
    val phrases: List<List<String>> = norm.filter { it.size > 1 }
}

fun vocab(vararg words: String, fuzzy: Boolean = true, exact: Boolean = false) = Vocab(words.toList(), fuzzy, exact)

internal val QUESTION_WORDS = setOf(
    "كم", "كام", "بكم", "بكام", "قديش", "امتي", "متي", "ايمتي", "امته", "فين", "وين", "اين", "هل", "ليه", "ليش", "لماذا", "ايش", "وش", "شو", "شنو", "مين", "ماذا",
    "كيف", "ازاي", "شلون", "how", "what", "when", "where", "who", "which", "whats", "is", "am", "are", "do", "does", "did", "can", "should", "anything", "any",
).map(::assistNormalize).toSet()

internal val WHERE_WORDS = setOf("فين", "وين", "اين", "where")

/** الكلمة وهي من غير حرف واحد لاصق في أولها (و · ف · ب · ل · ك) — من غير «ال». */
internal fun shortForms(token: String): List<String> =
    if (token.length > 2 && token[0] in "وفبلك") listOf(token, token.substring(1)) else listOf(token)

private val QUESTION_PHRASES = vocab("ايه اللي", "وش اللي", "عامل ايه", "عامله ايه", "ما المستحق", "ما الذي", "ما هو", "ما هي", "ما اكبر", "ما اكثر")
