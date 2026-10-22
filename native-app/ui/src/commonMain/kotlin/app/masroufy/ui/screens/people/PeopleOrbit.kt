package app.masroufy.ui.screens.people

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.runtime.State
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.masroufy.core.Id
import app.masroufy.core.PEOPLE_ROW_MAX
import app.masroufy.core.UiKey
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.MotionMs
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import kotlin.math.abs
import kotlin.math.hypot

/**
 * صف «أهم 5» فوق شخصيتك + **وضع التعديل** (OVERRIDES §84 — لوحة `PeopleOrbit` في النموذج، KOTLIN-MAP «وضع تعديل صف «أهم 5»»):
 * - **ضغطة طويلة 440** على أي شخص (وتتلغي لو الصباع اتحرك أكتر من 9) ⇒ وضع التعديل، وتقدر تسحب على طول من غير ما ترفع. الضغطة القصيرة بتفتح ملفه.
 * - السحب: الشخص بيكبر 1.08 ويمشي مع الصباع، وأقرب مكان بيبقى مكانه والباقي بيتزق بالنابض السريع. بيتحفظ لما ترفع صباعك.
 * - الاهتزاز `±2°` كل 260 ذهاب وعودة، وكل واحد متأخر 90 عن اللي قبله · «×» دايرة 22 (لمسها 40) · «تقليل الحركة» ⇒ من غير اهتزاز ولا انزلاق.
 * الأماكن ([orbitCenter]) بالـdp **من شمال المسرح فعليًا** (زي النموذج في العربي، وبالمراية في الإنجليزي) — نفس `PeopleStage`.
 */
private const val ITEM_W = 64f
private const val ITEM_TOP = 48f

/** مركز المكان [i] في المسرح (dp من الشمال الفعلي) لعرض [w]. */
internal fun orbitCenter(i: Int, w: Float, rtl: Boolean): Offset {
    val (x, y) = SPOTS[i.coerceIn(0, SPOTS.lastIndex)]
    val k = w / STAGE_W
    return Offset(if (rtl) x * k else w - x * k, y)
}

/** أي شخص من الأماكن الـ[count] الأولى تحت الصباع (الاسم والدايرة — 64 عرض)، أو -1. */
internal fun orbitHit(p: Offset, count: Int, w: Float, rtl: Boolean): Int = (0 until count.coerceAtMost(SPOTS.size)).firstOrNull { i ->
    val c = orbitCenter(i, w, rtl)
    abs(p.x - c.x) <= ITEM_W / 2 && p.y >= c.y - ITEM_TOP && p.y <= c.y + 30f
} ?: -1

/** أقرب مكان من الأماكن الـ[count] الأولى لـ[p] (مكان الشخص المرفوع الجديد). */
internal fun orbitNearest(p: Offset, count: Int, w: Float, rtl: Boolean): Int =
    (0 until count.coerceIn(1, SPOTS.size)).minBy { i -> orbitCenter(i, w, rtl).let { c -> hypot(p.x - c.x, p.y - c.y) } }

/** الصباع رفع قبل 440 من غير ما يتحرك = ضغطة (true) · اتحرك أكتر من [slop] أو التمرير خده = اتلغت (false). */
private suspend fun AwaitPointerEventScope.tapOrCancel(down: PointerInputChange, slop: Float): Boolean {
    while (true) {
        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return false
        if (ch.changedToUpIgnoreConsumed()) return !ch.isConsumed
        if (ch.isConsumed || (ch.position - down.position).getDistance() > slop) return false
    }
}

/**
 * الضغطة بدأت على «×» (لمسها 40 وبيتمدّ لـ48 فبيغطي ربع الدايرة): رفع من غير حركة = «×» هي اللي بتتصرف (false) ·
 * اتحرك أكتر من [slop] = سحب للشخص (true) — والحركة دي بتتاكل عشان «×» تتلغي والصفحة ما تتمررش.
 */
private suspend fun AwaitPointerEventScope.dragsPast(down: PointerInputChange, slop: Float): Boolean {
    while (true) {
        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return false
        if (ch.changedToUpIgnoreConsumed() || ch.isConsumed) return false
        if ((ch.position - down.position).getDistance() > slop) return true.also { ch.consume() }
    }
}

/**
 * لمس المسرح كله (مش كل شخص لوحده — عشان الصباع يفضل ماسك الشخص وهو بيتحرك): ضغطة على شخص تفتحه · ضغطة طويلة تدخل وضع التعديل
 * وتسحب على طول · في وضع التعديل الضغط على شخص بيسحبه على طول، والضغط في حتة فاضية جوه الكارت بيخرج. «×» و«تم» و«+» ليهم ضغطتهم — بس السحب اللي بدأ على «×» بيسحب الشخص.
 */
internal fun Modifier.orbitGestures(editor: RowEditor, w: Float, rtl: Boolean, onOpen: (Id) -> Unit, onEnter: () -> Unit): Modifier =
    pointerInput(editor, w, rtl) {
        val slop = MotionMs.holdCancelPx.dp.toPx()
        // نفس مسافة التمرير ⇒ السحب من على «×» بياخد الحركة قبل الصفحة
        val buttonSlop = minOf(slop, viewConfiguration.touchSlop)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // «×» و«تم» و«+» بياخدوا الضغطة (combinedClickable) — اللمس هنا بيسيبها لهم إلا لو بقت سحب لشخص
            val byButton = down.isConsumed
            val shown = editor.shown
            val hit = orbitHit(Offset(down.position.x.toDp().value, down.position.y.toDp().value), shown.size, w, rtl)
            if (hit < 0) {
                if (!byButton && editor.editing && waitForUpOrCancellation() != null) editor.exit()
                return@awaitEachGesture
            }
            val id = shown[hit]
            if (byButton && (!editor.editing || !dragsPast(down, buttonSlop))) return@awaitEachGesture
            if (!editor.editing) {
                when (withTimeoutOrNull(MotionMs.holdThreshold) { tapOrCancel(down, slop) }) {
                    true -> return@awaitEachGesture onOpen(id)
                    false -> return@awaitEachGesture
                    null -> onEnter()
                }
            }
            editor.lift(id, orbitCenter(hit, w, rtl))
            var moved = false
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (ch == null) {
                    editor.drop(commit = false)
                    break
                }
                if (ch.changedToUpIgnoreConsumed()) {
                    editor.drop(commit = moved)
                    break
                }
                val delta = Offset((ch.position.x - down.position.x).toDp().value, (ch.position.y - down.position.y).toDp().value)
                if (delta.getDistance() > 6f) moved = true
                editor.dragTo(delta, orbitNearest(editor.liftOrigin + delta, editor.shown.size, w, rtl))
                ch.consume()
            }
        }
    }

/** الأشخاص في أماكنهم (بترتيب [RowEditor.shown]) — كل واحد بمفتاحه عشان الحركة تبقى ليه هو لما الترتيب يتغير. */
@Composable
internal fun BoxScope.OrbitPeople(chips: Map<Id, PersonChip>, editor: RowEditor, w: Float, rtl: Boolean, onOpen: (PersonChip) -> Unit) {
    val reduce = LocalReduceMotion.current
    val shown = editor.shown
    // التأخير بترتيب ثابت (بالاسم — زي النموذج) عشان الاهتزاز ما يتلخبطش وإنت بتسحب
    val stable = shown.sortedBy { chips[it]?.name.orEmpty() }
    shown.forEachIndexed { k, id ->
        val chip = chips[id] ?: return@forEachIndexed
        key(id) {
            val lifted = editor.lifted == id
            val center = if (lifted) editor.liftOrigin + editor.liftDelta else orbitCenter(k, w, rtl)
            OrbitItem(chip, center, lifted, editor.editing, stable.indexOf(id), reduce, { onOpen(chip) }) { editor.remove(id, chip.name) }
        }
    }
}

@Composable
private fun BoxScope.OrbitItem(
    p: PersonChip, center: Offset, lifted: Boolean, editing: Boolean, stagger: Int, reduce: Boolean, onOpen: () -> Unit, onRemove: () -> Unit,
) {
    val target = Offset(center.x - ITEM_W / 2, center.y - ITEM_TOP)
    val pos = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target, lifted, reduce) { if (lifted || reduce) pos.snapTo(target) else pos.animateTo(target, Springs.SNAPPY.spec()) }
    val scale by animateFloatAsState(if (lifted && !reduce) 1.08f else 1f, Springs.SNAPPY.spec(), label = "lift")
    val wig = if (editing && !lifted && !reduce) wiggle(stagger) else null
    val label = if (editing) t(UiKey.PEOPLE_ROW_DRAG, p.name) else p.name
    Column(
        Modifier.align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset(pos.value.x.dp.roundToPx(), pos.value.y.dp.roundToPx()) }
            .zIndex(if (lifted) 3f else 2f)
            .width(ITEM_W.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (!editing) onClick { onOpen(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(p.name, style = Type.of(12, FontWeight.Bold).copy(color = Ink.onPrimary, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.graphicsLayer { rotationZ = wig?.value ?: 0f; scaleX = scale; scaleY = scale }) {
            InitialCircle(p.name, 56.dp, onHero = true)
            // «×» فوق يمين الدايرة في العربي (`right: -14; top: -14`) — بيشيله من الصف بس
            if (editing && !lifted) RemoveX(t(UiKey.PEOPLE_ROW_REMOVE, p.name), Modifier.align(Alignment.TopStart).offset(x = (-14).dp, y = (-14).dp), onRemove)
        }
    }
}

/** `rotate(±2deg)` كل 260 ذهاب وعودة بمنحنى `cubic-bezier(.45,0,.55,1)`، وكل واحد متقدّم 90 عن اللي قبله (`animation-delay: -90n`). */
@Composable
private fun wiggle(stagger: Int): State<Float> {
    val t = rememberInfiniteTransition(label = "wiggle")
    return t.animateFloat(
        -2f, 2f,
        InfiniteRepeatableSpec(tween(260, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)), RepeatMode.Reverse, StartOffset(90 * stagger, StartOffsetType.FastForward)),
        label = "wig",
    )
}

/** «×»: دايرة 22 لونها `rgba(25,61,51,0.92)` بحلقة 1.5 `#FFFDF9`، ومساحة لمسها 40. */
@Composable
private fun RemoveX(label: String, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    Box(modifier.size(40.dp).tap(press, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(25.dp).clip(CircleShape).background(Ink.surface), contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Ink.tint(0xEB)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(12.dp)) {
                    val a = size.width * 0.25f
                    val b = size.width * 0.75f
                    val s = 1.5.dp.toPx()
                    drawLine(Color.White, Offset(a, a), Offset(b, b), s, StrokeCap.Round)
                    drawLine(Color.White, Offset(b, a), Offset(a, b), s, StrokeCap.Round)
                }
            }
        }
    }
}

/** «تم»: فوق شمال الكارت في العربي — حبّاية 32 بيضا مكتوبة بالأخضر، ومساحة لمسها 48. */
@Composable
internal fun BoxScope.RowDone(onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 4.dp).height(48.dp).zIndex(4f).pressScale(press)
            .tap(press, label = t(UiKey.PEOPLE_ROW_END_EDIT), onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Ink.surface).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            BasicText(t(UiKey.PEOPLE_ROW_DONE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
        }
    }
}

/** «+» دايرة متقطعة مكتوب جنبها «إضافة» في **أول مكان فاضي** (وضع التعديل بس، والصف أقل من 5) ⇒ «أضف للصف». */
@Composable
internal fun BoxScope.RowAddSpot(index: Int, w: Float, rtl: Boolean, onClick: () -> Unit) {
    if (index >= PEOPLE_ROW_MAX) return
    val c = orbitCenter(index, w, rtl)
    val press = rememberPress()
    Column(
        Modifier.align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset((c.x - ITEM_W / 2).dp.roundToPx(), (c.y - ITEM_TOP).dp.roundToPx()) }
            .zIndex(2f).width(ITEM_W.dp).pressScale(press).tap(press, label = t(UiKey.PEOPLE_ROW_ADD_LABEL), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(t(UiKey.PEOPLE_ROW_ADD), style = Type.of(12, FontWeight.Bold).copy(color = Ink.onHeroMuted))
        val ring = Ink.onHeroMuted.copy(alpha = 0.75f)
        Box(
            Modifier.size(56.dp).drawBehind {
                val stroke = 1.5.dp.toPx()
                drawCircle(ring, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            },
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.PLUS, size = 22.dp, tint = Ink.onHeroMuted) }
    }
}

