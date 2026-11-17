package app.masroufy.android

import android.content.Context
import android.os.Bundle
import app.masroufy.core.UsageEvent
import app.masroufy.core.UsageProperty
import app.masroufy.core.UsageValue
import app.masroufy.port.AnalyticsPort
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * بيانات الاستخدام (Google Analytics for Firebase — OVERRIDES §114 · ARCHITECTURE «بيانات الاستخدام» · حدود §80):
 * - شغالة لو مفتاح «ابعت بيانات الاستخدام» مفتوح ([AndroidUsageSwitch] — OVERRIDES §121-17، لوحده ومنفصل عن الأعطال؛ الافتراضي مفتوح) في نسخ المالك (`debug` · `fast` — `BuildConfig.USAGE_ANALYTICS`)، **مقفولة** في نسخ
 *   المحاكي ومن غير إعدادات فايربيز ⇒ [create] بيرجّع null (و`Usage.port` بيفضل null — ولا حاجة).
 * - الجمع مقفول في المانيفست لحد ما ده يفتحه. معرّف الإعلانات والإعلانات المخصصة وتخزين الإعلانات مقفولين (المانيفست + [deny]).
 * - **مفيش `setUserId`** — ولا رقم فايربيز ولا إيميل. الأحداث والقيم من `core` بس (`UsageEvents` · `UsageValue`) ⇒ مفيش نص حر.
 */
class FirebaseUsage private constructor(private val analytics: FirebaseAnalytics) : AnalyticsPort {
    /** §121-17: مفتاح «بيانات الاستخدام» ⇒ الجمع في المكتبة نفسها يتفتح أو يتقفل (مش بس `Usage.allowed`). */
    fun collect(on: Boolean) {
        runCatching { analytics.setAnalyticsCollectionEnabled(on) }
    }

    override fun log(event: UsageEvent) {
        val bundle = Bundle()
        for ((p, v) in event.params) put(bundle, p.wire, v)
        analytics.logEvent(event.name.wire, bundle)
    }

    override fun set(property: UsageProperty, value: UsageValue) {
        analytics.setUserProperty(property.wire, value.toString())
    }

    private fun put(bundle: Bundle, key: String, v: UsageValue) {
        val n = v.number
        if (n != null) bundle.putLong(key, n) else bundle.putString(key, v.text)
    }

    private fun deny() {
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
            ),
        )
    }

    companion object {
        /** null ⇒ النسخة دي مالهاش بيانات استخدام (المحاكي · من غير فايربيز) — والجمع بيفضل مقفول زي المانيفست. */
        fun create(context: Context, on: Boolean = true): FirebaseUsage? {
            if (!BuildConfig.USAGE_ANALYTICS) return null
            val ready = runCatching { FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME } }.getOrDefault(false)
            if (!ready) return null
            val fa = runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull() ?: return null
            return FirebaseUsage(fa).also {
                it.deny()
                fa.setAnalyticsCollectionEnabled(on)
            }
        }
    }
}

/**
 * OVERRIDES §121-17 — مفتاح «ابعت بيانات الاستخدام» على مستوى الجهاز (ملف تفضيلات لوحده، منفصل عن «crash-reports»). الافتراضي مفتوح
 * زي مفتاح تقارير الأعطال. [usage] null (المحاكي · من غير فايربيز) ⇒ مش متاح. القفل بيقفل الجمع في المكتبة كمان ([FirebaseUsage.collect]).
 */
class AndroidUsageSwitch(context: Context) : app.masroufy.port.UsageSwitchPort {
    private val prefs = context.getSharedPreferences("usage-analytics", Context.MODE_PRIVATE)

    @Volatile var usage: FirebaseUsage? = null

    override val available: Boolean get() = usage != null

    /** المفتاح المحفوظ (من غير ما يهمه متاح ولا لأ) — للتجميع قبل ما المكتبة تتعمل. */
    fun saved(): Boolean = runCatching { prefs.getBoolean(KEY_ON, true) }.getOrDefault(true)

    override fun isOn(): Boolean = available && saved()

    override fun setOn(on: Boolean) {
        runCatching { prefs.edit().putBoolean(KEY_ON, on).apply() }
        usage?.collect(on)
    }

    private companion object {
        const val KEY_ON = "on"
    }
}
