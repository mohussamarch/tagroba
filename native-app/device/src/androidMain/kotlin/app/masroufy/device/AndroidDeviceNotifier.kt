package app.masroufy.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.masroufy.core.LocalMoment
import app.masroufy.core.SystemNotice
import app.masroufy.core.TextKey
import app.masroufy.core.isLockSafe
import app.masroufy.core.uiText
import app.masroufy.port.DeviceNotifier
import app.masroufy.port.NoticeOutcome
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * إشعارات الجوال على أندرويد (OVERRIDES §61 · §72) ورا [DeviceNotifier]:
 * - **النص من [SystemNotice] بس** (من نوع التنبيه — من غير مبالغ ولا محلات). وفوق ده: `VISIBILITY_PRIVATE` + نسخة عامة لشاشة القفل
 *   بنفس النص العام، وفحص [isLockSafe] تاني لحظة العرض — النص اللي فيه رقم **ما بيظهرش خالص**.
 * - **أندرويد 13+:** لو إذن `POST_NOTIFICATIONS` مش موجود (أو المستخدم قفل إشعارات التطبيق) ⇒ ولا حاجة بتحصل ⇒ [NoticeOutcome.BLOCKED].
 *   طلب الإذن (نافذة أندرويد) شغل الشاشة.
 * - الإشعار اللي المحرك أجّله لـ«وقتك المعتاد» بيتأجل بـWorkManager ([NoticeWorker]) بالنص العام بس.
 */
class AndroidDeviceNotifier(private val context: Context) : DeviceNotifier {
    override val available: Boolean = true

    override suspend fun permitted(): Boolean = canPost(context)

    override suspend fun post(tag: String, notice: SystemNotice, at: LocalMoment?): NoticeOutcome {
        if (!canPost(context)) return NoticeOutcome.BLOCKED
        val delay = at?.let { millisUntil(it) } ?: 0L
        if (delay <= 0L) return if (show(context, tag, notice.title, notice.body)) NoticeOutcome.SHOWN else NoticeOutcome.BLOCKED
        val request = OneTimeWorkRequestBuilder<NoticeWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TAG to tag, KEY_TITLE to notice.title, KEY_BODY to notice.body))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("notice-$tag", ExistingWorkPolicy.REPLACE, request)
        return NoticeOutcome.SCHEDULED
    }

    companion object {
        const val CHANNEL_ALERTS = "masroufy-alerts"
        const val CHANNEL_BACKGROUND = "masroufy-background"
        internal const val NOTICE_ID = 72
        internal const val KEY_TAG = "tag"
        internal const val KEY_TITLE = "title"
        internal const val KEY_BODY = "body"

        fun canPost(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /** من دلوقتي لأول الساعة [at] بتوقيت الجوال (`Calendar` — شغال من أندرويد 7 من غير `java.time`). */
        internal fun millisUntil(at: LocalMoment, nowMillis: Long = System.currentTimeMillis()): Long {
            val (y, m, d) = at.date.split("-").map { it.toInt() }
            val target = Calendar.getInstance().apply {
                clear()
                set(y, m - 1, d, at.hour, 0, 0)
            }
            return target.timeInMillis - nowMillis
        }

        internal fun ensureChannels(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(uiText(TextKey.ALERT_CHANNEL_NAME)).build(),
            )
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_BACKGROUND, NotificationManagerCompat.IMPORTANCE_MIN).setName(uiText(TextKey.BACKGROUND_WORK_NOTICE)).build(),
            )
        }

        private fun build(context: Context, title: String, body: String, visibility: Int, publicVersion: Notification?): Notification {
            val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_masroufy)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(visibility)
            publicVersion?.let(builder::setPublicVersion)
            // الضغطة بتفتح التطبيق (التفاصيل جوه بس)
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                builder.setContentIntent(PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            return builder.build()
        }

        /** بيعرض الإشعار لو الإذن موجود والنص آمن لشاشة القفل. `false` = ما اتعرضش. */
        @SuppressLint("MissingPermission") // [canPost] بيتحقق من الإذن قبلها
        internal fun show(context: Context, tag: String, title: String, body: String): Boolean {
            if (!canPost(context) || !isLockSafe(title) || !isLockSafe(body)) return false
            ensureChannels(context)
            val lockScreen = build(context, title, body, NotificationCompat.VISIBILITY_PUBLIC, null)
            val notification = build(context, title, body, NotificationCompat.VISIBILITY_PRIVATE, lockScreen)
            NotificationManagerCompat.from(context).notify(tag, NOTICE_ID, notification)
            return true
        }
    }
}

/** إشعار المحرك أجّله لوقتك المعتاد — النص العام بس في بيانات الشغل. الإذن والفحص بيتعادوا لحظة العرض. */
class NoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tag = inputData.getString(AndroidDeviceNotifier.KEY_TAG) ?: return Result.success()
        val title = inputData.getString(AndroidDeviceNotifier.KEY_TITLE) ?: return Result.success()
        val body = inputData.getString(AndroidDeviceNotifier.KEY_BODY) ?: return Result.success()
        AndroidDeviceNotifier.show(applicationContext, tag, title, body)
        return Result.success()
    }
}
