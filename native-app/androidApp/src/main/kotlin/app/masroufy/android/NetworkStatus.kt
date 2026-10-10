package app.masroufy.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * فيه نت ولا لأ — من النظام (`ConnectivityManager`) وبتتحدث لوحدها مع كل تغيير. الأزرار اللي محتاجة نت بتتقفل من غيره
 * (رد المالك L4: «أرسل الرابط» في تغيير كلمة السر). لو النظام ما ردش ⇒ «متصل» (الزرار بيفضل شغال والخطأ بيظهر جنب الخانة زي الأول).
 */
internal fun networkStatus(context: Context): StateFlow<Boolean> {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return MutableStateFlow(true)
    fun hasInternet(caps: NetworkCapabilities?) = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val state = MutableStateFlow(runCatching { hasInternet(cm.getNetworkCapabilities(cm.activeNetwork)) }.getOrDefault(true))
    runCatching {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                state.value = true
            }

            override fun onLost(network: Network) {
                state.value = false
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = hasInternet(caps)
            }
        })
    }.onFailure { state.value = true }
    return state
}
