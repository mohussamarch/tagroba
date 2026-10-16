package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import app.masroufy.port.SMS_HANDOFF_DONE
import app.masroufy.port.SMS_HANDOFF_PENDING_CARD

/**
 * تسليم «رسائل البنك» بعد أول تشغيل (قرار المالك 2026-10-15 مساءً — OVERRIDES §91) — القواعد من غير واجهة (`SmsHandoffTest`).
 * [pending] = فيه تسليم شغال · [asked] = الشرح اتعرض مرة ⇒ الكارت بدل الشرح · [requesting] = نافذة إذن النظام مفتوحة دلوقتي (r041 A6) ·
 * [history] = الإذن اتدى والبنوك اتربطت ⇒ سؤال «نقرأ الرسايل اللي فاتت؟» قبل أي حاجة تانية (r041 A8).
 */
data class HandoffState(val pending: Boolean = false, val asked: Boolean = false, val requesting: Boolean = false, val history: Boolean = false)

/** اللي يظهر دلوقتي: لا حاجة · الشرح (لوحة) · الكارت في الرئيسية · سؤال اللي فات (لوحة). */
enum class HandoffView { NONE, EXPLAIN, CARD, HISTORY }

/** [granted] = الإذن موجود (⇒ التسجيل على طول من غير شرح) · [readable] = الجهاز بيقرا رسايل (الآيفون لأ). */
fun handoffView(st: HandoffState, granted: Boolean, readable: Boolean): HandoffView = when {
    !st.pending || !readable -> HandoffView.NONE
    st.history -> HandoffView.HISTORY
    granted -> HandoffView.NONE
    !st.asked -> HandoffView.EXPLAIN
    else -> HandoffView.CARD
}

/** الإذن موجود والتسليم لسه ما ربطش البنوك ⇒ اربطها دلوقتي (أول مرة، أو بعد تشغيل جديد والإذن اتدى من الإعدادات). */
fun handoffNeedsConnect(st: HandoffState, granted: Boolean, readable: Boolean): Boolean = st.pending && readable && granted && !st.history

/**
 * r041 A6 — الترتيب المطلوب: الشرح ⇒ نافذة النظام ⇒ سؤال اللي فات ⇒ **بعدها** اللي بعد التسليم (لوحة الشكل من «كمّل بروفايلك»).
 * بيقفل وقت الشرح · وقت نافذة الإذن · وقت الربط (الإذن موجود) · وقت سؤال اللي فات. الكارت الهادي ما بيقفلش (المالك رفض أو قفل الشرح).
 */
fun lookMayOpen(st: HandoffState, granted: Boolean, readable: Boolean): Boolean = when {
    !st.pending || !readable -> true
    st.requesting || st.history -> false
    granted -> false
    else -> st.asked
}

/**
 * r041 A10 — الكارت بيرجع كل تشغيل لحد ما يسمح أو يقول «لاحقًا»: المتخزن على الجهاز ⇒ الحالة أول التشغيل. الكارت (مش الشرح تاني)؛ ولو الإذن
 * اتدى من الإعدادات في النص ⇒ نفس الحالة والمضيف بيربط ([handoffNeedsConnect]).
 */
fun restoredHandoff(stored: String?): HandoffState =
    if (stored == SMS_HANDOFF_PENDING_CARD) HandoffState(pending = true, asked = true) else HandoffState()

/** اللي يتخزن على الجهاز للحالة [st]: تسليم لسه ما ربطش ⇒ الكارت يرجع · غير كده خلص (سؤال اللي فات موجود كمان في «رسائل البنك»). */
fun persistedHandoff(st: HandoffState): String = if (st.pending && !st.history) SMS_HANDOFF_PENDING_CARD else SMS_HANDOFF_DONE

/**
 * r041 A8 — كلام الرئيسية الفاضية حسب القراءة (مش «فعّل قراءة رسائل البنك» والقراءة شغالة): الجهاز ما بيقراش رسايل (الآيفون) ⇒ الكشف ·
 * القراءة شغالة ⇒ «أول رسالة بتظهر هنا» · غير كده ⇒ النص القديم.
 */
fun homeEmptyBodyKey(readable: Boolean, readingOn: Boolean): UiKey = when {
    !readable -> UiKey.HOME_EMPTY_BODY_NO_SMS
    readingOn -> UiKey.HOME_EMPTY_BODY_READING
    else -> UiKey.HOME_EMPTY_BODY
}

/**
 * r041b P3: رسايل البنك المستنية «أكّد الأشكال» مرة واحدة (كل رسايل الأشكال الجديدة، مش سطر لكل شكل) — للكارت في الرئيسية
 * «عندك N رسالة من البنك مستنية تأكيدك». مفيش تقرير (الجهاز ما بيقراش · القراية فشلت) ⇒ 0 والكارت ما بيظهرش.
 */
fun smsShapeWaiting(report: app.masroufy.usecase.SmsHistoryReport?): Int = report?.shapes?.sumOf { it.count } ?: 0

/** r041b P3 (ج): بعد «أيوه، اقرأها» في آخر أول تشغيل ⇒ «رسائل البنك» (قايمة «أكّد الأشكال») على طول لو فيه رسايل مستنياها. */
fun openConfirmAfterHistory(report: app.masroufy.usecase.SmsHistoryReport?): Boolean = smsShapeWaiting(report) > 0
