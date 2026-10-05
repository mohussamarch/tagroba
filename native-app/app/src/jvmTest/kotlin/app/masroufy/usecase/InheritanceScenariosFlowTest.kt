package app.masroufy.usecase

import app.masroufy.core.Bequest
import app.masroufy.core.DistantBranch
import app.masroufy.core.EstateItem
import app.masroufy.core.EstateOwner
import app.masroufy.core.HeirKind
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.InheritanceResult
import app.masroufy.core.InheritanceScenarioError
import app.masroufy.core.PredeceasedChild
import app.masroufy.memory.MemoryInheritanceScenarioRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** حفظ حسبة الورث على الحساب (رد المالك §69.3) — حفظ · قايمة · فتح · تغيير الاسم · مسح. أسامي وأرقام مخترعة. */
class InheritanceScenariosFlowTest {
    private class StepClock : Clock {
        var now = "2026-10-05T10:00:00.000Z"

        override fun nowIso(): String = now
    }

    private val clock = StepClock()
    private val repo = MemoryInheritanceScenarioRepository()
    private val manage = ManageInheritanceScenarios(ManageInheritanceScenariosDeps(repo, SequentialIdGenerator(), clock))

    private val egypt = InheritanceCase(
        countryCode = "EG",
        heirs = mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2, HeirKind.DAUGHTER to 0),
        items = listOf(EstateItem("شقة وهمية", 120_000_000), EstateItem("حساب وهمي 1234567890123", 5_000_000)),
        funeralMinor = 1_000_000,
        debtsMinor = 2_000_000,
        bequest = Bequest(3_000_000, toHeir = true),
        predeceasedChildren = listOf(PredeceasedChild(false, 1, 2, givenInLifeMinor = 500_000)),
        names = mapOf(HeirKind.SON to listOf("سامي وهمي", "")),
    )

    @Test
    fun saveListLoadRenameDelete() = runBlocking<Unit> {
        val mine = manage.save(InheritanceScenarioDraft("  تركتي   الوهمية ", EstateOwner.MINE, null, egypt))
        assertEquals("تركتي الوهمية", mine.name)
        assertEquals(mine.createdAt, mine.updatedAt)
        assertEquals(mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2), mine.input.heirs, "الصفر ما بيتحفظش")
        clock.now = "2026-10-05T11:00:00.000Z"
        val saudi = InheritanceCase("SA", mapOf(HeirKind.DAUGHTER_SON to 1, HeirKind.DAUGHTER_DAUGHTER to 1), listOf(EstateItem("أرض وهمية", 7_200_000)),
            distantBranches = listOf(DistantBranch(HeirKind.DAUGHTER, 1, 1)))
        val other = manage.save(InheritanceScenarioDraft("تركة عمي الوهمي", EstateOwner.OTHER, "p-1", saudi))
        // الأحدث الأول
        assertEquals(listOf(other.id, mine.id), manage.list().map { it.id })
        // الفتح بنفس المدخلات بالظبط، والحساب بقانون بلدها (السعودية: الذكر زي الأنثى)
        assertEquals(other, manage.load(other.id))
        val r = assertIs<InheritanceResult.Computed>(manage.calculate(other.id))
        assertEquals(InheritanceLaw.SA, r.law)
        assertEquals(listOf(3_600_000L), r.heir(HeirKind.DAUGHTER_SON)!!.amountsMinor)
        assertEquals(InheritanceLaw.EG, assertIs<InheritanceResult.Computed>(manage.calculate(mine.id)).law)
        // التحديث بيحتفظ بتاريخ الإنشاء
        clock.now = "2026-10-05T12:00:00.000Z"
        val updated = manage.save(InheritanceScenarioDraft("تركتي الوهمية", EstateOwner.MINE, null, egypt.copy(debtsMinor = 0)), mine.id)
        assertEquals(mine.createdAt, updated.createdAt)
        assertEquals("2026-10-05T12:00:00.000Z", updated.updatedAt)
        assertEquals(2, repo.listAll().size)
        val renamed = manage.rename(other.id, "تركة وهمية تانية")
        assertEquals("تركة وهمية تانية", manage.load(other.id).name)
        assertEquals(renamed.updatedAt, "2026-10-05T12:00:00.000Z")
        // المسح مسموح (رد المالك)
        manage.delete(other.id)
        assertEquals(listOf(mine.id), manage.list().map { it.id })
        assertFailsWith<InheritanceScenarioError> { manage.load(other.id) }
        assertFailsWith<InheritanceScenarioError> { manage.delete(other.id) }
    }

    @Test
    fun badScenariosAreRejected() = runBlocking<Unit> {
        assertFailsWith<InheritanceScenarioError>("من غير اسم") { manage.save(InheritanceScenarioDraft("  ", EstateOwner.MINE, null, egypt)) }
        assertFailsWith<InheritanceScenarioError>("اسم طويل") { manage.save(InheritanceScenarioDraft("ا".repeat(61), EstateOwner.MINE, null, egypt)) }
        assertFailsWith<InheritanceScenarioError>("تركتي بشخص") { manage.save(InheritanceScenarioDraft("س", EstateOwner.MINE, "p-1", egypt)) }
        assertFailsWith<InheritanceScenarioError>("بلد مالهاش قانون") { manage.save(InheritanceScenarioDraft("س", EstateOwner.MINE, null, egypt.copy(countryCode = "AE"))) }
        assertFailsWith<InheritanceScenarioError>("مبلغ سالب") { manage.save(InheritanceScenarioDraft("س", EstateOwner.MINE, null, egypt.copy(debtsMinor = -1))) }
        assertFailsWith<InheritanceScenarioError>("عدد كبير") {
            manage.save(InheritanceScenarioDraft("س", EstateOwner.MINE, null, egypt.copy(heirs = mapOf(HeirKind.SON to 101))))
        }
        assertTrue(repo.listAll().isEmpty())
        // الحسبة الناقصة (من غير حاجات) بتتحفظ كمسودة — والحساب هو اللي بيقول إنها ناقصة
        val draft = manage.save(InheritanceScenarioDraft("مسودة", EstateOwner.OTHER, null, egypt.copy(items = emptyList())))
        assertIs<InheritanceResult.Invalid>(manage.calculate(draft.id))
    }
}
