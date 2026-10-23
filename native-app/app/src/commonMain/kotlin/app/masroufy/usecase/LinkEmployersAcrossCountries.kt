package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.IsoDate
import app.masroufy.core.LinkedEmployer
import app.masroufy.core.employerIdentity
import app.masroufy.core.isEmployer
import app.masroufy.core.linkedEmployers
import app.masroufy.port.IncomeSourceRepository

/** مصادر دخل بلد واحدة (مصادر الدخل لكل بلد — `FirestoreContainer`). */
data class EmployerSpaceSource(val spaceId: String, val incomeSources: IncomeSourceRepository)

/**
 * §111-2 «جهة واحدة بمبلغين»: الجهة اللي بتحوّلك في البلدين بتبان **جهة واحدة** ومعاها مبلغ لكل عملة (`core/EmployerIdentity.kt`).
 * نفس الاسم بيترابط لوحده؛ اسمين مختلفين ([link]) بيتربطوا بإن المصدر التاني ياخد هوية الأول (`employerKey`) — مفيش نقل ولا مسح بيانات.
 * المبلغ بيفضل على مصدر كل بلد بعملتها (الكتابة من «ملفك»/«مصادر الدخل» في البلد دي زي ما هي).
 */
class LinkEmployersAcrossCountries(private val spaces: List<EmployerSpaceSource>) {
    suspend fun list(today: IsoDate): List<LinkedEmployer> = linkedEmployers(spaces.associate { it.spaceId to it.incomeSources.listAll() }, today)

    /** يربط مصدر [otherId] في [otherSpace] بجهة [sourceId] في [spaceId]. غلط (مش جهة شغل · نفس البلد · مش موجود) ⇒ false ومن غير كتابة. */
    suspend fun link(spaceId: String, sourceId: Id, otherSpace: String, otherId: Id): Boolean {
        if (spaceId == otherSpace) return false
        val first = find(spaceId, sourceId) ?: return false
        val repo = spaces.firstOrNull { it.spaceId == otherSpace }?.incomeSources ?: return false
        val second = repo.listAll().firstOrNull { it.id == otherId } ?: return false
        if (!isEmployer(first) || !isEmployer(second)) return false
        repo.saveMany(listOf(second.copy(employerKey = employerIdentity(first))))
        return true
    }

    /** يفك الربط: المصدر يرجع هويته اسمه. */
    suspend fun unlink(spaceId: String, sourceId: Id): Boolean {
        val repo = spaces.firstOrNull { it.spaceId == spaceId }?.incomeSources ?: return false
        val s = repo.listAll().firstOrNull { it.id == sourceId }?.takeIf { it.employerKey != null } ?: return false
        repo.saveMany(listOf(s.copy(employerKey = null)))
        return true
    }

    private suspend fun find(spaceId: String, id: Id): IncomeSource? =
        spaces.firstOrNull { it.spaceId == spaceId }?.incomeSources?.listAll()?.firstOrNull { it.id == id }
}
