package app.masroufy.device

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.masroufy.core.LocalMoment
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.usecase.RunBackgroundCycle
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * الشغل في الخلفية على أندرويد (OVERRIDES §72 · ARCHITECTURE §31.29):
 * - **بعد كل رسالة بنك دخلت الصندوق** (`BankSmsReceiver`) ⇒ [MasroufyBackground.requestRun]: شغل واحد بالاسم (expedited لو النظام سامح)
 *   بيشغّل [RunBackgroundCycle]: التسجيل لوحده ⇒ المحرك ⇒ إشعار للمستني بس.
 * - **كل 4 ساعات** (والبطارية مش واطية): نفس الدورة — بتلحق أي رسالة الاستقبال فاتها (المزامنة بتكمّل من آخر مؤشر) وبتشغّل المحرك
 *   (ميعاد النهارده · القسط المتأخر …). السبب في §31.29.
 * - **التجميع (composition):** مفيش تطبيق قابل للتثبيت لسه ⇒ التطبيق الجاي بينادي [MasroufyBackground.install] في `Application.onCreate`
 *   بـ[BackgroundGraph] بيبني الدورة للحساب اللي داخل. من غيره ⇒ العامل بيخلص بنجاح ومن غير ما يعمل حاجة.
 * ⚠️ عمره ما بيكتب نص رسالة في السجل — ولا أي سجل أصلًا.
 */
fun interface BackgroundGraph {
    /** الدورة للحساب اللي داخل دلوقتي (بكل بلاده)، أو null لو مفيش حد داخل. */
    suspend fun cycle(): RunBackgroundCycle?
}

object MasroufyBackground {
    const val SMS_WORK = "masroufy-sms-cycle"
    const val PERIODIC_WORK = "masroufy-periodic-cycle"
    const val PERIOD_HOURS = 4L
    internal const val MAX_ATTEMPTS = 3

    @Volatile private var graph: BackgroundGraph? = null

    /** من `Application.onCreate` — بيتنادى في كل تشغيل للعملية (العامل بيصحّي التطبيق فبيتسجل قبله). */
    fun install(context: Context, graph: BackgroundGraph) {
        this.graph = graph
        schedulePeriodic(context)
    }

    /** للاختبار وتسجيل الخروج. */
    fun uninstall() {
        graph = null
    }

    internal fun graph(): BackgroundGraph? = graph

    /**
     * دورة بعد رسالة: `APPEND_OR_REPLACE` ⇒ رسالة وصلت والدورة شغالة بتتسجل في دورة بعدها (مش بتضيع)، والدورة نفسها آمنة لو اتكررت.
     * `false` = WorkManager مش جاهز (ما بيوقعش الاستقبال) — الدورة الدورية بتلحقها.
     */
    fun requestRun(context: Context): Boolean = try {
        val request = OneTimeWorkRequestBuilder<MasroufyCycleWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SMS_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        true
    } catch (_: IllegalStateException) {
        false
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<MasroufyCycleWorker>(PERIOD_HOURS, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        try {
            // KEEP: إعادة التسجيل في كل فتحة ما بتصفّرش الميعاد
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } catch (_: IllegalStateException) {
            // WorkManager مش جاهز — التسجيل الجاي بيعيد
        }
    }

    /** الساعة المحلية بتوقيت الجوال — نفس اللي المحرك بيتعلم بيه ساعاتك. */
    fun localNow(): LocalMoment {
        val c = Calendar.getInstance()
        val date = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return LocalMoment(date, c.get(Calendar.HOUR_OF_DAY))
    }
}

class MasroufyCycleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cycle = try {
            MasroufyBackground.graph()?.cycle()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return Result.success()
        val result = try {
            cycle.run(MasroufyBackground.localNow())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val failed = result == null || result.smsFailed || result.alertsFailed
        // الرسايل لسه في الصندوق لو فشل ⇒ نعيد كام مرة، وبعدها الدورية بتكمّل
        return if (failed && runAttemptCount + 1 < MasroufyBackground.MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    /** أندرويد أقدم من 12 بيشغّل الشغل المستعجل كخدمة ظاهرة ⇒ إشعار هادي بنص عام. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        AndroidDeviceNotifier.ensureChannels(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, AndroidDeviceNotifier.CHANNEL_BACKGROUND)
            .setSmallIcon(R.drawable.ic_stat_masroufy)
            .setContentTitle(uiText(TextKey.BACKGROUND_WORK_NOTICE))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        return ForegroundInfo(FOREGROUND_ID, notification)
    }

    private companion object {
        const val FOREGROUND_ID = 7201
    }
}
