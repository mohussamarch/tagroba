package app.masroufy.usecase

import app.masroufy.core.ClassificationRule
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SAUDI_PACK
import app.masroufy.core.matchesText
import app.masroufy.core.normalizeText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.seed.BundledSeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** الدفعة التانية من محلات دراهم (OVERRIDES §109 تعديل — مراجعة المالك 2026-10-23): وضع «مطابقة تامة» للكلمات القصيرة + تصليح مرة واحدة. */
class RepairDarahimRules2Test {
    private val clock = FixedClock("2026-10-23T10:00:00.000Z")

    @Test fun shortWordsAreExactAndOthersStayContains() = runBlocking<Unit> {
        val batch = BundledSeeds.load(SAUDI_PACK).rules.filter { it.priority >= DARAHIM_RULES2_FROM_PRIORITY }
        val exact = batch.filter { it.matchMode == RuleMatchMode.EXACT }
        assertEquals(setOf("nmc", "سلام", "huna", "هنا"), exact.map { it.matchText }.toSet())
        assertTrue(batch.filter { it !in exact }.all { it.matchMode == RuleMatchMode.CONTAINS })
        val salam = exact.single { it.matchText == "سلام" }
        assertTrue(matchesText(salam.matchText, salam.matchMode, "سلام"))
        assertFalse(matchesText(salam.matchText, salam.matchMode, "مخبز السلام"))
        assertFalse(matchesText(salam.matchText, salam.matchMode, "سلام للمخابز"))
    }

    @Test fun secondRepairAddsOnlyTheNewBatchExactlyOnce() = runBlocking<Unit> {
        val seed = BundledSeeds.load(SAUDI_PACK)
        val first = seed.rules.filter { it.priority < DARAHIM_RULES2_FROM_PRIORITY }
        val batch = seed.rules.filter { it.priority >= DARAHIM_RULES2_FROM_PRIORITY }
        assertTrue(batch.size in 20..29, "الدفعة التانية في البذرة: ${batch.size}")
        val rules = MemoryRuleRepository(first) // حساب قديم: عنده الدفعة الأولى بس
        val settings = MemoryUserSettingsStore()
        val repair = RepairDarahimRules(
            "sa", MemoryCategoryRepository(seed.categories), rules, BundledSeeds, SAUDI_PACK, settings, clock,
            DARAHIM_RULES2_REPAIR, DARAHIM_RULES2_FROM_PRIORITY, Int.MAX_VALUE,
        )
        assertEquals(batch.map { normalizeText(it.matchText) }.distinct().count { w -> first.none { normalizeText(it.matchText) == w } }, repair.run())
        val after = rules.listAll()
        first.forEach { r -> assertEquals(r, after.single { it.id == r.id }, "القديمة ما اتغيرتش") }
        val added = after.filter { a -> first.none { it.id == a.id } }
        assertTrue(added.all { a -> batch.any { it.matchText == a.matchText && it.matchMode == a.matchMode } })
        assertEquals(setOf("nmc", "سلام", "huna", "هنا"), added.filter { it.matchMode == RuleMatchMode.EXACT }.map { it.matchText }.toSet())
        assertEquals(listOf("repairDone.darahimRules2.sa"), settings.listAll().map { it.key })
        assertNull(repair.run(), "مرة واحدة بس")
        assertEquals(after, rules.listAll())
    }

    @Test fun ownersRuleWithTheSameWordWins() = runBlocking<Unit> {
        val seed = BundledSeeds.load(SAUDI_PACK)
        val batch = seed.rules.filter { it.priority >= DARAHIM_RULES2_FROM_PRIORITY }
        val clash = batch.first()
        val otherCat = seed.categories.first { it.id != clash.categoryId && it.parentId != null }.id
        val owner = ClassificationRule("rule-owner-9", 400, " ${clash.matchText.uppercase()} ", RuleMatchMode.CONTAINS, otherCat, true)
        val rules = MemoryRuleRepository(seed.rules.filter { it.priority < DARAHIM_RULES2_FROM_PRIORITY } + owner)
        val repair = RepairDarahimRules(
            "sa", MemoryCategoryRepository(seed.categories), rules, BundledSeeds, SAUDI_PACK, MemoryUserSettingsStore(), clock,
            DARAHIM_RULES2_REPAIR, DARAHIM_RULES2_FROM_PRIORITY, Int.MAX_VALUE,
        )
        repair.run()
        val after = rules.listAll()
        assertEquals(owner, after.single { it.id == owner.id })
        assertEquals(1, after.count { normalizeText(it.matchText) == normalizeText(clash.matchText) }, "كلمة المالك هي اللي تمشي")
        assertTrue(after.filter { it.id != owner.id && it.priority > 400 }.size >= batch.size - 1)
    }
}
