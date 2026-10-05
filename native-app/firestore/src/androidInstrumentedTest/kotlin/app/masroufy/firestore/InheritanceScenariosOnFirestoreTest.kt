package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Bequest
import app.masroufy.core.DistantBranch
import app.masroufy.core.EstateItem
import app.masroufy.core.EstateOwner
import app.masroufy.core.HeirKind
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceResult
import app.masroufy.data.InheritanceCodecs
import app.masroufy.memory.FixedClock
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.InheritanceScenarioDraft
import app.masroufy.usecase.ManageInheritanceScenarios
import app.masroufy.usecase.ManageInheritanceScenariosDeps
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * حسابات الورث المحفوظة (رد المالك §69.3 — OVERRIDES §69.4) على Firestore Emulator: الحسبة على **الحساب** (`users/{uid}`) مش جوه البلد،
 * فاللي اتحفظ من مصر بيتقري من السعودية بنفس المدخلات (وده اللي بيخليها تبان على كل الأجهزة). والتعديل بـmerge ما بيسيبش نوع وارث
 * اتشال ولا وصية اتشالت. أسامي وأرقام مخترعة. التشغيل زي `SavingsGoalsOnFirestoreTest`.
 */
@RunWith(AndroidJUnit4::class)
class InheritanceScenariosOnFirestoreTest {
    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun scenariosLiveOnTheAccountAndReadTheSameFromAnyCountry() = run {
        val uid = "kt-inherit-" + java.util.UUID.randomUUID()
        val db = Emulator.firestore()
        val account = FirestoreSpace.forAccount(db, uid)
        val egRoot = FirestoreSpace.forSpace(db, uid, "eg")
        val saudi = FirestoreContainer(FirestoreSpace.forUser(db, uid))
        val eg = FirestoreContainer(account, egRoot, "eg")
        val manage = ManageInheritanceScenarios(ManageInheritanceScenariosDeps(eg.inheritanceScenarios, SequentialIdGenerator(), FixedClock("2026-10-05T10:00:00.000Z")))

        val input = InheritanceCase(
            countryCode = "EG",
            heirs = mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2, HeirKind.DAUGHTER to 1),
            items = listOf(EstateItem("شقة وهمية", 90_000_000), EstateItem("ذهب وهمي", 6_000_000)),
            debtsMinor = 1_000_000,
            bequest = Bequest(2_000_000),
            names = mapOf(HeirKind.SON to listOf("ابن وهمي", "")),
        )
        val saved = manage.save(InheritanceScenarioDraft("تركتي الوهمية", EstateOwner.MINE, null, input))
        val other = manage.save(
            InheritanceScenarioDraft(
                "تركة وهمية", EstateOwner.OTHER, "p-1",
                InheritanceCase("SA", mapOf(HeirKind.DAUGHTER_SON to 1, HeirKind.DAUGHTER_DAUGHTER to 3), listOf(EstateItem("أرض وهمية", 7_200_000)),
                    distantBranches = listOf(DistantBranch(HeirKind.DAUGHTER, 1, 0), DistantBranch(HeirKind.DAUGHTER, 0, 3))),
            ),
        )

        // المكان: جذر الحساب، ومفيش نسخة جوه البلد
        val group = InheritanceCodecs.scenarios.group
        assertNotNull(account.readDoc(group, saved.id))
        assertNull(egRoot.readDoc(group, saved.id))

        // نفس المدخلات بالظبط من السعودية بعد الرحلة لفايربيز والرجوع
        assertEquals(setOf(saved, other), saudi.inheritanceScenarios.listAll().toSet())
        val fromSaudi = ManageInheritanceScenarios(ManageInheritanceScenariosDeps(saudi.inheritanceScenarios, SequentialIdGenerator(), FixedClock("2026-10-05T11:00:00.000Z")))
        val r = assertIs<InheritanceResult.Computed>(fromSaudi.calculate(other.id))
        assertEquals(listOf(3_600_000L), r.heir(HeirKind.DAUGHTER_SON)!!.amountsMinor)
        assertEquals(listOf(1_200_000L, 1_200_000L, 1_200_000L), r.heir(HeirKind.DAUGHTER_DAUGHTER)!!.amountsMinor)

        // التعديل (merge): البنت اتشالت والوصية اتشالت ⇒ مش فاضلين في المستند
        val edited = fromSaudi.save(
            InheritanceScenarioDraft("تركتي الوهمية", EstateOwner.MINE, null, input.copy(heirs = mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2), bequest = null)),
            saved.id,
        )
        val doc = assertNotNull(account.readDoc(group, saved.id))
        assertTrue("bequest" !in doc, "الوصية اتشالت ⇒ الحقل اتمسح")
        assertEquals(listOf("wife", "son"), (doc["heirs"] as List<*>).map { (it as Map<*, *>)["kind"] })
        assertEquals(edited, saudi.inheritanceScenarios.listAll().single { it.id == saved.id })
        assertEquals(saved.createdAt, edited.createdAt)

        // تغيير الاسم والمسح (مسموح — رد المالك)
        fromSaudi.rename(other.id, "اسم وهمي جديد")
        assertEquals("اسم وهمي جديد", eg.inheritanceScenarios.listAll().single { it.id == other.id }.name)
        fromSaudi.delete(other.id)
        assertNull(account.readDoc(group, other.id))
        assertEquals(listOf(saved.id), eg.inheritanceScenarios.listAll().map { it.id })
    }
}
