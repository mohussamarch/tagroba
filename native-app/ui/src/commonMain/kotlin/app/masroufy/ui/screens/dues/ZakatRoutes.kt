package app.masroufy.ui.screens.dues

import app.masroufy.core.Id
import app.masroufy.ui.nav.Route

/**
 * الزكاة بقت تحت «المستحقات» (قرار المالك 2026-10-23 — AppMap). **المنطق والشاشة زي ما هما** (`ZakatScreen` في `screens/investment/`)؛
 * اللي اتنقل: الاسم والمدخل (رابط في «المستحقات») — والتسجيل لسه في `registerInvestment` عشان الاعتماديات (`InvestmentDeps`).
 */
object ZakatRoute : Route {
    override val name = "Zakat"
}

/**
 * «دفع زكاة السنة» لسنة متثبّتة بمعرّفها (= يوم ميعادها). في النموذج `ZakatPay` مرسومة **جوه** `Zakat` بعد «ثبّت» (وده اللي بيتعرض
 * هناك)؛ المسار ده عشان أي مكان تاني (تنبيه الزكاة مثلًا) يفتح دفع سنة بعينها.
 */
data class ZakatPayRoute(val yearId: Id) : Route {
    override val name = "ZakatPay"
}
