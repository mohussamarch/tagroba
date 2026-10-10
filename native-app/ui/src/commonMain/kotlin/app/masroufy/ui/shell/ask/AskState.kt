package app.masroufy.ui.shell.ask

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.masroufy.core.AskKey
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistTab
import app.masroufy.core.ChipRef
import app.masroufy.core.ForgottenMark
import app.masroufy.core.LearnedItem
import app.masroufy.core.StartItem
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.uiText
import app.masroufy.ui.app.AskDeps
import app.masroufy.usecase.AssistPanel
import app.masroufy.usecase.ChatView
import app.masroufy.usecase.HistoryRow
import app.masroufy.usecase.MemoryView

/** اللي مكتوب في خانة المساعد ولسه ما اتبعتش — بيفضل لو الشات اتقفل أو البلد اتبدّلت (بيعيش في `ShellState`). المحادثة نفسها على الحساب. */
@Stable
class AskState {
    var draft by mutableStateOf("")
}

/** «تراجع» لمدة ٤ ثواني: كارت بداية اتقفل · سؤال اتمسح · محادثة اتمسحت (المسح بيتم بعد الوقت). */
sealed interface AskUndo {
    val message: String

    data class StartCard(val mark: ForgottenMark, override val message: String) : AskUndo

    data class Unknown(val question: UnknownQuestion, override val message: String) : AskUndo

    data class Conversation(val row: HistoryRow, override val message: String) : AskUndo
}

/** مدة «تراجع» (قرار المالك: ٤ ثواني زي الإشعارات). */
const val ASK_UNDO_MS = 4_000L

/**
 * شاشة المساعد فوق المحرك (`AssistantSuite` — OVERRIDES §78 · §79.2 · §79.3): **مفيش أي فهم ولا حساب هنا** — كل رسالة وكارت ورقم وتحية
 * واقتراح من المحرك، والشاشة بتعرض وبتبعت الضغطة بس. المحادثة بتخلص بعد ساعة من غير رسايل (المحرك)، و«محادثة جديدة» بتودّي الحالية للسجل.
 * بيتختبر على JVM من غير Compose (`AskPresenterTest`).
 */
@Stable
class AskPresenter(private val deps: AskDeps, private val tab: () -> AssistTab) {
    var view by mutableStateOf<ChatView?>(null)
        private set

    /** «مصروفي بيكتب» — طول ما المحرك شغال. */
    var busy by mutableStateOf(false)
        private set

    /** محادثة قديمة مفتوحة من السجل (للقراية — الكتابة بترجع للحالية). */
    var past by mutableStateOf<List<AssistMessage>?>(null)
        private set

    var historyOpen by mutableStateOf(false)
    var history by mutableStateOf<List<HistoryRow>>(emptyList())
        private set
    var memory by mutableStateOf<MemoryView?>(null)
        private set
    var unknown by mutableStateOf<List<UnknownQuestion>>(emptyList())
        private set
    var unknownCount by mutableStateOf(0)
        private set
    var undo by mutableStateOf<AskUndo?>(null)
        private set

    /** الرسايل الظاهرة: القديمة من السجل لو مفتوحة، وإلا الحالية. */
    val messages: List<AssistMessage> get() = past ?: view?.messages.orEmpty()

    private suspend fun suite() = deps.suite()

    /** النهارده بتوقيت البلد (لتاريخ الكارت «النهارده» · «٢٨ أكتوبر» — `assistDate`). */
    var today by mutableStateOf("")
        private set

    private suspend fun ctx() = deps.context(tab()).also { today = it.today }

    private suspend fun run(block: suspend () -> ChatView) {
        busy = true
        try {
            val v = block()
            view = v
            if (v.panel != AssistPanel.NONE) showHistory()
        } finally {
            busy = false
        }
    }

    suspend fun open() = run { suite().chat.open(ctx()) }

    suspend fun send(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return false
        past = null
        run { suite().chat.send(clean, ctx()) }
        return true
    }

    /** اقتراح تحت الخانة أو «سجّلها» على كارت بداية — نفس النية والاسم بالظبط. */
    suspend fun chip(ref: ChipRef) {
        past = null
        run { suite().chat.ask(ref, ctx()) }
    }

    /** «محادثة جديدة» — `false` = الحالية فاضية أصلًا (الزرار معطّل). */
    suspend fun newConversation(): Boolean {
        if (view?.messages.isNullOrEmpty()) return false
        past = null
        run { suite().chat.newConversation(ctx()) }
        return true
    }

    suspend fun confirm(messageId: String) = run { suite().chat.confirm(messageId, ctx()) }

    suspend fun cancel(messageId: String) = run { suite().chat.cancel(messageId, ctx()) }

    suspend fun pick(messageId: String, optionId: String) = run { suite().chat.pick(messageId, optionId, ctx()) }

    suspend fun edit(messageId: String) = run { suite().chat.askEdit(messageId, ctx()) }

    suspend fun keep(messageId: String) = run { suite().chat.keepExisting(messageId, ctx()) }

    /** «×» على كارت في «أمور لم تُنجزها بعد» (L1): بيتقفل على الحساب حتى لو الحاجة ما اتعملتش، و«تراجع» ٤ ثواني. */
    suspend fun closeStart(item: StartItem) {
        commitUndo()
        val mark = suite().startCards.close(item.key)
        undo = AskUndo.StartCard(mark, uiText(AskKey.CHAT_START_CLOSED))
        open()
    }

    suspend fun showHistory() {
        historyOpen = true
        reloadPanel()
    }

    private suspend fun reloadPanel() {
        val s = suite()
        val c = ctx()
        val gone = (undo as? AskUndo.Conversation)?.row?.conversation?.id
        history = s.history.list(c.today).filter { it.conversation.id != gone }
        memory = s.memory.list(c)
        unknown = s.unknown.recent()
        unknownCount = s.unknown.list().size
    }

    suspend fun openPast(conversationId: String) {
        val current = view?.conversation?.id
        past = if (conversationId == current) null else suite().history.messages(conversationId)
        historyOpen = false
    }

    fun backToCurrent() {
        past = null
    }

    /** مسح محادثة من السجل: بتستخبى دلوقتي وبتتمسح فعلًا بعد «تراجع» (٤ ثواني) — [expireUndo]. */
    suspend fun deleteConversation(row: HistoryRow) {
        commitUndo()
        undo = AskUndo.Conversation(row, uiText(app.masroufy.core.UiKey.ASK_HISTORY_DELETED, row.conversation.title))
        history = history.filter { it.conversation.id != row.conversation.id }
    }

    suspend fun forget(item: LearnedItem) {
        suite().memory.forget(item.key)
        reloadPanel()
    }

    suspend fun clearMemory() {
        suite().memory.clearAll(ctx().today)
        reloadPanel()
    }

    suspend fun setLearning(on: Boolean) {
        suite().memory.setLearning(on)
        reloadPanel()
    }

    suspend fun removeUnknown(question: UnknownQuestion) {
        commitUndo()
        val gone = suite().unknown.remove(question.id) ?: return
        undo = AskUndo.Unknown(gone, uiText(AskKey.CHAT_UNKNOWN_REMOVED))
        reloadPanel()
    }

    suspend fun unknownText(): String = suite().unknown.asText()

    /** «تراجع»: الحاجة بترجع زي ما كانت. */
    suspend fun runUndo() {
        val u = undo ?: return
        undo = null
        when (u) {
            is AskUndo.StartCard -> suite().startCards.undo(u.mark)
            is AskUndo.Unknown -> suite().unknown.restore(u.question)
            is AskUndo.Conversation -> Unit
        }
        if (historyOpen) reloadPanel()
        if (u is AskUndo.StartCard) open()
    }

    /** وقت «تراجع» خلص (أو اللوحة اتقفلت): المسح المؤجّل بيتم. */
    suspend fun expireUndo() = commitUndo()

    private suspend fun commitUndo() {
        val u = undo ?: return
        undo = null
        if (u is AskUndo.Conversation) {
            suite().history.delete(u.row.conversation.id)
            if (u.row.conversation.id == view?.conversation?.id) open()
            if (past != null) past = null
        }
    }
}
