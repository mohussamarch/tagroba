package app.masroufy.ui.shell.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.AskKey
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistantReply
import app.masroufy.core.CardState
import app.masroufy.core.CardType
import app.masroufy.core.ScreenLink
import app.masroufy.core.SplitDraft
import app.masroufy.core.TxnDraft
import app.masroufy.core.formatMoney
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.ShadowLayer
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.assistDate

/** الضغطات على رسالة — كلها بتروح للمحرك ([AskPresenter]) أو للتنقل. */
class MessageActions(
    val confirm: (String) -> Unit,
    val cancel: (String) -> Unit,
    val edit: (String) -> Unit,
    /** «دي هي، سيبها» على كارت شبه عملية النهارده. */
    val keep: (String) -> Unit,
    val pick: (messageId: String, optionId: String) -> Unit,
    val open: (ScreenLink) -> Unit,
)

private val mineShadow = listOf(ShadowLayer(0.dp, 8.dp, 18.dp, Color(0x3808634F)))

/**
 * فقاعة (`AssistantMessage`): بتاعتك خضرا بعرض 80٪ بالكتير، ورد المساعد كارت أبيض 88٪ — النص + نوع الرد من المحرك ([AssistMessage.reply]):
 * كارت عملية · كارت تقسيم · «بتصرف عادةً منين؟» بزرارين · اختيار · لحد ٣ زراير شاشات بتنقل. [live] = false (محادثة قديمة أو المحرك شغال) ⇒
 * الزراير مقفولة.
 */
@Composable
internal fun MessageBubble(m: AssistMessage, today: String, actions: MessageActions, live: Boolean) {
    val mine = m.from == AssistSpeaker.ME
    val reply = m.reply
    val rich = reply is AssistantReply.TxnCard || reply is AssistantReply.SplitCard
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(if (mine) 0.8f else 0.88f), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
            val shape = if (mine) AbsoluteRoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else AbsoluteRoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            val surface = if (mine) Modifier.layeredShadow(shape, mineShadow).clip(shape).background(Glass.primary)
            else Modifier.layeredShadow(shape, calmShadow).clip(shape).background(Glass.card).innerSheen(shape, calmShadow)
            Column(
                (if (rich) Modifier.fillMaxWidth() else Modifier).then(surface).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (m.text.isNotBlank()) BasicText(m.text, style = Type.of(14, lineHeight = 1.7).copy(color = if (mine) Color.White else Ink.text))
                when (reply) {
                    is AssistantReply.Text -> Links(reply.links, actions)
                    is AssistantReply.Unknown -> Links(reply.links, actions)
                    is AssistantReply.TxnCard -> TxnCardView(m.id, reply.draft, reply.state, today, actions, live)
                    is AssistantReply.SplitCard -> SplitCardView(m.id, reply.draft, reply.state, actions, live)
                    is AssistantReply.WalletPick -> Options(m.id, reply.options, reply.picked, live && m.pending, actions)
                    is AssistantReply.Choice -> Options(m.id, reply.options, reply.picked, live && m.pending, actions)
                    null -> Unit
                }
            }
        }
    }
}

/** لحد ٣ زراير شاشات تحت الرد (أقرب الشاشات في «مش فاهم»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(links: List<ScreenLink>, actions: MessageActions) {
    if (links.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in links.take(3)) SmallAction(l.label) { actions.open(l) }
    }
}

/** أزرار الاختيار («بتصرف عادةً منين؟» · «تقصد مين؟» · «أيوه/لأ»): اللي اتختار بيفضل متعلّم والباقي يتقفل. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Options(messageId: String, options: List<AssistOption>, picked: String?, enabled: Boolean, actions: MessageActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) SmallAction(o.label, primary = o.id == picked, enabled = enabled || o.id == picked) {
            if (enabled) actions.pick(messageId, o.id)
        }
    }
}

/** سطر حالة الكارت بعد ما يخلص: «اتسجّلت» أخضر · «اتعدّلت تحت» · «اتلغت». */
@Composable
private fun Settled(state: CardState) {
    val (key, color) = when (state) {
        CardState.DONE -> AskKey.CHAT_CARD_DONE to Ink.income
        CardState.DROPPED -> AskKey.CHAT_CARD_DROPPED to Ink.muted
        CardState.CANCELLED -> AskKey.CHAT_CARD_CANCELLED to Ink.muted
        CardState.PENDING -> return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(if (state == CardState.DONE) Lucide.CHECK else Lucide.X, size = 16.dp, tint = color)
        BasicText(t(key), style = Type.of(13, FontWeight.Bold).copy(color = color))
    }
}

/**
 * كارت عملية (§78-٣ + §79.2-7): الاسم · التفاصيل (التصنيف أو النوع · المحفظة · اليوم) · المبلغ بإشارة النوع ([CardType.sign] — من المحرك).
 * عملية شبهها النهارده (§79.2-6) ⇒ تنبيه + «سجّلها برضه» / «دي هي، سيبها».
 */
@Composable
private fun TxnCardView(messageId: String, d: TxnDraft, state: CardState, today: String, actions: MessageActions, live: Boolean) {
    val type = d.cardType
    val color = when (type) {
        CardType.EXPENSE, CardType.LENT -> Ink.expense
        CardType.INCOME, CardType.BORROWED -> Ink.income
        CardType.MOVE -> Ink.transfer
    }
    val icon = when (type) {
        CardType.EXPENSE -> Lucide.RECEIPT
        CardType.INCOME -> Lucide.BANKNOTE
        CardType.LENT, CardType.BORROWED -> Lucide.HAND_COINS
        CardType.MOVE -> Lucide.ARROW_LEFT_RIGHT
    }
    val day = if (today.isNotEmpty()) assistDate(d.occurredOn, today) else d.occurredOn
    val sub = when (type) {
        CardType.EXPENSE -> listOfNotNull(d.categoryName, d.walletName, day)
        CardType.INCOME -> listOfNotNull(t(AskKey.CHAT_SUB_INCOME), d.walletName, day)
        CardType.LENT -> listOfNotNull(t(AskKey.CHAT_SUB_LENT, d.personName.orEmpty()), d.walletName, day)
        CardType.BORROWED -> listOfNotNull(t(AskKey.CHAT_SUB_BORROWED, d.personName.orEmpty()), d.walletName, day)
        CardType.MOVE -> listOf(t(AskKey.CHAT_SUB_MOVE, d.walletName.orEmpty(), d.toWalletName.orEmpty()), day)
    }.joinToString(" · ")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            LucideIcon(icon, size = 18.dp, tint = color)
        }
        Column(Modifier.weight(1f)) {
            BasicText(d.title, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(sub, style = Type.caption().copy(color = Ink.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BasicText(type.sign + formatMoney(d.amountMinor, d.currency), style = Type.of(15, FontWeight.Bold).copy(color = color))
    }
    if (state != CardState.PENDING) return Settled(state)
    // عملية شبهها النهارده: النص فوق الكارت من المحرك («فيه عملية شبهها النهارده — …»)، والزرارين هنا
    val similar = d.similarTransactionId != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (similar) {
            SmallAction(t(AskKey.CHAT_SIMILAR_RECORD), primary = true, enabled = live) { actions.confirm(messageId) }
            SmallAction(t(AskKey.CHAT_SIMILAR_KEEP), enabled = live) { actions.keep(messageId) }
        } else {
            SmallAction(t(AskKey.CHAT_CARD_SAVE), primary = true, enabled = live) { actions.confirm(messageId) }
            SmallAction(t(AskKey.CHAT_CARD_EDIT), enabled = live) { actions.edit(messageId) }
        }
    }
}

/** كارت تقسيم: سطر لكل واحد (الحرف الأول · الاسم · نصيبه من المحرك) + «سجّل التقسيم». */
@Composable
private fun SplitCardView(messageId: String, d: SplitDraft, state: CardState, actions: MessageActions, live: Boolean) {
    Column {
        d.shares.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Ink.selected), contentAlignment = Alignment.Center) {
                    BasicText(s.name.take(1), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
                }
                BasicText(s.name, Modifier.weight(1f), style = Type.body(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(formatMoney(s.amountMinor, d.currency), style = Type.of(14, FontWeight.Bold))
            }
            if (i < d.shares.lastIndex) Box(Modifier.fillMaxWidth().padding(vertical = 1.dp).heightIn(min = 1.dp).background(Color(0x8CCCD8CC)))
        }
    }
    if (state != CardState.PENDING) return Settled(state)
    SmallAction(t(AskKey.CHAT_SPLIT_SAVE), primary = true, enabled = live) { actions.confirm(messageId) }
}
