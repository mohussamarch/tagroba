package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.Space
import app.masroufy.memory.FixedClock
import app.masroufy.usecase.LoadPeopleOverview
import app.masroufy.usecase.LoadPeopleOverviewDeps
import app.masroufy.usecase.ManagePersonCircles
import app.masroufy.usecase.ManagePersonCirclesDeps
import app.masroufy.usecase.PeopleSpaceSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * دواير الأشخاص والصلات (جلسة 16) على Firestore Emulator: على مستوى الحساب، و**مستند الشخص بالحرف زي ما هو**.
 * التشغيل زي `EventRepositoriesTest` (Firestore Emulator على 8088 + محاكي أندرويد). أسماء مخترعة.
 */
@RunWith(AndroidJUnit4::class)
class PersonCirclesOnFirestoreTest {
    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun circlesAndRelationsLiveOnTheAccountAndLeaveThePersonDocumentAlone() = run {
        val db = Emulator.firestore()
        val uid = "kt-" + java.util.UUID.randomUUID()
        val account = FirestoreSpace.forAccount(db, uid)
        val egypt = FirestoreSpace.forSpace(db, uid, "eg")
        val c = FirestoreContainer(account, egypt, "eg")
        c.people.save(Person("p-1", "أم وهمية"))
        c.people.save(Person("p-2", "بنت وهمية"))
        val personBefore = account.collection("people").document("p-1").get().rawData()
        val circles = ManagePersonCircles(ManagePersonCirclesDeps(c.people, c.personProfiles, c.personRelations, FixedClock("2026-10-05T10:00:00.000Z")))
        circles.setProfile("p-1", PersonCircle.FAMILY, "أمي")
        val relation = circles.relate("p-2", "p-1", "أمها")
        assertEquals(personBefore, account.collection("people").document("p-1").get().rawData(), "مستند الشخص ما اتلمسش")
        assertEquals(setOf("personId", "circle", "relationLabel", "updatedAt"), account.collection("personProfiles").document("p-1").get().rawData()!!.keys)
        assertEquals("p-1", account.collection("personRelations").document(relation.id).get().rawData()!!["personAId"])
        assertEquals(0, egypt.collection("personProfiles").get().documents.size, "مش جوه البلد")
        val space = Space("eg", "مصر", "EG", Currency.EGP, "c")
        val overview = LoadPeopleOverview(
            LoadPeopleOverviewDeps(c.people, c.personProfiles, c.personRelations, c.occasions, listOf(PeopleSpaceSource(space, c.obligations, c.settlements, c.lifeEvents, c.eventLinks, c.transactions))),
        ).forSpace("2026-10-05", "eg")
        assertEquals(listOf("p-1", "p-2"), overview.rings.getValue(PersonCircle.FAMILY) + overview.rings.getValue(PersonCircle.OTHER))
        assertEquals(1, overview.relations.size)
        circles.unrelate("p-1", "p-2")
        circles.setProfile("p-1", null, null)
        assertEquals(0, account.collection("personRelations").get().documents.size)
        assertEquals(0, account.collection("personProfiles").get().documents.size)
    }
}
