package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.SheetRoute
import app.masroufy.ui.nav.Slots
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.text.t
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.ManageInstallments
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManageRecurring
import app.masroufy.usecase.ManageRoscas
import app.masroufy.usecase.RoscaSetup

/**
 * منطقة «المستحقات» (`SCREENS.md` §٢.٦ + تفاصيل الدين والتسوية والدين القديم): خانة «المستحقات» جوه مبدّل «العمليات» ([Slots.DUES]) ·
 * `DuesDebts` · `DebtDetail` · `SettleSheet` · `OpeningDebtSheet` · `Installments` · `InstallmentDetail` · `InstallmentEdit` · `Roscas` ·
 * `RoscaDetail` · `RoscaWizard` · `Subscriptions` · `SubscriptionDetail`. **الملف ده بتاع المنطقة بس.**
 * الأشخاص (`PersonProfile`) بيفتحوا [DebtDetailRoute] و[SettleSheetRoute] و[OpeningDebtSheetRoute] من هنا.
 */
interface DuesDeps {
    /** الأرصدة والمواعيد وسطر الشهر — `LoadDues.load` · `dueItems`. */
    val loadDues: LoadDues

    /** الديون مع الأشخاص — `listWithBalances` · `settle` · `addOpeningDebt`. */
    val people: ManagePeople

    /** الجمعيات — `list` · `forecast` · `createFromDraft`. */
    val roscas: ManageRoscas

    /** «جمعية جديدة» سؤال سؤال — `begin` · `answer`. */
    val roscaSetup: RoscaSetup

    /** الأقساط ومواعيد الديون — `list` · `save` · `unlink` · `setDebtTerms` · `clearDebtTerms`. */
    val installments: ManageInstallments

    /** الاشتراكات والفواتير — `load` · `save`. */
    val recurring: ManageRecurring

    /** الفترة المالية اللي فيها [today] (من يوم الراتب في الملف — 28 لو مش متسجل). */
    suspend fun period(today: IsoDate): Period
}

/** «لك» أو «عليك» أو الاتنين — فلتر شاشة الديون (الكارت المضغوط بيصفّي، والضغط تاني يرجّع الكل). */
enum class DebtSide { ALL, FOR_YOU, ON_YOU }

data class DuesDebtsRoute(val side: DebtSide = DebtSide.ALL) : Route {
    override val name = "DuesDebts"
}

/** تفاصيل دين واحد (التزام) — من «الديون» ومن ملف الشخص. */
data class DebtDetailRoute(val obligationId: String) : Route {
    override val name = "DebtDetail"
}

/** «سجّل تحصيلًا/سدادًا» لالتزام — لوحة بتتفتح من أي منطقة (ملف الشخص). */
data class SettleSheetRoute(val obligationId: String) : SheetRoute {
    override val name = "SettleSheet"
}

/** «دين قديم مع X» — من غير عملية (OVERRIDES §27). */
data class OpeningDebtSheetRoute(val personId: String, val personName: String) : SheetRoute {
    override val name = "OpeningDebtSheet"
}

object InstallmentsRoute : Route {
    override val name = "Installments"
}

data class InstallmentDetailRoute(val planId: String) : Route {
    override val name = "InstallmentDetail"
}

/** [planId] = null ⇒ «خطة جديدة». */
data class InstallmentEditRoute(val planId: String? = null) : Route {
    override val name = "InstallmentEdit"
}

object RoscasRoute : Route {
    override val name = "Roscas"
}

data class RoscaDetailRoute(val roscaId: String) : Route {
    override val name = "RoscaDetail"
}

object RoscaWizardRoute : Route {
    override val name = "RoscaWizard"
}

object SubscriptionsRoute : Route {
    override val name = "Subscriptions"
}

data class SubscriptionDetailRoute(val itemId: String) : Route {
    override val name = "SubscriptionDetail"
}

/**
 * «حاجة اتغيرت في المستحقات» (تسوية · دين قديم · خطة · جمعية · اشتراك): الشاشات المفتوحة بتحمّل تاني لوحدها (مفتاح `LaunchedEffect`).
 * ملف الشخص (منطقة الأشخاص) يقدر يقرا [version] عشان يحدّث نفسه بعد لوحة اتفتحت من عنده.
 */
object DuesChanges {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}

fun RouteRegistry.registerDues() {
    slot(Slots.DUES) { DuesPanel() }
    screen<DuesDebtsRoute> { r -> DuesDebtsScreen(r.side) }
    screen<DebtDetailRoute> { r -> DebtDetailScreen(r.obligationId) }
    sheet<SettleSheetRoute> { r, close -> RouteSheet(t(UiKey.SETTLE_SHEET_TITLE_GENERIC), close) { dismiss -> SettleSheetBody(r.obligationId, dismiss) } }
    sheet<OpeningDebtSheetRoute> { r, close ->
        val nav = LocalNavigator.current
        RouteSheet(t(UiKey.OPENING_DEBT_TITLE, r.personName), close) { dismiss ->
            OpeningDebtBody(r.personId, r.personName, onSaved = { o -> dismiss(); nav.push(DebtDetailRoute(o.id)) })
        }
    }
    screen<InstallmentsRoute> { InstallmentsScreen() }
    screen<InstallmentDetailRoute> { r -> InstallmentDetailScreen(r.planId) }
    screen<InstallmentEditRoute> { r -> InstallmentEditScreen(r.planId) }
    screen<RoscasRoute> { RoscasScreen() }
    screen<RoscaDetailRoute> { r -> RoscaDetailScreen(r.roscaId) }
    screen<RoscaWizardRoute> { RoscaWizardScreen() }
    screen<SubscriptionsRoute> { SubscriptionsScreen() }
    screen<SubscriptionDetailRoute> { r -> SubscriptionDetailScreen(r.itemId) }
}
