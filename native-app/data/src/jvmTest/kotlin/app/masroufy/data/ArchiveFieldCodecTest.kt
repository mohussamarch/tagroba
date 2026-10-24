package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Rosca
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * r06-archive (OVERRIDES §118-ب-1): حقل الأرشفة الجديد في الخطة والجمعية ومصدر الدخل — المستند القديم (من غير الحقل) بيتقري زي ما هو
 * ومش مؤرشف، والمش مؤرشف ما بيكتبش الحقل أصلًا (المستند هو هو)، والمؤرشف بيرجع زي ما اتكتب. كل البيانات مخترعة.
 */
class ArchiveFieldCodecTest {
    private val plan = InstallmentPlan("pl-1", "خطة تجربة", "جهة تجربة", InstallmentKind.FINANCING, Currency.SAR, 1_000_00, 1_200_00, 100_00, 1, "2026-01-10", createdAt = "x")
    private val rosca = Rosca("r-1", "جمعية تجربة", Currency.SAR, 500_00, 1, "2026-01-01", 10, listOf(3), 5_000_00, createdAt = "x")
    private val job = IncomeSource("inc-1", "شركة تجربة", "شركة تجربة", IncomeSourceKind.JOB, Currency.SAR, "2026-01-01", createdAt = "x")

    @Test
    fun oldDocsWithoutTheField_decodeUnarchived_andNothingNewIsWritten() {
        val p = DuesCodecs.installmentPlans.encode(plan)
        val r = DuesCodecs.roscas.encode(rosca)
        val s = IncomeCodecs.incomeSources.encode(job)
        for (doc in listOf(p, r, s)) assertFalse("archivedAt" in doc || "endedByArchive" in doc)
        // المستند القديم = نفس المستند من غير الحقول الجديدة
        assertEquals(plan, DuesCodecs.installmentPlans.decode(p))
        assertEquals(rosca, DuesCodecs.roscas.decode(r))
        assertEquals(job, IncomeCodecs.incomeSources.decode(s))
        assertFalse(DuesCodecs.installmentPlans.decode(p).archived || DuesCodecs.roscas.decode(r).archived || IncomeCodecs.incomeSources.decode(s).archived)
    }

    @Test
    fun archivedDocs_roundTrip() {
        val at = "2026-10-24T10:00:00Z"
        val p = plan.copy(archivedAt = at)
        val r = rosca.copy(archivedAt = at)
        val s = job.copy(archivedAt = at, endedAt = "2026-10-24", endedByArchive = true)
        assertEquals(p, DuesCodecs.installmentPlans.decode(DuesCodecs.installmentPlans.encode(p)))
        assertEquals(r, DuesCodecs.roscas.decode(DuesCodecs.roscas.encode(r)))
        assertEquals(s, IncomeCodecs.incomeSources.decode(IncomeCodecs.incomeSources.encode(s)))
        assertTrue(DuesCodecs.roscas.decode(DuesCodecs.roscas.encode(r)).archived)
    }
}
