package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «حدّث الرصيد» (`BalanceCheckpoint.kt` — قايمة المالك 2026-10-15 البنود 4 و5): سلسلة الرصيد بالنقط للبنك والكاش · يوم فات · اللي وصل متأخر ·
 * الأجزاء اللي اتفصلت · الترتيب جوه اليوم · الكاش · البلدين بعملتين. كل البيانات مخترعة، والمبالغ هللات صحيحة.
 */
class BalanceCheckpointTest {
    private fun txn(
        id: String, date: String, dir: Direction, amount: Long, wallet: String = "w-bank", created: String = "2026-10-01T09:00:00Z",
        to: String? = null, explains: String? = null, currency: Currency = Currency.SAR,
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = created, updatedAt = created,
        walletId = wallet, transferToWalletId = to, explainsCheckpointId = explains,
    )

    private fun cp(id: String, day: String, stated: Long, at: String? = null, recorded: Long = 0, kept: Long? = null, created: String = "2026-10-15T08:00:00Z") =
        BalanceCheckpoint(id, day, at, stated, recorded, created, kept)

    /** بنك وهمي: 5,000.00 من 1 أكتوبر ⇒ صرف 300.00 يوم 5 ⇒ راتب 2,000.00 يوم 10 ⇒ المحسوب 6,700.00. */
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2026-10-01")
    private val base = listOf(
        txn("t-1", "2026-10-05", Direction.OUT, 30_000),
        txn("t-2", "2026-10-10", Direction.IN, 200_000),
    )

    @Test
    fun `النقطة بتثبّت الرصيد على اللي المالك كتبه والفرق بإشارته`() {
        val w = bank.copy(checkpoints = listOf(cp("c-1", "2026-10-12", 650_000, recorded = -20_000)))
        val e = checkpointEffects(w, base).single()
        assertEquals(670_000L, e.beforeMinor)
        assertEquals(-20_000L, e.adjustmentMinor, "معاه أقل بـ200.00")
        assertEquals(0L, e.lateMinor)
        assertTrue(e.needsReview)
        // بعدها: المكتوب + الحركات اللي بعدها
        val later = base + txn("t-3", "2026-10-13", Direction.OUT, 5_000)
        assertEquals(645_000L, walletBalancesOn(listOf(w), later, "2026-10-13")["w-bank"])
        assertEquals(650_000L, walletBalancesOn(listOf(w), later, "2026-10-12")["w-bank"])
        assertEquals(670_000L, walletBalancesOn(listOf(w), later, "2026-10-11")["w-bank"], "قبل النقطة المحسوب زي ما هو")
        assertEquals(-20_000L, balanceAdjustmentsMinor(w, later))
        assertEquals(0L, balanceAdjustmentsMinor(w, later, from = "2026-10-13"), "الفترة بعد النقطة مالهاش تعديلات")
    }

    @Test
    fun `دلوقتي — اللي اتسجل بعد الساعة دي بيتحسب بعدها، واللي قبلها جوه الفرق`() {
        val now = "2026-10-15T08:00:00Z"
        val w = bank.copy(checkpoints = listOf(cp("c-1", "2026-10-15", 600_000, at = now)))
        val morning = txn("t-m", "2026-10-15", Direction.OUT, 10_000, created = "2026-10-15T06:30:00Z")
        val coffee = txn("t-c", "2026-10-15", Direction.OUT, 1_500, created = "2026-10-15T09:10:00Z")
        val rows = base + morning + coffee
        val e = checkpointEffects(w, rows).single()
        assertEquals(660_000L, e.beforeMinor, "مشتريات الصبح قبل التحديث")
        assertEquals(-60_000L, e.adjustmentMinor)
        assertEquals(598_500L, walletBalancesOn(listOf(w), rows, "2026-10-15")["w-bank"], "القهوة بعد التحديث بتنقص من اللي كتبه")
        // نفس الترتيب في خطوات المطابقة
        val steps = withCheckpointSteps(w, rows)
        assertEquals(listOf("t-1", "t-2", "t-m", "c-1", "t-c"), steps.map { if (it is LedgerStep.Txn) it.transaction.id else (it as LedgerStep.Checkpoint).effect.checkpoint.id })
    }

    @Test
    fun `يوم فات من غير ساعة — كل عمليات اليوم ده قبل النقطة حتى اللي اتسجلت بعدين`() {
        val w = bank.copy(checkpoints = listOf(cp("c-1", "2026-10-10", 660_000)))
        val lateSms = txn("t-late", "2026-10-10", Direction.OUT, 10_000, created = "2026-10-14T20:00:00Z")
        val e = checkpointEffects(w, base + lateSms).single()
        assertEquals(660_000L, e.beforeMinor)
        assertEquals(0L, e.adjustmentMinor, "اللي وصل متأخر ليوم النقطة شرح الفرق كله")
        assertFalse(e.needsReview)
        assertEquals(660_000L, walletBalancesOn(listOf(w), base + lateSms, "2026-10-12")["w-bank"])
    }

    @Test
    fun `رسالة لأيام قبل النقطة بتوصل متأخر — الرصيد المؤكد ما بيتكسرش والفرق اتغيّر وبيبان`() {
        val w = bank.copy(checkpoints = listOf(cp("c-1", "2026-10-12", 650_000, recorded = -20_000, kept = -20_000)))
        assertFalse(checkpointEffects(w, base).single().needsReview, "المالك قال سيبه")
        val older = txn("t-old", "2026-10-07", Direction.OUT, 12_000, created = "2026-10-16T10:00:00Z")
        val e = checkpointEffects(w, base + older).single()
        assertEquals(-8_000L, e.adjustmentMinor, "الرسالة شرحت 120.00 من الفرق")
        assertEquals(12_000L, e.lateMinor, "التغيير من وقت التسجيل بيبان")
        assertTrue(e.needsReview, "اتغيّر بعد «سيبه» ⇒ يرجع للمراجعة")
        assertEquals(650_000L, walletBalancesOn(listOf(w), base + older, "2026-10-16")["w-bank"], "الرصيد اللي المالك أكده ثابت")
    }

    @Test
    fun `الجزء اللي اتفصل من الفرق بيشرحه ومش بيغيّر الرصيد`() {
        val w = bank.copy(checkpoints = listOf(cp("c-1", "2026-10-15", 600_000, at = "2026-10-15T08:00:00Z", recorded = -70_000)))
        val part = txn("t-part", "2026-10-15", Direction.OUT, 45_000, created = "2026-10-15T12:00:00Z", explains = "c-1")
        val e = checkpointEffects(w, base + part).single()
        assertEquals(-25_000L, e.adjustmentMinor)
        assertEquals(-45_000L, e.explainedMinor)
        assertEquals(0L, e.lateMinor, "التوزيع مش «وصل متأخر»")
        assertEquals(600_000L, walletBalancesOn(listOf(w), base + part, "2026-10-15")["w-bank"])
    }

    @Test
    fun `نقطتين ورا بعض — التانية بتثبّت من اللي قبلها`() {
        val w = bank.copy(checkpoints = listOf(cp("c-2", "2026-10-14", 640_000), cp("c-1", "2026-10-11", 680_000)))
        val rows = base + txn("t-3", "2026-10-12", Direction.OUT, 50_000)
        val (first, second) = checkpointEffects(w, rows)
        assertEquals("c-1", first.checkpoint.id)
        assertEquals(10_000L, first.adjustmentMinor)
        assertEquals(630_000L, second.beforeMinor, "680.00 − 500 بعد الأولى")
        assertEquals(10_000L, second.adjustmentMinor)
        assertEquals(640_000L, walletBalancesOn(listOf(w), rows, "2026-10-20")["w-bank"])
        assertEquals(20_000L, balanceAdjustmentsMinor(w, rows))
    }

    @Test
    fun `النقطة قبل بداية المحفظة ما بتتحسبش، والرصيد التجريبي مش معروف قبلها`() {
        val w = bank.copy(checkpoints = listOf(cp("c-0", "2026-09-20", 1)))
        assertTrue(checkpointEffects(w, base).isEmpty())
        assertEquals(670_000L, walletBalancesOn(listOf(w), base, "2026-10-12")["w-bank"])
        assertEquals(null, balanceAtCheckpoint(bank, base, cp("p", "2026-09-30", 0)))
        assertEquals(670_000L, balanceAtCheckpoint(bank, base, cp("p", "2026-10-11", 0)))
    }

    @Test
    fun `الكاش — الفرق في الرصيد بس ومش في المصروف ولا الداخل والخارج`() {
        val cash = Wallet("w-cash", "كاش", Currency.SAR, "cash", 100_000, "2026-10-01")
        val rows = listOf(
            txn("k-1", "2026-10-03", Direction.OUT, 20_000, wallet = "w-cash").copy(economicKind = EconomicKind.PURCHASE),
            txn("k-2", "2026-10-04", Direction.OUT, 30_000, to = "w-cash"),
        )
        val plain = summarizeCash(cash, rows, emptyList(), "2026-09-28", "2026-10-27")
        val counted = summarizeCash(cash.copy(checkpoints = listOf(cp("c-k", "2026-10-06", 70_000))), rows, emptyList(), "2026-09-28", "2026-10-27")
        assertEquals(110_000L, plain.balanceMinor)
        assertEquals(70_000L, counted.balanceMinor, "اللي المالك عدّه")
        assertEquals(-40_000L, counted.checkpoints.single().adjustmentMinor)
        assertEquals(plain.spentInPeriodMinor, counted.spentInPeriodMinor, "الفرق مش مصروف")
        assertEquals(plain.inSinceOpeningMinor to plain.outSinceOpeningMinor, counted.inSinceOpeningMinor to counted.outSinceOpeningMinor)
    }

    @Test
    fun `مصر بالجنيه — نفس القاعدة بعملة المحفظة`() {
        val egp = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 1_000_000, "2026-10-01")
        val rows = listOf(txn("e-1", "2026-10-02", Direction.OUT, 250_050, wallet = "w-eg", currency = Currency.EGP))
        val w = egp.copy(checkpoints = listOf(cp("c-e", "2026-10-03", 700_000)))
        assertEquals(-49_950L, checkpointEffects(w, rows).single().adjustmentMinor)
        assertEquals(700_000L, walletBalancesOn(listOf(w), rows, "2026-10-03")["w-eg"])
    }

    @Test
    fun `الفحص في النسخة — النقطة السليمة بتعدّي والغلط بيترفض باسم الحقل`() {
        val good = mapOf("id" to "c-1", "day" to "2026-10-12", "statedMinor" to 650_000L, "recordedDiffMinor" to -20_000L, "createdAt" to "2026-10-12T08:00:00Z")
        assertEquals(null, checkpointBackupProblem("wallets", mapOf("balanceCheckpoints" to listOf(good))))
        assertEquals(null, checkpointBackupProblem("wallets", mapOf("name" to "x")), "المحفظة القديمة من غير الحقل")
        assertEquals("balanceCheckpoints", checkpointBackupProblem("wallets", mapOf("balanceCheckpoints" to listOf(good + ("statedMinor" to -1L)))))
        assertEquals("balanceCheckpoints", checkpointBackupProblem("wallets", mapOf("balanceCheckpoints" to listOf(good, good))), "معرّف مكرر")
        assertEquals("balanceCheckpoints", checkpointBackupProblem("wallets", mapOf("balanceCheckpoints" to listOf(good + ("day" to "2026-13-01")))))
        assertEquals("balanceCheckpoints", checkpointBackupProblem("wallets", mapOf("balanceCheckpoints" to listOf(good + ("recordedDiffMinor" to 1.5)))))
        assertEquals("explainsCheckpointId", checkpointBackupProblem("transactions", mapOf("explainsCheckpointId" to "")))
        assertEquals(null, checkpointBackupProblem("transactions", mapOf("explainsCheckpointId" to "c-1")))
    }
}
