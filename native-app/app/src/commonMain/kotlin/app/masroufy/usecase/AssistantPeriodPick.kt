package app.masroufy.usecase

import app.masroufy.core.AskKey
import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistNote
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistPeriod
import app.masroufy.core.AssistPeriodKind
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.CardState
import app.masroufy.core.IsoDate
import app.masroufy.core.asksWhichEid
import app.masroufy.core.checkPickedRange
import app.masroufy.core.eidChoices
import app.masroufy.core.isAnyEid
import app.masroufy.core.occasionIn
import app.masroufy.core.uiText
import app.masroufy.core.understandAssist

/**
 * §82-1 (رد المالك 2026-10-10): «صرفت كام في رمضان؟» ⇒ **يسألك عن أول وآخر يوم** (مش من التقويم الهجري). الكارت رسالة «اختيار» نوعها
 * [AssistChoiceKind.PICK_PERIOD] (الكلام والنية والمناسبة في الـpayload)، والشاشة بتبعت اليومين لـ`AssistantChat.pickPeriod` ⇒ الإجابة من
 * **نفس** حالة الاستخدام اللي بتجاوب أي فترة (`AssistantAnswers` بالمدى [AssistPeriodKind.BETWEEN]) — المساعد ما بيحسبش رقم بنفسه.
 */
internal const val PERIOD_TEXT = "text"
internal const val PERIOD_INTENT = "intent"
internal const val PERIOD_OCCASION = "occasion"

/**
 * الكارت: «رمضان كان من إمتى لإمتى؟» مستني اليومين. §89-2: «العيد» لوحده في السعودية ⇒ «أنهي عيد؟» الأول ([pickEid] بيكمّل بنفس الكارت)؛
 * في مصر (وأي بلد تاني) كارت اليومين على طول — ما بنخمّنش أنهي عيد.
 */
internal fun TurnKit.askPeriod(u: AssistUnderstanding): TurnOut {
    if (isAnyEid(u.signals) && asksWhichEid(ctx.space.countryCode)) {
        val msg = bot(AssistMessageKind.CHOICE, uiText(AskKey.CHAT_EID_WHICH), u.wire).copy(
            choice = AssistChoiceKind.PICK_OCCASION, state = CardState.PENDING, subjectId = u.subject?.id,
            options = eidChoices().map { (id, name) -> AssistOption(id, name) },
            payload = mapOf(PERIOD_TEXT to u.signals.raw, PERIOD_INTENT to u.intent.wire),
        )
        return TurnOut(listOf(msg), countTopic = false)
    }
    return TurnOut(listOf(periodCard(u.signals.raw, u.intent.wire, u.subject?.id, occasionIn(u.signals).orEmpty(), u.wire)), countTopic = false)
}

/** «أنهي عيد؟» اتجاوب ⇒ كارت اليومين باسم العيد ده (نفس كارت «عيد الفطر» بالاسم). اختيار مش من الاتنين ⇒ null (ولا حاجة بتتقفل). */
internal fun TurnKit.pickEid(msg: AssistMessage, optionId: String): TurnOut? {
    val name = eidChoices().firstOrNull { it.first == optionId }?.second ?: return null
    return TurnOut(listOf(periodCard(msg.payload[PERIOD_TEXT].orEmpty(), msg.payload[PERIOD_INTENT].orEmpty(), msg.subjectId, name, msg.topic)), countTopic = false)
}

private fun TurnKit.periodCard(said: String, intent: String, subjectId: String?, occasion: String, topic: String?): AssistMessage =
    bot(AssistMessageKind.CHOICE, uiText(AskKey.CHAT_PERIOD_ASK, occasion), topic).copy(
        choice = AssistChoiceKind.PICK_PERIOD, state = CardState.PENDING, subjectId = subjectId,
        payload = mapOf(PERIOD_TEXT to said, PERIOD_INTENT to intent, PERIOD_OCCASION to occasion),
    )

/**
 * اليومين اتختاروا: الكارت بيتقفل باليومين ([AssistMessage.picked] = «من|لحد») والإجابة رسالة جديدة بنفس النية والاسم الأساسي. مدى غلط ⇒ رسالة
 * بالسبب والكارت بيفضل مستني. الكارت اللي اتجاوب قبل كده ⇒ ولا حاجة (ضغطتين ورا بعض ما بيجاوبوش مرتين).
 */
internal suspend fun TurnKit.answerPeriod(msg: AssistMessage, from: IsoDate, to: IsoDate): TurnOut {
    if (!msg.pending || msg.choice != AssistChoiceKind.PICK_PERIOD) return TurnOut(countTopic = false)
    checkPickedRange(from, to, ctx.today)?.let { return TurnOut(listOf(text(it, msg.topic)), countTopic = false) }
    val said = msg.payload[PERIOD_TEXT].orEmpty()
    val base = understandAssist(said, chat.understandContext(lex, msgs, ctx))
    val intent = msg.payload[PERIOD_INTENT]?.let { AssistIntent.fromWire(it) } ?: base.intent
    val subject = msg.subjectId?.let { id -> base.subject?.takeIf { it.id == id } ?: entityById(lex, id) } ?: base.subject
    val name = msg.payload[PERIOD_OCCASION]?.takeIf { it.isNotBlank() }
    val u = base.copy(intent = intent, subject = subject, note = null, period = AssistPeriod(AssistPeriodKind.BETWEEN, from = from, to = to, name = name))
    val r = chat.answers.answer(u, ctx)
    val answer = text(r.text, u.wire, r.links, r.approximate).copy(subjectId = subject?.id)
    return TurnOut(listOf(answer), listOf(msg.copy(state = CardState.DONE, picked = "$from|$to")))
}

/** الملاحظة اللي بتطلب الكارت. */
internal fun AssistUnderstanding.asksPeriod(): Boolean = note == AssistNote.ASK_PERIOD
