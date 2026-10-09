package app.masroufy.android

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import app.masroufy.core.Texts
import app.masroufy.device.AndroidAlertInteractionStore
import app.masroufy.device.AndroidActiveSpaceStore
import app.masroufy.device.AndroidAppLockSettings
import app.masroufy.device.AndroidDeviceLock
import app.masroufy.device.AndroidDeviceNotifier
import app.masroufy.device.AndroidFeedCache
import app.masroufy.device.AndroidSmsInbox
import app.masroufy.device.AndroidUsualHoursStore
import app.masroufy.device.MasroufyBackground
import app.masroufy.device.PlatformHttpText
import app.masroufy.firestore.AccountSession
import app.masroufy.firestore.FirebaseAuthAdapter
import app.masroufy.port.AccountPort
import app.masroufy.ui.app.AppDeps
import app.masroufy.ui.app.AppSession
import app.masroufy.ui.app.Permissions
import app.masroufy.usecase.AppLock
import app.masroufy.usecase.LoadOnlineFeeds
import app.masroufy.usecase.RunBackgroundCycle
import app.masroufy.usecase.SignIn
import app.masroufy.wiring.DeviceEnv
import app.masroufy.wiring.RandomIdGenerator
import app.masroufy.wiring.SessionLinks
import app.masroufy.wiring.SpaceGraph
import app.masroufy.wiring.SpaceRepositories
import app.masroufy.wiring.SystemClock
import app.masroufy.wiring.backgroundCycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.Calendar

/**
 * **التجميع** (composition root — CLAUDE.md #6، زي `app/container.ts`): بيتعمل مرة في `MasroufyApplication`.
 * فايربيز ⇒ `FirebaseAuthAdapter` + `AccountSession` (`:firestore`) ⇒ لكل حالة «جاهز»: `FirestoreContainer` البلد الشغالة ⇒ `SpaceRepositories`
 * ⇒ `SpaceGraph` (`:wiring` — حالات الاستخدام لكل منطقة) ⇒ الشاشات (`AppSession.Ready.deps`). الخلفية (§72) بتاخد نفس المستودعات.
 * الشاشة اللي ظاهرة ([activity]) بتتحط هنا للنوافذ اللي محتاجاها (البصمة · دخول جوجل).
 */
class AppContainer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nowMillis: () -> Long = System::currentTimeMillis
    private val clock = SystemClock(nowMillis)
    private val ids = RandomIdGenerator(nowMillis)
    private val firebase = openFirebase(context)
    private var activityRef = WeakReference<FragmentActivity>(null)

    /** الشاشة الظاهرة دلوقتي (أو null). */
    var activity: FragmentActivity?
        get() = activityRef.get()
        set(value) {
            activityRef = WeakReference(value)
        }

    val emulator: Boolean = firebase?.emulator == true
    private val auth: FirebaseAuthAdapter? = firebase?.let { FirebaseAuthAdapter(it.auth, scope, googleReady = it.webClientId != null) }
    private val session: AccountSession? = firebase?.let { f ->
        AccountSession(auth!!, f.firestore, scope, AndroidFirstSyncMarks(context)) { uid -> AndroidActiveSpaceStore(context, uid) }
    }
    private val google = firebase?.webClientId?.let { AndroidGoogleIdTokens({ activity }, it) }
    val signIn = SignIn(auth ?: NoFirebaseAuth, google)
    val appLock = AppLock(AndroidDeviceLock(context) { activity }, AndroidAppLockSettings(context), nowMillis)

    /** ملفات الأسعار (نسخة واحدة على الجهاز لكل البلاد). */
    val feeds = LoadOnlineFeeds(PlatformHttpText(), AndroidFeedCache(context), clock, nowMillis)

    @OptIn(ExperimentalCoroutinesApi::class)
    val appSession: StateFlow<AppSession> = (session?.state ?: flowOf(AccountSession.State.SignedOut)).flatMapLatest { st ->
        when (st) {
            AccountSession.State.SignedOut -> flowOf(AppSession.SignedOut)
            is AccountSession.State.Opening -> combine(st.sync.syncedGroups, st.accountSync.syncedGroups) { a, b ->
                AppSession.Opening(a.size + b.size, st.sync.total + st.accountSync.total)
            }
            is AccountSession.State.Ready -> flowOf(ready(st))
        }
    }.stateIn(scope, SharingStarted.Eagerly, AppSession.Starting)

    fun start() {
        session?.start()
    }

    fun appDeps(permissions: Permissions): AppDeps = object : AppDeps {
        override val session = appSession
        override val signIn = this@AppContainer.signIn
        override val lock: AppLock? = appLock
        override val permissions = permissions
        override val emulator = this@AppContainer.emulator
    }

    private fun ready(st: AccountSession.State.Ready): AppSession {
        // الفصحى/المصري من بلد الحساب الشغال قبل أول رسم (الجلسة بتعملها هي كمان — هنا عشان الترتيب يبقى مضمون)
        Texts.followCountry(st.active.space.countryCode)
        val graph = SpaceGraph(st.active.space, st.repos.toRepositories(), envFor(st.user.uid), links(), feeds)
        return AppSession.Ready(st.user.uid, graph)
    }

    private fun links() = object : SessionLinks {
        override val account: AccountPort = auth ?: error("no Firebase")

        override fun spaces(): List<Pair<Space, SpaceRepositories>> = spacesOf(session?.state?.value as? AccountSession.State.Ready)

        override fun switchSpace(spaceId: String): Boolean = session?.switchSpace(spaceId) == true

        // التحويل لنفسك بين بلدين (`SpaceTransfer`): كاتب ذرّي على الحساب بأماكن الجلسة نفسها
        override fun spaceTransferWriter() = (session?.state?.value as? AccountSession.State.Ready)?.spaceTransferWriter()
    }

    private fun spacesOf(ready: AccountSession.State.Ready?): List<Pair<Space, SpaceRepositories>> =
        ready?.spaces?.values.orEmpty().sortedBy { if (it.space.id == DEFAULT_SPACE_ID) "" else it.space.id }.map { it.space to it.repos.toRepositories() }

    private fun envFor(uid: String) = DeviceEnv(
        clock = clock, ids = ids, today = { MasroufyBackground.localNow().date }, hourNow = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) },
        nowMillis = nowMillis, interactions = AndroidAlertInteractionStore(context, uid), usualHours = AndroidUsualHoursStore(context, uid),
        seenAlerts = AndroidSeenAlerts(context, uid), http = PlatformHttpText(), feedCache = AndroidFeedCache(context),
    )

    /**
     * دورة الخلفية (`MasroufyBackground` — العامل بيصحّي التطبيق): الحساب الداخل بس، بعد ما الجلسة تجهز (من نسخة الجهاز — ثواني).
     * مفيش حد داخل أو ما جهزتش خلال 30 ثانية ⇒ null (العامل بيخلص من غير ما يعمل حاجة، والدورية بتكمّل).
     */
    suspend fun backgroundCycle(): RunBackgroundCycle? {
        val s = session ?: return null
        if (auth?.currentUser() == null) return null
        s.start()
        val ready = withTimeoutOrNull(30_000) { s.state.first { it is AccountSession.State.Ready } } as? AccountSession.State.Ready ?: return null
        return backgroundCycle(spacesOf(ready), AndroidSmsInbox(context, ready.user.uid), AndroidDeviceNotifier(context), envFor(ready.user.uid))
    }
}
