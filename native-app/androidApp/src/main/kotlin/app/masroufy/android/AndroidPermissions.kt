package app.masroufy.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.masroufy.ui.app.Permissions
import kotlinx.coroutines.CompletableDeferred

/**
 * أذونات الجهاز بنافذة النظام نفسها (الشرح قبلها شغل الشاشة — `SmsPermission` في «الاستيراد»/«أول تشغيل»):
 * - **رسايل البنك:** `RECEIVE_SMS` + `READ_SMS` مع بعض (القراية بالطلب والصندوق في الخلفية — §21 · §72).
 * - **الإشعارات:** أندرويد 13+ بس؛ أقدم = موجود.
 * لازم تتسجل في `onCreate` قبل ما الشاشة تبدأ (قيد أندرويد على `registerForActivityResult`).
 */
class AndroidPermissions(private val activity: ComponentActivity) : Permissions {
    private var pending: CompletableDeferred<Boolean>? = null
    private val sms: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { pending?.complete(hasSms()) }
    private val notifications: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> pending?.complete(granted) }

    override fun hasSms(): Boolean = granted(activity, Manifest.permission.RECEIVE_SMS) && granted(activity, Manifest.permission.READ_SMS)

    override suspend fun requestSms(): Boolean {
        if (hasSms()) return true
        val wait = CompletableDeferred<Boolean>().also { pending = it }
        sms.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
        return wait.await()
    }

    override suspend fun requestNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (granted(activity, Manifest.permission.POST_NOTIFICATIONS)) return true
        val wait = CompletableDeferred<Boolean>().also { pending = it }
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        return wait.await()
    }

    private fun granted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
