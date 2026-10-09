package app.masroufy.android

import android.app.Application
import app.masroufy.device.BackgroundGraph
import app.masroufy.device.MasroufyBackground

/**
 * بداية العملية (الشاشة أو عامل الخلفية): التجميع مرة واحدة + الجلسة + **تسجيل دورة الخلفية** (`MasroufyBackground.install` — ARCHITECTURE §31.29):
 * رسايل البنك الجديدة بتتسجل لوحدها بعد وصولها وكل ٤ ساعات، للحساب اللي داخل.
 */
class MasroufyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
        MasroufyBackground.install(this, BackgroundGraph { container.backgroundCycle() })
    }
}
