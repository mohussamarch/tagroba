package app.masroufy.usecase

import app.masroufy.core.EstateOwner
import app.masroufy.core.Id
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceResult
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.InheritanceScenarioError
import app.masroufy.core.TextKey
import app.masroufy.core.calculateInheritance
import app.masroufy.core.checkInheritanceScenario
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.InheritanceScenarioRepository

/** اللي المستخدم كتبه في الحاسبة قبل الحفظ. [personId] لتركة شخص تاني بس (اختياري). */
data class InheritanceScenarioDraft(val name: String, val estateOf: EstateOwner, val personId: Id?, val input: InheritanceCase)

data class ManageInheritanceScenariosDeps(val scenarios: InheritanceScenarioRepository, val ids: IdGenerator, val clock: Clock)

/**
 * **حفظ حسبة الورث** (رد المالك §69.3 — «تتحفظ على الحساب وتبان على كل الأجهزة»): حفظ · قايمة · فتح · تغيير الاسم · مسح.
 * - بتتحفظ **المدخلات** بس، والنتيجة بتتحسب تاني وقت الفتح ([calculate]) — أي تصحيح في المحرك يوصل للحسابات القديمة.
 * - **اختيار Claude:** الحسبة المحفوظة بتتحسب بقانون **البلد اللي اتعملت فيه** (المحفوظ معاها)، حتى لو اتفتحت من بلد تانية —
 *   عشان نفس الحسبة تطلع نفس النتيجة على كل الأجهزة. الحسبة الجديدة القانون من المساحة الشغالة (`CalculateInheritance`).
 * - المسح مسموح (رد المالك): مش بيانات فلوس.
 */
class ManageInheritanceScenarios(private val deps: ManageInheritanceScenariosDeps) {
    private suspend fun find(id: Id): InheritanceScenario =
        deps.scenarios.listAll().firstOrNull { it.id == id } ?: throw InheritanceScenarioError(uiText(TextKey.INHERIT_SCENARIO_NOT_FOUND))

    /** [id] null ⇒ حسبة جديدة. غير كده ⇒ تحديث نفس الحسبة (تاريخ الإنشاء بيفضل). */
    suspend fun save(draft: InheritanceScenarioDraft, id: Id? = null): InheritanceScenario {
        val now = deps.clock.nowIso()
        val old = id?.let { find(it) }
        val scenario = checkInheritanceScenario(
            InheritanceScenario(
                id = old?.id ?: deps.ids.next("inherit"),
                name = draft.name,
                estateOf = draft.estateOf,
                personId = draft.personId,
                input = draft.input,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        deps.scenarios.save(scenario)
        return scenario
    }

    /** الأحدث تعديلًا الأول. */
    suspend fun list(): List<InheritanceScenario> = deps.scenarios.listAll().sortedWith(compareByDescending<InheritanceScenario> { it.updatedAt }.thenBy { it.name })

    suspend fun load(id: Id): InheritanceScenario = find(id)

    suspend fun rename(id: Id, name: String): InheritanceScenario {
        val renamed = checkInheritanceScenario(find(id).copy(name = name, updatedAt = deps.clock.nowIso()))
        deps.scenarios.save(renamed)
        return renamed
    }

    suspend fun delete(id: Id) {
        find(id)
        deps.scenarios.remove(id)
    }

    /** الحسبة المحفوظة بقانون بلدها (المحفوظ معاها). */
    suspend fun calculate(id: Id): InheritanceResult = calculateInheritance(find(id).input)
}
