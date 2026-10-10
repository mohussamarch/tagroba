package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.PEOPLE_ROW_MAX
import app.masroufy.core.PeopleRow
import app.masroufy.core.PeopleRowCandidate
import app.masroufy.core.PersonCircle
import app.masroufy.core.UserSetting
import app.masroufy.core.peopleRow
import app.masroufy.core.rankForPeopleRow
import app.masroufy.port.Clock
import app.masroufy.port.PersonProfileRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.UserSettingsStore

data class ManagePeopleRowDeps(
    val people: PersonRepository,
    val profiles: PersonProfileRepository,
    val settings: UserSettingsStore,
    /** البلاد اللي بنعدّ فيها التعاملات (نفس بلاد `LoadPeopleOverview` — الشخص مشترك بين البلاد). */
    val spaces: List<PeopleSpaceSource>,
    val clock: Clock,
)

/**
 * صف «أهم 5» فوق شخصيتك (OVERRIDES §84): [row] = الصف الظاهر (ترتيبك المحفوظ، أو الافتراضي «المُعالون أولًا ثم الأكثر تعاملًا») ·
 * [save] = بيحفظ الترتيب في الحساب **مع كل تغيير** (سحب · «×» · «تراجع» · «+») · [pickable] = الناس اللي مش في الصف («أضف للصف»).
 * الحساب كله في `core/PeopleRow.kt`؛ هنا القراية والكتابة بس.
 */
class ManagePeopleRow(private val deps: ManagePeopleRowDeps) {
    suspend fun row(): PeopleRow = peopleRow(stored(), candidates())

    /** بيحفظ [ids] كترتيبك (من غير المؤرشفين والمكرر، أقصى 5) ويرجّع الصف الجديد. */
    suspend fun save(ids: List<Id>): PeopleRow {
        val active = deps.people.listAll().filter { !it.archived }.map { it.id }.toSet()
        val clean = ids.distinct().filter { it in active }.take(PEOPLE_ROW_MAX)
        deps.settings.save(UserSetting.PeopleRow(clean, deps.clock.nowIso()))
        return PeopleRow(clean, custom = true)
    }

    /** «أضف للصف»: كل الناس (مش مؤرشفين) اللي مش في [row]، بنفس ترتيب الافتراضي (المُعالون أولًا ثم الأكثر تعاملًا). */
    suspend fun pickable(row: List<Id>): List<Id> = rankForPeopleRow(candidates()).filter { it !in row }

    private suspend fun stored(): List<Id>? = deps.settings.listAll().filterIsInstance<UserSetting.PeopleRow>().firstOrNull()?.personIds

    private suspend fun candidates(): List<PeopleRowCandidate> {
        val people = deps.people.listAll().filter { !it.archived }
        val family = deps.profiles.listAll().filter { it.circle == PersonCircle.FAMILY }.map { it.personId }.toSet()
        return people.map { p ->
            var count = 0
            for (s in deps.spaces) {
                val debts = s.obligations.listByPerson(p.id)
                count += debts.size + s.links.listByPerson(p.id).size
                if (debts.isNotEmpty()) count += s.settlements.listByObligations(debts.map { it.id }).size
            }
            PeopleRowCandidate(p.id, p.id in family, count)
        }
    }
}
