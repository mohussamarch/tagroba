package app.masroufy.ui.screens.people

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.masroufy.core.R06Key
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.LocalStartAsk
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.screens.dues.AddDebtSheet
import app.masroufy.ui.screens.dues.DuesDebtsRoute
import app.masroufy.ui.screens.dues.RoscaWizardRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import kotlinx.coroutines.launch

/**
 * اللي مفتوح فوق صفحة الشخص (r06-person): أيقونات «معاه» ([fan] = مكان الزرار) · لوحة المشروع أو طلب الاتصال · تقويم المناسبات · «ضيف دين».
 * [look] = آخر لون/رمز اتختار (بيظهر على طول قبل ما الحفظ يخلص).
 */
@Stable
internal class PersonActs {
    var fan by mutableStateOf<Anchor?>(null)
    var together by mutableStateOf<TogetherAct?>(null)
    var calendar by mutableStateOf(false)
    var addDebt by mutableStateOf(false)
    var look by mutableStateOf<PersonLookUi?>(null)
    var setLook: (Int, String?) -> Unit = { _, _ -> }
    var addOccasion: () -> Unit = {}
}

@Composable
internal fun rememberPersonActs(personId: String, ui: PersonPageUi?): PersonActs {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val acts = remember(personId) { PersonActs() }
    // اللون والرمز بيتحفظوا على طول (`ManagePersonCircles.setLook`) — الفشل ⇒ يرجع المحفوظ ورسالة
    acts.setLook = { color, badge ->
        acts.look = PersonLookUi(color, badge)
        scope.launch {
            runCatching { deps.people.circles.setLook(personId, color, badge) }.onFailure { e ->
                acts.look = null
                toaster.show(e.message ?: t(app.masroufy.core.UiKey.PPL_LOAD_FAILED))
            }
        }
    }
    acts.addOccasion = { nav.open(OccasionSheetRoute(personId)) }
    return acts
}

/** اللوحات والطبقات فوق الصفحة — بتترسم برّه القايمة عشان تفضل مفتوحة وهي بتسكرول. */
@Composable
internal fun PersonActsHost(ui: PersonPageUi, acts: PersonActs, today: String) {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val startAsk = LocalStartAsk.current
    acts.fan?.let { anchor ->
        PersonFan(anchor, onClose = { acts.fan = null }) { a ->
            acts.fan = null
            when (a) {
                // «ضيف دين» بالشخص ده متختار — لو «ضيف دين» مش متوصّل ⇒ صفحة «الديون»
                FanAction.DEBT -> if (deps.dues.addDebt != null) acts.addDebt = true else nav.push(DuesDebtsRoute())
                // التقسيم الموجود = تقسيم المساعد (`AssistantSplit`): بيتفتح ومكتوب «قسّم فاتورة مع {الاسم}» والمستخدم يكمّل المبلغ
                FanAction.SPLIT -> startAsk?.invoke(t(R06Key.PH_SPLIT_PROMPT, ui.name))
                FanAction.ROSCA -> nav.push(RoscaWizardRoute)
                FanAction.PROJECT -> acts.together = TogetherAct.PROJECT
            }
        }
    }
    TogetherSheet(acts.together, ui.name) { acts.together = null }
    OccasionCalendarSheet(acts.calendar, ui.id, ui.name, ui.occasions, personOccasionsUi(ui.occasions, today).once, today) { acts.calendar = false }
    AddDebtSheet(acts.addDebt, app.masroufy.ui.screens.dues.DebtSide.ALL, onDismiss = { acts.addDebt = false }, presetPersonId = ui.id) {
        acts.addDebt = false
        PeopleChanges.bump()
    }
}
