package app.masroufy.data

import app.masroufy.core.PEOPLE_BACKUP_GROUPS
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonProfile
import app.masroufy.core.PersonRelation
import app.masroufy.core.emptyBackupData
import app.masroufy.core.mergeFullBackup
import app.masroufy.core.personRelationId
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** دواير الأشخاص والصلات (جلسة 16) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class PersonCircleBackupTest {
    private val mom = Person("p-1", "أم وهمية")
    private val daughter = Person("p-2", "بنت وهمية")
    private val profile = PersonProfile("p-1", PersonCircle.FAMILY, "أمي", "2026-10-05T10:00:00.000Z")
    private val bare = PersonProfile("p-2", null, null, "2026-10-05T10:00:00.000Z")
    private val relation = PersonRelation(personRelationId("p-1", "p-2"), "p-1", "p-2", "أمها", "2026-10-05T10:00:00.000Z")

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        return d
    }

    @Test
    fun `الدايرة والصلة بيتكتبوا ويتقروا ومستند الشخص هو هو`() {
        assertEquals(mapOf("personId" to "p-1", "circle" to "family", "relationLabel" to "أمي", "updatedAt" to profile.updatedAt), roundTrip(PersonCircleCodecs.personProfiles, profile).toMap())
        assertEquals(setOf("personId", "updatedAt"), roundTrip(PersonCircleCodecs.personProfiles, bare).keys, "الفاضي ما بيتكتبش")
        assertEquals("p-1", PersonCircleCodecs.personProfiles.id(profile), "معرّف المستند = معرّف الشخص")
        assertFalse("label" in roundTrip(PersonCircleCodecs.personRelations, relation.copy(label = null)))
        assertEquals(relation.id, PersonCircleCodecs.personRelations.id(roundTrip(PersonCircleCodecs.personRelations, relation).let(PersonCircleCodecs.personRelations::decode)))
        val bad = PersonCircleCodecs.personProfiles.toStore(profile) + ("circle" to "enemies")
        assertTrue("enemies" in assertFailsWith<DocumentError> { PersonCircleCodecs.personProfiles.decode(bad) }.message!!)
        // التطبيق الحالي بيقرا الشخص بالشكل ده بالظبط — ولا حقل جديد
        assertEquals(setOf("id", "name", "archived"), ReferenceCodecs.people.toStore(mom).keys)
    }

    private fun account(withCircles: Boolean) = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(mom)
        it.getValue("people") += ReferenceCodecs.people.toStore(daughter)
        if (withCircles) {
            it.getValue("personProfiles") += PersonCircleCodecs.personProfiles.toStore(profile)
            it.getValue("personProfiles") += PersonCircleCodecs.personProfiles.toStore(bare)
            it.getValue("personRelations") += PersonCircleCodecs.personRelations.toStore(relation)
        }
    }

    @Test
    fun `الدواير بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withCircles = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-05T12:00:00.000Z")
        val text = file.toJsonText()
        for (g in PEOPLE_BACKUP_GROUPS) assertTrue("\"$g\"" in text, g)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(listOf(2, 1), PEOPLE_BACKUP_GROUPS.map { outcome.added[it] })
        for (g in PEOPLE_BACKUP_GROUPS) assertEquals(source.getValue(g), target.read().getValue(g), g)
        assertEquals(file.checksum, FullBackup(target).create("2026-10-05T12:00:00.000Z").checksum)
        val again = restore.apply(restore.plan(text).file)
        assertEquals(listOf(0, 0), PEOPLE_BACKUP_GROUPS.map { again.added[it] ?: 0 })
    }

    @Test
    fun `من غير دواير المجموعات ما بتتكتبش والمكسور بيترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withCircles = false))).create("2026-10-05T12:00:00.000Z").toJsonText()
        for (g in PEOPLE_BACKUP_GROUPS) assertFalse("\"$g\"" in text, g)
        fun rejects(why: String, change: (Map<String, MutableList<Map<String, Any?>>>) -> Unit) {
            val data = account(withCircles = true).also(change)
            assertFailsWith<IllegalArgumentException>(why) { runBlocking { FullBackup(MemoryFullBackup(data)).create("2026-10-05T12:00:00.000Z") } }
        }
        rejects("صلة لنفس الشخص") { it.getValue("personRelations")[0] = it.getValue("personRelations")[0] + ("personBId" to "p-1") }
        rejects("صلة بشخص مش موجود") { it.getValue("personRelations")[0] = it.getValue("personRelations")[0] + ("personBId" to "p-404") }
        rejects("دايرة لشخص مش موجود") { it.getValue("personProfiles")[1] = it.getValue("personProfiles")[1] + ("personId" to "p-404") }
        rejects("دايرة مش معروفة") { it.getValue("personProfiles")[0] = it.getValue("personProfiles")[0] + ("circle" to "enemies") }
        rejects("صلة أطول من 30") { it.getValue("personProfiles")[0] = it.getValue("personProfiles")[0] + ("relationLabel" to "ا".repeat(31)) }
    }

    @Test
    fun `الصلة المقلوبة بعد دمج الأشخاص ما بتتكررش`() {
        // نفس الشخصين، والصلة جاية من ملف تاني بمعرّف تاني وبالترتيب العكسي
        val existing = account(withCircles = true)
        val incoming = emptyBackupData().also {
            it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "أم وهمية"))
            it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-2", "بنت وهمية"))
            it.getValue("personRelations") += PersonCircleCodecs.personRelations.toStore(PersonRelation("prel-other", "p-2", "p-1", null, "2026-10-06"))
        }
        assertEquals(0, mergeFullBackup(incoming, existing).getValue("personRelations").size, "نفس الزوج = نفس الصلة")
    }
}
