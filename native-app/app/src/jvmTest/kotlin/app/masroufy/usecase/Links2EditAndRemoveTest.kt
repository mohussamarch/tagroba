package app.masroufy.usecase

import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.GoalContribution
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SavingsGoal
import app.masroufy.core.Settlement
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAuth
import app.masroufy.memory.MemoryRepairBackup
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.AccountDataEraser
import app.masroufy.port.SignOutCleanup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** r06-links2 — فجوة ٥ (اسم الحساب ونوعه · تعديل السداد وإلغاؤه) والمسح الناقص (§118-ج). بيانات مخترعة · المبالغ بالوحدة الصغرى. */
class Links2EditAndRemoveTest {
    private val bank = Wallet("w-1", "بنك وهمي", Currency.SAR, "bank", 10_000, "2026-01-01", accountLast4 = "1234")
    private val cash = Wallet("w-cash", "كاش", Currency.SAR, "cash", 0, "2026-01-01")

    private fun wallets(txns: MemoryTransactionRepository, vararg w: Wallet) =
        ManageWallets(MemoryWalletRepository(w.toList()), SequentialIdGenerator(), FixedClock("2026-10-24T10:00:00Z"), Currency.SAR, txns = txns)

    @Test fun walletNameAndSafeKindChangesWork() = runBlocking<Unit> {
        val m = wallets(MemoryTransactionRepository(listOf(op("t1", 1_000, walletId = "w-1"))), bank, cash)
        assertEquals("بنك جديد", m.rename("w-1", "  بنك   جديد ").name)
        assertFailsWith<WalletError> { m.rename("w-1", "كاش") } // مكرر
        assertEquals("digital_wallet", m.setKind("w-1", "digital_wallet").kind) // بنك ⇄ رقمية آمن حتى بعمليات
    }

    @Test fun unsafeKindChangesAreBlockedWithAReason() = runBlocking<Unit> {
        val withOps = wallets(MemoryTransactionRepository(listOf(op("t1", 1_000, walletId = "w-1"))), bank)
        assertFailsWith<WalletError> { withOps.setKind("w-1", "cash") } // عليه عمليات
        val twoCash = wallets(MemoryTransactionRepository(), bank, cash)
        assertFailsWith<WalletError> { twoCash.setKind("w-1", "cash") } // كاش تاني
        val ok = wallets(MemoryTransactionRepository(), bank).setKind("w-1", "cash")
        assertNull(ok.accountLast4, "الكاش من غير آخر 4")
    }

    private fun settlementsKit(k: RemovalKit) = ManageSettlements(
        ManageSettlementsDeps(k.settlements, k.obligations, k.txns, k.wallets, PassthroughUnitOfWork(), FixedClock("2026-10-24T10:00:00Z")),
    )

    @Test fun aWrongSettlementCanBeEditedOrCancelledWithUndo() = runBlocking<Unit> {
        val k = RemovalKit()
        k.wallets.save(bank)
        k.txns.saveMany(listOf(op("t-lend", 30_000), op("t-back", 20_000, dir = Direction.IN).copy(sourceOrder = MANUAL_SOURCE_ORDER)))
        k.obligations.saveMany(listOf(Obligation("obl-1", "p-1", "t-lend", ObligationKind.RECEIVABLE, 30_000, Currency.SAR)))
        k.settlements.saveMany(listOf(Settlement("stl-1", "t-back", "obl-1", 20_000)))
        val m = settlementsKit(k)
        assertFailsWith<SettlementEditError> { m.edit("obl-1", "p-1", "stl-1", 40_000, null, null) } // أكتر من الدين
        assertEquals(15_000, m.edit("obl-1", "p-1", "stl-1", 15_000, "2026-10-11", null).amountMinor)
        assertEquals("2026-10-11", k.txns.findByIds(listOf("t-back")).single().occurredAt, "العملية اليدوية اتنقل تاريخها")
        val totals = k.totals()
        val done = k.removals.settlement.remove(settlementKey("obl-1", "stl-1"), RemoveChoice.DELETE)
        assertTrue(k.settlements.listByObligations(listOf("obl-1")).isEmpty())
        assertEquals(totals, k.totals(), "العملية نفسها بتفضل")
        done.undo()
        assertEquals(listOf("stl-1"), k.settlements.listByObligations(listOf("obl-1")).map { it.id })
    }

    @Test fun anImportedSettlementKeepsItsBankDate() = runBlocking<Unit> {
        val k = RemovalKit()
        k.txns.saveMany(listOf(op("t-back", 20_000, dir = Direction.IN)))
        k.obligations.saveMany(listOf(Obligation("obl-1", "p-1", null, ObligationKind.RECEIVABLE, 30_000, Currency.SAR)))
        k.settlements.saveMany(listOf(Settlement("stl-1", "t-back", "obl-1", 20_000)))
        assertFailsWith<SettlementEditError> { settlementsKit(k).edit("obl-1", "p-1", "stl-1", 20_000, "2026-10-01", null) }
        assertFalse(settlementsKit(k).list("obl-1", "p-1").single().movable)
    }

    @Test fun savingsGoalAndRuleCanBeDeletedWithUndo() = runBlocking<Unit> {
        val k = RemovalKit()
        val goal = SavingsGoal("g-1", "خطة وهمية", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "c", updatedAt = "c")
        k.goals.save(goal)
        k.goalContributions.save(GoalContribution("c-1", "g-1", "2026-02-01", 10_000, "x"))
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.DELETE), k.removals.goal.ask("g-1").choices)
        val gone = k.removals.goal.remove("g-1", RemoveChoice.DELETE)
        assertTrue(k.goals.listAll().isEmpty() && k.goalContributions.listAll().isEmpty())
        gone.undo()
        assertEquals(listOf("c-1"), k.goalContributions.listAll().map { it.id })
        k.rules.saveMany(listOf(ClassificationRule("rule-0001", 1, "كلمة", RuleMatchMode.CONTAINS, "cat-1", true)))
        val r = k.removals.rule.remove("rule-0001", RemoveChoice.DELETE)
        assertTrue(k.rules.listAll().isEmpty())
        r.undo()
        assertEquals(1, k.rules.listAll().size)
    }

    @Test fun savedBackupsListAndDeleteOnlyKnownFiles() = runBlocking<Unit> {
        val port = MemoryRepairBackup()
        port.save("a.json", "{}")
        port.save("b.json", "{}")
        val m = ManageSavedBackups(port)
        assertEquals(listOf("b.json", "a.json"), m.list().map { it.name })
        assertFalse(m.delete("../x.json"))
        assertTrue(m.delete("a.json"))
        assertEquals(listOf("b.json"), m.list().map { it.name })
    }

    private class Recorder : SignOutCleanup, AccountDataEraser {
        val log = mutableListOf<String>()
        override suspend fun beforeSignOut(uid: String) { log += "device:$uid" }
        override suspend fun afterSignOut() { log += "cache" }
        override suspend fun eraseAll(uid: String): Int { log += "server:$uid"; return 3 }
    }

    @Test fun deleteAccountErasesServerThenDeviceThenSignInAccount() = runBlocking<Unit> {
        val auth = MemoryAuth()
        val user = auth.registerWithEmail("fake@example.test", "pass-word-1")
        val rec = Recorder()
        val signIn = SignIn(auth, null, rec, rec)
        assertTrue(signIn.canDeleteAccount)
        assertEquals(DeleteAccountResult.Done, signIn.deleteAccount())
        assertEquals(listOf("server:${user.uid}", "device:${user.uid}", "cache"), rec.log)
        assertNull(auth.currentUser())
    }

    @Test fun deleteAccountAsksForARecentSignIn() = runBlocking<Unit> {
        val auth = MemoryAuth()
        auth.registerWithEmail("fake@example.test", "pass-word-1")
        auth.requiresRecentLogin = true
        val rec = Recorder()
        assertEquals(DeleteAccountResult.NeedsRecentLogin, SignIn(auth, null, rec, rec).deleteAccount())
        assertNull(auth.currentUser(), "خرج عشان يدخل تاني")
        assertFalse(SignIn(auth, null).canDeleteAccount)
    }
}
