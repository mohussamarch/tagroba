package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * كتالوج النوايا (OVERRIDES §78 — قايمة النوايا اللي اتصممت): **كل مثال في التصميم بيوصل لنيته**، وصيغ زيادة (فصحى · مصري · خليجي ·
 * إنجليزي · أخطاء إملائية · أرقام عربي) بالنية والاسم الأساسي والفترة والمبلغ. كل الأسامي مخترعة. الأخطاء بتطلع كلها مرة واحدة.
 */
class AssistCatalogueTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val lexicon by lazy { AssistTestLexicon.lexicon() }

    private fun check(cases: List<AssistCase>): List<String> = cases.mapNotNull { c ->
        val u = understandAssist(c.text, AssistUnderstandContext(lexicon, pendingCard = c.pending))
        val problems = buildList {
            if (u.wire != c.intent) add("النية ${u.wire}")
            if (c.subject != null && u.subject?.id != c.subject) add("الاسم ${u.subject?.id}")
            if (c.period != null && u.signals.period?.kind != c.period) add("الفترة ${u.signals.period?.kind}")
            if (c.amount != null && u.signals.money.amounts.firstOrNull()?.minor != c.amount) add("المبلغ ${u.signals.money.amounts.map { it.minor }}")
        }
        if (problems.isEmpty()) null else "«${c.text}» ⇒ متوقع ${c.intent}: ${problems.joinToString(" · ")}"
    }

    @Test
    fun everyDesignExampleReachesItsIntent() {
        val all = CATALOGUE_CASES_1 + CATALOGUE_CASES_2 + CATALOGUE_CASES_3
        assertTrue(all.size >= 500, "أمثلة التصميم كلها: ${all.size}")
        val failures = check(all.map { CATALOGUE_ADJUSTED[it.text]?.let { (intent, _) -> it.copy(intent = intent) } ?: it })
        assertTrue(failures.isEmpty(), "${failures.size} من ${all.size}:\n" + failures.joinToString("\n"))
    }

    @Test
    fun extraPhrasingsWithEntitiesPeriodsAndAmounts() {
        val all = EXTRA_CASES_1 + EXTRA_CASES_2
        assertTrue(all.size >= 150, "صيغ زيادة: ${all.size}")
        val failures = check(all)
        assertTrue(failures.isEmpty(), "${failures.size} من ${all.size}:\n" + failures.joinToString("\n"))
    }

    /** كل شاشة بيتنقل ليها بالكلام ليها ٥ صيغ على الأقل (فصحى · مصري · خليجي · إنجليزي) — من الكتالوج. */
    @Test
    fun everyNavigableScreenHasFivePhrasings() {
        val thin = SCREEN_WORDS.filter { (screen, words) -> !screen.needsEntity && words.size < 5 }.keys
        assertTrue(thin.isEmpty(), "شاشات صيغها أقل من ٥: $thin")
    }
}

/**
 * أمثلة في التصميم نيتها اتعدّلت هنا **بسبب مكتوب** (المثال عام ومالوش اسم من أسامي المستخدم المخترعة، أو التصميم نفسه بيقول يروح
 * لنية تانية في الحالة دي).
 */
internal val CATALOGUE_ADJUSTED: Map<String, Pair<String, String>> = mapOf(
    // «تفاصيل السهم» من غير سهم متسمي في الأسامي ⇒ شاشة الاستثمار (مفيش أصل يتفتح)
    "تفاصيل السهم" to ("nav.investment" to "مفيش أصل اسمه «السهم» في الأسامي"),
    "asset details" to ("nav.investment" to "من غير اسم أصل ⇒ الاستثمار"),
    "wallet details" to ("nav.wallets" to "من غير اسم محفظة ⇒ المحافظ"),
    "merchant page" to ("nav.rules" to "من غير اسم تاجر ⇒ القواعد والتجار"),
    // الأسامي العربي مكتوبة بحروف إنجليزي (الفرانكو) مش مفهومة في أول نسخة — سؤال المالك ١١
    "Ahmed's profile" to ("nav.account" to "«Ahmed» مش في الأسامي، و«profile» = ملفك"),
    "how much did I give Ahmed" to ("fallback.unknown" to "«Ahmed» مش في الأسامي ⇒ مش فاهم بصراحة بدل تخمين شخص"),
    "how much does Khaled owe me" to ("data.owed_to_me" to "«Khaled» مش في الأسامي ⇒ اللي ليك عند الناس كلهم"),
)
