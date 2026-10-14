package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeCategories
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.ReviewState
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryRepairBackup
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «جهات الشغل» من أول الرد على التحويل لحد «طبّق على اللي فات» والتراجع (قايمة المالك 2026-10-15، البندين 6 و7). كل الأسامي والمبالغ
 * مخترعة: «الفجر» لحد آخر أغسطس بحسابين بالتبادل، و«النور» من أول سبتمبر — ممنوع أي اسم جهة حقيقي (المستودع عام).
 */
class EmployersFlowTest {
    private val txns = MemoryTransactionRepository()
    private val sources = MemoryIncomeSourceRepository()
    private val categories = MemoryCategoryRepository()
    private val backup = MemoryRepairBackup()
    private val clock = FixedClock("2026-10-15T01:30:00.000Z")
    private val uow = MemoryUnitOfWork(listOf(txns, sources))
    private val employers = ManageEmployers(ManageEmployersDeps(sources, txns, SequentialIdGenerator(), clock, Currency.SAR))
    private fun retroWith(port: MemoryRepairBackup?) = EmployerRetroApply(EmployerRetroDeps(sources, txns, uow, clock, port))
    private val retro = retroWith(backup)
    private val answers = IncomeAnswers(IncomeAnswersDeps(txns, categories, employers, retro, uow, clock))

    @AfterTest fun msa() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private var seq = 0

    private fun deposit(sender: String, last4: String, date: String, amount: Long = 1_200_000, kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false) =
        Transaction(
            id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
            observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = "W-/FRACCT/0000000000000$last4" + "FR$sender:ملاحظة", sourceOperationType = "حوالة واردة داخل البنك",
        )

    private suspend fun one(id: String) = txns.findByIds(listOf(id)).single()

    private suspend fun kindOf(id: String) = one(id).let { Triple(it.economicKind, it.economicKindConfirmed, it.reviewState) }

    private val salary = Triple(EconomicKind.SALARY, true, ReviewState.CONFIRMED)
    private val waiting = Triple(EconomicKind.UNCLASSIFIED, false, ReviewState.NEEDS_REVIEW)

    // الفجر: حساب 1111 وحساب 2222 بالتبادل · النور من سبتمبر بحساب 3333
    private val june = deposit("FAJRCO", "1111", "2026-06-27")
    private val july = deposit("FAJRCO", "2222", "2026-07-27")
    private val august = deposit("FAJRCO", "1111", "2026-08-27")
    private val afterLeaving = deposit("FAJRCO", "2222", "2026-09-15")
    private val september = deposit("NOORCO", "3333", "2026-09-27")
    private val handGift = deposit("FAJRCO", "2222", "2026-05-27", kind = EconomicKind.GIFT_RECEIVED, confirmed = true)

    private suspend fun seed() = txns.saveMany(listOf(june, july, august, afterLeaving, september, handGift))

    @Test fun salaryAnswerCreatesTheEmployerLinksTheAccountAndOffersThePast() = runBlocking<Unit> {
        seed()
        val answer = answers.markSalary(august.id, newEmployer = EmployerInput("جهة الفجر", "2025-01-01", "2026-08-31"))
        val fajr = answer.employer!!
        assertEquals(listOf(answer.party!!.key), fajr.payerKeys, "الحساب اتربط بالجهة")
        assertEquals(salary, kindOf(august.id))
        assertEquals(IncomeCategories.SALARY to true, one(august.id).let { it.categoryId to it.categoryConfirmed })
        assertEquals(4, categories.listAll().count { it.id.startsWith(IncomeCategories.ROOT) }, "«الدخل» وفروعه اتعملوا لو مش موجودين")
        // الباقي من نفس الحساب جوه الفترة بس (يونيو) — الحساب التاني لسه مش مربوط
        assertEquals(listOf(june.id), answer.retro!!.toSalaryIds)
        // المالك بيضيف الحساب التاني من «مين حوّلك؟» ⇒ المعاينة فيها الحسابين
        val second = employers.senders().first { it.party.last4 == "2222" }.party
        employers.attach(fajr.id, second)
        val preview = retro.preview(fajr.id)
        assertEquals(listOf(june.id, july.id), preview.toSalaryIds, "الحسابين بالتبادل — وبعد الخروج (سبتمبر) برّه")
        assertEquals(listOf(handGift.id), preview.handSetIds, "اللي حدده بإيده بيتعدّ لوحده")
        val done = retro.apply(preview)
        assertEquals(2, done.changed)
        assertEquals(salary to salary, kindOf(june.id) to kindOf(july.id))
        assertEquals(waiting, kindOf(afterLeaving.id), "تحويل من حساب الفجر بعد آخر أغسطس ما بيتعلّمش")
        assertEquals(EconomicKind.GIFT_RECEIVED, kindOf(handGift.id).first, "اللي اتحدد بإيده ما بيتلمسش من غير اختيار صريح")
        assertEquals(1, backup.files.size, "نسخة قبل الكتابة")
        assertTrue(backup.files.values.single().contains(june.id))
        // التراجع بيرجّع كل واحدة زي ما كانت (ونسخة تانية قبله)
        assertEquals(2, retro.undo(done.undo))
        assertEquals(waiting to waiting, kindOf(june.id) to kindOf(july.id))
        assertEquals(2, backup.files.size)
    }

    @Test fun twoEmployersSwitchAtTheEndOfAugust() = runBlocking<Unit> {
        seed()
        answers.markSalary(august.id, newEmployer = EmployerInput("جهة الفجر", "2025-01-01", "2026-08-31"))
        val noor = answers.markSalary(september.id, newEmployer = EmployerInput("جهة النور", "2026-09-01")).employer!!
        assertEquals(listOf("جهة النور", "جهة الفجر"), employers.list().map { it.source.name }, "الحالية الأول")
        val all = retro.preview(null)
        assertEquals(listOf(june.id), all.toSalaryIds, "حساب الفجر الأول جوه فترته بس — ولا حاجة من النور قبل بدايتها")
        // حساب النور بيحوّل من قبل سبتمبر؟ ⇒ برّه فترتها ⇒ ما بيتعلّمش
        val earlyNoor = deposit("NOORCO", "3333", "2026-08-20")
        txns.saveMany(listOf(earlyNoor))
        assertTrue(earlyNoor.id !in retro.preview(noor.id).toSalaryIds)
    }

    @Test fun includingHandSetIsAnExplicitChoiceAndUndoable() = runBlocking<Unit> {
        seed()
        val fajr = employers.save(null, EmployerInput("جهة الفجر", "2025-01-01", "2026-08-31"))
        for (s in employers.senders().filter { it.party.label == "FAJRCO" }) employers.attach(fajr.id, s.party)
        val preview = retro.preview(fajr.id)
        val done = retro.apply(preview, includeHandSet = true)
        assertEquals(4, done.changed)
        assertEquals(salary, kindOf(handGift.id))
        retro.undo(done.undo)
        assertEquals(Triple(EconomicKind.GIFT_RECEIVED, true, ReviewState.NEEDS_REVIEW), kindOf(handGift.id), "رجعت زي ما كانت بالظبط")
    }

    @Test fun nothingIsWrittenWithoutAVerifiedBackupOrWhenThePreviewIsStale() = runBlocking<Unit> {
        seed()
        val fajr = employers.save(null, EmployerInput("جهة الفجر", "2025-01-01", "2026-08-31"))
        employers.attach(fajr.id, employers.senders().first { it.party.last4 == "1111" }.party)
        val preview = retro.preview(fajr.id)
        val noBackup = assertFailsWith<IncomeSourceError> { retroWith(null).apply(preview) }
        assertEquals(uiText(UiKey.EMPLOYER_RETRO_NO_BACKUP), noBackup.message)
        assertFailsWith<IllegalStateException> { retroWith(MemoryRepairBackup(truncate = true)).apply(preview) }
        assertEquals(waiting, kindOf(june.id), "ولا كتابة من غير نسخة متأكدة")
        txns.saveMany(listOf(deposit("FAJRCO", "1111", "2026-04-27")))
        val stale = assertFailsWith<IncomeSourceError> { retro.apply(preview) }
        assertEquals(uiText(UiKey.EMPLOYER_RETRO_CHANGED), stale.message)
    }

    @Test fun anAccountCannotServeTwoEmployersAtOnce() = runBlocking<Unit> {
        seed()
        val fajr = employers.save(null, EmployerInput("جهة الفجر", "2025-01-01", "2026-08-31"))
        val acc = employers.senders().first { it.party.last4 == "1111" }.party
        employers.attach(fajr.id, acc)
        val overlap = employers.save(null, EmployerInput("جهة تانية", "2026-08-01"))
        val e = assertFailsWith<IncomeSourceError> { employers.attach(overlap.id, acc) }
        assertEquals(uiText(UiKey.EMPLOYER_ERR_CLASH, "جهة الفجر"), e.message)
        // الفترة بعد ما تتعدّل وتبقى من غير تداخل ⇒ مسموح
        employers.save(overlap.id, EmployerInput("جهة تانية", "2026-09-01"))
        employers.attach(overlap.id, acc)
        assertFailsWith<IncomeSourceError>("تعديل الفترة لتداخل تاني بيترفض") { employers.save(overlap.id, EmployerInput("جهة تانية", "2026-08-15")) }
        assertEquals(listOf("FAJRCO" to "1111"), employers.list().first { it.source.id == fajr.id }.accounts.map { it.label to it.last4 })
    }

    @Test fun sideJobAndSaleAreIncomeWithTheirOwnCategoriesAndCanBeUndone() = runBlocking<Unit> {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val side = deposit("PERSONX", "5555", "2026-09-10", 300_000)
        val sale = deposit("PERSONY", "6666", "2026-09-12", 150_000)
        txns.saveMany(listOf(side, sale))
        answers.markIncome(side.id, EconomicKind.FREELANCE)
        val undoSale = answers.markIncome(sale.id, EconomicKind.PERSONAL_SALE)
        assertEquals(EconomicKind.FREELANCE to IncomeCategories.SIDE_JOB, one(side.id).let { it.economicKind to it.categoryId })
        assertEquals(EconomicKind.PERSONAL_SALE to IncomeCategories.SALE, one(sale.id).let { it.economicKind to it.categoryId })
        assertEquals(listOf("الدخل", "مرتب", "شغل إضافي", "بيعة"), categories.listAll().filter { it.id.startsWith(IncomeCategories.ROOT) }.map { it.name }, "بالمصري في مصر")
        val income = app.masroufy.core.computePeriodTotals(txns.findByIds(listOf(side.id, sale.id)), emptyList()).incomeMinor
        assertEquals(450_000L, income, "الشغل الإضافي والبيعة دخل")
        answers.undo(undoSale)
        assertEquals(waiting, kindOf(sale.id))
        assertNull(one(sale.id).categoryId)
        assertFailsWith<IncomeSourceError> { answers.markIncome(side.id, EconomicKind.LOAN_RECEIVED) }
    }

    @Test fun anExistingIncomingTransferCanBeTurnedIntoSalaryAndTheAnswerUndone() = runBlocking<Unit> {
        seed()
        val fajr = employers.save(null, EmployerInput("جهة الفجر", "2025-01-01", "2026-08-31"))
        // كان اتسجل «هدية» بإيده — المالك بيغيّره لراتب الدوام
        val answer = answers.markSalary(handGift.id, employerId = fajr.id)
        assertEquals(salary, kindOf(handGift.id))
        assertEquals(1, employers.list().single().accounts.size)
        answers.undo(answer.undo)
        assertEquals(Triple(EconomicKind.GIFT_RECEIVED, true, ReviewState.NEEDS_REVIEW), kindOf(handGift.id))
        assertTrue(employers.list().single().accounts.isEmpty(), "التراجع بيشيل الحساب اللي الرد ربطه")
    }
}
