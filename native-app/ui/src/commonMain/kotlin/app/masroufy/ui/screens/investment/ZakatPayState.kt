package app.masroufy.ui.screens.investment

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPayment
import app.masroufy.core.IsoDate
import app.masroufy.core.ZakatYearStatus
import app.masroufy.core.parseMoney
import app.masroufy.core.uiText
import app.masroufy.usecase.TransactionsScreenData

/**
 * «دفع زكاة السنة» (`ZakatPay` جوه `Zakat`): من `PayZakat.status` (المطلوب · اللي اتدفع · الباقي · الصدقة الزيادة · السطور بخاناتها)
 * و`PayZakat.payments` — زي ما هم، من غير حساب. السنة اللي بتتدفع = اللي اتثبّتت (معرّفها = بداية السنة المفتوحة بعدها).
 */
data class PayLine(val kind: ZakatLineKind, val label: String, val dueMinor: Halalas, val sub: String, val done: Boolean)

data class PaymentRow(val id: Id, val label: String, val sub: String, val amountMinor: Halalas)

data class ZakatPayUi(
    val yearId: Id,
    val yearLabel: String,
    val dueMinor: Halalas,
    val paidMinor: Halalas,
    val leftMinor: Halalas,
    /** «دُفعت زكاة هذه السنة كاملة» (والصدقة الزيادة لو فيه) — null لو لسه فيه باقي. */
    val allPaidText: String?,
    val lines: List<PayLine>,
    val payments: List<PaymentRow>,
)

fun zakatPayUi(yearId: Id, dueAt: IsoDate, status: ZakatYearStatus, payments: List<ZakatPayment>, ops: TransactionsScreenData?, currency: Currency): ZakatPayUi {
    val lines = status.lines.map { l ->
        val sub = when {
            l.paid -> uiText(TextKey.ZAKAT_PAY_LINE_DONE)
            l.paidMinor > 0 -> uiText(TextKey.ZAKAT_PAY_LINE_PART, moneyText(l.paidMinor, currency), moneyText(l.remainingMinor, currency))
            else -> uiText(TextKey.ZAKAT_PAY_LINE_NONE)
        }
        PayLine(l.kind, l.kind.label, l.dueMinor, sub, l.paid)
    }
    val rows = payments.sortedWith(compareByDescending<ZakatPayment> { it.paidAt }.thenByDescending { it.createdAt }).map { p ->
        val txn = p.transactionId?.let { id -> ops?.transactions?.firstOrNull { it.id == id } }
        val label = when {
            p.transactionId == null -> uiText(TextKey.ZAKAT_PAY_CASH_ROW)
            txn != null && ops != null -> opTitle(txn, ops)
            else -> uiText(TextKey.ZAKAT_PAY_FROM_STATEMENT)
        }
        val names = p.lines.joinToString("، ") { it.label }
        PaymentRow(p.id, label, uiText(TextKey.INVEST_ROW_SUB, names, dateText(p.paidAt)), p.amountMinor)
    }
    val allPaid = status.remainingMinor <= 0L && status.dueMinor > 0L
    val allPaidText = when {
        !allPaid -> null
        status.extraCharityMinor > 0 -> uiText(TextKey.ZAKAT_PAY_ALL_DONE_EXTRA, moneyText(status.extraCharityMinor, currency))
        else -> uiText(TextKey.ZAKAT_PAY_ALL_DONE)
    }
    return ZakatPayUi(
        yearId = yearId,
        yearLabel = uiText(TextKey.ZAKAT_PAY_FOR, dateText(dueAt)),
        dueMinor = status.dueMinor,
        paidMinor = status.paidMinor,
        leftMinor = status.remainingMinor,
        allPaidText = allPaidText,
        lines = lines,
        payments = rows,
    )
}

/** السطور اللي لسه عليها باقي (لـ«دفعت الكل»). */
fun unpaidKinds(ui: ZakatPayUi): Set<ZakatLineKind> = ui.lines.filter { !it.done }.map { it.kind }.toSet()

/** اختيار «دفعتها كاش» في «كيف دفعتها؟» (بدل معرّف عملية). */
const val PAY_CASH = "cash"

/** من اختيارات «دفع زكاة السنة» لمدخل `PayZakat` — قراية بس (المبلغ بـ`parseMoney`)، والتوزيع والفحص الحقيقي في حالة الاستخدام. */
sealed interface ZakatPayRequest {
    data class Cash(val lines: List<ZakatLineKind>, val amountMinor: Halalas) : ZakatPayRequest
    data class FromOperation(val lines: List<ZakatLineKind>, val transactionId: Id) : ZakatPayRequest
    data class Invalid(val message: String) : ZakatPayRequest
}

/** [picked] السطور المختارة · [how] معرّف العملية أو [PAY_CASH] · [cash] المبلغ المكتوب لو كاش. السطور بترتيب السنة. */
fun zakatPayRequest(picked: Set<ZakatLineKind>, how: Id?, cash: String, currency: Currency): ZakatPayRequest {
    val lines = ZakatLineKind.entries.filter { it in picked }
    return when {
        lines.isEmpty() -> ZakatPayRequest.Invalid(uiText(TextKey.ZAKAT_LINES_INVALID))
        how == null -> ZakatPayRequest.Invalid(uiText(TextKey.ZAKAT_PAY_ERR_HOW))
        how == PAY_CASH -> {
            val amount = runCatching { parseMoney(cash, currency) }.getOrNull()
            if (amount == null || amount <= 0) ZakatPayRequest.Invalid(uiText(TextKey.ZAKAT_PAY_ERR_CASH)) else ZakatPayRequest.Cash(lines, amount)
        }
        else -> ZakatPayRequest.FromOperation(lines, how)
    }
}
