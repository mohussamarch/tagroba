package app.masroufy.device

import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.UsualHours

/**
 * تعلّم محرك التنبيهات **على الجوال بس** (OVERRIDES §61 — قرار المالك: ساعاتك وتفاعلك ما بيتزامنوش) — شكل النص اللي بيتحفظ
 * في إعدادات الجهاز (`SharedPreferences` على أندرويد · `NSUserDefaults` على الآيفون)، مفتاح لكل حساب.
 * مش بيانات مالية: القيمة البايظة أو الناقصة = المحرك بيتعلم من الأول (زي جهاز جديد)، ونوع تنبيه مش معروف بيتساب.
 */
object AlertLearningFormat {
    /** `due_soon=5/2;zakat_today=1/0` — مترتبة بالاسم عشان نفس البيانات = نفس النص. */
    fun encodeStats(stats: Map<AlertKind, KindStats>): String =
        stats.entries.sortedBy { it.key.wire }.joinToString(";") { (kind, s) -> "${kind.wire}=${s.shown}/${s.opened}" }

    fun decodeStats(text: String?): Map<AlertKind, KindStats> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<AlertKind, KindStats>()
        for (part in text.split(';')) {
            val (wire, counts) = part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: continue
            val kind = AlertKind.fromWire(wire) ?: continue
            val nums = counts.split('/').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: continue
            runCatching { KindStats(nums[0], nums[1]) }.getOrNull()?.let { out[kind] = it }
        }
        return out
    }

    /** 24 عدد مفصولين بفاصلة — عدد فتحات التطبيق في كل ساعة. */
    fun encodeHours(hours: UsualHours): String = hours.opens.joinToString(",")

    fun decodeHours(text: String?): UsualHours {
        val nums = text?.split(',')?.map { it.trim().toIntOrNull() } ?: return UsualHours()
        if (nums.size != 24 || nums.any { it == null || it < 0 }) return UsualHours()
        return UsualHours(nums.map { it!! })
    }

    /** مفتاح لكل حساب ([uid]) — حسابين على نفس الجوال ما يتلخبطوش. */
    fun statsKey(uid: String): String = "masroufy-alert-kinds-v1:$uid"

    fun hoursKey(uid: String): String = "masroufy-alert-hours-v1:$uid"
}
