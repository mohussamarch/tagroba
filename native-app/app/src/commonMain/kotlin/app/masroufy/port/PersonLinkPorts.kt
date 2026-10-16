package app.masroufy.port

import app.masroufy.core.Id
import app.masroufy.core.PersonLink

/**
 * ربط العمليات بالأشخاص (جولة r05 — `PersonLinks.kt`): مستند لكل عملية، والمعرّف = معرّف العملية (العملية الواحدة مع شخص واحد).
 * في البلد زي الديون (الشخص نفسه على الحساب).
 */
interface PersonLinkRepository {
    suspend fun listByPerson(personId: Id): List<PersonLink>

    suspend fun listByTransactionIds(ids: List<Id>): List<PersonLink>

    suspend fun saveMany(links: List<PersonLink>)

    /** بمعرّفات العمليات (= معرّفات المستندات). */
    suspend fun deleteMany(transactionIds: List<Id>)
}
