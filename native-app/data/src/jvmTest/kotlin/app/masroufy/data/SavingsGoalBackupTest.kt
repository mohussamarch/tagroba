package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.GOAL_BACKUP_GROUPS
import app.masroufy.core.GoalContribution
import app.masroufy.core.SavingsGoal
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** خطط الادخار وإيداعاتها (OVERRIDES §68) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class SavingsGoalBackupTest {
    private val manual = SavingsGoal("g-1", "سفر وهمي", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "2026-10-05T10:00:00.000Z", updatedAt = "2026-10-05T10:00:00.000Z")
    private val linked = manual.copy(id = "g-2", linkedWalletId = "w-1", linkedSpaceId = "default", archived = true)
    private val deposit = GoalContribution("gc-1", "g-1", "2026-02-01", 50_000, "2026-10-05T10:00:00.000Z", note = "إيداع وهمي")

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        return d
    }

    @Test
    fun `الخطة والإيداع بيتكتبوا ويتقروا والفاضي ما بيتكتبش`() {
        assertEquals(
            setOf("id", "name", "targetMinor", "currency", "startedAt", "deadline", "archived", "createdAt", "updatedAt"),
            roundTrip(SavingsGoalCodecs.savingsGoals, manual).keys, "اليدوية من غير محفظة",
        )
        assertEquals("w-1", roundTrip(SavingsGoalCodecs.savingsGoals, linked)["linkedWalletId"])
        assertEquals("إيداع وهمي", roundTrip(SavingsGoalCodecs.goalContributions, deposit)["note"])
        assertFalse("note" in roundTrip(SavingsGoalCodecs.goalContributions, deposit.copy(note = null)))
        assertEquals(49, DocumentCodecs.byGroup.size)
    }

    @Test
    fun `النجمة بتتكتب بس لو موجودة والمستند القديم بيتقري من غيرها`() {
        val starred = roundTrip(SavingsGoalCodecs.savingsGoals, manual.copy(starred = true))
        assertEquals(true, starred["starred"])
        assertFalse("starred" in roundTrip(SavingsGoalCodecs.savingsGoals, manual), "من غير نجمة ⇒ المستند هو هو زي قبلها")
        assertEquals(setOf("linkedWalletId", "linkedSpaceId", "starred"), SavingsGoalCodecs.savingsGoals.omittedFields(manual), "شيل النجمة بيمسح الحقل (merge)")
        // مستند اتكتب قبل النجمة (من غير الحقل خالص) ⇒ false
        val old = SavingsGoalCodecs.savingsGoals.toStore(manual).filterKeys { it != "starred" }
        assertEquals(false, SavingsGoalCodecs.savingsGoals.decode(old).starred)
        assertEquals(manual, SavingsGoalCodecs.savingsGoals.decode(old))
    }

    @Test
    fun `النجمة بتسافر في النسخة الشاملة ونوعها بيتفحص`() = runBlocking<Unit> {
        val source = account(withGoals = true).also { it.getValue("savingsGoals")[0] = SavingsGoalCodecs.savingsGoals.toStore(manual.copy(starred = true)) }
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-05T12:00:00.000Z")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(file.toJsonText()).file)
        assertEquals(listOf(true, false), target.read().getValue("savingsGoals").map { SavingsGoalCodecs.savingsGoals.decode(it).starred })
        val broken = account(withGoals = true).also { it.getValue("savingsGoals")[0] = it.getValue("savingsGoals")[0] + ("starred" to "yes") }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(broken)).create("2026-10-05T12:00:00.000Z") }
    }

    private fun account(withGoals: Boolean) = emptyBackupData().also {
        if (withGoals) {
            it.getValue("savingsGoals") += SavingsGoalCodecs.savingsGoals.toStore(manual)
            it.getValue("savingsGoals") += SavingsGoalCodecs.savingsGoals.toStore(linked)
            it.getValue("goalContributions") += SavingsGoalCodecs.goalContributions.toStore(deposit)
        }
    }

    @Test
    fun `الخطط بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withGoals = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-05T12:00:00.000Z")
        val text = file.toJsonText()
        for (g in GOAL_BACKUP_GROUPS) assertTrue("\"$g\"" in text, g)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(listOf(2, 1), GOAL_BACKUP_GROUPS.map { outcome.added[it] })
        for (g in GOAL_BACKUP_GROUPS) assertEquals(source.getValue(g), target.read().getValue(g), g)
        assertEquals(file.checksum, FullBackup(target).create("2026-10-05T12:00:00.000Z").checksum)
    }

    @Test
    fun `من غير خطط المجموعات ما بتتكتبش والمكسور بيترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withGoals = false))).create("2026-10-05T12:00:00.000Z").toJsonText()
        for (g in GOAL_BACKUP_GROUPS) assertFalse("\"$g\"" in text, g)
        fun rejects(why: String, change: (Map<String, MutableList<Map<String, Any?>>>) -> Unit) {
            val data = account(withGoals = true).also(change)
            assertFailsWith<IllegalArgumentException>(why) { runBlocking { FullBackup(MemoryFullBackup(data)).create("2026-10-05T12:00:00.000Z") } }
        }
        rejects("هدف صفر") { it.getValue("savingsGoals")[0] = it.getValue("savingsGoals")[0] + ("targetMinor" to 0L) }
        rejects("الهدف قبل البداية") { it.getValue("savingsGoals")[0] = it.getValue("savingsGoals")[0] + ("deadline" to "2025-12-31") }
        rejects("محفظة من غير بلد") { it.getValue("savingsGoals")[1] = it.getValue("savingsGoals")[1] - "linkedSpaceId" }
        rejects("إيداع لخطة مش موجودة") { it.getValue("goalContributions")[0] = it.getValue("goalContributions")[0] + ("goalId" to "g-404") }
        rejects("إيداع سالب") { it.getValue("goalContributions")[0] = it.getValue("goalContributions")[0] + ("amountMinor" to -5L) }
        rejects("تاريخ مستحيل") { it.getValue("goalContributions")[0] = it.getValue("goalContributions")[0] + ("date" to "2026-02-30") }
    }
}
