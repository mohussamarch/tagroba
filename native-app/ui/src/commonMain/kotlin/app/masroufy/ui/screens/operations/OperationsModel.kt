package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.monthName
import app.masroufy.core.parseIsoDate
import app.masroufy.ui.text.t
import app.masroufy.usecase.TransactionsScreenData

/**
 * حالة خانة «العمليات» من `TransactionsScreenData` (عرض بس — المجاميع زي ما هي من حالة الاستخدام، و`null` ⇒ «غير متاح»).
 * الحالات: بيحمّل · خطأ · فاضي · عادي · تقريبي (فيه نوع تقديري) · غير متاح (المجاميع `null`).
 */
sealed interface OpsState {
    data object Loading : OpsState

    data object Failed : OpsState

    data class Ready(val view: OpsView) : OpsState
}

data class OpsView(
    /** «أكتوبر» — اسم الشهر اللي الفترة بتخلص فيه (الشهر المالي من يوم الراتب). */
    val periodLabel: String,
    val currency: Currency,
    val incomeMinor: Halalas?,
    val expenseMinor: Halalas?,
    /** فيه عمليات اتحسبت بنوع تقديري ⇒ شارة «تقريبي» (§18). */
    val approx: Boolean,
    val days: List<OpDay>,
    /** «محتاجة تأكيد» ⇒ شريط المراجعة. */
    val reviewCount: Int,
) {
    val empty: Boolean get() = days.isEmpty()
}

/** اسم الفترة = اسم الشهر اللي بتخلص فيه («٢٨ سبتمبر – ٢٧ أكتوبر» ⇒ «أكتوبر»). */
fun periodLabel(period: Period): String = monthName(parseIsoDate(period.end).month)

fun operationsView(data: TransactionsScreenData, wallets: List<Wallet>, today: IsoDate, currency: Currency): OpsView {
    val ctx = RowContext(data.categories, data.merchantNamesByTransaction, wallets)
    return OpsView(
        periodLabel = periodLabel(data.period),
        currency = currency,
        incomeMinor = data.incomeMinor,
        expenseMinor = data.expenseMinor,
        approx = data.estimatedCount > 0,
        days = dayGroups(data.transactions, ctx, today),
        reviewCount = data.needsReviewCount,
    )
}

/** شريط «مستنياك» — بيظهر **بس** لما يبقى فيه حاجة (اختيار المالك 2026-10-08). */
enum class BannerKind { BANK_SMS, REVIEW, TRANSFERS }

data class Banner(val kind: BannerKind, val text: String)

/**
 * [bankSmsWaiting] رسائل البنك المستنية تأكيد — ⚠️ مالهاش حالة استخدام في «العمليات» لسه (شغل منطقة الاستيراد: `ReviewSmsInbox` محتاج صندوق
 * الرسايل من الجهاز) ⇒ `null` ⇒ الشريط ما بيظهرش. [reviewCount] من `TransactionsScreenData.needsReviewCount`، و[partiesWaiting] من
 * `ManageTransfers.zone().questions`.
 */
fun banners(bankSmsWaiting: Int?, reviewCount: Int, partiesWaiting: Int): List<Banner> = buildList {
    if (bankSmsWaiting != null && bankSmsWaiting > 0) {
        add(Banner(BannerKind.BANK_SMS, countText(bankSmsWaiting, TextKey.OPERATIONS_SMS_ONE, TextKey.OPERATIONS_SMS_TWO, TextKey.OPERATIONS_SMS_FEW, TextKey.OPERATIONS_SMS_MANY)))
    }
    if (reviewCount > 0) add(Banner(BannerKind.REVIEW, t(TextKey.OPERATIONS_BANNER_REVIEW, operationsCount(reviewCount))))
    if (partiesWaiting > 0) {
        add(Banner(BannerKind.TRANSFERS, countText(partiesWaiting, TextKey.OPERATIONS_PARTIES_ONE, TextKey.OPERATIONS_PARTIES_TWO, TextKey.OPERATIONS_PARTIES_FEW, TextKey.OPERATIONS_PARTIES_MANY)))
    }
}

/** خانات المبدّل فوق: العمليات · الميزانيات · المستحقات. */
enum class OpsTab { OPERATIONS, BUDGETS, DUES }
