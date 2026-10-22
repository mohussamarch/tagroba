package app.masroufy.ui.screens.common

import app.masroufy.core.R06Key

/** حالة السحب للتحديث: ساكن · بيتسحب (قبل الحد) · وصل الحد (السيب هيحدّث) · بيحدّث · خلص (بيرجع). */
enum class PullPhase { IDLE, PULL, READY, BUSY, DONE }

/** أرقام السحب للتحديث بوحدة dp (`PullRefresh.dc.html`): الحد 64 بعد المقاومة 0.5 · الأقصى 120 · الصفحة بتفضل نازلة 60 وقت التحديث. */
object PullMetrics {
    const val THRESHOLD_DP = 64f
    const val MAX_DP = 120f
    const val BUSY_DP = 60f
    const val RESISTANCE = 0.5f
    const val DONE_HOLD_MS = 900
    const val RETURN_MS = 320
    const val COINS = 3

    /** عدد العملات المرصوصة وانت بتسحب: عملة لكل تلت من المسافة لحد الحد (الأقصى 3). */
    fun coinCount(pull: Float, threshold: Float = THRESHOLD_DP): Int =
        if (pull <= 0f || threshold <= 0f) 0 else (pull * COINS / threshold).toInt().coerceIn(0, COINS)

    /** حالة السحب لحظة الإصبع على الشاشة: قبل الحد [PullPhase.PULL] · عنده أو بعده [PullPhase.READY] · صفر ⇒ [PullPhase.IDLE]. */
    fun draggingPhase(pull: Float, threshold: Float = THRESHOLD_DP): PullPhase =
        if (pull <= 0f) PullPhase.IDLE else if (pull >= threshold) PullPhase.READY else PullPhase.PULL

    /** عدد العملات اللي بتتعرض في الحالة: الشغالة (-1 = حركة) · الخالصة 3 ثابتة · غير كده بالمسافة. */
    fun coinsFor(phase: PullPhase, pull: Float, threshold: Float = THRESHOLD_DP): Int = when (phase) {
        PullPhase.BUSY -> -1
        PullPhase.DONE -> COINS
        else -> coinCount(pull, threshold)
    }

    fun labelKey(phase: PullPhase): R06Key = when (phase) {
        PullPhase.READY -> R06Key.LOADER_RELEASE
        PullPhase.BUSY -> R06Key.LOADER_UPDATING
        PullPhase.DONE -> R06Key.LOADER_DONE
        else -> R06Key.LOADER_PULL
    }
}
