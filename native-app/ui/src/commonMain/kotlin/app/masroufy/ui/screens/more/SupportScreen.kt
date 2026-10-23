package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.masroufy.core.SupportChrome
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.investment.insights.FaqRoute

/** «الدعم» (`Support.dc.html`): نموذج «ابعتلنا» + رابط «شوف الأسئلة الشائعة الأول». الرسايل بتتحفظ على الجهاز بس (الوجهة مفتوحة للمالك). */
@Composable
fun SupportScreen() {
    val egypt = LocalSpace.current.space.countryCode.equals("EG", ignoreCase = true)
    val chrome = SupportChrome(egypt)
    val nav = LocalNavigator.current
    InnerScaffold(chrome.pageTitle) {
        item(key = "form") { SupportForm(egypt) }
        item(key = "faq") { TonalButton(chrome.faqFirst, { nav.push(FaqRoute) }, Modifier.fillMaxWidth()) }
    }
}
