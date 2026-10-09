package app.masroufy.ui.shell.ask

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** رسالة في المحادثة — [mine] = اللي إنت كتبته، وإلا رد المساعد. */
data class ChatMessage(val id: Long, val mine: Boolean, val text: String)

/** محادثة: عنوانها في السجل = أول سؤال ليك، وتحته أول رد (زي `AssistantHistory` في النموذج). */
data class Conversation(val id: Long, val messages: List<ChatMessage>) {
    val title: String get() = messages.firstOrNull { it.mine }?.text.orEmpty()
    val firstReply: String get() = messages.firstOrNull { !it.mine }?.text.orEmpty()
    val isEmpty: Boolean get() = messages.isEmpty()
}

/**
 * المحادثة مع المساعد (`AskSheet` = `AssistantChat` في النموذج — OVERRIDES §76): **بتفضل** لو قفلت الشات أو اتنقلت بين الشاشات أو البلاد (§67)
 * لحد «محادثة جديدة» — اللي بتودّي الحالية للسجل. بتعيش في `ShellState` طول ما التطبيق مفتوح.
 * ⚠️ **في الذاكرة بس** (بتروح لما التطبيق يتقفل) — ❓ مستني المالك (آخر §76): السجل واللي المساعد بيتعلمه يفضلوا على الجهاز ولا يتزامنوا، ولمدة قد إيه.
 * ده حالة الشاشة بس (مين قال إيه) — **مفيش أي حساب ولا فهم للرسالة هنا** (لما المساعد يتوصل: حالة استخدام في `:app`).
 */
@Stable
class AskState {
    var current by mutableStateOf(Conversation(1, emptyList()))
        private set
    private val past = mutableStateListOf<Conversation>()
    private var nextId = 2L

    /** المساعد «بيكتب» (النقط التلاتة). */
    var typing by mutableStateOf(false)

    /** اللي مكتوب في الخانة ولسه ما اتبعتش (بيفضل لو الشات اتقفل). */
    var draft by mutableStateOf("")

    /** السجل: الحالية (لو فيها كلام) وبعدها اللي قبلها، الأحدث الأول. */
    val history: List<Conversation> get() = (if (current.isEmpty) emptyList() else listOf(current)) + past

    fun add(mine: Boolean, text: String) {
        current = current.copy(messages = current.messages + ChatMessage(nextId++, mine, text))
    }

    /** «محادثة جديدة»: الحالية بتروح للسجل. `false` = الحالية فاضية (الزرار معطّل أصلًا). */
    fun startNew(): Boolean {
        if (current.isEmpty) return false
        past.add(0, current)
        current = Conversation(nextId++, emptyList())
        typing = false
        draft = ""
        return true
    }

    /** يفتح محادثة من السجل (والحالية لو فيها كلام بترجع للسجل). */
    fun open(id: Long) {
        if (id == current.id) return
        val chosen = past.firstOrNull { it.id == id } ?: return
        past.remove(chosen)
        if (!current.isEmpty) past.add(0, current)
        current = chosen
        typing = false
    }

    /** يمسح محادثة (الحالية ⇒ تبدأ واحدة فاضية). بيرجّع اللي اتمسحت عشان «تراجع». */
    fun delete(id: Long): Conversation? {
        if (id == current.id) {
            val gone = current
            current = Conversation(nextId++, emptyList())
            typing = false
            return gone.takeUnless { it.isEmpty }
        }
        val i = past.indexOfFirst { it.id == id }
        return if (i < 0) null else past.removeAt(i)
    }

    /** «تراجع» بعد المسح: المحادثة بترجع السجل (مش بتبقى الحالية). */
    fun restore(conversation: Conversation) {
        if (conversation.id == current.id || past.any { it.id == conversation.id }) return
        past.add(0, conversation)
    }
}
