package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonCircleError
import app.masroufy.core.PersonProfile
import app.masroufy.core.PersonRelation
import app.masroufy.core.TextKey
import app.masroufy.core.cleanRelationLabel
import app.masroufy.core.newPersonRelation
import app.masroufy.core.relationPairKey
import app.masroufy.core.uiText
import app.masroufy.core.visibleRelations
import app.masroufy.port.Clock
import app.masroufy.port.PersonProfileRepository
import app.masroufy.port.PersonRelationRepository
import app.masroufy.port.PersonRepository

/**
 * دايرة الشخص وصلته بيك، والصلات بين الأشخاص (جلسة 16). **مستند الشخص ما بيتكتبش** — الدايرة والصلة في مجموعاتهم.
 * الأرشفة (`ManagePeople.archivePerson`) بتخبّي صلات الشخص ومش بتمسحها — رجوعه من الأرشيف بيرجّعها.
 */
data class ManagePersonCirclesDeps(
    val people: PersonRepository,
    val profiles: PersonProfileRepository,
    val relations: PersonRelationRepository,
    val clock: Clock,
)

class ManagePersonCircles(private val deps: ManagePersonCirclesDeps) {
    private suspend fun checkPerson(personId: Id) {
        if (deps.people.listAll().none { it.id == personId }) throw PersonCircleError(uiText(TextKey.PEOPLE_PERSON_NOT_FOUND))
    }

    /**
     * الدايرة والصلة بيك ([circle] null = من غير دايرة ⇒ «آخرون»). الاتنين فاضيين ⇒ المستند بيتشال (اختيار Claude).
     * الشخص المؤرشف مسموح (معلومة مش فلوس).
     */
    suspend fun setProfile(personId: Id, circle: PersonCircle?, relationLabel: String?): PersonProfile? {
        checkPerson(personId)
        val label = cleanRelationLabel(relationLabel)
        if (circle == null && label == null) {
            deps.profiles.remove(personId)
            return null
        }
        val profile = PersonProfile(personId, circle, label, deps.clock.nowIso())
        deps.profiles.save(profile)
        return profile
    }

    suspend fun profileOf(personId: Id): PersonProfile? = deps.profiles.listAll().firstOrNull { it.personId == personId }

    /** صلة بين شخصين بنص اختياري (أمها · أخوه). نفس الزوج تاني (بأي ترتيب) ⇒ بيعدّل نفس الصلة، مش صلة جديدة. */
    suspend fun relate(a: Id, b: Id, label: String?): PersonRelation {
        val existing = findPair(a, b)
        val relation = newPersonRelation(a, b, label, deps.people.listAll(), deps.clock.nowIso(), existing)
        deps.relations.save(relation)
        return relation
    }

    /** شيل الصلة (مش فلوس ⇒ مسح عادي). */
    suspend fun unrelate(a: Id, b: Id) {
        val existing = findPair(a, b) ?: throw PersonCircleError(uiText(TextKey.PERSON_RELATION_NOT_FOUND))
        deps.relations.remove(existing.id)
    }

    /** الصلات اللي بتظهر — الشخصين مش مؤرشفين. [personId] null = كلها. */
    suspend fun visible(personId: Id? = null): List<PersonRelation> =
        visibleRelations(deps.relations.listAll(), deps.people.listAll()).filter { personId == null || it.personAId == personId || it.personBId == personId }

    private suspend fun findPair(a: Id, b: Id): PersonRelation? {
        val key = if (a <= b) a to b else b to a
        return deps.relations.listAll().filter { relationPairKey(it) == key }.minWithOrNull(compareBy<PersonRelation> { it.createdAt }.thenBy { it.id })
    }
}
