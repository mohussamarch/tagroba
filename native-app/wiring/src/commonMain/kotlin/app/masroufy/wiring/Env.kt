package app.masroufy.wiring

import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Space
import app.masroufy.port.AccountPort
import app.masroufy.port.AlertInteractionStore
import app.masroufy.port.BankSmsPort
import app.masroufy.port.SmsInboxPort
import app.masroufy.usecase.PdfPagesPort
import app.masroufy.port.Clock
import app.masroufy.port.FeedCachePort
import app.masroufy.port.HttpTextPort
import app.masroufy.port.IdGenerator
import app.masroufy.port.UsualHoursStore
import kotlin.random.Random

/**
 * اللي بييجي من **الجهاز** (مش من بيانات الحساب): الوقت · المعرّفات · تعلّم التنبيهات (على الجوال — §61) · المقروء في الجرس · النت · نسخة الملفات.
 * `:androidApp` بيبنيه من أندرويد، والاختبار من الذاكرة.
 */
class DeviceEnv(
    val clock: Clock,
    val ids: IdGenerator,
    /** النهارده بتوقيت الجوال (ISO). */
    val today: () -> IsoDate,
    /** الساعة 0–23 بتوقيت الجوال. */
    val hourNow: () -> Int,
    val nowMillis: () -> Long,
    val interactions: AlertInteractionStore,
    val usualHours: UsualHoursStore,
    val seenAlerts: SeenAlerts,
    val http: HttpTextPort,
    val feedCache: FeedCachePort,
    /**
     * منطقة «الاستيراد» (ARCHITECTURE §31.31 — «محتاج حاجة من الجهاز؟ ضيفها هنا»): صندوق رسايل البنك (§72) · قراية رسايل فترة بالطلب ·
     * كلمات صفحات الـPDF. null = الجهاز مالوش (الآيفون للرسايل · الاختبار) ⇒ الشاشة بتقول «غير متاح» بسببه.
     */
    val smsInbox: SmsInboxPort? = null,
    val bankSms: BankSmsPort? = null,
    val pdfPages: PdfPagesPort? = null,
    /**
     * ملف «نسخة قبل التغيير» على الجهاز (`saveVerifiedBackup`) — «طبّق على اللي فات» في «جهات الشغل» (البند 7) ما بيكتبش من غيره.
     * «راجع المكرر» في «افهم رصيدك» كمان بيرفض يدمج من غيره.
     * null (الآيفون لحد ما يتجمّع · الاختبار) ⇒ التطبيق على اللي فات بيترفض برسالة، والباقي شغال.
     */
    val repairBackup: app.masroufy.port.RepairBackupPort? = null,
    /** r041b P2: مسودة «أول تشغيل» على الجهاز (لكل حساب) — الفتح الجاي بيكمّل من نفس الكارت. null (الاختبار · الآيفون لحد ما يتجمّع) ⇒ الذاكرة بس. */
    val onboardingDraft: app.masroufy.ui.screens.onboarding.OnbDraftStore? = null,
    /**
     * r041c: الشغل التقيل اللي الشاشات بتطلبه (قراية رسايل البنك وفهمها ومطابقتها وتسجيلها · قوايم العمليات الكبيرة) بيتعمل هنا **برّه خيط
     * الواجهة** — كان على خيط الواجهة فالبرنامج كله بيعلّق ٤–٥ ثواني في «اسحب لتحت». الاختبار بيدّي سياقه (`EmptyCoroutineContext`).
     */
    val work: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default,
    /** 2026-10-16: مسودة «ابنِ/عدّل شخصيتك» على الجهاز (لكل حساب) — لو البرنامج اتقفل قبل الحفظ. null ⇒ من غير مسودة. */
    val characterDraft: app.masroufy.ui.screens.character.CharacterDraftStore? = null,
)

/**
 * الجلسة من ناحية التجميع: الحساب (لصفحة «ملفك» وإيميل تغيير كلمة السر) والبلاد المفتوحة بمستودعاتها (للوحة تبديل البلد) والتبديل نفسه
 * (`AccountSession.switchSpace` — على الجهاز بس، من غير كتابة على البيانات).
 */
interface SessionLinks {
    val account: AccountPort

    /** البلاد المفتوحة في الجلسة (السعودية الأول) بمستودعاتها. */
    fun spaces(): List<Pair<Space, SpaceRepositories>>

    fun switchSpace(spaceId: String): Boolean

    /**
     * كتابة «التحويل لنفسك» على البلدين ذرّيًا (`AccountSession.State.Ready.spaceTransferWriter` على الجوال) — قرار شريحة «العمليات»
     * 2026-10-09 (`SpaceTransfer`): الكاتب على مستوى الحساب مش البلد، فمكانه الجلسة. `null` (الافتراضي) ⇒ التسجيل والربط والفك بيرفضوا
     * بـ«غير متاح» والقايمة بتتعرض عادي.
     */
    fun spaceTransferWriter(): app.masroufy.port.SpaceTransferWriter? = null

    /**
     * إدارة البلاد (`ManageSpaces` — §41 · §64 · رد المالك §88-8 «اضافة دولة زي مصر»): مكانها الجلسة — السجل على مستوى الحساب، والبلد
     * الجديدة محتاجة مكان في فايربيز، وبعد أي تغيير الجلسة بتفتحها أو تقفلها من غير إعادة تشغيل ([SpaceAdminLinks.reopen]).
     * `null` (الافتراضي — المعاينة والآيفون لحد ما يتجمّع) ⇒ «إضافة بلد غير متاحة بعد» في «البلدان» و«أول تشغيل».
     */
    fun spaceAdmin(): SpaceAdminLinks? = null
}

/**
 * «تعليم الكل كمقروء» في نافذة الجرس — **على الجهاز ده بس** (OVERRIDES §74: «مقروء» مالوش حالة في البيانات لسه؛ النموذج كان بيحفظه في الجلسة).
 * ❓ مستني المالك: يتزامن بين الأجهزة؟ (محتاج حقل جديد في صفحة الإشعارات).
 */
interface SeenAlerts {
    fun read(): Set<String>

    fun addAll(threadKeys: Collection<String>)
}

class MemorySeenAlerts : SeenAlerts {
    private val keys = mutableSetOf<String>()

    override fun read(): Set<String> = keys.toSet()

    override fun addAll(threadKeys: Collection<String>) {
        keys += threadKeys
    }
}

/** ساعة الجهاز بصيغة `toISOString()` بالظبط (`2026-10-09T07:05:03.120Z`) — نفس `systemClock` في التطبيق الحالي. */
class SystemClock(private val nowMillis: () -> Long) : Clock {
    override fun nowIso(): String = isoOfMillis(nowMillis())
}

/** مللي ثانية من 1970 ⇒ `YYYY-MM-DDTHH:MM:SS.mmmZ` (UTC). */
fun isoOfMillis(millis: Long): String {
    val day = millis.floorDiv(86_400_000L)
    val ms = millis.mod(86_400_000L)
    val date = app.masroufy.core.dayNumberToIso(day.toInt())
    val h = ms / 3_600_000
    val m = ms / 60_000 % 60
    val s = ms / 1000 % 60
    val f = ms % 1000
    return date + "T" + h.pad(2) + ":" + m.pad(2) + ":" + s.pad(2) + "." + f.pad(3) + "Z"
}

private fun Long.pad(n: Int) = toString().padStart(n, '0')

/**
 * معرّفات للتشغيل الحقيقي — نقل `RandomIdGenerator` من التطبيق الحالي: `<بادئة>-<توقيت base36 تصاعدي><عدّاد>-<عشوائي>`.
 * التوقيت الأول ⇒ الترتيب بالحروف = الترتيب بالزمن. `SequentialIdGenerator` للاختبار بس (بيبدأ من 1 كل مرة وبيكتب فوق القديم).
 */
class RandomIdGenerator(private val nowMillis: () -> Long, private val random: Random = Random.Default) : IdGenerator {
    private var lastMillis = 0L
    private var counter = 0

    override fun next(prefix: String): Id {
        val now = nowMillis()
        if (now == lastMillis) counter++ else {
            lastMillis = now
            counter = 0
        }
        val time = now.toString(36).padStart(9, '0')
        val seq = counter.toString(36).padStart(3, '0')
        val suffix = (0 until 6).joinToString("") { random.nextInt(256).toString(36).padStart(2, '0') }
        return "$prefix-$time$seq-$suffix"
    }
}
