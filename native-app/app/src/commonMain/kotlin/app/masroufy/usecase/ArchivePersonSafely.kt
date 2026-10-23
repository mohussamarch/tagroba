package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.TextKey
import app.masroufy.core.uiText

/** نتيجة الأرشفة: اتعملت، أو مستنية تأكيد لأن فيه دين مفتوح في بلد من البلاد ([debts] سطر لكل بلد بعملته، و[warning] الجملة). */
sealed interface ArchiveOutcome {
    data object Done : ArchiveOutcome

    data class OpenDebts(val debts: List<PersonSpaceDebt>, val warning: String) : ArchiveOutcome
}

/**
 * أرشفة شخص (cty-6): الشخص واحد للحساب، فأرشفته من أي بلد بتخفيه من البلدين — **قبلها** بنشوف ديونه في كل البلاد ([across])، ولو فيه دين
 * مفتوح ما بنأرشفش غير بتأكيد صريح ([confirmed]). فك الأرشفة من غير سؤال.
 */
class ArchivePersonSafely(private val people: ManagePeople, private val across: PersonAcrossSpaces, private val names: suspend (Id) -> String?) {
    suspend fun archive(personId: Id, archived: Boolean, confirmed: Boolean = false): ArchiveOutcome {
        if (archived && !confirmed) {
            val open = across.debts(personId).filter { it.receivableMinor > 0 || it.payableLoanMinor > 0 || it.payableCustodyMinor > 0 }
            if (open.isNotEmpty()) {
                val name = names(personId) ?: throw IllegalStateException(uiText(TextKey.PERSON_NOT_FOUND))
                return ArchiveOutcome.OpenDebts(open, uiText(R06Key.CTY_ARCHIVE_OPEN, name))
            }
        }
        people.archivePerson(personId, archived)
        return ArchiveOutcome.Done
    }
}
