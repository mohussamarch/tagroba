package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.DueFlow
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DuesMonthLine
import app.masroufy.core.DuesTotals
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Language
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonBalance
import app.masroufy.core.Texts
import app.masroufy.core.dueStatusOf
import app.masroufy.usecase.DuesView
import app.masroufy.usecase.PersonObligationRow
import app.masroufy.usecase.PersonRow

/**
 * بيانات وهمية لاختبارات شاشات «المستحقات» (المستودع عام — **مفيش بيانات حقيقية**). نتايج حالات الاستخدام مبنية بإيدنا بأنواعها نفسها،
 * والاختبار بيتأكد إن الشاشة بتنقلها لحالة العرض صح (من غير ما تحسب مبلغ).
 */
internal const val TODAY: IsoDate = "2026-10-09"

/** اللغة والنسخة قبل كل اختبار وبعده (الحالة عامة في `Texts`). */
internal fun useArabic(variant: ArabicVariant = ArabicVariant.MSA) {
    Texts.language = Language.AR
    Texts.arabicVariant = variant
}

internal fun useEnglish() {
    Texts.language = Language.EN
    Texts.arabicVariant = ArabicVariant.MSA
}

internal fun resetTexts() = useArabic(ArabicVariant.MSA)

internal fun obligation(id: String, personId: String, kind: ObligationKind, originalMinor: Halalas, opening: Boolean = false, currency: Currency = Currency.SAR) =
    Obligation(id, personId, if (opening) null else "txn-$id", kind, originalMinor, currency)

/** شخص بديونه: كل التزام مع المتبقي منه (زي ما `listWithBalances` بترجّعه). الرصيد قيم ثابتة للاختبار — الشاشة ما بتقراهوش. */
internal fun personRow(id: String, name: String, vararg rows: Pair<Obligation, Halalas>) = PersonRow(
    person = Person(id, name),
    balance = PersonBalance(id, 0, 0, 0),
    obligations = rows.map { (o, remaining) -> PersonObligationRow(o, remaining) },
)

internal fun debtDue(obligationId: String, dueAt: IsoDate, amountMinor: Halalas, receive: Boolean, today: IsoDate = TODAY) =
    DueItem(DueSource.DEBT, obligationId, "debt $obligationId", dueAt, amountMinor, Currency.SAR, if (receive) DueFlow.RECEIVE else DueFlow.PAY, dueStatusOf(dueAt, today))

internal fun dueItem(source: DueSource, id: String, title: String, dueAt: IsoDate, amountMinor: Halalas, flow: DueFlow, today: IsoDate = TODAY) =
    DueItem(source, id, title, dueAt, amountMinor, Currency.SAR, flow, dueStatusOf(dueAt, today))

internal fun totals(
    receivable: Halalas = 0, roscaSaved: Halalas = 0, loans: Halalas = 0, custody: Halalas = 0, installments: Halalas = 0, roscaOwed: Halalas = 0,
) = DuesTotals(receivable, roscaSaved, loans, custody, installments, roscaOwed)

internal fun duesView(totals: DuesTotals, agenda: List<DueItem> = emptyList(), pay: Halalas = 0, receive: Halalas = 0, financing: Halalas = 0) =
    DuesView(totals, agenda, DuesMonthLine(pay, receive, null, 0), financing)
