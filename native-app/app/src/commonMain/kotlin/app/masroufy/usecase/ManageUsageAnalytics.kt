package app.masroufy.usecase

import app.masroufy.port.UsageSwitchPort

/**
 * OVERRIDES §121-17 — مفتاح «ابعت بيانات الاستخدام» في الإعدادات (جنب تقارير الأعطال ومنفصل عنه). القفل بيوقف الجمع نفسه
 * (`Usage.allowed` في التجميع + المكتبة على أندرويد)، مش بس الشاشة.
 */
class ManageUsageAnalytics(private val port: UsageSwitchPort) {
    val available: Boolean get() = port.available

    fun isOn(): Boolean = port.available && port.isOn()

    fun setOn(on: Boolean) {
        if (port.available) port.setOn(on)
    }
}
