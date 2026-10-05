package app.masroufy.port

import app.masroufy.core.Id
import app.masroufy.core.PersonProfile
import app.masroufy.core.PersonRelation

/**
 * دواير الأشخاص والصلات بينهم (جلسة 16) — على مستوى الحساب (الشخص مشترك بين البلاد §41 · §64).
 * `listAll` مسموح: العدد صغير بطبيعته (على قد الأشخاص).
 */
interface PersonProfileRepository {
    suspend fun listAll(): List<PersonProfile>

    suspend fun save(profile: PersonProfile)

    /** الدايرة والصلة مش فلوس ⇒ بتتمسح عادي لما الاتنين يفضوا. */
    suspend fun remove(personId: Id)
}

interface PersonRelationRepository {
    suspend fun listAll(): List<PersonRelation>

    suspend fun save(relation: PersonRelation)

    suspend fun remove(id: Id)
}
