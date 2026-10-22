package app.masroufy.usecase

import app.masroufy.core.ClassificationRule
import app.masroufy.core.EGYPT_PACK
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SAUDI_PACK
import app.masroufy.core.normalizeText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.seed.BundledSeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** r06-1e (OVERRIDES §110-2): قواعد محلات دراهم بتوصل للحسابات الموجودة — مرة واحدة، ومن غير ما تلمس قاعدة أو اختيار المالك. */
class RepairDarahimRulesTest {
    @Test fun oldAccountGetsDarahimRulesOnceWithoutTouchingOwnerChoices() = runBlocking<Unit> {
        val seed = BundledSeeds.load(SAUDI_PACK)
        val darahim = seed.rules.filter { it.priority >= DARAHIM_RULES_FROM_PRIORITY }
        assertTrue(darahim.size > 10, "قايمة دراهم في البذرة")
        val oldRules = seed.rules.filter { it.priority < DARAHIM_RULES_FROM_PRIORITY }
        // المالك عنده قاعدة بنفس كلمة واحدة من دراهم بتصنيف تاني (اختياره) + قاعدة عملها بأولوية عالية
        val clash = darahim.first()
        val otherCat = seed.categories.first { it.id != clash.categoryId && it.parentId != null }.id
        val ownerClash = ClassificationRule("rule-owner-1", 300, " ${clash.matchText.uppercase()} ", RuleMatchMode.CONTAINS, otherCat, true)
        val ownerOwn = ClassificationRule("rule-owner-2", 500, "TEST OWN SHOP", RuleMatchMode.CONTAINS, otherCat, false)
        val rules = MemoryRuleRepository(oldRules + ownerClash + ownerOwn)
        val settings = MemoryUserSettingsStore()
        val repair = RepairDarahimRules("sa", MemoryCategoryRepository(seed.categories), rules, BundledSeeds, SAUDI_PACK, settings, FixedClock("2026-10-22T10:00:00.000Z"))

        // كلمة بتتكرر بعد التطبيع (جوه القايمة أو مع قاعدة قديمة) بتتضاف مرة واحدة بس
        val oldWords = oldRules.map { normalizeText(it.matchText) }.toSet() + normalizeText(clash.matchText)
        val expected = darahim.map { normalizeText(it.matchText) }.distinct().count { it !in oldWords }
        assertEquals(expected, repair.run())
        val after = rules.listAll()
        assertEquals(ownerClash, after.single { it.id == ownerClash.id }, "قاعدة المالك زي ما هي")
        assertEquals(ownerOwn, after.single { it.id == ownerOwn.id })
        assertEquals(1, after.count { normalizeText(it.matchText) == normalizeText(clash.matchText) }, "الكلمة مش متكررة — كلمة المالك تمشي")
        oldRules.forEach { r -> assertEquals(r, after.single { it.id == r.id }, "القديمة ما اتغيرتش") }
        val added = after.filter { it.id !in (oldRules + ownerClash + ownerOwn).map { r -> r.id } }
        assertTrue(added.all { it.priority > 500 }, "الجديدة بعد آخر قاعدة — ما بتسبقش قاعدة المالك")
        assertEquals(listOf("repairDone.darahimRules.sa"), settings.listAll().map { it.key })

        assertNull(repair.run(), "مرة واحدة بس")
        rules.deleteMany(listOf(added.first().id))
        assertNull(repair.run(), "اللي المالك مسحه ما بيرجعش")
        assertTrue(rules.listAll().none { it.id == added.first().id })
    }

    @Test fun unseededSpaceIsLeftForTheSeedAndNotMarked() = runBlocking<Unit> {
        val settings = MemoryUserSettingsStore()
        val rules = MemoryRuleRepository(emptyList())
        val repair = RepairDarahimRules("eg", MemoryCategoryRepository(emptyList()), rules, BundledSeeds, EGYPT_PACK, settings, FixedClock("2026-10-22T10:00:00.000Z"))
        assertNull(repair.run())
        assertTrue(rules.listAll().isEmpty() && settings.listAll().isEmpty())
    }

    @Test fun ruleWhoseCategoryIsMissingIsSkipped() = runBlocking<Unit> {
        val seed = BundledSeeds.load(SAUDI_PACK)
        val darahim = seed.rules.filter { it.priority >= DARAHIM_RULES_FROM_PRIORITY }
        val gone = darahim.first().categoryId
        val out = missingDarahimRules(emptyList(), seed.rules, seed.categories.filter { it.id != gone })
        assertTrue(out.none { it.categoryId == gone } && out.isNotEmpty())
        assertTrue(out.all { it.priority >= 10 })
    }
}
