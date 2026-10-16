package app.masroufy.usecase

import app.masroufy.core.HOME_CARDS_KEY
import app.masroufy.core.HomeCardKind
import app.masroufy.core.HomeCardsLayout
import app.masroufy.core.UserSetting
import app.masroufy.core.defaultHomeCards
import app.masroufy.core.homeCardsLayout
import app.masroufy.port.Clock
import app.masroufy.port.UserSettingsStore

/**
 * كروت الرئيسية بترتيبك (اختيار المالك 2026-10-15): [layout] = المحفوظ في الحساب أو الترتيب الأول · [save] = «تمام» في «عدّل الكروت» ·
 * [reset] = «الترتيب الأصلي» (بيمسح المستند ⇒ أي كارت جديد بعد كده بيظهر بحالته الأولى). الحساب كله في `core/HomeCards.kt`.
 */
class ManageHomeCards(private val settings: UserSettingsStore, private val clock: Clock) {
    suspend fun layout(): HomeCardsLayout {
        val stored = settings.listAll().filterIsInstance<UserSetting.HomeCards>().firstOrNull() ?: return defaultHomeCards()
        return homeCardsLayout(stored.order, stored.hidden)
    }

    /** بيحفظ الترتيب كله (الظاهر والمخفي) ويرجّعه بعد التنضيف. */
    suspend fun save(order: List<HomeCardKind>, hidden: Set<HomeCardKind>): HomeCardsLayout {
        val clean = homeCardsLayout(order.map { it.wire }, hidden.map { it.wire })
        settings.save(UserSetting.HomeCards(clean.order.map { it.wire }, clean.order.filter { it in clean.hidden }.map { it.wire }, clock.nowIso()))
        return clean
    }

    suspend fun reset(): HomeCardsLayout {
        settings.remove(listOf(HOME_CARDS_KEY))
        return defaultHomeCards()
    }
}
