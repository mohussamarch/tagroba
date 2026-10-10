package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * §75-4 (الشريحة S2 — مراجعتها): أنهي سحب كاش من رسالة البنك **بيتنقل لمحفظة الكاش لوحده**. السحب العادي من الحساب جوه البلد بس —
 * مش لو:
 * - **سلفة نقدية من كارت ائتمان** («credit card … CASH WITHDRAWAL» · «cash advance» · «بطاقة ائتمانية»): الفلوس طلعت من الكارت مش من
 *   الحساب، فنقلها من محفظة الحساب للكاش بيغلط الرصيد (زي سؤال (أو) في §72.5 — محفظة للكارت؟).
 * - **صرّاف برّه البلد** (عنوان البنك المركزي «سحب صراف آلي دولي» / «International ATM Withdrawal»): اللي في الإيد عملة تانية،
 *   فإضافته لمحفظة الكاش بعملة البلد غلط.
 * دول بيستنوا تأكيد المالك، ولو أكّدهم بيتسجلوا زي ما هم (من غير نقل) — سؤال مفتوح للمالك.
 */

private val SI = setOf(RegexOption.IGNORE_CASE)

/** سلفة نقدية من كارت ائتمان. */
private val CREDIT_CARD = Regex("ائتمان|${B}credit$S*card$B|${B}cash$S*advance$B|سلف[ةه]$S*نقدي", SI)

/** صرّاف برّه البلد. «دولي» لوحدها لأ: جوه أسامي بنوك («التجاري الدولي» · «Commercial International Bank»). */
private val ABROAD = Regex(
    "(?:صراف$S*(?:آلي|الي)?|سحب(?:$S*نقدي)?)$S*دولي|${B}international$S+(?:ATM|cash|withdrawal)$B|${B}ATM$S+international$B|خارج$S*(?:ال)?مملكة",
    SI,
)

/** سحب كاش صادر **من الحساب** وجوه البلد ⇒ بيتنقل لمحفظة الكاش لوحده (لو فيه محفظة كاش واحدة بنفس العملة — `CashWithdrawalEffect`). */
fun SmsRow.movesToCash(): Boolean =
    kind == SmsKind.CASH_WITHDRAWAL && direction == Direction.OUT && !CREDIT_CARD.containsMatchIn(raw) && !ABROAD.containsMatchIn(raw)
