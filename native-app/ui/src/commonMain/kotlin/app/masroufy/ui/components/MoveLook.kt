package app.masroufy.ui.components

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.Liquidity
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.ruleFor
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.text.t

/*
 * **شكل التحويل بين محافظك** (طلب المالك 2026-10-15 — HANDOVER §6.5-10: «ليه الحوالة بين حساباتي بتظهر سالب احمر؟ المفروض يكون في
 * سيمبول زي علامة الرفرش اللي بيكون سهمين نص دايرة … انه منه فيه»):
 * - اللون **محايد**: لون التحويل من نظام التصميم ([AmountTone.TRANSFER] = `Ink.transfer`) — مش أحمر الصرف ولا أخضر الدخل.
 * - **من غير «−» ولا «+»** — مكان العلامة رمز «منه فيه» ([MOVE_SWAP]) جنب الرقم (`AmountText`).
 * - «من الراجحي ← إلى الكاش» لما فيه مكان ([moveLineOf])، وجوه سجل محفظة واحدة «إلى الكاش» / «من الراجحي» ([moveSideLineOf]).
 * - **عرض بس**: المبلغ زي ما هو بالهللة، والنوع والمجاميع والأرصدة ما بتتلمسش (اختبار `MoveLookTest` بيتأكد).
 * - التحويل **لشخص تاني** مش منهم (نوعه مش «تحويل داخلي») ⇒ بيفضل بشكله (أحمر «−» / أخضر «+»).
 * الدوال نقية (JVM) — الشاشات بتنادي عليها بس.
 */

/**
 * رمز «منه فيه»: سهمين **كل واحد نص دايرة** بيلفّوا ورا بعض (زي علامة «تحديث» — كلام المالك)، مرسوم بإيدنا بخط لوسيد (1.5 · نهايات
 * مدورة · من غير ملء — `LucideIcon`) من غير مكتبة. دايرة نص قطرها 7 حوالين (12، 12)، وكل سهم 150° ورأسه زاوية 90° على آخره.
 */
val MOVE_SWAP: Lucide = Lucide(
    "MOVE_SWAP",
    "M5.24 10.19A7 7 0 0 1 18.76 10.19", "M20.26 7.59L18.76 10.19L16.16 8.69",
    "M18.76 13.81A7 7 0 0 1 5.24 13.81", "M3.74 16.41L5.24 13.81L7.84 15.31",
)

/**
 * تحويل بين محافظك؟ **من النموذج الموجود بس** (مفيش تصنيف جديد): النوع سيولته «داخلي» (`INTERNAL_TRANSFER` — من «+» · المساعد ·
 * «حسابي التاني» في الزون §60 · آخر 4 أرقام §75-11 · السحب للكاش §75-4 · التحويل لنفسك بين بلدين §64) **أو** العملية ليها محفظة مستلمة
 * ([Transaction.transferToWalletId] — ARCHITECTURE §17). التحويل اللي لسه «غير محدد» مش منهم لحد ما يتأكد.
 */
fun isOwnMove(tx: Transaction): Boolean = ruleFor(tx.economicKind).liquidity == Liquidity.INTERNAL || tx.transferToWalletId != null

/** عملية ورجوعها اتلغوا مع بعض (§77-D — نوعهم «تحويل داخلي» بس مش نقل بين محفظتين) ⇒ من غير «من ← إلى». */
fun isCancelledPair(tx: Transaction): Boolean = tx.reversalOfId != null || tx.reversedById != null

/** نبرة المبلغ: بين محافظك ⇒ محايد من غير علامة · الداخل ⇒ أخضر «+» · غير كده ⇒ أحمر «−». */
fun moneyToneOf(tx: Transaction): AmountTone = when {
    isOwnMove(tx) -> AmountTone.TRANSFER
    tx.observedDirection == Direction.IN -> AmountTone.INCOME
    else -> AmountTone.EXPENSE
}

/** طرفين التحويل بالأسامي. رجل لوحدها (من رسالة أو كشف — مالهاش محفظة مستلمة) ⇒ الطرف التاني `null` («حسابك الآخر»). */
data class MoveEnds(val from: String?, val to: String?)

/** [walletName] = اسم المحفظة بالمعرّف (null ⇒ مش معروفة). الطالع من المحفظة ⇒ هي «من»، والداخل فيها ⇒ هي «إلى». */
fun moveEnds(tx: Transaction, walletName: (Id?) -> String?): MoveEnds = when {
    tx.transferToWalletId != null -> MoveEnds(walletName(tx.walletId), walletName(tx.transferToWalletId))
    tx.observedDirection == Direction.IN -> MoveEnds(null, walletName(tx.walletId))
    else -> MoveEnds(walletName(tx.walletId), null)
}

/**
 * «من الراجحي ← إلى الكاش» (العربي من اليمين: «من» الأول و«←» في اتجاه القراية) · «From X → Y». الطرف المجهول ⇒ «حسابك الآخر».
 * مش تحويل بين محافظك · عملية ورجوعها · الطرفين مجهولين ⇒ `null` (الصف بيرجع لسطره العادي).
 */
fun moveLineOf(tx: Transaction, walletName: (Id?) -> String?): String? {
    if (!isOwnMove(tx) || isCancelledPair(tx)) return null
    val ends = moveEnds(tx, walletName)
    if (ends.from == null && ends.to == null) return null
    val other = t(UiKey.MOVE_OTHER_ACCOUNT)
    return t(UiKey.MOVE_FROM_TO, ends.from ?: other, ends.to ?: other)
}

/**
 * جوه سجل **محفظة واحدة** ([walletId]): الطالع منها ⇒ «إلى الكاش» · الداخل فيها ⇒ «من الراجحي» (الطرف المجهول ⇒ «حسابك الآخر»).
 * الاتجاه بيتقري من الكلمة مش من اللون. مش تحويل بين محافظك أو عملية ورجوعها ⇒ `null`.
 */
fun moveSideLineOf(tx: Transaction, walletId: Id, walletName: (Id?) -> String?): String? {
    if (!isOwnMove(tx) || isCancelledPair(tx)) return null
    val ends = moveEnds(tx, walletName)
    val incoming = if (tx.transferToWalletId != null) tx.transferToWalletId == walletId && tx.walletId != walletId else tx.observedDirection == Direction.IN
    val other = t(UiKey.MOVE_OTHER_ACCOUNT)
    return if (incoming) t(UiKey.MOVE_SIDE_FROM, ends.from ?: other) else t(UiKey.MOVE_SIDE_TO, ends.to ?: other)
}

/** السطر الصغير تحت اسم الحركة: «12 أكتوبر، إلى الكاش» — [note] = null ⇒ التاريخ لوحده. */
fun withMoveNote(date: String, note: String?): String = if (note == null) date else t(UiKey.MOVE_DATED, date, note)

/** «زون التحويلات»: طرف اتقرر إنه **حسابك التاني** ⇒ الداخل والطالع محايدين («وارد منه» / «صادر إليه») بدل الأخضر والأحمر. */
fun partyAmountTone(ownAccount: Boolean, incoming: Boolean): AmountTone = when {
    ownAccount -> AmountTone.TRANSFER
    incoming -> AmountTone.INCOME
    else -> AmountTone.EXPENSE
}

/** الكلمة الصغيرة جنب مبلغ الطرف اللي هو حسابك التاني (الاتجاه من غير لون). مش حسابك ⇒ `null` (اللون والعلامة كفاية). */
fun partyAmountLabel(ownAccount: Boolean, incoming: Boolean): String? =
    if (!ownAccount) null else t(if (incoming) UiKey.MOVE_PARTY_IN else UiKey.MOVE_PARTY_OUT)
