package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey

/**
 * المحفظة اللي رسايل البنك بتتسجل فيها + أسباب إن رسالة **شكلها معروف** تستنى تأكيد المالك (اتنقلوا من `ReviewSmsInbox.kt` — عقد C0:
 * كل قاعدة في سطر لوحدها عشان كل شريحة تلمس سطرها بس).
 */

/**
 * `accountIdentity` = اسم المحفظة — نطاق تفرّد المرجع، زي شاشة الاستيراد. [currency] = عملة المحفظة: من غيرها رسايل QNB مصر
 * كانت هتتسجل بالريال (الاستيراد افتراضيه ريال) — اتكشف في جلسة 31. الافتراضي ريال عشان ملفات المرجع والتطبيق الحالي.
 */
data class SmsReviewTarget(
    val walletId: Id,
    val accountIdentity: String,
    val currency: Currency = Currency.SAR,
    /** آخر 4 أرقام حساب المحفظة دي (لو مكتوبة). */
    val accountLast4: String? = null,
    /**
     * آخر 4 أرقام حسابات المالك **التانية** في نفس البلد (الجولة السادسة): الرسالة اللي أرقام حسابها واحد منهم ومش المحفظة دي ⇒ ما
     * بتتسجلش لوحدها في المحفظة دي (كانت بتتسجل في محفظة البنك المربوط لمجرد إن المرسل نفسه) — بتستنى ومعاها سببها.
     */
    val otherAccountsLast4: Set<String> = emptySet(),
    /** عقد C0 (§75-4): محفظة الكاش الوحيدة في البلد (لو فيه واحدة بس)، وإلا null. */
    val cashWalletId: Id? = null,
)

/** الرسالة بتقول حساب تاني من حسابات المالك (مش حساب المحفظة دي). */
internal fun SmsReviewTarget.otherAccount(row: SmsRow): Boolean {
    val own = row.ownLast4 ?: return false
    return own in otherAccountsLast4 && own != accountLast4
}

/**
 * سبب إن رسالة **شكلها معروف** ما تتسجلش لوحدها، أو null: حساب تاني من حسابات المالك (الجولة السادسة) · **استرداد** (§75-6 ✗ — قرار
 * المالك: «الاسترداد ⇒ يقترح «استرداد» ويستنى تأكيده»؛ كان بيتسجل لوحده زي أي عملية) · **سحب كاش** (§75-4 — النقل لمحفظة الكاش لسه ما
 * اتبناش، فكان بيتسجل صرف عادي من البنك من غير ما يروح الكاش). الجولة السابعة.
 * الجولة التامنة: فلوس **داخلة على كارت ائتمان** («Credit Card Credited» · «تم قيد مبلغ … لبطاقتك الائتمانية») مش دخل لحساب البنك — كانت
 * بتتسجل داخل المحفظة وتلغي خصم «Credit Card Payment» فالرصيد يزيد بمبلغ السداد كله. إلا لو المحفظة دي **هي** الكارت (أرقامها نفس الكارت).
 * وإيداع كاش = نقل من محفظة الكاش (§75-4 بالعكس، لسه ما اتبناش) · شراء ومعاه كاش.
 */
internal fun waitReasonOf(row: SmsRow, target: SmsReviewTarget): TextKey? = when {
    target.otherAccount(row) -> TextKey.SMS_WAIT_OTHER_ACCOUNT
    row.kind == SmsKind.REFUND -> TextKey.SMS_WAIT_REFUND
    row.kind == SmsKind.CASH_WITHDRAWAL && target.cashWalletId == null -> TextKey.SMS_WAIT_NO_CASH_WALLET // §75-4 (S2): له محفظة كاش واحدة ⇒ `CashWithdrawalEffect`
    row.kind == SmsKind.CARD_PAYMENT && row.direction == Direction.IN && (row.ownLast4 == null || row.ownLast4 != target.accountLast4) -> TextKey.SMS_WAIT_CARD_CREDIT
    row.kind == SmsKind.CASH_DEPOSIT -> TextKey.SMS_WAIT_CASH_DEPOSIT
    row.kind == SmsKind.PURCHASE_WITH_CASH -> TextKey.SMS_WAIT_PURCHASE_CASH
    else -> null
}
