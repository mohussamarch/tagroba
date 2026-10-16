package app.masroufy.ui.screens.more

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.DEPENDENT_KINDS
import app.masroufy.core.MAX_NAME_LENGTH
import app.masroufy.core.SalaryState
import app.masroufy.core.TextKey
import app.masroufy.core.profileSalaryIn
import app.masroufy.core.UserProfile
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t

/**
 * «ملفك» (`Account`) — من الملف للعرض، **دالة نقية** (بتتختبر على JVM). [AccountRow.value] = null ⇒ «لم تُجب بعد» (مش «لا» — القاعدة 10).
 * الحفظ نفسه في `ManageProfile.saveWithQuestions` (التأكد من القيم هناك) — هنا بس بيتبني الملف الجديد من اختيار المستخدم.
 */
enum class AccountField { NAME, GENDER, DEPENDENTS, SALARY, PAYDAY, CAR, RENTER, MAID, BUSINESS }

/** [hintArg] = قيمة `{0}` في [hint] (مثلًا عدد الوظايف في «مجموع مرتبات N وظايف»). */
data class AccountRow(val field: AccountField, val label: TextRef, val value: String?, val hint: TextRef? = null, val hintArg: String? = null)

data class AccountGroup(val title: TextRef, val note: TextRef?, val rows: List<AccountRow>)

private fun yesNo(v: Boolean?): String? = when (v) {
    true -> t(UiKey.MORE_YES)
    false -> t(UiKey.MORE_NO)
    null -> null
}

fun genderLabel(gender: String?): String? = when (gender) {
    "male" -> t(UiKey.ACC_GENDER_MALE)
    "female" -> t(UiKey.ACC_GENDER_FEMALE)
    else -> null
}

fun dependentLabel(kind: String): String = when (kind) {
    "spouse" -> t(UiKey.ACC_DEP_SPOUSE)
    "children" -> t(UiKey.ACC_DEP_CHILDREN)
    else -> t(UiKey.ACC_DEP_PARENTS)
}

/** «من تعول»: «لا» ⇒ «لا أحد» · أنواع ⇒ أسماؤها بفاصلة · «نعم» من غير أنواع ⇒ «نعم» · ما اتجاوبش ⇒ null. */
fun dependentsValue(p: UserProfile): String? = when {
    p.supportsDependents == false -> t(UiKey.ACC_DEP_NONE)
    !p.dependentKinds.isNullOrEmpty() -> p.dependentKinds!!.joinToString("، ") { dependentLabel(it) }
    p.supportsDependents == true -> t(UiKey.MORE_YES)
    else -> null
}

/** العربية + «هل تذهب بها للدوام؟» لو اتجاوب. */
fun carValue(p: UserProfile): String? {
    val base = yesNo(p.hasCar) ?: return null
    if (p.hasCar != true || p.carToWork == null) return base
    return t(UiKey.ACC_CAR_WITH_WORK, base, t(if (p.carToWork == true) UiKey.ACC_CAR_TO_WORK else UiKey.ACC_CAR_NOT_WORK))
}

/**
 * المرتب (قرار المالك 2026-10-16: **مبلغ بالظبط**): المبلغ بعملة البلد · «أفضّل ألا أقول» · ما اتجاوبش ⇒ null (والنطاق القديم كمان null —
 * عمره ما بيتحول لرقم). [salary] = null ⇒ حالة الاستخدام مش متوصلة (المعاينة) ⇒ مبلغ الملف بعملة البلد بس لو موجود.
 */
fun salaryValue(p: UserProfile, currency: Currency, salary: SalaryState?): String? = when {
    salary == null -> profileSalaryIn(p, currency)?.let { amountLabel(it, currency) }
    salary.amountMinor != null -> amountLabel(salary.amountMinor, salary.currency)
    salary.declined -> t(UiKey.ACC_SAL_SKIP)
    else -> null
}

/**
 * تحت سطر المرتب: نطاق قديم ⇒ «إجابتك القديمة كانت نطاق — اكتب المبلغ بالظبط» · أكتر من جهة شغالة ⇒ «مجموع مرتبات N وظايف» (لو المجموع
 * معروف) أو «اكتب مرتب كل شغلانة لوحدها» (رد المالك 2026-10-16) · غير كده الجملة العادية.
 */
fun salaryHint(salary: SalaryState?): TextRef = when {
    salary?.wasRange == true -> UiKey.ACC_SALARY_WAS_RANGE
    (salary?.employers?.size ?: 0) > 1 -> if (salary?.amountMinor != null) UiKey.ACC_SALARY_TOTAL_OF else UiKey.ACC_SALARY_EACH_JOB
    else -> UiKey.ACC_SALARY_HINT
}

/** عدد الوظايف لـ«مجموع مرتبات N وظايف»، وإلا null. */
fun salaryHintArg(salary: SalaryState?): String? =
    salary?.takeIf { salaryHint(it) == UiKey.ACC_SALARY_TOTAL_OF }?.let { sentenceNumber(it.employers.size) }

fun accountGroups(p: UserProfile, currency: Currency, salary: SalaryState? = null): List<AccountGroup> = listOf(
    AccountGroup(
        UiKey.ACC_GROUP_ABOUT, null,
        listOf(
            AccountRow(AccountField.NAME, UiKey.ACC_NAME, p.displayName?.takeIf { it.isNotBlank() }),
            AccountRow(AccountField.GENDER, UiKey.ACC_GENDER, genderLabel(p.gender)),
            AccountRow(AccountField.DEPENDENTS, UiKey.ACC_DEPENDENTS, dependentsValue(p)),
        ),
    ),
    AccountGroup(
        UiKey.ACC_GROUP_INCOME, null,
        listOf(
            AccountRow(AccountField.SALARY, UiKey.ACC_SALARY, salaryValue(p, currency, salary), salaryHint(salary), salaryHintArg(salary)),
            AccountRow(AccountField.PAYDAY, UiKey.ACC_PAYDAY, t(UiKey.ACC_DAY_N, sentenceNumber(p.payday)), UiKey.ACC_PAYDAY_HINT),
        ),
    ),
    AccountGroup(
        UiKey.ACC_GROUP_LIFE, UiKey.ACC_LIFE_NOTE,
        listOf(
            AccountRow(AccountField.CAR, UiKey.ACC_CAR, carValue(p)),
            AccountRow(AccountField.RENTER, UiKey.ACC_RENTER, yesNo(p.renter)),
            AccountRow(AccountField.MAID, UiKey.ACC_MAID, yesNo(p.domesticWorker)),
            AccountRow(AccountField.BUSINESS, UiKey.ACC_BUSINESS, yesNo(p.business)),
        ),
    ),
)

/** «المستحقات ضمن حد الميزانية»: ما اتحددش ⇒ بتتحسب احتياطًا (§56 — اختيار Claude في `UserProfile.duesInBudget`). */
fun duesOn(p: UserProfile): Boolean = p.duesInBudget != false

fun duesDescription(p: UserProfile): TextRef = when (p.duesInBudget) {
    null -> UiKey.ACC_DUES_UNSET
    true -> UiKey.ACC_DUES_ON
    false -> UiKey.ACC_DUES_OFF
}

/** الاسم أطول من الحد (`MAX_NAME_LENGTH`) — نفس فحص `checkProfile`، بيبان وإنت بتكتب. */
fun nameTooLong(draft: String): Boolean = draft.trim().length > MAX_NAME_LENGTH

/** الملف بعد إجابة سؤال (المسودة من اللوحة). null = مفيش إجابة لسه ⇒ زرار الحفظ مقفول. */
fun applyAnswer(p: UserProfile, field: AccountField, draft: Any?): UserProfile? = when (field) {
    AccountField.NAME -> (draft as? String)?.let { p.copy(displayName = it.trim().ifEmpty { null }) } ?: p.copy(displayName = null)
    AccountField.GENDER -> p.copy(gender = draft as? String)
    AccountField.DEPENDENTS -> (draft as? List<*>)?.filterIsInstance<String>()?.let { kinds ->
        val ordered = DEPENDENT_KINDS.filter { it in kinds }
        p.copy(supportsDependents = ordered.isNotEmpty(), dependentKinds = ordered)
    }
    AccountField.PAYDAY -> (draft as? Int)?.let { p.copy(payday = it) }
    // «لا» على العربية بيمسح «هل تذهب بها للدوام؟» (`checkProfile`)، و«نعم» من «لا» بيسأله من جديد (`saveWithQuestions`)
    AccountField.CAR -> (draft as? Boolean)?.let { p.copy(hasCar = it) }
    AccountField.RENTER -> (draft as? Boolean)?.let { p.copy(renter = it) }
    AccountField.MAID -> (draft as? Boolean)?.let { p.copy(domesticWorker = it) }
    AccountField.BUSINESS -> (draft as? Boolean)?.let { p.copy(business = it) }
    AccountField.SALARY -> null
}
