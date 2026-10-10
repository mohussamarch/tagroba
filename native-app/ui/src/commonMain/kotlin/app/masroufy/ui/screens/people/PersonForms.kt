package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ObligationKind
import app.masroufy.core.OccasionKind
import app.masroufy.core.PersonCircle
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.text.t
import app.masroufy.usecase.OccasionInput

/**
 * «شخص جديد» (`AddPersonSheet`) و«الديون القديمة» (`ProfileDebtSheet`) من غير رسم: الخانات والفحص **قبل أي كتابة**، وبعدين الكتابة
 * بحالات الاستخدام بالترتيب (`ManagePeople.addPerson` ⇒ `ManagePersonCircles.setProfile` ⇒ `ManageOccasions.add` ⇒ `ManagePeople.addOpeningDebt`).
 * ⚠️ مش كتابة واحدة: لو خطوة بعد إضافة الشخص فشلت، الشخص بيفضل (missingLogic — «إضافة شخص بمناسبة ودين قديم» محتاجة حالة استخدام واحدة).
 * ⚠️ `addOpeningDebt` مثبّت على الريال ⇒ في بلد عملتها غير الريال الدين القديم مقفول برسالة (مش هنكتب جنيه على إنه ريال).
 */
internal enum class DebtSide(val kind: ObligationKind) { LAK(ObligationKind.RECEIVABLE), ALEK(ObligationKind.LOAN_PAYABLE) }

internal data class NewPersonDraft(
    val name: String = "",
    val circle: PersonCircle? = null,
    val relation: String = "",
    val occOpen: Boolean = false,
    val occ: OccasionDraft = OccasionDraft(),
    val debtOpen: Boolean = false,
    val side: DebtSide? = null,
    val amount: String = "",
)

/** الكتابة اللي هتتعمل بعد ما الفحص يعدّي. */
internal data class NewPersonPlan(
    val name: String,
    val circle: PersonCircle?,
    val relation: String?,
    val occasion: OccasionInput?,
    val debt: Pair<ObligationKind, Halalas>?,
)

internal sealed interface PlanCheck<out T> {
    data class Ok<T>(val plan: T) : PlanCheck<T>
    data class Bad(val message: String) : PlanCheck<Nothing>
}

/** نفس ترتيب رسايل النموذج. الاسم المكرر بيترفض من `addPerson` نفسها (أول كتابة — مفيش حاجة اتكتبت قبلها). */
internal fun checkNewPerson(d: NewPersonDraft, currency: Currency): PlanCheck<NewPersonPlan> {
    val name = jsTrim(d.name).replace(Regex("\\s+"), " ")
    if (name.isEmpty()) return PlanCheck.Bad(t(TextKey.ADD_PERSON_NEED_NAME))
    var occasion: OccasionInput? = null
    if (d.occOpen) {
        if (d.occ.month == null) return PlanCheck.Bad(t(TextKey.ADD_PERSON_NEED_MONTH))
        // من الإضافة: الفرح مرة واحدة والباقي كل سنة، والتذكير قبلها بأسبوع (بيتغير من ملفه)
        val draft = d.occ.copy(yearly = d.occ.kind != OccasionKind.WEDDING, lead = OccasionDraft.DEFAULT_LEAD)
        when (val c = checkDraft(draft, null)) {
            is OccasionCheck.Bad -> return PlanCheck.Bad(c.message)
            is OccasionCheck.Ok -> occasion = c.input
        }
    }
    var debt: Pair<ObligationKind, Halalas>? = null
    if (d.debtOpen) {
        val side = d.side ?: return PlanCheck.Bad(t(TextKey.ADD_PERSON_NEED_SIDE))
        val minor = tryParseMoney(d.amount, currency)
        if (minor == null || minor <= 0) return PlanCheck.Bad(t(TextKey.ADD_PERSON_NEED_AMOUNT))
        if (currency != Currency.SAR) return PlanCheck.Bad(t(TextKey.PPL_SAR_ONLY))
        debt = side.kind to minor
    }
    return PlanCheck.Ok(NewPersonPlan(name, d.circle, d.relation.trim().ifEmpty { null }, occasion, debt))
}

/** الكتابة بالترتيب — بترجع معرّف الشخص الجديد (أو بترمي رسالة حالة الاستخدام زي ما هي). */
internal suspend fun saveNewPerson(deps: PeopleDeps, plan: NewPersonPlan): Id {
    val person = deps.people.addPerson(plan.name)
    if (plan.circle != null || plan.relation != null) deps.circles.setProfile(person.id, plan.circle, plan.relation)
    plan.occasion?.let { deps.occasions.add(it.copy(personId = person.id)) }
    plan.debt?.let { (kind, minor) -> deps.people.addOpeningDebt(person.id, kind, minor) }
    return person.id
}

// ── «الديون القديمة» من «كمّل ملفك» (`ProfileDebtSheet`)

/** سطر دين قديم: شخص موجود ([personId]) أو اسم جديد. */
internal data class OldDebtRow(val personId: Id?, val name: String, val side: DebtSide, val amountMinor: Halalas)

internal data class OldDebtDraft(val who: Id? = null, val whoName: String = "", val newName: String = "", val amount: String = "", val side: DebtSide)

internal sealed interface OldDebtCheck {
    data class Ok(val row: OldDebtRow) : OldDebtCheck
    /** [nameBad] و[amountBad] = الخانة اللي ناقصة (بتتعلّم بالأحمر). */
    data class Bad(val nameBad: Boolean, val amountBad: Boolean, val message: String) : OldDebtCheck
    /** الخانات فاضية خالص (للـ«تم» من غير سطر جديد). */
    data object Empty : OldDebtCheck
}

internal fun checkOldDebt(d: OldDebtDraft, currency: Currency): OldDebtCheck {
    val typed = jsTrim(d.newName)
    val name = typed.ifEmpty { d.whoName }
    if (name.isEmpty() && d.amount.isBlank()) return OldDebtCheck.Empty
    val minor = tryParseMoney(d.amount, currency)
    val nameBad = name.isEmpty()
    val amountBad = minor == null || minor <= 0
    if (nameBad || amountBad) {
        val msg = when {
            nameBad -> t(TextKey.PROFILE_DEBT_NEED_NAME)
            name.isNotEmpty() -> t(TextKey.PROFILE_DEBT_NEED_AMOUNT_OF, name)
            else -> t(TextKey.PROFILE_DEBT_NEED_AMOUNT)
        }
        return OldDebtCheck.Bad(nameBad, amountBad, msg)
    }
    return OldDebtCheck.Ok(OldDebtRow(if (typed.isEmpty()) d.who else null, name, d.side, minor!!))
}

/** كل سطر: الشخص الجديد بيتضاف الأول، وبعدين الدين من غير عملية (`addOpeningDebt` — ما بيدخلش المصروف ولا الدخل ولا الكاش). */
internal suspend fun saveOldDebts(deps: PeopleDeps, rows: List<OldDebtRow>, currency: Currency) {
    if (currency != Currency.SAR) throw IllegalStateException(t(TextKey.PPL_SAR_ONLY))
    val made = HashMap<String, Id>()
    for (r in rows) {
        // نفس الاسم الجديد في سطرين ⇒ شخص واحد
        val id = r.personId ?: made.getOrPut(r.name) { deps.people.addPerson(r.name).id }
        deps.people.addOpeningDebt(id, r.side.kind, r.amountMinor)
    }
}
