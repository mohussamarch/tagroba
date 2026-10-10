package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.TextKey
import app.masroufy.core.normalizeText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.PersonRow

/** نوع ربط الشراء بشخص (spec/02 «حسابات الأشخاص»): دين عليه (`linkToPerson` RECEIVABLE) · هدية له (`asGift`). */
enum class PersonLinkKind { RECEIVABLE, GIFT }

/** سطر ربط ظاهر على الكارت: «خالد — دين عليه» والمبلغ. */
data class PersonLinkLine(val label: String, val amountMinor: Halalas, val currency: Currency, val tone: AmountTone)

/** سطر في «الأثر على أرقامك» — القيمة نص جاهز (المبلغ اللي كتبته زي ما هو، أو «بلا تغيير») ولونها. */
data class EffectLine(val label: String, val value: String, val tone: AmountTone?)

/** الديون اللي اتعملت من العملية دي قبل كده (`ManagePeople.listWithBalances` ⇒ التزامات أصلها العملية). الهدايا ما بتبانش هنا (مالهاش التزام). */
fun existingPersonLinks(rows: List<PersonRow>, transactionId: Id): List<PersonLinkLine> = rows.flatMap { row ->
    row.obligations.filter { it.obligation.originTransactionId == transactionId && it.obligation.kind == ObligationKind.RECEIVABLE }.map {
        PersonLinkLine(t(UiKey.LINK_PERSON_LINE, row.person.name, t(UiKey.LINK_PERSON_KIND_RECEIVABLE)), it.obligation.originalMinor, it.obligation.currency, AmountTone.INCOME)
    }
}

/** الأشخاص في اللوحة: المطابقين للبحث (بالاسم المطبّع)، وأول ٨ لو مفيش بحث — والمختار دايمًا ظاهر. */
fun personChoices(people: List<Person>, query: String, chosen: Person?): List<Person> {
    val q = normalizeText(query)
    val live = people.filter { !it.archived }
    val list = if (q.isEmpty()) live.take(8) else live.filter { normalizeText(it.name).contains(q) }
    return if (chosen == null || list.any { it.id == chosen.id }) list else listOf(chosen) + list
}

/** «+ أضف «الاسم»» بيظهر لو فيه كلام ومفيش شخص بنفس الاسم بالظبط. */
fun canAddPerson(people: List<Person>, query: String): Boolean {
    val q = normalizeText(query)
    return q.isNotEmpty() && people.none { normalizeText(it.name) == q }
}

/** رصيدك مع الشخص (من غير جمع): لك عنده · عليك له · أمانة له عندك · لا رصيد. */
fun personBalanceLine(row: PersonRow?, currency: Currency): String {
    val b = row?.balance ?: return t(UiKey.LINK_PERSON_NO_BALANCE)
    return when {
        b.receivableMinor > 0 -> t(UiKey.LINK_PERSON_OWES_YOU, amountLabel(b.receivableMinor, currency))
        b.payableLoanMinor > 0 -> t(UiKey.LINK_PERSON_YOU_OWE, amountLabel(b.payableLoanMinor, currency))
        b.payableCustodyMinor > 0 -> t(UiKey.LINK_PERSON_CUSTODY, amountLabel(b.payableCustodyMinor, currency))
        else -> t(UiKey.LINK_PERSON_NO_BALANCE)
    }
}

/** الأثر قبل الحفظ — **من غير حساب**: المبلغ اللي كتبته زي ما هو (الفرق نفسه)، أو «بلا تغيير». */
fun personEffect(kind: PersonLinkKind?, person: String?, amount: Halalas?, currency: Currency): List<EffectLine> {
    if (person == null || kind == null) return listOf(EffectLine(t(if (person == null) UiKey.LINK_PERSON_PICK_BOTH else UiKey.LINK_PERSON_PICK_KIND), t(UiKey.LINK_PERSON_EFFECT_HERE), null))
    if (amount == null || amount <= 0) return listOf(EffectLine(t(UiKey.LINK_PERSON_WRITE_AMOUNT), t(UiKey.LINK_PERSON_EFFECT_HERE), null))
    return when (kind) {
        PersonLinkKind.RECEIVABLE -> listOf(
            EffectLine(t(UiKey.LINK_PERSON_EFFECT_SPEND), amountLabel(amount, currency, AmountTone.EXPENSE), AmountTone.EXPENSE),
            EffectLine(t(UiKey.LINK_PERSON_EFFECT_OWES, person), amountLabel(amount, currency, AmountTone.INCOME), AmountTone.INCOME),
        )
        PersonLinkKind.GIFT -> listOf(
            EffectLine(t(UiKey.LINK_PERSON_EFFECT_SPEND), t(UiKey.LINK_PERSON_SAME), null),
            EffectLine(t(UiKey.LINK_PERSON_EFFECT_DEBT_ON, person), t(UiKey.LINK_PERSON_NOTHING), null),
        )
    }
}

/** خطأ الحفظ قبل ما نكلّم حالة الاستخدام (الشخص · النوع · المبلغ)؛ `null` = جاهز. حد «مايزيدش عن العملية» بيقوله `linkToPerson` نفسه. */
fun personLinkError(hasPerson: Boolean, kind: PersonLinkKind?, amount: Halalas?): String? = when {
    !hasPerson -> t(UiKey.LINK_PERSON_ERR_PERSON)
    kind == null -> t(UiKey.LINK_PERSON_ERR_KIND)
    amount == null || amount <= 0 -> t(UiKey.LINK_PERSON_ERR_AMOUNT)
    else -> null
}
