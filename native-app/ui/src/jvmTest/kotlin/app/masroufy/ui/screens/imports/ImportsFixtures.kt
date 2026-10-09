package app.masroufy.ui.screens.imports

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Language
import app.masroufy.core.MatchingState
import app.masroufy.core.ParsedRow
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.port.QueuedSms
import app.masroufy.usecase.InboxItem
import app.masroufy.usecase.InboxView
import app.masroufy.usecase.SmsReview
import app.masroufy.usecase.SmsReviewLine

/**
 * بيانات مخترعة صغيرة لاختبارات حالة شاشات «الاستيراد» (من نتايج حالات الاستخدام ⇒ حالة الشاشة). **مفيش بيانات حقيقية** (المستودع عام).
 * المبالغ بالوحدة الصغرى.
 */
internal object Fx {
    fun resetTexts() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    fun smsRow(line: Int, merchant: String, amount: Halalas, direction: Direction = Direction.OUT, date: String = "2026-10-08") =
        SmsRow(line, date, amount, direction, merchant, null, "BANK", merchant, "raw $line")

    fun item(id: String, sender: String, parsed: SmsParseResult, at: String = "2026-10-08T09:00:00.000Z") = InboxItem(id, sender, at, parsed)

    fun queued(id: String, sender: String, body: String) = QueuedSms(id, sender, "2026-10-08T09:00:00.000Z", body)

    fun inbox(
        enabled: Boolean = true,
        permission: Boolean = true,
        senders: List<String> = listOf("BANK-A"),
        items: List<InboxItem> = emptyList(),
        messages: List<QueuedSms> = emptyList(),
    ) = InboxView(enabled, permission, more = false, count = items.size, senders = senders, messages = messages, items = items)

    fun reviewLine(
        id: String,
        merchant: String,
        amount: Halalas,
        state: MatchingState = MatchingState.NEW,
        categoryId: String? = null,
        confirmReason: String? = null,
    ) = SmsReviewLine(id, 1, "2026-10-08", merchant, amount, Direction.OUT, categoryId, remembered = false, state = state, reason = "سبب $id", confirmReason = confirmReason)

    fun review(ready: List<SmsReviewLine> = emptyList(), similar: List<SmsReviewLine> = emptyList()) =
        SmsReview(enabled = true, permission = true, senders = listOf("BANK-A"), more = false, ready = ready, similar = similar, duplicates = emptyList(), failed = emptyList(), categories = emptyList())

    fun txn(
        id: String,
        merchant: String?,
        amount: Halalas,
        categoryId: String? = null,
        confirmed: Boolean = false,
        direction: Direction = Direction.OUT,
        date: String = "2026-10-08",
        currency: Currency = Currency.SAR,
        createdAt: String = "2026-10-08T09:00:00.000Z",
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = direction, amountMinor = amount, currency = currency, categoryConfirmed = confirmed, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = createdAt, updatedAt = createdAt, categoryId = categoryId,
        rawMerchantName = merchant, rawDescription = "وصف $id",
    )

    fun category(id: String, name: String, group: String? = null, parent: String? = null, active: Boolean = true, order: Int = 0) =
        Category(id, parent, name, "tag", "#3866A7", "#9BB8E8", active, order, groupKey = group)

    fun wallet(id: String, name: String, kind: String = "bank", currency: Currency = Currency.SAR, last4: String? = null) =
        Wallet(id, name, currency, kind, 0, "2026-01-01", accountLast4 = last4)

    fun parsed(line: Int, merchant: String, amount: Halalas, direction: Direction = Direction.OUT, date: String = "2026-09-0${line % 9 + 1}") =
        ParsedRow(line, date, amount, direction, merchant, null, "BANK", merchant, "raw $line")

    fun batch(
        id: String,
        at: String,
        state: ImportBatchState = ImportBatchState.COMMITTED,
        type: ImportSourceType = ImportSourceType.PDF_ALRAJHI,
        counts: ImportCounts = ImportCounts(total = 5, imported = 3, duplicates = 1, similar = 0, conflicts = 0, invalid = 1),
        file: String = "statement-$id.pdf",
    ) = ImportBatch(id, type, "hash-$id", file, at, state, counts)
}
