package app.masroufy.device

import android.content.Context
import app.masroufy.port.BackgroundReadPort
import java.util.TimeZone

/**
 * آخر مرة العامل قرا رسايل البنك من غير خطأ — إعداد جهاز (`SharedPreferences`) مش بيانات حساب ومش بيتزامن.
 * القراية لو فشلت = «لسه ما قراش»؛ الكتابة لو فشلت بتتساب (سطر معلومات بس).
 */
class AndroidBackgroundRead(context: Context) : BackgroundReadPort {
    private val prefs = context.getSharedPreferences("background-read", Context.MODE_PRIVATE)

    override fun last(): Long? = runCatching { prefs.getLong(KEY, -1L).takeIf { it > 0 } }.getOrNull()

    override fun mark(atMillis: Long) {
        runCatching { prefs.edit().putLong(KEY, atMillis).apply() }
    }

    override fun zoneOffsetMillis(atMillis: Long): Long = TimeZone.getDefault().getOffset(atMillis).toLong()

    private companion object {
        const val KEY = "last-sms-read-millis"
    }
}
