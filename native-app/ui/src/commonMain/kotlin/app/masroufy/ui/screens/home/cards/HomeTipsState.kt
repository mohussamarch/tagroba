package app.masroufy.ui.screens.home.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.masroufy.core.HomeTipsClosed
import app.masroufy.core.IsoDate
import app.masroufy.core.advisorTipShown
import app.masroufy.core.profileTipShown
import app.masroufy.ui.app.LocalSpace
import app.masroufy.usecase.ManageHomeCards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * «×» على كارت النصيحة وكارت «ملفك» (رد المالك 2026-10-16 — قاعدة الرجوع في `core/HomeTips.kt`، ❓ اختيار Claude مستني المالك).
 * لحد ما المقفول يتقري الكارتين مستخبيين (عشان كارت مقفول ما يظهرش ثانية ويختفي). الضغط على «×» بيخبّي على طول ويحفظ في الحساب؛
 * الحفظ لو فشل الكارت بيفضل مستخبي في الجلسة دي وبيرجع المرة الجاية (مش خطر — مجرد كارت).
 */
@Stable
internal class HomeTipsHolder(private val manage: ManageHomeCards, private val scope: CoroutineScope, private val today: IsoDate) {
    var closed by mutableStateOf<HomeTipsClosed?>(null)

    suspend fun read(): HomeTipsClosed = runCatching { manage.tipsClosed() }.getOrElse { HomeTipsClosed.NONE }

    fun advisorShown(key: String): Boolean = closed?.let { advisorTipShown(key, it) } ?: false

    fun profileShown(key: String): Boolean = closed?.let { profileTipShown(key, it, today) } ?: false

    fun closeAdvisor(key: String) {
        closed = (closed ?: HomeTipsClosed.NONE).copy(advisorKey = key)
        scope.launch { runCatching { manage.closeAdvisor(key) } }
    }

    fun closeProfile(key: String) {
        closed = (closed ?: HomeTipsClosed.NONE).copy(profileKey = key, profileClosedOn = today)
        scope.launch { runCatching { manage.closeProfile(key, today) } }
    }
}

@Composable
internal fun rememberHomeTips(): HomeTipsHolder {
    val deps = LocalSpace.current
    val scope = rememberCoroutineScope()
    val holder = remember(deps) { HomeTipsHolder(deps.home.cards.layout, scope, deps.shell.today()) }
    LaunchedEffect(holder) { holder.closed = holder.read() }
    return holder
}
