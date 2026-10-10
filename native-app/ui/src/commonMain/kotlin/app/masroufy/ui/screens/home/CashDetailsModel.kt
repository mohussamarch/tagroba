package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import app.masroufy.core.CashSummary
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayMonth
import app.masroufy.core.ruleFor
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.text.t

/**
 * لوحة الكاش (`CashDetails` — §26 · §32 · `LoadCashSummary`): الرصيد (من يوم رصيد البداية) · المصروف كاش في الشهر ·
 * عمليات الكاش في الشهر. كل الأرقام من `CashSummary` بالهللة — هنا ترتيب وكلام بس.
 * ⚠️ **ناقص في المنطق** (النموذج راسمه — مش متبني في كوتلن، فمش ظاهر هنا): «آخر عدّ» · «عددت الكاش الآن» والفرق («صرف كاش غير مسجّل»
 * بنقطة حمرا / «كاش زائد») · التحديث لوحده (السحب من الصرّاف · الدفع بالصوت · سؤال «كم معك كاش؟» كل مدة) · أكتر من محفظة كاش في البلد
 * (`LoadCashSummary` بياخد أول واحدة بس).
 */
data class CashView(
    val walletName: String,
    val currency: Currency,
    val balanceMinor: Halalas,
    val spentInPeriodMinor: Halalas,
    /** «من رصيد البداية في ١ سبتمبر». */
    val sinceLine: String,
    val rows: List<CashRow>,
)

const val CASH_ROWS = 5

data class CashRow(val id: String, val title: String, val date: IsoDate, val amountMinor: Halalas, val tone: AmountTone)

/** null من حالة الاستخدام = مفيش محفظة كاش ⇒ «غير متاح» (مش صفر). */
fun cashViewOf(summary: CashSummary?): CashView? {
    val s = summary ?: return null
    val w = s.wallet
    return CashView(
        walletName = w.name,
        currency = w.currency,
        balanceMinor = s.balanceMinor,
        spentInPeriodMinor = s.spentInPeriodMinor,
        sinceLine = t(UiKey.CASH_DETAILS_SINCE, dayMonth(w.openingAt)),
        // اللوحة طولها ثابت ⇒ أحدث [CASH_ROWS] بس (القايمة الكاملة في «العمليات» بفلتر المحفظة)
        rows = s.periodTransactions.take(CASH_ROWS).map { tx ->
            // داخل الكاش (سحب من البنك للكاش أو وارد عليه) أخضر، والخارج منه أحمر — من اتجاه الحركة نفسها، من غير حساب
            val intoCash = tx.transferToWalletId == w.id || (tx.walletId == w.id && tx.observedDirection == Direction.IN)
            CashRow(tx.id, transactionTitle(tx), tx.occurredAt, tx.amountMinor, if (intoCash) AmountTone.INCOME else AmountTone.EXPENSE)
        },
    )
}

/** اسم العملية في الصفوف: التاجر ⇒ الملاحظة ⇒ الوصف ⇒ اسم نوعها. */
fun transactionTitle(tx: Transaction): String =
    listOf(tx.rawMerchantName, tx.note, tx.rawDescription).firstOrNull { !it.isNullOrBlank() }?.trim() ?: ruleFor(tx.economicKind).label
