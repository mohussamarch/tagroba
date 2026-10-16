package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.DayGroup
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.LocalRegistry
import app.masroufy.ui.nav.Slots
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.overlay.QuickMenuHost
import app.masroufy.ui.overlay.rememberQuickMenu
import app.masroufy.ui.screens.common.PullRefreshIndicator
import app.masroufy.ui.screens.common.TabHeader
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.screens.common.pullRefresh
import app.masroufy.ui.screens.common.rememberPullRefresh
import app.masroufy.ui.screens.home.PeriodChoice
import app.masroufy.ui.screens.home.PeriodPickerRoute
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadTransactionsScreenRequest
import kotlinx.coroutines.launch

/**
 * تبويب «العمليات» (`Operations` + `OperationMenu`): الرأس (الفلتر · الشهر · الترس) · المبدّل (العمليات · الميزانيات · المستحقات) ·
 * شرايط «مستنياك» · كارتين «دخل الشهر» و«المصروف الحقيقي» · العمليات بالأيام. كل الأرقام من `LoadTransactionsScreen` (CLAUDE.md #4).
 * الفترة = الشهر اللي اتختار في «اختر الشهر» ([PeriodChoice] للبلد الشغالة — المحاكي 2026-10-10: كانت بتتجاهله)، وإلا الشهر الحالي.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OperationsScreen() {
    val space = LocalSpace.current
    val deps = space.operations
    val nav = LocalNavigator.current
    var tab by rememberSaveable { mutableStateOf(OpsTab.OPERATIONS) }
    var ui by remember(deps) { mutableStateOf(OpsUi()) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val quick = rememberQuickMenu()
    // قراية المختار في الرسم نفسه ⇒ الرجوع من «اختر الشهر» بيعيد القراية بالشهر الجديد
    val chosenKey = PeriodChoice.of(space.space.id)
    // r041b P5: الحفظ من «+» فوق «العمليات» (أو أي كتابة من لوحة فوقها) بيزوّد `DataChanges.version` ⇒ القايمة والمجاميع بتتقري تاني
    val changes = app.masroufy.ui.app.LocalDataChanges.current
    LaunchedEffect(deps, reload, chosenKey, changes.version) {
        app.masroufy.perf.PerfTrace.span("screen:operations") {
            val today = space.shell.today()
            val view = attempt {
                val trace = app.masroufy.perf.PerfTrace
                val payday = trace.span("ops:payday") { deps.payday() }
                val data = trace.span("ops:transactions") { deps.transactions.load(LoadTransactionsScreenRequest(PeriodChoice.periodOf(chosenKey, payday), today, payday)) }
                trace.span("ops:view") { operationsView(data, attempt { deps.wallets() }.orEmpty(), today, space.space.currency, space.space.countryCode) }
            }
            // r041c: القايمة بتبان على طول — عدد أسئلة التحويلات (لفة على كل العمليات) بييجي بعدها ومش بيأخّرها
            ui = ui.loaded(view, null)
        }
        refreshing = false // دايرة «اسحب لتحت» بتفضل ظاهرة لحد ما القراية دي تخلص
        val parties = attempt { app.masroufy.perf.PerfTrace.span("ops:zone") { deps.transfers.zone().questions.size } }
        if (parties != null && ui.view != null) ui = ui.copy(partiesWaiting = parties)
    }
    // وضع «تحديد» والأفعال الجماعية (قايمة المالك §6.5 بند 15) — الحالة في [OpsSelection]، والمجموع والكتابة من `BulkTransactions`
    var sel by remember(deps) { mutableStateOf(OpsSelection()) }
    var bulkOpen by remember { mutableStateOf<app.masroufy.usecase.BulkAction?>(null) }
    var done by remember { mutableStateOf<BulkDone?>(null) }
    var erasing by remember { mutableStateOf<app.masroufy.core.Id?>(null) } // «امسح» من قايمة العملية (OVERRIDES §91-11)
    val toaster = app.masroufy.ui.shell.LocalToaster.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // «اسحب لتحت عشان تحدّث» (قايمة المالك بند 9): نفس تسجيل رسايل البنك بتاع تشغيلة الخلفية ⇒ رسالة لو فشل ⇒ قراية من جديد
    val pull = rememberPullRefresh(refreshing) {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                val sms = space.imports.sms
                // r041c: الجديد بس وبرّه خيط الواجهة (`SmsDeps.recordNew`) — كان `overview` كامل على خيط الواجهة فالبرنامج بيعلّق ٤–٥ ثواني
                val fresh = if (sms == null) null else attempt { sms.recordNew() }
                if (sms != null && fresh == null) toaster.show(t(UiKey.OPERATIONS_REFRESH_FAILED))
                if (fresh?.isNotEmpty() == true) changes.changed()
                reload++
            }
        }
    }
    val visibleIds = ui.view?.days?.flatMap { d -> d.rows.map { it.id } }.orEmpty()
    val summary by androidx.compose.runtime.produceState<app.masroufy.usecase.SelectionSummary?>(null, sel.ids, reload) {
        value = attempt { deps.bulk?.summary(sel.ids) }
    }
    LaunchedEffect(ui.view) { if (sel.active) sel = sel.keepOnly(visibleIds) }
    val undoWindow = undoWindowFor(done) { done = null } // 8 ثواني من ساعة رتيبة، وواقفة والإيد على الشريط (r041 A5)
    if (sel.active) app.masroufy.ui.overlay.Overlay(onBack = { sel = sel.cancel() }) {}
    fun undo(d: BulkDone) {
        val u = d.undo ?: return
        done = null
        scope.launch {
            toaster.show(failureOf { deps.bulk?.undo(u) } ?: t(UiKey.OPS_BULK_UNDONE))
            reload++
        }
    }
    val title = t(UiKey.TAB_OPERATIONS)
    Box(Modifier.fillMaxSize()) {
        TabScaffold(title, Modifier.pullRefresh(pull), header = {
            TabHeader(title, actions = {
                if (deps.bulk != null && tab == OpsTab.OPERATIONS && !sel.active) {
                    SurfaceIconButton(Lucide.CHECK, t(UiKey.OPS_SELECT), onClick = { sel = sel.start() }, iconSize = 20.dp)
                }
                SurfaceIconButton(Lucide.FILTER, t(UiKey.OPERATIONS_FILTER), onClick = { nav.push(OperationFiltersRoute) }, iconSize = 20.dp)
                MonthButton(ui.view?.periodLabel ?: t(UiKey.OPERATIONS_PERIOD)) { nav.push(PeriodPickerRoute) }
            })
        }) {
            item(key = "tabs") {
                SegmentedTabs(
                    listOf(OpsTab.OPERATIONS to t(UiKey.OPERATIONS_TAB_OPS), OpsTab.BUDGETS to t(UiKey.OPERATIONS_TAB_BUDGETS), OpsTab.DUES to t(UiKey.OPERATIONS_TAB_DUES)),
                    tab, {
                        tab = it
                        sel = sel.cancel()
                    }, Modifier.fillMaxWidth(),
                )
            }
            when (tab) {
                OpsTab.BUDGETS -> item(key = "budgets") { LocalRegistry.current.Slot(Slots.BUDGETS) }
                OpsTab.DUES -> item(key = "dues") { LocalRegistry.current.Slot(Slots.DUES) }
                OpsTab.OPERATIONS -> {
                    if (sel.active) stickyHeader(key = "select-bar") {
                        SelectionBar(sel, summary, visibleIds, onToggleAll = { sel = sel.toggleAll(visibleIds) }, onCancel = { sel = sel.cancel() }) { action ->
                            when {
                                sel.count == 0 -> toaster.show(t(UiKey.OPS_BULK_PICK_FIRST))
                                action == app.masroufy.usecase.BulkAction.DELETE && (summary?.deletable ?: 0) == 0 -> toaster.show(t(UiKey.OPS_BULK_DELETE_NONE))
                                else -> bulkOpen = action
                            }
                        }
                    }
                    done?.let { d -> stickyHeader(key = "bulk-undo") { UndoStrip(d.text, undoWindow) { undo(d) } } }
                    operationsTab(
                        ui, onRetry = { ui = ui.retrying(); reload++ },
                        onOpen = { r -> if (sel.active) sel = sel.toggle(r.id) else quick.guardTap { nav.push(OperationDetailRoute(r.id)) }() },
                        onMenu = { r, a ->
                            if (sel.active) {
                                sel = sel.toggle(r.id)
                            } else {
                                quick.show(
                                    operationMenuRequest(
                                        r, a,
                                        onDetail = { nav.push(OperationDetailRoute(it.id)) },
                                        onPerson = { nav.push(OperationDetailRoute(it.id, openPerson = true)) },
                                        onSelect = if (deps.bulk == null) null else { row -> sel = sel.start(row.id) },
                                        onErase = if (deps.erase == null || deps.bulk == null) null else { row -> erasing = row.id },
                                    ),
                                )
                            }
                        },
                        selection = sel,
                    )
                }
            }
        }
        PullRefreshIndicator(pull, t(UiKey.OPERATIONS_REFRESHING), Modifier.align(Alignment.TopCenter))
    }
    QuickMenuHost(quick)
    OperationEraseSheet(erasing, deps.erase, onDismiss = { erasing = null }) { e -> erasing = null; reload++; done = e.asBulk() }
    BulkSheets(bulkOpen, deps, sel.ids, summary?.deletable ?: 0, onDismiss = { bulkOpen = null }) { d ->
        bulkOpen = null
        sel = sel.cancel()
        reload++
        if (d.undo == null) toaster.show(d.text) else done = d
    }
}

private fun LazyListScope.operationsTab(ui: OpsUi, onRetry: () -> Unit, onOpen: (OpRow) -> Unit, onMenu: (OpRow, Anchor) -> Unit, selection: OpsSelection) {
    if (ui.skeleton) {
        item(key = "loading") { LoadingBlocks() }
        return
    }
    if (ui.failed) item(key = "error") {
        ErrorBanner(t(UiKey.OPERATIONS_ERROR_TITLE), t(UiKey.OPERATIONS_ERROR_BODY), t(UiKey.SHELL_RETRY), onRetry)
    }
    val v = ui.view ?: return
    if (v.empty) {
        item(key = "empty") { EmptyState(t(UiKey.OPERATIONS_EMPTY_TITLE, v.periodLabel), t(UiKey.OPERATIONS_EMPTY_BODY)) }
        return
    }
    // شريط رسايل البنك: عددها مالوش حالة استخدام في «العمليات» لسه (صندوق الرسايل شغل منطقة الاستيراد) ⇒ `null` ⇒ ما بيظهرش
    items(banners(bankSmsWaiting = null, reviewCount = v.reviewCount, partiesWaiting = ui.partiesWaiting), key = { "banner-${it.kind}" }) { b -> BannerItem(b) }
    // «دفعت الإيجار؟» (`QuickAddStrip`) بيستنى حالة استخدام «المتوقع ولم يُسجَّل» — من غيرها ما بيظهرش
    item(key = "summary") { Summary(v.incomeMinor, v.expenseMinor, v.currency, v.approx) }
    items(v.days, key = { "day-${it.date}" }) { day ->
        DayGroup(day.label) {
            day.rows.forEachIndexed { i, row ->
                // كل صف في مجموعة بمفتاحه ومفتاح الوضع: التبديل بين الصف العادي وصف التحديد ما يقراش خانات «remember» غلط
                // (نفس سبب وقعة «تحويل» في لوحة «+» — جولة 0.4)
                key(row.id, selection.active) {
                    if (i > 0) Divider()
                    if (selection.active) {
                        SelectableOperationRow(row, selection.isSelected(row.id)) { onOpen(row) }
                    } else {
                        OperationListRow(row, onOpen = { onOpen(row) }, onMenu = { a -> onMenu(row, a) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerItem(b: Banner) {
    val nav = LocalNavigator.current
    when (b.kind) {
        BannerKind.BANK_SMS -> WaitingBanner(b.text, Ink.focus, Ink.alertBg) { nav.push(BankSmsRoute) }
        BannerKind.REVIEW -> WaitingBanner(b.text, Ink.transfer, Color(0x142469BA)) { nav.push(ReviewQueueRoute) }
        BannerKind.TRANSFERS -> WaitingBanner(b.text, Ink.primary, Color(0x1408634F)) { nav.push(TransfersRoute) }
    }
}

/** كارتين «دخل الشهر» و«المصروف الحقيقي» (18 عريض) — `null` ⇒ «غير متاح» (عمره ما يبقى صفر). */
@Composable
private fun Summary(income: Halalas?, expense: Halalas?, currency: Currency, approx: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(t(UiKey.OPERATIONS_INCOME), income, currency, AmountTone.INCOME, Modifier.weight(1f))
            SummaryCard(t(UiKey.OPERATIONS_EXPENSE), expense, currency, AmountTone.EXPENSE, Modifier.weight(1f))
        }
        if (approx) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ApproxBadge()
            BasicText(t(UiKey.OPERATIONS_APPROX_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun SummaryCard(label: String, minor: Halalas?, currency: Currency, tone: AmountTone, modifier: Modifier) {
    FloatingCard(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), contentPadding = PaddingValues(12.dp)) {
        BasicText(label, style = Type.caption().copy(color = Ink.muted))
        AmountText(minor, currency, Modifier.fillMaxWidth(), tone = tone, size = 18, showCurrency = false)
    }
}

/** «بيحمّل»: نفس شكل المحتوى رمادي (مش دوّامة) — الكارتين · عنوان اليوم · كارت الصفوف. */
@Composable
private fun LoadingBlocks() {
    val label = t(UiKey.SHELL_LOADING)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(Modifier.weight(1f).height(66.dp), radius = 18.dp)
            Skeleton(Modifier.weight(1f).height(66.dp), radius = 18.dp)
        }
        Skeleton(Modifier.size(48.dp, 16.dp), radius = 8.dp, strong = true)
        Skeleton(Modifier.fillMaxWidth().height(176.dp))
    }
}
