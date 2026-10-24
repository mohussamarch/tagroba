package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import app.masroufy.ui.theme.Ink
import app.masroufy.core.Id
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.choiceArea
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.rememberChoice
import app.masroufy.ui.nav.rememberEntryScrollState
import app.masroufy.ui.screens.common.TabHeader
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * تبويب «الأشخاص» (لوحة `People`): المسرح البترولي (أقرب 5 فوق «أنت») وتحته شبكة الناس كلهم 5 في الصف و«لك» و«عليك».
 * **السحب لفوق** بيطوي المسرح ويفرد صفوف الشبكة زي الأسطوانة (`p` من 0 لـ1 على 420)، و«الكل (N)» بيعمل نفس الحركة.
 * الحالات: بيحمّل (هيكل) · فاضي («لم تُضف أحدًا بعد») · خطأ («تعذّر تحميل الأرصدة» + «أعد المحاولة») · عادي.
 * **وضع تعديل الصف** (§84 — `PeopleOrbit.kt`/`PeopleRowEdit.kt`): الخروج بـ«تم» أو ضغطة برّه الأشخاص (طبقة شفافة فوق الشاشة تحت المسرح) أو التمرير
 * أو تبديل التبويب (الحالة بـ`remember` ⇒ بتروح مع الشاشة) أو **زرار الرجوع** (`rememberChoice` — HANDOVER §6.5 بند 11).
 */
private const val RANGE = 420f
private const val ROW = 106f
private const val GAP = 8f
private const val HERO_TOP = 82f
private const val DRUM_TOP = 456f

/** طول تقريبي لكارت «لسه مفيش حد» (العنوان + السطرين + الزرار) — عشان يتحط فوق شريط السؤال والتنقل. */
private const val EMPTY_H = 200f

private sealed interface Cell {
    data class Person(val chip: PersonChip) : Cell
    data object Add : Cell
}

@Composable
internal fun PeopleScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val version = PeopleChanges.version
    val scope = rememberCoroutineScope()
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps) { mutableStateOf<Load<PeopleTabUi>>(Load.Loading) }
    var me by remember(deps) { mutableStateOf<app.masroufy.ui.app.MeInfo?>(null) }
    val editor = remember(deps) { RowEditor(scope) { deps.people.row.save(it) } }
    // وضع التعديل في الآلية الواحدة (HANDOVER §6.5 بند 11): زرار الرجوع بيخرج منه، والضغط برّه المسرح وشريط «تراجع» كمان
    // (الطبقة الشفافة تحت بتفضل تاكل الضغطة اللي على الشاشة نفسها — §84)
    val rowChoice = rememberChoice(editor.editing, onClose = editor::exit)
    LaunchedEffect(deps, version, retry) {
        val row = runCatching { deps.people.row.row() }.getOrNull()
        val full = loadOf { app.masroufy.perf.PerfTrace.span("screen:people") { peopleTabUi(deps.people.overview.forSpace(deps.shell.today(), deps.space.id), deps.space.id, deps.space.currency, row) } }
        load = if (full !is Load.Failed) full else loadOf { peopleWithoutBalances(deps.people.people.listWithBalances(), row) }.let { if (it is Load.Ready) it else full }
        (load as? Load.Ready)?.value?.let { editor.load(it.orbit.map { c -> c.id }, it.custom) }
        me = runCatching { deps.shell.me() }.getOrNull()
    }
    val pickable by produceState(emptyList<Id>(), deps, editor.ids, version) { value = runCatching { deps.people.row.pickable(editor.ids) }.getOrDefault(emptyList()) }
    val open = { p: PersonChip -> nav.push(PersonProfileRoute(p.id)) }
    val add = { nav.open(AddPersonSheetRoute) }
    val ui = (load as? Load.Ready)?.value?.withRow(editor.ids)
    val chips = remember(ui) { ui?.all.orEmpty().associateBy { it.id } }
    val empty = ui != null && ui.total == 0
    val failed = load is Load.Failed

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val viewDp = maxHeight.value
        val groups = ui?.let { cellsOf(it) } ?: emptyList()
        val fullDrum = groups.size * (ROW + GAP)
        val scrolls = ui != null && !empty
        // fix055: آخر صف في «الكل» كان لازق في شريط «اسأل مصروفي» ⇒ نفس مسافة القوايم التانية تحت (156) + شريط أزرار الجوال
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val extra = if (scrolls) allListExtra(top, fullDrum, viewDp, navInset) else 0f
        // بيرجع لمكانه (المسرح مطوي ولا مفرود) مع الرجوع وتبديل التبويب — §85
        val scroll = rememberEntryScrollState()
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
        // التمرير بيخرج من وضع التعديل (زي النموذج: أكتر من 8)
        LaunchedEffect(editor.editing) {
            if (!editor.editing) return@LaunchedEffect
            val start = scroll.value
            val limit = with(density) { 8.dp.toPx() }
            snapshotFlow { scroll.value }.first { abs(it - start) > limit }
            editor.exit()
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
                    // smoke 0.5.4: في «الكل» الكروت كانت بتعدّي تحت الهيدر الشفاف ⇒ الهيدر فوق كل حاجة وخلفيته بتبقى مصمتة مع التمرير
                    TabHeader(
                        t(UiKey.TAB_PEOPLE),
                        Modifier.zIndex(6f).background(Ink.background.copy(alpha = headerFill(p)))
                            .padding(start = Space.gutter, end = Space.gutter, top = (Space.gutter.value + top).dp),
                        actions = {
                            SecondaryButton(t(UiKey.PEOPLE_EVENTS), onClick = { nav.push(EventsRoute) })
                            if (scrolls) SecondaryButton(if (p > 0.5f) t(UiKey.PEOPLE_BACK) else t(UiKey.PEOPLE_ALL, app.masroufy.core.sentenceNumber(ui!!.total)), onClick = toggleAll)
                        },
                    )
                    if (load is Load.Loading) LoadingStage(top)
                    else {
                        val heroAlpha = max(0f, 1f - 2.2f * p)
                        PeopleStage(
                            chips, editor, pickable.isNotEmpty(), me?.displayName, open,
                            Modifier.zIndex(5f).padding(horizontal = Space.gutter).offset(y = (top + HERO_TOP).dp).choiceArea(rowChoice)
                                .graphicsLayer {
                                    translationY = -70f * p * density.density
                                    scaleX = 1f - 0.18f * p
                                    scaleY = 1f - 0.18f * p
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                    alpha = heroAlpha
                                }.height(STAGE_H.dp),
                            interactive = heroAlpha > 0.5f,
                            character = me?.character,
                            onBuild = { nav.push(app.masroufy.ui.screens.character.CharacterBuilderRoute) },
                        )
                    }
                    when {
                        empty -> {
                            // فوق شريط السؤال والتنقل دايمًا (المحاكي 2026-10-10: «إضافة شخص» كان متغطي بشريط السؤال في الشاشات القصيرة)
                            val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
                            val bars = (Space.navBottom + Space.navHeight + Space.askGap + Space.askHeight).value + inset + 12f
                            EmptyPeople(add, Modifier.offset(y = max(top + HERO_TOP, min(top + DRUM_TOP, viewDp - bars - EMPTY_H)).dp))
                        }
                        failed -> ErrorCard({ retry++ }, Modifier.padding(horizontal = Space.gutter).offset(y = (top + DRUM_TOP).dp), t(UiKey.PPL_LOAD_FAILED), "")
                        ui != null -> {
                            val drumTop = top + DRUM_TOP - 374f * p - over
                            val height = Drum(groups, p, reduce, open, add, Modifier.padding(horizontal = Space.gutter).offset(y = drumTop.dp))
                            val below = Modifier.padding(horizontal = Space.gutter).offset(y = (drumTop + height + 4f).dp).alpha(max(0f, 1f - 2.4f * p))
                            if (ui.balancesFailed) ErrorCard({ retry++ }, below)
                            else Boxes(ui, nav, below, enabled = p < 0.3f)
                        }
                    }
                    // ضغطة في أي حتة برّه الأشخاص بتخرج من وضع التعديل (طبقة شفافة فوق الشاشة وتحت المسرح — ما بتمنعش التمرير)
                    if (editor.editing) {
                        Box(
                            Modifier.fillMaxSize().zIndex(4f).pointerInput(editor) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    if (waitForUpOrCancellation() != null) editor.exit()
                                }
                            },
                        )
                    }
                }
            }
        }
        val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        RowUndoBar(
            editor,
            Modifier.align(Alignment.BottomCenter)
                .padding(start = Space.gutter, end = Space.gutter, bottom = Space.navBottom + Space.navHeight + Space.askGap + Space.askHeight + 14.dp + inset)
                .choiceArea(rowChoice),
        )
    }
    PeopleRowPick(editor, pickable.mapNotNull { chips[it] })
}

/** الصفوف: أقرب 5 (مطوي فوق — بيظهر في الصفحة الكاملة بس)، وبعده الباقيين 5 5، و«إضافة» في الآخر. */
private fun cellsOf(ui: PeopleTabUi): List<List<Cell>> {
    val rest = ui.rest.map { Cell.Person(it) } + Cell.Add
    return listOf(ui.orbit.map { Cell.Person(it) }) + rest.chunked(5)
}

/** خلفية هيدر «الأشخاص»: شفاف فوق المسرح (p = 0) ⇒ مصمت من ثلث الطريق لـ«الكل» (الكروت بتتحرك لفوق تحته). */
internal fun headerFill(p: Float): Float = (p * 3f).coerceIn(0f, 1f)

/** الطول الزيادة للتمرير في «الكل» (dp): آخر صف بيقف فوق [Space.tabContentBottom] + شريط أزرار الجوال [navInset] — زي باقي القوايم. */
internal fun allListExtra(top: Float, drum: Float, view: Float, navInset: Float): Float =
    max(0f, top + HERO_TOP + drum - (view - Space.tabContentBottom.value - navInset))

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
