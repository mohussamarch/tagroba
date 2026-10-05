package app.masroufy.firestore

import app.masroufy.core.ACCOUNT_GROUPS
import app.masroufy.core.SPACE_GROUPS
import app.masroufy.core.Space
import app.masroufy.core.Texts
import app.masroufy.core.activeSpaceOf
import app.masroufy.core.defaultSpace
import app.masroufy.memory.MemoryActiveSpaceStore
import app.masroufy.port.ActiveSpaceStore
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
 * - حد دخل ⇒ مزامنة للحساب (المجموعات المشتركة — [ACCOUNT_GROUPS]) + مزامنة لكل بلد مش مؤرشفة ([SPACE_GROUPS] — §64)
 *   ⇒ «بيفتح» لحد التنزيل الأول ما يخلص ⇒ «جاهز».
 * - **البلد الشغالة** من الجهاز ([ActiveSpaceStore]) — بلد مش موجودة أو مؤرشفة ⇒ السعودية. التبديل ([switchSpace]) بيغيّر المعرّف بس.
 * - **نسخة العربي بتتبع البلد الشغالة** (§66): «جاهز» والتبديل والتحديث ⇒ [Texts.followCountry] ببلدها (مصر ⇒ المصري، غيرها ⇒ الفصحى)،
 *   والخروج ⇒ الافتراضي (الفصحى). فحالات الاستخدام والشاشات ما تعرفش عنها حاجة.
 * - خرج أو حساب تاني دخل ⇒ **كل** المزامنات بتقف و**الذاكرة بتتمسح** قبل أي حاجة للحساب الجديد — حساب ما يشوفش بيانات حساب تاني.
 * ⚠️ نسخة المكتبة على الجهاز نفسه **ما بتتمسحش** عند الخروج (زي التطبيق الحالي) — القواعد على السيرفر هي اللي بتمنع،
 *    وقراية الشاشة بتبقى تحت `users/{uid}` للحساب الداخل بس.
 */
class AccountSession(
    private val auth: AuthPort,
    private val db: FirebaseFirestore,
    private val scope: CoroutineScope,
    /** المساحة الشغالة على الجهاز لكل حساب (`AndroidActiveSpaceStore` / `IosActiveSpaceStore` في التشغيل). */
    private val activeStore: (uid: String) -> ActiveSpaceStore = { MemoryActiveSpaceStore() },
) {
    /** بلد واحدة في الجلسة: مستودعاتها ومزامنتها. */
    class SpaceSession(val space: Space, val repos: FirestoreContainer, val sync: FirestoreSync)

    sealed interface State {
        data object SignedOut : State

        /** التنزيل الأول شغال — `sync.syncedGroups` من `sync.total` لشريط التقدم ([sync] = السعودية، و[accountSync] = المشترك). */
        data class Opening(val user: AuthUser, val repos: FirestoreContainer, val sync: FirestoreSync, val accountSync: FirestoreSync) : State

        data class Ready(
            val user: AuthUser,
            /** مكان البيانات المشتركة بذاكرته — أي بلد جديدة في الجلسة بتتجمّع عليه هو (عشان كتابتها تبان في نفس الذاكرة). */
            val account: FirestoreSpace,
            val accountSync: FirestoreSync,
            val spaces: Map<String, SpaceSession>,
            val activeSpaceId: String,
        ) : State {
            val active: SpaceSession get() = spaces.getValue(activeSpaceId)

            /** مستودعات البلد الشغالة — حالات الاستخدام العادية بتاخدها من هنا. */
            val repos: FirestoreContainer get() = active.repos
            val sync: FirestoreSync get() = active.sync

            /** البلاد التانية في النسخة الشاملة بأماكن الجلسة (اللي بيتضاف يبان في ذاكرتها لحظتها). */
            fun spacesBackup(): FirestoreSpacesBackup =
                FirestoreSpacesBackup(account) { id -> spaces[id]?.repos?.spaceRoot ?: FirestoreSpace.forSpace(account.db, user.uid, id) }

            /** كتابة التحويل لنفسك ذرّيًا على البلدين — بأماكن الجلسة نفسها (بذاكرتها). البلد المؤرشفة بمكانها من غير ذاكرة. */
            fun spaceTransferWriter(): FirestoreSpaceTransferWriter =
                FirestoreSpaceTransferWriter(account) { id -> spaces[id]?.repos?.spaceRoot ?: FirestoreSpace.forSpace(account.db, user.uid, id) }
        }
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

    /** يبدّل البلد الشغالة (موجودة ومش مؤرشفة). مفيش ولا كتابة على البيانات. `false` = مش جاهز أو البلد مش في الجلسة. */
    fun switchSpace(spaceId: String): Boolean {
        val ready = current.value as? State.Ready ?: return false
        if (spaceId !in ready.spaces) return false
        activeStore(ready.user.uid).write(spaceId)
        val switched = current.compareAndSet(ready, ready.copy(activeSpaceId = spaceId))
        if (switched) Texts.followCountry(ready.spaces.getValue(spaceId).space.countryCode)
        return switched
    }

    /** بعد ما بلد اتعملت أو اترجعت من الأرشيف (هنا أو من جهاز تاني): مزامنة للبلاد الجديدة، والجلسة بتفضل «جاهز». */
    suspend fun refreshSpaces() {
        val ready = current.value as? State.Ready ?: return
        val registry = FirestoreSpaceRegistry(ready.account).listAll()
        val added = registry.filter { !it.archived && it.id !in ready.spaces }.map { openSpace(ready.user.uid, it, ready.account) }
        added.forEach { it.sync.awaitComplete() }
        val spaces = ready.spaces + added.associateBy { it.space.id }
        val active = activeSpaceOf(activeStore(ready.user.uid).read(), registry.filter { it.id in spaces })
        if (current.compareAndSet(ready, ready.copy(spaces = spaces, activeSpaceId = active.id))) Texts.followCountry(active.countryCode)
        else added.forEach { it.sync.stop() }
    }

    private fun switchTo(user: AuthUser?) {
        val before = current.value
        val beforeUser = (before as? State.Opening)?.user ?: (before as? State.Ready)?.user
        if (beforeUser?.uid == user?.uid) return
        stopAll(before)
        if (user == null) {
            current.value = State.SignedOut
            // مفيش بلد شغالة ⇒ الافتراضي (الفصحى) — اختيار Claude §66، المالك يقدر يغيّره
            Texts.followCountry(null)
            return
        }
        val account = spaceOf(user.uid, null)
        val accountSync = FirestoreSync(account, ACCOUNT_GROUPS)
        val saudi = openSpace(user.uid, defaultSpace(), account)
        val opening = State.Opening(user, saudi.repos, saudi.sync, accountSync)
        current.value = opening
        accountSync.start(scope)
        scope.launch {
            accountSync.awaitComplete()
            val registry = FirestoreSpaceRegistry(account).listAll()
            val others = registry.filter { !it.archived }.map { openSpace(user.uid, it, account) }
            saudi.sync.awaitComplete()
            others.forEach { it.sync.awaitComplete() }
            val spaces = (listOf(saudi) + others).associateBy { it.space.id }
            val active = activeSpaceOf(activeStore(user.uid).read(), registry)
            // لو الحساب اتغير في النص، ما نعلّمش الحساب القديم «جاهز»
            if (current.compareAndSet(opening, State.Ready(user, account, accountSync, spaces, active.id))) Texts.followCountry(active.countryCode)
            else others.forEach { it.sync.stop() }
        }
    }

    private fun spaceOf(uid: String, spaceId: String?): FirestoreSpace =
        if (spaceId == null) FirestoreSpace.forAccount(db, uid) else FirestoreSpace.forSpace(db, uid, spaceId)

    private fun openSpace(uid: String, space: Space, account: FirestoreSpace): SpaceSession {
        val root = spaceOf(uid, space.id)
        val sync = FirestoreSync(root, SPACE_GROUPS)
        sync.start(scope)
        return SpaceSession(space, FirestoreContainer(account, root, space.id), sync)
    }

    private fun stopAll(state: State) {
        when (state) {
            is State.Opening -> { state.sync.stop(); state.accountSync.stop() }
            is State.Ready -> { state.spaces.values.forEach { it.sync.stop() }; state.accountSync.stop() }
            State.SignedOut -> Unit
        }
    }
}
