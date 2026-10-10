package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.CycleUnit
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.RoscaMember
import app.masroufy.core.Transaction
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «المستحقات» في النسخة الشاملة (OVERRIDES §55): بتتحفظ وترجع بنفس البصمة · من غيرها الملف هو هو زي التطبيق الحالي ·
 * علاقة مكسورة بتترفض. بيكتب ملفين في `build/` عشان `scripts/golden/kotlinBackupInOldApp.ts` يجربهم على التطبيق الحالي.
 */
class DuesBackupTest {
    private fun txn(kind: EconomicKind) = Transaction(
        id = "t-1", occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 100_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "2026-09-10T00:00:00.000Z", updatedAt = "2026-09-10T00:00:00.000Z",
    )

    private fun account(withDues: Boolean, kind: EconomicKind = EconomicKind.ROSCA_CONTRIBUTION) = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص وهمي"))
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn(kind))
        it.getValue("obligations") += LedgerCodecs.obligations.toStore(Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 50_000, Currency.SAR))
        if (withDues) {
            it.getValue("roscas") += DuesCodecs.roscas.toStore(
                Rosca("rc-1", "جمعية وهمية", Currency.SAR, 100_000, 1, "2026-01-01", 10, listOf(4), 1_000_000, members = listOf(RoscaMember(4, "أنا")), organizerPersonId = "p-1", createdAt = "x", unit = CycleUnit.MONTH),
            )
            it.getValue("roscaEntries") += DuesCodecs.roscaEntries.toStore(RoscaEntry("e-1", "rc-1", "t-1", RoscaEntryKind.CONTRIBUTION, 100_000))
            it.getValue("installmentPlans") += DuesCodecs.installmentPlans.toStore(
                InstallmentPlan("ip-1", "تمويل وهمي", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_000_000, 1_200_000, 100_000, 1, "2026-01-10", true, "x"),
            )
            it.getValue("installmentPayments") += DuesCodecs.installmentPayments.toStore(InstallmentPayment("pay-1", "ip-1", "t-1", 100_000))
            it.getValue("debtTerms") += DuesCodecs.debtTerms.toStore(DebtTerms("o-1", "p-1", "2026-02-01", 1, 50_000, false))
        }
    }

    @Test fun duesTravelInTheBackup() = runBlocking<Unit> {
        val source = account(withDues = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-01T00:00:00.000Z")
        val text = file.toJsonText()
        assertTrue("\"roscas\"" in text && "\"debtTerms\"" in text)
        File("build/kotlin-dues-backup.json").writeText(text)
        // نفس الحساب بس العملية بنوع يعرفه التطبيق الحالي — عشان نشوف التطبيق الحالي بيعمل إيه في «المستحقات» نفسها
        File("build/kotlin-dues-backup-old-kinds.json").writeText(FullBackup(MemoryFullBackup(account(withDues = true, kind = EconomicKind.PURCHASE))).create("2026-10-01T00:00:00.000Z").toJsonText())

        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val plan = restore.plan(text)
        assertEquals(1, plan.lines.single { it.key == "roscas" }.toAdd)
        val outcome = restore.apply(plan.file)
        for (g in listOf("roscas", "roscaEntries", "installmentPlans", "installmentPayments", "debtTerms")) {
            assertEquals(1, outcome.added[g], g)
            assertEquals(source.getValue(g), target.read().getValue(g), g)
        }
        assertEquals(file.checksum, FullBackup(target).create("2026-10-01T00:00:00.000Z").checksum, "اللي اترجع = الأصل")
    }

    @Test fun withoutDuesTheFileIsTheOldAppsFile() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withDues = false))).create("2026-10-01T00:00:00.000Z").toJsonText()
        for (g in listOf("roscas", "roscaEntries", "installmentPlans", "installmentPayments", "debtTerms")) assertFalse("\"$g\"" in text, g)
        // ويترجع عادي (المجموعات الناقصة بتتقري فاضية)
        assertEquals(3, FullBackup(MemoryFullBackup()).plan(text).totalToAdd)
    }

    /**
     * مبلغ التمويل المستلم (§59): نفس العملية متخزنة على الجهاز التاني بمعرّف تاني ⇒ الخطة المسترجعة بتشاور على **الموجودة**
     * (مش على معرّف مالوش عملية). ولو العملية مش في الملف أصلًا ⇒ النسخة بترفض.
     */
    @Test fun receivedFinancingFollowsTheTransactionOnRestore() = runBlocking<Unit> {
        val got = txn(EconomicKind.FINANCING_RECEIVED).copy(id = "t-got", observedDirection = Direction.IN, amountMinor = 980_000)
        val plan = InstallmentPlan("ip-1", "تمويل وهمي", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_000_000, 1_200_000, 100_000, 1, "2026-01-10", true, "x", receivedTransactionId = "t-got")
        val source = emptyBackupData().also {
            it.getValue("transactions") += LedgerCodecs.transactions.toStore(got)
            it.getValue("installmentPlans") += DuesCodecs.installmentPlans.toStore(plan)
        }
        val text = FullBackup(MemoryFullBackup(source)).create("2026-10-01T00:00:00.000Z").toJsonText()
        val target = MemoryFullBackup(emptyBackupData().also { it.getValue("transactions") += LedgerCodecs.transactions.toStore(got.copy(id = "t-other-device")) })
        val restore = FullBackup(target)
        restore.apply(restore.plan(text).file)
        assertEquals(listOf("t-other-device"), target.read().getValue("transactions").map { it["id"] }, "العملية ما اتكررتش")
        assertEquals("t-other-device", target.read().getValue("installmentPlans").single()["receivedTransactionId"])

        val broken = emptyBackupData().also { it.getValue("installmentPlans") += DuesCodecs.installmentPlans.toStore(plan) }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(broken)).create("2026-10-01T00:00:00.000Z") }
        assertTrue("installmentPlans" in (e.message ?: ""), e.message)
    }

    /**
     * §75-8 (الشريحة S4): «مش ده» على الخطة والجمعية ([InstallmentPlan.dismissedTxnIds]) — **ما بيتكتبش لو فاضي** (المستند القديم هو هو)،
     * بيرجع زي ما هو من المحوّل ومن النسخة الشاملة، وبيتحوّل لمعرّف العملية الموجودة لو نفس العملية متخزنة بمعرّف تاني.
     */
    @Test fun dismissedSuggestionsTravelInTheBackup() = runBlocking<Unit> {
        val rosca = Rosca("rc-1", "جمعية وهمية", Currency.SAR, 100_000, 1, "2026-01-01", 10, listOf(4), 1_000_000, createdAt = "x", dismissedTxnIds = listOf("t-1"))
        val plan = InstallmentPlan("ip-1", "تمويل وهمي", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_000_000, 1_200_000, 100_000, 1, "2026-01-10", true, "x", dismissedTxnIds = listOf("t-1"))
        assertEquals(rosca, DuesCodecs.roscas.decode(DuesCodecs.roscas.toStore(rosca)))
        assertEquals(plan, DuesCodecs.installmentPlans.decode(DuesCodecs.installmentPlans.toStore(plan)))
        assertEquals(listOf("t-1"), DuesCodecs.installmentPlans.toStore(plan)["dismissedTxnIds"])
        for (doc in listOf(DuesCodecs.roscas.toStore(rosca.copy(dismissedTxnIds = emptyList())), DuesCodecs.installmentPlans.toStore(plan.copy(dismissedTxnIds = emptyList())))) {
            assertFalse("dismissedTxnIds" in doc, "فاضي ⇒ ما بيتكتبش")
        }
        // ولو اتفضى بعد ما كان فيه حاجة ⇒ الحفظ بـmerge بيمسحه صريح
        assertTrue("dismissedTxnIds" in DuesCodecs.roscas.omittedFields(rosca.copy(dismissedTxnIds = emptyList())))

        val t1 = txn(EconomicKind.UNCLASSIFIED).copy(id = "t-1")
        val source = emptyBackupData().also {
            it.getValue("transactions") += LedgerCodecs.transactions.toStore(t1)
            it.getValue("roscas") += DuesCodecs.roscas.toStore(rosca)
            it.getValue("installmentPlans") += DuesCodecs.installmentPlans.toStore(plan)
        }
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-01T00:00:00.000Z")
        val target = MemoryFullBackup()
        FullBackup(target).apply(FullBackup(target).plan(file.toJsonText()).file)
        assertEquals(rosca, DuesCodecs.roscas.decode(target.read().getValue("roscas").single()))
        assertEquals(plan, DuesCodecs.installmentPlans.decode(target.read().getValue("installmentPlans").single()))
        assertEquals(file.checksum, FullBackup(target).create("2026-10-01T00:00:00.000Z").checksum, "اللي اترجع = الأصل")

        // نفس العملية على الجهاز التاني بمعرّف تاني ⇒ «مش ده» بيشاور على الموجودة
        val other = MemoryFullBackup(emptyBackupData().also { it.getValue("transactions") += LedgerCodecs.transactions.toStore(t1.copy(id = "t-other-device")) })
        FullBackup(other).apply(FullBackup(other).plan(file.toJsonText()).file)
        assertEquals(listOf("t-other-device"), other.read().getValue("roscas").single()["dismissedTxnIds"])
        assertEquals(listOf("t-other-device"), other.read().getValue("installmentPlans").single()["dismissedTxnIds"])

        // قايمة غلط ⇒ النسخة بترفض
        val bad = emptyBackupData().also { it.getValue("roscas") += DuesCodecs.roscas.toStore(rosca.copy(dismissedTxnIds = emptyList())) + ("dismissedTxnIds" to listOf(5L)) }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(bad)).create("2026-10-01T00:00:00.000Z") }
        assertTrue("dismissedTxnIds" in (e.message ?: ""), e.message)
    }

    @Test fun brokenDuesLinkIsRefused() = runBlocking<Unit> {
        val broken = account(withDues = true).also { it.getValue("roscas").clear() }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(broken)).create("2026-10-01T00:00:00.000Z") }
        assertTrue("roscaEntries" in (e.message ?: ""), e.message)
    }
}
