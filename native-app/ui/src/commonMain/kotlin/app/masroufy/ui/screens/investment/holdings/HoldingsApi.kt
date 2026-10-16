package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestHoldKey
import app.masroufy.core.holdingsView
import app.masroufy.core.uiText
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster

/**
 * «حاجاتي» — الواجهة اللي شاشة «الاستثمار» بتناديها (مواصفة r05 §4.2، **التوقيعات ثابتة**): الحالة بتتعمل مرة في الشاشة، والقسم
 * عنصر في القايمة الطويلة بمفتاح "holdings"، واللوحة والرسالة في [HoldingsOverlays] برّه القايمة.
 * الصورة ([HoldingsSnapshot]) بتتحمّل في الشاشة مرة واحدة؛ بعد الحفظ [rememberHoldings]`(onChanged)` بيطلب تحميلها تاني.
 */
@Stable
class HoldingsState internal constructor(internal val deps: InvestHoldingsDeps, internal var onChanged: () -> Unit, snapshot: HoldingsSnapshot?) {
    var snapshot by mutableStateOf(snapshot)
        internal set

    /** الغرض اللي اتضغط عليه في «اعرض حسب الغرض» (null = الكل). */
    var filter by mutableStateOf<HoldingPurpose?>(null)

    var addOpen by mutableStateOf(false)
        internal set

    /** مفتوحة من صفحة نوع ⇒ أنواعه بس. */
    var addOnly by mutableStateOf<HoldingGroup?>(null)
        internal set

    fun openAdd(only: HoldingGroup? = null) {
        addOnly = only
        addOpen = true
    }

    fun closeAdd() {
        addOpen = false
    }
}

@Composable
fun rememberHoldings(deps: InvestHoldingsDeps, snapshot: HoldingsSnapshot?, onChanged: () -> Unit): HoldingsState {
    val state = remember(deps) { HoldingsState(deps, onChanged, snapshot) }
    SideEffect {
        state.snapshot = snapshot
        state.onChanged = onChanged
    }
    return state
}

/** «حاجاتي» في القايمة (مفتاح "holdings"). لسه بتحمّل (الصورة null) ⇒ مفيش عنصر (الشاشة عندها هياكل التحميل). */
fun LazyListScope.holdingsSection(state: HoldingsState) {
    if (state.snapshot == null) return
    item(key = "holdings") {
        val snapshot = state.snapshot ?: return@item
        val nav = LocalNavigator.current
        val scope = rememberCoroutineScope()
        val view = remember(snapshot, state.filter) { holdingsView(snapshot, state.filter) }
        HoldingsBlock(
            view = view, currency = snapshot.currency, filter = state.filter, onFilter = { state.filter = it },
            onOpen = { nav.push(AssetCategoryRoute(it.wire)) }, onAdd = { state.openAdd() },
            onIncludePersonal = { on ->
                // الإجمالي بيتغيّر على طول، والحفظ في الخلفية ⇒ الشاشة تحمّل تاني (التوقّع بيقرا نفس الاختيار)
                state.snapshot = snapshot.copy(includePersonal = on)
                scope.launch {
                    runCatching { state.deps.prefs.setIncludePersonal(on) }
                    state.onChanged()
                }
            },
        )
    }
}

/** لوحة «ضيف حاجة» + رسالة «اتضافت». */
@Composable
fun HoldingsOverlays(state: HoldingsState) {
    val toaster = LocalToaster.current
    val currency = state.snapshot?.currency ?: return
    AssetAddSheet(
        visible = state.addOpen, only = state.addOnly, currency = currency, deps = state.deps, onDismiss = state::closeAdd,
        onSaved = { name ->
            state.closeAdd()
            toaster.show(uiText(InvestHoldKey.HOLD_TOAST_ADDED, name))
            state.onChanged()
        },
    )
}
