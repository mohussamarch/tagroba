package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «غيّرت شغلي» وأسئلته (OVERRIDES §48 · §64) — أسماء ومبالغ مخترعة. */
class IncomeChangesTest {
    private fun src(id: String, kind: IncomeSourceKind = IncomeSourceKind.JOB, from: String = "2024-01-01", to: String? = null, day: Int? = null, amount: Long? = null, currency: Currency = Currency.SAR) =
        IncomeSource(id, "شركة $id الوهمية", "شركة $id الوهمية", kind, currency, from, to, day, amount)

    @Test fun endingAloneSaysNothingAndOnlyAsksAboutTheBenefitForASaudiJob() {
        val ended = jobChangeOutcome(src("a", to = "2026-09-30"), null, 28)
        assertFalse(ended.congratulate, "قفل مصدر لوحده: ولا كلمة (ممكن يكون اتفصل)")
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.EndOfServiceBenefit("a")), ended.followUps)
        // عميل أو وظيفة برا السعودية: ولا سؤال حتى
        assertEquals(JobChangeOutcome(false, emptyList()), jobChangeOutcome(src("b", IncomeSourceKind.CLIENT, to = "2026-09-30"), null, 28))
        assertEquals(JobChangeOutcome(false, emptyList()), jobChangeOutcome(src("c", to = "2026-09-30", currency = Currency.EGP), null, 28))
    }

    @Test fun aNewJobIsCongratulatedAndAsksPaydayThenSalaryButNeverTransport() {
        val opened = jobChangeOutcome(null, src("n", from = "2026-10-01"), 28)
        assertTrue(opened.congratulate)
        assertEquals(listOf(IncomeFollowUp.AskPayday("n"), IncomeFollowUp.AskExpectedSalary("n")), opened.followUps)
        // القديم والجديد مع بعض: سؤال المكافأة (بتاعة القديم) + أسئلة الجديد — ومفيش «هتروح بيها الشغل؟»
        val both = jobChangeOutcome(src("a", to = "2026-09-30"), src("n", from = "2026-10-01"), 28)
        assertTrue(both.congratulate)
        assertEquals(listOf(IncomeFollowUp.EndOfServiceBenefit("a"), IncomeFollowUp.AskPayday("n"), IncomeFollowUp.AskExpectedSalary("n")), both.followUps)
        assertTrue(IncomeFollowUp.CarToWork !in both.followUps)
    }

    @Test fun aDifferentPaydayAsksToMoveTheFinancialMonthOnlyForAJob() {
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.ChangeMonthStart("n", 25)), newSourceFollowUps(src("n", day = 25, amount = 900_000), 28))
        assertEquals(emptyList(), newSourceFollowUps(src("n", day = 28, amount = 900_000), 28), "نفس اليوم ⇒ مفيش سؤال")
        // بارت تايم بيوم مختلف: ما يحرّكش شهرك، بس المتوقع لسه بيتسأل
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.AskExpectedSalary("p")), newSourceFollowUps(src("p", IncomeSourceKind.PART_TIME, day = 10), 28))
        assertNull(monthStartFollowUp(src("p", IncomeSourceKind.PART_TIME, day = 10), 28))
        // عميل أو إيجار: مالهمش «مرتب» ⇒ مفيش أسئلة مرتب
        assertEquals(emptyList(), newSourceFollowUps(src("r", IncomeSourceKind.RENT), 28))
    }

    @Test fun carQuestionOnlyWhenTheCarIsNewAndAJobIsRunning() {
        val noCar = emptyProfile().copy(hasCar = false)
        val withCar = emptyProfile().copy(hasCar = true)
        val job = listOf(src("a"))
        assertTrue(shouldAskCarToWork(noCar, withCar, job, "2026-10-04"))
        assertTrue(shouldAskCarToWork(null, withCar, job, "2026-10-04"), "ما اتجاوبش قبل كده ⇒ ده تغيير برضه")
        assertTrue(shouldAskCarToWork(noCar, withCar, listOf(src("p", IncomeSourceKind.PART_TIME)), "2026-10-04"))
        assertFalse(shouldAskCarToWork(withCar, withCar, job, "2026-10-04"), "لسه عنده نفس العربية ⇒ مرة واحدة بس")
        assertFalse(shouldAskCarToWork(withCar, noCar, job, "2026-10-04"))
        assertFalse(shouldAskCarToWork(noCar, withCar, listOf(src("a", to = "2026-09-30")), "2026-10-04"), "الشغل خلص")
        assertFalse(shouldAskCarToWork(noCar, withCar, listOf(src("c", IncomeSourceKind.CLIENT)), "2026-10-04"), "عميل مش شغل بيروحه")
        assertFalse(shouldAskCarToWork(noCar, withCar, listOf(src("f", from = "2026-11-01")), "2026-10-04"), "لسه ما بدأش")
        assertFalse(shouldAskCarToWork(noCar, withCar, emptyList(), "2026-10-04"))
    }

    @Test fun carAnswerIsKeptWithACarAndClearedWithout() {
        val ok = checkProfile(emptyProfile().copy(hasCar = true, carToWork = true)) as ProfileCheck.Ok
        assertEquals(true, ok.profile.carToWork)
        val sold = checkProfile(emptyProfile().copy(hasCar = false, carToWork = true)) as ProfileCheck.Ok
        assertNull(sold.profile.carToWork, "من غير عربية الرد مالوش معنى")
        assertEquals(false, parseStoredProfile(mapOf("hasCar" to true, "carToWork" to false)).carToWork)
        checkBackupProfile(mapOf("payday" to 28L, "carToWork" to true))
        kotlin.test.assertFailsWith<BackupError> { checkBackupProfile(mapOf("payday" to 28L, "carToWork" to "yes")) }
    }

    @Test fun textsExistInBothLanguagesAndCongratsNamesTheNewJob() {
        val all = listOf(IncomeFollowUp.EndOfServiceBenefit("a"), IncomeFollowUp.AskPayday("a"), IncomeFollowUp.ChangeMonthStart("a", 25), IncomeFollowUp.AskExpectedSalary("a"), IncomeFollowUp.CarToWork)
        try {
            for (lang in Language.entries) {
                Texts.language = lang
                for (f in all) assertTrue(incomeFollowUpText(f).isNotBlank(), "$lang $f")
                assertTrue(incomeFollowUpText(IncomeFollowUp.ChangeMonthStart("a", 25)).contains("25"))
                assertTrue(incomeCongratsText(src("n")).contains("شركة n الوهمية"))
            }
            Texts.language = Language.AR
            assertEquals("هتروح بيها الشغل؟", incomeFollowUpText(IncomeFollowUp.CarToWork))
            assertEquals("فيه مكافأة نهاية خدمة جاية؟", incomeFollowUpText(IncomeFollowUp.EndOfServiceBenefit("a")))
            assertFalse(incomeFollowUpText(IncomeFollowUp.EndOfServiceBenefit("a")).contains("مبروك"))
        } finally {
            Texts.language = Language.AR
        }
    }
}
