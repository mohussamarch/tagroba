package app.masroufy.firestore

import app.masroufy.data.DocumentCodecs
import app.masroufy.port.AuthPort
import app.masroufy.port.AuthUser
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * جلسة الحساب: مين داخل ⇒ بيانات **الحساب ده بس** (`users/{uid}`). الشاشات بتاخد المستودعات من هنا، مش من فايربيز.
 * - حد دخل ⇒ تجميع مستودعات لحسابه + مزامنة ([FirestoreSync]) ⇒ «بيفتح» لحد التنزيل الأول ما يخلص ⇒ «جاهز».
 * - خرج أو حساب تاني دخل ⇒ المزامنة القديمة بتقف و**الذاكرة بتتمسح** قبل أي حاجة للحساب الجديد — حساب ما يشوفش بيانات حساب تاني.
 * ⚠️ نسخة المكتبة على الجهاز نفسه **ما بتتمسحش** عند الخروج (زي التطبيق الحالي) — القواعد على السيرفر هي اللي بتمنع،
 *    وقراية الشاشة بتبقى تحت `users/{uid}` للحساب الداخل بس.
 */
class AccountSession(
    private val auth: AuthPort,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
    private val groups: List<String> = DocumentCodecs.byGroup.keys.toList(),
) {
    sealed interface State {
        data object SignedOut : State

        /** التنزيل الأول شغال — `sync.syncedGroups` من `sync.total` لشريط التقدم. */
        data class Opening(val user: AuthUser, val repos: FirestoreContainer, val sync: FirestoreSync) : State

        data class Ready(val user: AuthUser, val repos: FirestoreContainer, val sync: FirestoreSync) : State
    }

    private val current = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = current.asStateFlow()
    private var cancel: (() -> Unit)? = null

    fun start() {
        if (cancel != null) return
        cancel = auth.observe(::switchTo)
    }

    fun stop() {
        cancel?.invoke()
        cancel = null
        switchTo(null)
    }

    private fun switchTo(user: AuthUser?) {
        val before = current.value
        val beforeUser = (before as? State.Opening)?.user ?: (before as? State.Ready)?.user
        if (beforeUser?.uid == user?.uid) return
        (before as? State.Opening)?.sync?.stop()
        (before as? State.Ready)?.sync?.stop()
        if (user == null) {
            current.value = State.SignedOut
            return
        }
        val space = FirestoreSpace.forUser(db, user.uid)
        val sync = FirestoreSync(space, groups)
        val opening = State.Opening(user, FirestoreContainer(space), sync)
        current.value = opening
        sync.start(scope)
        scope.launch {
            sync.awaitComplete()
            // لو الحساب اتغير في النص، ما نعلّمش الحساب القديم «جاهز»
            current.compareAndSet(opening, State.Ready(user, opening.repos, sync))
        }
    }
}
