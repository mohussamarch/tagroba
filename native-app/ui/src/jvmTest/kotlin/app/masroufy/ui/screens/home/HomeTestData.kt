package app.masroufy.ui.screens.home

import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertDecision
import app.masroufy.core.AlertKind
import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DueFlow
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.mutedAlertDecision
import app.masroufy.port.AlertInboxEntry
import app.masroufy.usecase.AlertInboxView
import app.masroufy.usecase.WalletNow
import app.masroufy.usecase.WithYouNow

/** بيانات وهمية لاختبارات حالة الشاشات (من غير أي رقم من بيانات المالك). */
internal object HomeTestData {
    val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 20_000, "2026-09-01")

    fun now(total: Halalas? = 120_000, cashMinor: Halalas? = 20_000, wallets: List<Wallet> = listOf(bank, cash)) =
        WithYouNow(Currency.SAR, wallets.map { WalletNow(it, if (total == null && it.kind != "cash") null else it.openingBalanceMinor) }, total, cashMinor)

    fun inbox(
        threadKey: String,
        kind: AlertKind,
        createdAt: String = "2026-10-09T08:00:00.000Z",
        muted: Boolean = false,
        openedAt: String? = null,
        flow: DueFlow = DueFlow.PAY,
        spaceLabel: String? = null,
    ): AlertInboxView {
        val decision = if (muted) mutedAlertDecision(kind) else AlertDecision(kind, AlertDelivery.SEND_NOW, null, false, emptyList())
        val entry = AlertInboxEntry(threadKey, "$threadKey|1", kind, flow, "عنوان $threadKey", "تفاصيل $threadKey", decision, createdAt, openedAt, spaceLabel)
        return AlertInboxView(entry, "سبب $threadKey", "مجموعة", muted)
    }

    fun tx(
        id: String,
        date: String,
        amount: Halalas,
        direction: Direction = Direction.OUT,
        wallet: String? = bank.id,
        to: String? = null,
        merchant: String? = null,
        note: String? = null,
        kind: EconomicKind = EconomicKind.UNCLASSIFIED,
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = false,
        observedDirection = direction, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-10-01T00:00:00.000Z", updatedAt = "2026-10-01T00:00:00.000Z",
        walletId = wallet, transferToWalletId = to, rawMerchantName = merchant, note = note,
    )

    fun item(
        type: CalendarItemType,
        date: String,
        daysLeft: Int,
        amount: Halalas? = null,
        flow: DueFlow? = DueFlow.PAY,
        reserved: Halalas? = null,
        title: String = "ميعاد $date",
    ) = CalendarItem(type, "src-$date-${type.wire}", title, date, amount, Currency.SAR, flow, daysLeft, reserved)
}
