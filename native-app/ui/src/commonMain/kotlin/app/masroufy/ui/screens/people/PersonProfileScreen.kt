package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.LensOnHero
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.dues.DebtDetailRoute
import app.masroufy.ui.text.t
import kotlinx.coroutines.launch
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type

/**
 * ملف الشخص (لوحة `PersonProfile`): رأس بترولي 168 بالرجوع ودايرته · الاسم وصلته · مناسباته (`OccasionSheet`) · «لك عنده» و«عليك له»
 * جنب بعض من غير مقاصة · «معًا» (4 أفعال) · «السجل بينكما» (الالتزامات المفتوحة ⇒ `DebtDetail`) · النقوط بينكما.
 */
@Composable
internal fun PersonProfileScreen(personId: String) {
    val deps = LocalSpace.current
    // cty-6: دين البلد التانية في صفحة الشخص ⇒ سطر قصير «الدين ده في …» وزرار «بدّل» (قبل كده الدوسة ما كانتش بتعمل حاجة)
    val toaster = app.masroufy.ui.shell.LocalToaster.current
    val other = app.masroufy.ui.app.LocalCountries.current.other
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val openAbroad: () -> Unit = {
        if (other != null) {
            val name = app.masroufy.core.countryLabel(other.countryCode)
            toaster.show(t(app.masroufy.core.R06Key.CTY_ABROAD_DEBT, name), t(app.masroufy.core.R06Key.CTY_ABROAD_SWITCH)) {
                scope.launch { deps.shell.switchSpace(other.id) }
            }
        }
    }
    val nav = LocalNavigator.current
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps, personId) { mutableStateOf<Load<PersonPageUi?>>(Load.Loading) }
    // r05: «السجل بينا» والحسابات المربوطة (`ManagePersonLinks`) — فشلها ما بيوقعش الصفحة (بيرجع للسجل القديم)
    var ledger by remember(deps, personId) { mutableStateOf<Pair<LedgerUi, app.masroufy.usecase.PersonPageData>?>(null) }
    LaunchedEffect(deps, personId, version, retry) {
        // A2: القواعد بتشتغل لحظة التسجيل (مش هنا) · A3: كل البلاد · السرعة: بالفهرس وبرّه الخيط الرئيسي
        ledger = deps.people.links?.let { l ->
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    l.page.load(personId, deps.people.otherSpaceLinks())?.let { d -> ledgerUi(d.ledger, d.accounts.size, deps.shell.today()) to d }
                }
            }.getOrNull()
        }
        load = loadOf {
            val p = deps.people
            val today = deps.shell.today()
            val row = p.people.listWithBalances().firstOrNull { it.person.id == personId } ?: return@loadOf null
            val overview = runCatching { p.overview.forSpace(today, deps.space.id) }.getOrNull()
            val until = dayNumberToIso(toDayNumber(parseIsoDate(today)) + 400)
            personPageUi(
                row,
                overview?.rows?.firstOrNull { it.person.id == personId },
                overviewLoaded = overview != null,
                profile = runCatching { p.circles.profileOf(personId) }.getOrNull(),
                occasions = runCatching { p.occasions.forPerson(personId, today) }.getOrDefault(emptyList()),
                badges = runCatching { p.events.personBadges(personId) }.getOrDefault(emptyList()),
                dues = runCatching { p.dues.dueItems(today, until) }.getOrDefault(emptyList()),
                today = today,
            )
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(Modifier.fillMaxSize(), state = app.masroufy.ui.nav.rememberEntryLazyListState(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(Space.block)) {
        when (val l = load) {
            Load.Loading -> item { Head(null, top) { nav.pop() }; Skeleton(Modifier.padding(Space.gutter).fillMaxWidth().height(220.dp)) }
            Load.Failed -> item { Head(null, top) { nav.pop() }; ErrorCard({ retry++ }, Modifier.padding(Space.gutter), t(UiKey.PPL_LOAD_FAILED), "") }
            is Load.Ready -> {
                val ui = l.value
                if (ui == null) item { Head(null, top) { nav.pop() }; EmptyState(t(UiKey.PERSON_PAGE_NOT_FOUND)) }
                else personBody(ui, top, ledger, deps.shell.today(), { nav.push(app.masroufy.ui.screens.operations.OperationDetailRoute(it)) }, { if (!it.startsWith(app.masroufy.usecase.ABROAD_ID_PREFIX)) nav.push(DebtDetailRoute(it)) else openAbroad() }) { nav.pop() }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.personBody(
    ui: PersonPageUi,
    top: androidx.compose.ui.unit.Dp,
    ledger: Pair<LedgerUi, app.masroufy.usecase.PersonPageData>?,
    today: String,
    openTxn: (String) -> Unit,
    openDebt: (String) -> Unit,
    back: () -> Unit,
) {
    item(key = "head") {
        Head(ui, top, back)
        Column(Modifier.fillMaxWidth().offset(y = (-66).dp).padding(horizontal = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // دايرة 104 بحلقة 4 بلون الخلفية (`0 0 0 4px #FAF9F3`)
            Box(Modifier.size(112.dp).clip(CircleShape).background(Ink.surface), contentAlignment = Alignment.Center) {
                InitialCircle(ui.name, 104.dp, onHero = true)
            }
            BasicText(ui.name, Modifier.padding(top = 6.dp), style = Type.of(22, FontWeight.Bold))
            ui.relLine?.let { BasicText(it, style = Type.of(13).copy(color = Ink.muted)) }
            if (ui.archived) Badge(t(UiKey.PPL_ARCHIVED), BadgeKind.NOT_AVAILABLE)
            OccasionCards(ui.id, ui.name, ui.occasions, Modifier.padding(top = 8.dp))
        }
    }
    item(key = "balances") {
        Row(Modifier.fillMaxWidth().offset(y = (-66).dp).padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BalanceBox(t(UiKey.PERSON_PAGE_LAK), ui.owed, AmountTone.INCOME, ui.overdue, null, Modifier.weight(1f))
            BalanceBox(t(UiKey.PERSON_PAGE_ALEK), ui.owe, AmountTone.EXPENSE, null, t(UiKey.PERSON_PAGE_NO_NETTING), Modifier.weight(1f))
        }
    }
    val section = Modifier.offset(y = (-66).dp).padding(horizontal = Space.gutter)
    // r06-debts (OVERRIDES §112-2): القايمة الأساسية للديون في «المستحقات» — الصفحة بتوصّلها بس
    item(key = "debts-link") { app.masroufy.ui.screens.dues.DuesDebtsLink(section) }
    if (ledger == null) item(key = "history") { History(ui, section) }
    else {
        // r05: السجل كله · الديون (المفتوحة والمقفولة) · الحسابات المربوطة · اربط عمليات · أضف عملية
        personLedgerItems(ledger.first, section, openTxn, openDebt)
        item(key = "accounts") {
            val links = app.masroufy.ui.app.LocalSpace.current.people.links
            if (links != null) PersonAccountsCard(ui.id, ui.name, ledger.second.accounts, links, section)
        }
        // العمليات اللي اتعلّمت بتصنيف «فلوس مع شخص» ومش مربوطة ⇒ ضغطة ⇒ «معناها إيه؟» على الشخص ده
        item(key = "needs-person") { NeedsPersonCard(section, personId = ui.id, onDemand = true) }
        item(key = "link-ops") {
            val links = app.masroufy.ui.app.LocalSpace.current.people.links
            if (links != null) PersonLinkOpsCard(ui.id, ui.name, links, today, section)
        }
    }
    item(key = "together") { Together(ui, section) }
    item(key = "dues-rules") { app.masroufy.ui.screens.operations.DuesRulesCard(ui.id, section) }
    if (ui.badges.isNotEmpty()) item(key = "badges") {
        Column(Modifier.offset(y = (-66).dp).padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.PERSON_PAGE_BADGES), style = Type.section())
            FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                ui.badges.forEach { BasicText(it, Modifier.padding(vertical = 4.dp), style = Type.body()) }
            }
        }
    }
}

/** الرأس البترولي 168 (زاوية تحت 28): الرجوع (عدسة 48) يمين ودايرته (الصحاب · العائلة …) شمال. */
@Composable
private fun Head(ui: PersonPageUi?, top: androidx.compose.ui.unit.Dp, back: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = Radius.hero, bottomEnd = Radius.hero)
    Row(
        Modifier.fillMaxWidth().height(168.dp + top).clip(shape).background(Glass.heroBase).padding(start = 20.dp, end = 20.dp, top = 20.dp + top),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        val press = rememberPress()
        LensOnHero(Modifier.size(48.dp).pressScale(press).tap(press, label = t(UiKey.SHELL_BACK), onClick = back)) {
            LucideIcon(Lucide.CHEVRON_RIGHT, size = 22.dp, tint = Ink.lensInk, modifier = Modifier.align(Alignment.Center).mirrorInLtr())
        }
        if (ui != null) BasicText(ui.circleLine, Modifier.padding(top = 14.dp), style = Type.caption().copy(color = Ink.onHeroMuted))
    }
}

@Composable
private fun BalanceBox(title: String, lines: List<MoneyLine>?, tone: AmountTone, chip: String?, note: String?, modifier: Modifier) {
    FloatingCard(modifier, shape = RoundedCornerShape(Radius.control), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        BasicText(title, style = Type.caption().copy(color = Ink.muted))
        when {
            lines == null -> AmountText(null, app.masroufy.core.Currency.SAR, size = 20)
            lines.isEmpty() -> BasicText(t(UiKey.PERSON_PAGE_NOTHING), style = Type.of(20, FontWeight.Bold).copy(color = Ink.muted))
            else -> lines.forEach { AmountText(it.minor, it.currency, Modifier.fillMaxWidth(), size = 20, color = tone.color) }
        }
        chip?.let { Pill(it, Ink.focus, Ink.alertBg, Modifier.padding(top = 2.dp)) }
        note?.let { BasicText(it, style = Type.of(11).copy(color = Ink.muted)) }
    }
}

@Composable
private fun History(ui: PersonPageUi, modifier: Modifier) {
    val nav = LocalNavigator.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.PERSON_PAGE_HISTORY), style = Type.section())
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (ui.history.isEmpty()) BasicText(t(UiKey.PERSON_PAGE_HISTORY_EMPTY), Modifier.padding(vertical = 16.dp), style = Type.body().copy(color = Ink.muted))
            ui.history.forEachIndexed { i, h ->
                val press = rememberPress()
                Rowed(i == ui.history.lastIndex, Modifier.pressScale(press).tap(press, label = h.title, onClick = { nav.push(DebtDetailRoute(h.obligationId)) })) {
                    Column(Modifier.weight(1f)) {
                        BasicText(h.title, style = Type.bodyBold())
                        BasicText(h.sub, style = Type.caption().copy(color = Ink.muted))
                    }
                    AmountText(h.amountMinor, h.currency, tone = h.tone, showCurrency = false)
                    LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
                }
            }
        }
    }
}
