package app.masroufy.usecase

import app.masroufy.core.UserSetting
import app.masroufy.core.isoInstantMillis
import app.masroufy.port.Clock
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.UserSettingsStore

/**
 * **§83-2 (المالك 2026-10-10): «بعد كده بيكمّل لوحده»** — كل تشغيلة في الخلفية بتقرا من آخر نقطة اتعاملت لحد دلوقتي، حتى الفترة اللي البرنامج
 * ما كانش متثبت فيها أو القراية كانت واقفة، وبتكمّل الناقص. التكرار مستحيل: الرسالة اللي اتقرت على الجهاز ما بتدخلش الصندوق تاني، واللي
 * اتسجلت (حتى من جهاز تاني أو قبل ما البرنامج يتمسح) بتطلع «مكررة» بمرجعها `SMS:<بصمة>` وبتتشال من غير تسجيل.
 * - **نقطة كل بلد** ([UserSetting.SmsReadPoint] — في الحساب، بتعيش بعد مسح البرنامج): كل رسايل البنك قبلها اتعاملت (اتسجلت · اتشالت ·
 *   مكررة) = الأقدم من (مؤشر الجهاز، أقدم رسالة لسه مستنية في الصندوق).
 * - **[before]**: الجهاز ده بدأ يقرا **بعد** نقطة بلد (اتثبت تاني · جوال جديد) ⇒ يرجع يقرا من النقطة. على نفس الجهاز النقطة عمرها ما بتبقى
 *   قبل أول نقطة قرا منها ⇒ ما بيرجعش كل مرة.
 * - التفعيل بعد إيقاف بيكمّل من مؤشر الجهاز (`SmsInboxStore.configure`) — الفترة اللي القراية كانت واقفة فيها بتتقري.
 */
class SmsCatchUp(
    private val inbox: SmsInboxPort,
    settings: UserSettingsStore,
    /** البلاد اللي ليها قارئ رسايل (حارات التسجيل). */
    private val spaceIds: List<String>,
    clock: Clock,
) {
    private val points = SmsReadPoints(settings, clock)

    /** قبل التشغيلة. `true` = الجهاز رجع يقرا من نقطة اتسجلت قبل كده (الفجوة هتتقري في المزامنة الجاية). */
    suspend fun before(): Boolean {
        if (!inbox.available || spaceIds.isEmpty()) return false
        val marks = inbox.readMarks() ?: return false
        val earliest = points.all().filterKeys { it in spaceIds }.values.map { it.point }.minByOrNull(::millisOf) ?: return false
        if (millisOf(earliest) >= millisOf(marks.floor)) return false
        inbox.readFrom(earliest)
        return true
    }

    /** بعد التشغيلة: نقطة كل بلد بتتحفظ — البلد اللي مالهاش مستند بيتعملها (من غير ما بنوكها تتحسب «اتسألت» — `SmsHistory.question`). */
    suspend fun after() {
        if (!inbox.available || spaceIds.isEmpty()) return
        val marks = inbox.readMarks() ?: return
        val oldestWaiting = inbox.sync().oldestQueuedAt
        val point = if (oldestWaiting != null && millisOf(oldestWaiting) < millisOf(marks.point)) oldestWaiting else marks.point
        val stored = points.all()
        for (space in spaceIds) {
            val doc = stored[space]
            if (doc?.point == point) continue
            points.save(space, point, doc?.asked.orEmpty())
        }
    }
}

/** مستندات نقطة القراية في إعدادات الحساب ([UserSetting.SmsReadPoint]). */
internal class SmsReadPoints(private val settings: UserSettingsStore, private val clock: Clock) {
    suspend fun all(): Map<String, UserSetting.SmsReadPoint> =
        settings.listAll().filterIsInstance<UserSetting.SmsReadPoint>().associateBy { it.spaceId }

    suspend fun save(spaceId: String, point: String, asked: List<String>) =
        settings.save(UserSetting.SmsReadPoint(spaceId, point, asked.distinct(), clock.nowIso()))
}

/** وقت ISO بالمللي — اللي مش مقروء بيبقى الأقدم (ما يعدّيش حاجة). */
internal fun millisOf(iso: String): Long = isoInstantMillis(iso) ?: Long.MIN_VALUE
