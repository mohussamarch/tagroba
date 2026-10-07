package app.masroufy.device

import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.UsualHours
import app.masroufy.port.AlertInteractionStore
import app.masroufy.port.UsualHoursStore
import platform.Foundation.NSUserDefaults

/**
 * تعلّم محرك التنبيهات على الآيفون (OVERRIDES §61): **على الجوال بس ومش بيتزامن** — `NSUserDefaults` بمفتاح لكل حساب،
 * نفس [AndroidAlertInteractionStore]/[AndroidUsualHoursStore] ونفس [IosSyncCursor].
 */
class IosAlertInteractionStore(uid: String, private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : AlertInteractionStore {
    private val key = AlertLearningFormat.statsKey(uid)

    override suspend fun load(): Map<AlertKind, KindStats> = AlertLearningFormat.decodeStats(runCatching { defaults.stringForKey(key) }.getOrNull())

    override suspend fun save(kind: AlertKind, stats: KindStats) {
        val all = load().toMutableMap().also { it[kind] = stats }
        runCatching { defaults.setObject(AlertLearningFormat.encodeStats(all), key) }
    }
}

class IosUsualHoursStore(uid: String, private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : UsualHoursStore {
    private val key = AlertLearningFormat.hoursKey(uid)

    override suspend fun load(): UsualHours = AlertLearningFormat.decodeHours(runCatching { defaults.stringForKey(key) }.getOrNull())

    override suspend fun save(hours: UsualHours) {
        runCatching { defaults.setObject(AlertLearningFormat.encodeHours(hours), key) }
    }
}
