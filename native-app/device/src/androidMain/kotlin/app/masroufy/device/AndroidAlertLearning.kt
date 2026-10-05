package app.masroufy.device

import android.content.Context
import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.UsualHours
import app.masroufy.port.AlertInteractionStore
import app.masroufy.port.UsualHoursStore

/**
 * تعلّم محرك التنبيهات على أندرويد (OVERRIDES §61): **على الجوال بس ومش بيتزامن** (قرار المالك) — `SharedPreferences` بمفتاح لكل حساب،
 * نفس فكرة [AndroidSyncCursor] و[AndroidActiveSpaceStore]. القراية لو فشلت = من الأول؛ الكتابة لو فشلت بتتساب (مش بيانات مالية).
 */
class AndroidAlertInteractionStore(context: Context, uid: String) : AlertInteractionStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = AlertLearningFormat.statsKey(uid)

    override suspend fun load(): Map<AlertKind, KindStats> = AlertLearningFormat.decodeStats(runCatching { prefs.getString(key, null) }.getOrNull())

    override suspend fun save(kind: AlertKind, stats: KindStats) {
        val all = load().toMutableMap().also { it[kind] = stats }
        runCatching { prefs.edit().putString(key, AlertLearningFormat.encodeStats(all)).apply() }
    }
}

class AndroidUsualHoursStore(context: Context, uid: String) : UsualHoursStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = AlertLearningFormat.hoursKey(uid)

    override suspend fun load(): UsualHours = AlertLearningFormat.decodeHours(runCatching { prefs.getString(key, null) }.getOrNull())

    override suspend fun save(hours: UsualHours) {
        runCatching { prefs.edit().putString(key, AlertLearningFormat.encodeHours(hours)).apply() }
    }
}

private const val PREFS = "alert-learning"
