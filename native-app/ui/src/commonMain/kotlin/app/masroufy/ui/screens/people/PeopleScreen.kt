package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.TabHeader
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * تبويب «الأشخاص» (لوحة `People`): المسرح البترولي (أقرب ٥ فوق «أنت») وتحته شبكة الناس كلهم ٥ في الصف و«لك» و«عليك».
 * **السحب لفوق** بيطوي المسرح ويفرد صفوف الشبكة زي الأسطوانة (`p` من ٠ لـ١ على ٤٢٠)، و«الكل (N)» بيعمل نفس الحركة.
 * الحالات: بيحمّل (هيكل) · فاضي («لم تُضف أحدًا بعد») · خطأ («تعذّر تحميل الأرصدة» + «أعد المحاولة») · عادي.
 */
private const val RANGE = 420f
private const val ROW = 106f
private const val GAP = 8f
private const val HERO_TOP = 82f
private const val DRUM_TOP = 456f

private sealed interface Cell {
    data class Person(val chip: PersonChip) : Cell
    data object Add : Cell
}

@Composable
internal fun PeopleScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps) { mutableStateOf<Load<PeopleTabUi>>(Load.Loading) }
    var me by remember(deps) { mutableStateOf<String?>(null) }
    LaunchedEffect(deps, version, retry) {
        val full = loadOf { peopleTabUi(deps.people.overview.forSpace(deps.shell.today(), deps.space.id), deps.space.id, deps.space.currency) }
        load = if (full !is Load.Failed) full else loadOf { peopleWithoutBalances(deps.people.people.listWithBalances()) }.let { if (it is Load.Ready) it else full }
        me = runCatching { deps.shell.me().displayName }.getOrNull()
    }
    val open = { p: PersonChip -> nav.push(PersonProfileRoute(p.id)) }
    val add = { nav.open(AddPersonSheetRoute) }
    val ui = (load as? Load.Ready)?.value
    val empty = ui != null && ui.total == 0
    val failed = load is Load.Failed

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val viewDp = maxHeight.value
        val groups = ui?.let { cellsOf(it) } ?: emptyList()
        val fullDrum = groups.size * (ROW + GAP)
        val scrolls = ui != null && !empty
        val extra = if (scrolls) max(0f, top + HERO_TOP + fullDrum - (viewDp - Space.tabContentBottom.value)) else 0f
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val reduce = LocalReduceMotion.current
        val rangePx = with(density) { RANGE.dp.toPx() }
        val p = (scroll.value / rangePx).coerceIn(0f, 1f)
        val over = with(density) { max(0f, scroll.value - rangePx).toDp().value }
        // تثبيت على الطرفين لما السحب يقف في النص (زي `scroll-snap: proximity` في النموذج)
        LaunchedEffect(scroll.isScrollInProgress) {
            if (!scroll.isScrollInProgress && scroll.value > 0 && scroll.value < rangePx) {
                val target = if (p > 0.5f) rangePx.roundToInt() else 0
                if (reduce) scroll.scrollTo(target) else scroll.animateScrollTo(target)
            }
        }
        val toggleAll = {
            scope.launch {
                val target = if (p > 0.5f) 0 else rangePx.roundToInt()
                if (reduce) scroll.scrollTo(target) else scroll.animateScrollTo(target)
            }
            Unit
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = scrolls)) {
            Box(Modifier.fillMaxWidth().height((viewDp + if (scrolls) RANGE + extra else 0f).dp)) {
                // المسرح لازق في الشاشة وإنت بتسحب (زي `position: sticky`)
                Box(Modifier.fillMaxWidth().height(viewDp.dp).offset { IntOffset(0, scroll.value) }) {
                    TabHeader(
                        t(UiKey.TAB_PEOPLE),
                        Modifier.padding(start = Space.gutter, end = Space.gutter, top = (Space.gutter.value + top).dp),
                        actions = {
                            SecondaryButton(t(UiKey.PEOPLE_EVENTS), onClick = { nav.push(EventsRoute) })
                            if (scrolls) SecondaryButton(if (p > 0.5f) t(UiKey.PEOPLE_BACK) else t(UiKey.PEOPLE_ALL, app.masroufy.core.sentenceNumber(ui!!.total)), onClick = toggleAll)
                        },
                    )
                    if (load is Load.Loading) LoadingStage(top)
                    else {
                        val heroAlpha = max(0f, 1f - 2.2f * p)
                        PeopleStage(
                            ui?.orbit.orEmpty(), me, open,
                            Modifier.padding(horizontal = Space.gutter).offset(y = (top + HERO_TOP).dp)
                                .graphicsLayer {
                                    translationY = -70f * p * density.density
                                    scaleX = 1f - 0.18f * p
                                    scaleY = 1f - 0.18f * p
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                    alpha = heroAlpha
                                }.height(STAGE_H.dp),
                        )
                    }
                    when {
                        empty -> EmptyPeople(add, Modifier.offset(y = (top + DRUM_TOP).dp))
                        failed -> ErrorCard({ retry++ }, Modifier.padding(horizontal = Space.gutter).offset(y = (top + DRUM_TOP).dp), t(UiKey.PPL_LOAD_FAILED), "")
                        ui != null -> {
                            val drumTop = top + DRUM_TOP - 374f * p - over
                            val height = Drum(groups, p, reduce, open, add, Modifier.padding(horizontal = Space.gutter).offset(y = drumTop.dp))
                            val below = Modifier.padding(horizontal = Space.gutter).offset(y = (drumTop + height + 4f).dp).alpha(max(0f, 1f - 2.4f * p))
                            if (ui.balancesFailed) ErrorCard({ retry++ }, below)
                            else Boxes(ui, nav, below, enabled = p < 0.3f)
                        }
                    }
                }
            }
        }
    }
}

/** الصفوف: أقرب ٥ (مطوي فوق — بيظهر في الصفحة الكاملة بس)، وبعده الباقيين ٥ ٥، و«إضافة» في الآخر. */
private fun cellsOf(ui: PeopleTabUi): List<List<Cell>> {
    val rest = ui.rest.map { Cell.Person(it) } + Cell.Add
    return listOf(ui.orbit.map { Cell.Person(it) }) + rest.chunked(5)
}

private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

/** الأسطوانة — بترجع ارتفاعها الحالي (dp) عشان «لك/عليك» يتحطوا تحتها. */
@Composable
private fun Drum(groups: List<List<Cell>>, p: Float, reduce: Boolean, open: (PersonChip) -> Unit, add: () -> Unit, modifier: Modifier): Float {
    var y = 0f
    val placed = groups.mapIndexed { k, cells ->
        val e = when (k) { 0 -> smooth((p - 0.35f) / 0.5f); 1 -> 1f; 2 -> smooth((p - 0.05f) / 0.5f); else -> smooth((p - 0.2f) / 0.5f) }
        val angle = if (k >= 2 && !reduce) -84f * (1f - e) else 0f
        val alpha = when (k) { 0 -> e; 1 -> 1f; else -> 0.14f + 0.86f * e }
        val h = when {
            k == 0 -> (ROW + GAP) * e
            k == 1 -> ROW + GAP
            reduce -> (ROW + GAP) * e
            else -> ROW * cos(angle * PI.toFloat() / 180f) + GAP * e
        }
        val row = Triple(y, Triple(cells, angle, alpha), if (k == 0) 0.6f + 0.4f * e else 1f)
        y += h
        row
    }
    Box(modifier.fillMaxWidth().height(y.dp)) {
        for ((top, spec, scale) in placed) {
            val (cells, angle, alpha) = spec
            Row(
                Modifier.fillMaxWidth().height(ROW.dp).offset(y = top.dp).graphicsLayer {
                    rotationX = angle
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 12f * density
                },
                verticalAlignment = Alignment.Top,
            ) {
                val live = alpha > 0.6f
                for (i in 0 until 5) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        when (val c = cells.getOrNull(i)) {
                            is Cell.Person -> GridPerson(c.chip, { if (live) open(c.chip) })
                            Cell.Add -> GridAdd({ if (live) add() })
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
    return y
}

@Composable
private fun Boxes(ui: PeopleTabUi, nav: app.masroufy.ui.nav.Navigator, modifier: Modifier, enabled: Boolean) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideBox(t(UiKey.PEOPLE_OWED_BOX), ui.owed, app.masroufy.ui.components.AmountTone.INCOME, t(UiKey.PEOPLE_OWED_COUNT, countOf(ui.owedPeople, Noun.PEOPLE)), enabled, Modifier.weight(1f)) { nav.push(OwedToYouRoute) }
        val oweCount = if (ui.owePeople == 2) t(UiKey.PEOPLE_OWE_TWO) else t(UiKey.PEOPLE_OWE_COUNT, countOf(ui.owePeople, Noun.PEOPLE))
        SideBox(t(UiKey.PEOPLE_OWE_BOX), ui.owe, app.masroufy.ui.components.AmountTone.EXPENSE, oweCount, enabled, Modifier.weight(1f)) { nav.push(YouOweRoute) }
    }
}

@Composable
private fun SideBox(title: String, lines: List<MoneyLine>?, tone: app.masroufy.ui.components.AmountTone, count: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FloatingCard(modifier, onClick = onClick, enabled = enabled, clickLabel = title) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Type.bodyBold())
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
        if (lines == null) AmountText(null, LocalSpace.current.space.currency, Modifier.fillMaxWidth(), size = 22)
        else for (l in lines) AmountText(l.minor, l.currency, Modifier.fillMaxWidth(), size = 22, color = tone.color)
        BasicText(count, style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
private fun LoadingStage(top: Float) {
    Column(Modifier.padding(horizontal = Space.gutter).offset(y = (top + HERO_TOP).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(STAGE_H.dp), radius = 28.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(5) { Skeleton(Modifier.size(56.dp), radius = 28.dp) } }
    }
}

@Composable
private fun EmptyPeople(add: () -> Unit, modifier: Modifier) {
    EmptyState(
        t(UiKey.PEOPLE_EMPTY_TITLE), t(UiKey.PEOPLE_EMPTY_BODY), modifier.padding(horizontal = Space.gutter),
        action = { PrimaryButton(t(UiKey.PEOPLE_ADD_PERSON), onClick = add, modifier = Modifier.padding(top = 8.dp)) },
    )
}

@Composable
internal fun ErrorCard(onRetry: () -> Unit, modifier: Modifier = Modifier, title: String = t(UiKey.PEOPLE_ERROR_TITLE), body: String = t(UiKey.PEOPLE_ERROR_BODY)) {
    Row(
        modifier.fillMaxWidth().background(Ink.alertBg, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
            if (body.isNotBlank()) BasicText(body, style = Type.caption().copy(color = Ink.focus))
        }
        SecondaryButton(t(UiKey.PPL_RETRY), onClick = onRetry)
    }
}
