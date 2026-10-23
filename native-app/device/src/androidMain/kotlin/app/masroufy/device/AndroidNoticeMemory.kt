package app.masroufy.device

import android.content.Context
import app.masroufy.port.NoticeMemoryPort

/**
 * آخر حالة اتبعت عنها إشعار حالة القراية (PLAN-0.6 §13 ج) — إعداد جهاز (`SharedPreferences`) مش بيانات حساب ومش بيتزامن.
 * القراية لو فشلت = «ما اتبعتش» (أسوأ حاجة إشعار زيادة)؛ الكتابة لو فشلت بتتساب.
 */
class AndroidNoticeMemory(context: Context) : NoticeMemoryPort {
    private val prefs = context.getSharedPreferences("sms-state-notices", Context.MODE_PRIVATE)

    override fun get(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()

    override fun set(key: String, value: String?) {
        runCatching { prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
    }
}
