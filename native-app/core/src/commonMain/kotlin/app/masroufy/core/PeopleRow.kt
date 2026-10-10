package app.masroufy.core

/**
 * صف «أهم 5» فوق شخصيتك في تبويب «الأشخاص» (OVERRIDES §84 — طلب المالك 2026-10-10، والتفاصيل في KOTLIN-MAP «وضع تعديل صف «أهم 5»»):
 * - **الترتيب بإيدك** بيتحفظ في الحساب مع كل تغيير (`userSettings/peopleRow` — [UserSetting.PeopleRow]) ويتزامن ويدخل النسخة الشاملة.
 * - **قبل أي ترتيب بإيدك:** المُعالون أولًا ثم الأكثر تعاملًا ([defaultPeopleRow]).
 * - «×» بيشيل الشخص **من الصف بس** (رد المالك — الشخص بيفضل في قايمة الناس) · «+» بيضيف في **آخر** الصف · أقصى [PEOPLE_ROW_MAX].
 * دوال نقية بس — مفيش تخزين هنا (حالة الاستخدام `ManagePeopleRow`).
 */
const val PEOPLE_ROW_MAX = 5

/** معرّف مستند الصف في `userSettings`. */
const val PEOPLE_ROW_KEY = "peopleRow"

/**
 * شخص ممكن يظهر في الصف (مش مؤرشف). [dependent] = من «المُعالين» (اختيار Claude: دايرته «عيلة» — مفيش علامة «مُعال» على الشخص نفسه)،
 * [interactions] = عدد تعاملاته (ديون · تسوياتها · روابط الأحداث) في كل البلاد.
 */
data class PeopleRowCandidate(val personId: Id, val dependent: Boolean, val interactions: Int)

/** الصف الظاهر: المعرّفات بالترتيب، و[custom] = اتعمل ترتيب بإيدك (جملة «المُعالون أولًا…» بتختفي لأنها مابقتش صح). */
data class PeopleRow(val ids: List<Id>, val custom: Boolean)

/** كل الناس بالترتيب الافتراضي: المُعالون أولًا ثم الأكثر تعاملًا — والتعادل بترتيب [candidates] نفسه (ثابت، مش عشوائي). */
fun rankForPeopleRow(candidates: List<PeopleRowCandidate>): List<Id> =
    candidates.withIndex()
        .sortedWith(compareBy<IndexedValue<PeopleRowCandidate>>({ if (it.value.dependent) 0 else 1 }, { -it.value.interactions }, { it.index }))
        .map { it.value.personId }
        .distinct()

/** الصف الافتراضي (قبل أي ترتيب بإيدك): أول [PEOPLE_ROW_MAX] من [rankForPeopleRow]. */
fun defaultPeopleRow(candidates: List<PeopleRowCandidate>): List<Id> = rankForPeopleRow(candidates).take(PEOPLE_ROW_MAX)

/**
 * الصف اللي بيتعرض: الترتيب بإيدك [manual] (من غير اللي اتأرشف أو اتمسح ومن غير تكرار، وأقصى 5) — ولو مفيش ترتيب بإيدك (null) ⇒ الافتراضي.
 * ترتيب بإيدك فاضي (شال الكل) بيفضل فاضي — ما بيرجعش للافتراضي من وراه.
 */
fun peopleRow(manual: List<Id>?, candidates: List<PeopleRowCandidate>): PeopleRow {
    if (manual == null) return PeopleRow(defaultPeopleRow(candidates), custom = false)
    val live = candidates.map { it.personId }.toSet()
    return PeopleRow(manual.distinct().filter { it in live }.take(PEOPLE_ROW_MAX), custom = true)
}

/** السحب: [id] بياخد المكان [to] والباقي بيتزق (مش تبديل اتنين بس). مش في الصف ⇒ زي ما هو. */
fun rowMove(row: List<Id>, id: Id, to: Int): List<Id> {
    val from = row.indexOf(id)
    if (from < 0) return row
    val target = to.coerceIn(0, row.lastIndex)
    if (target == from) return row
    return row.toMutableList().apply { removeAt(from); add(target, id) }
}

/** «×»: بيشيله من الصف بس. */
fun rowRemove(row: List<Id>, id: Id): List<Id> = row.filter { it != id }

/** «تراجع» بعد «×»: يرجع **لنفس مكانه** ([index] — لو الصف قصر بيروح آخره). موجود أو الصف مليان ⇒ زي ما هو. */
fun rowRestore(row: List<Id>, id: Id, index: Int): List<Id> {
    if (id in row || row.size >= PEOPLE_ROW_MAX) return row
    return row.toMutableList().apply { add(index.coerceIn(0, size), id) }
}

/** «+» ⇒ «أضف للصف»: في **آخر** الصف. موجود أو الصف مليان (5) ⇒ زي ما هو. */
fun rowAppend(row: List<Id>, id: Id): List<Id> = if (id in row || row.size >= PEOPLE_ROW_MAX) row else row + id
