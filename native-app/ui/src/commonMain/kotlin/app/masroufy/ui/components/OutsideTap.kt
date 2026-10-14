package app.masroufy.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalFocusManager

/** سجل الاختيارات المفتوحة للشاشة كلها (`OutsideTapRoot` بيديه). `null` (اختبار · معاينة) ⇒ الآلية مش شغالة والشاشة عادية. */
val LocalOpenChoices = staticCompositionLocalOf<OpenChoices?> { null }

/** حارس الكلام المكتوب للوحة المفتوحة (`Sheet` بيديه لمحتواه) — `TextInput` بيعلّم عليه أول ما المالك يكتب. */
val LocalFormGuard = staticCompositionLocalOf<FormGuard?> { null }

/**
 * جذر الآلية (`AppShell` · `OverlayRoot`): بيتفرّج على **كل** لمسة في التطبيق قبل أي حد (`PointerEventPass.Initial`) ومن غير ما ياكلها:
 * ضغطة خلصت ⇒ [OpenChoices.tapped] (الاختيارات اللي برّاها بتتلغي + الكيبورد بيتقفل لو مش جوه خانة كتابة) · سحب ⇒ [OpenChoices.dragged].
 * الأماكن كلها بإحداثيات الشباك (`boundsInWindow`) — نفس اللي المناطق بتتسجل بيها.
 */
@Composable
fun OutsideTapRoot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val choices = remember { OpenChoices() }
    val focus = LocalFocusManager.current
    val here = remember { CoordinatesHolder() }
    CompositionLocalProvider(LocalOpenChoices provides choices) {
        Box(
            modifier
                .onGloballyPositioned { here.coordinates = it }
                .pointerInput(choices) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        choices.pressed()
                        val slop = viewConfiguration.touchSlop
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!dragging && choices.tapped(here.toWindow(change.position))) focus.clearFocus()
                                break
                            }
                            if (!dragging && (change.position - down.position).getDistance() > slop) {
                                dragging = true
                                choices.dragged(here.toWindow(down.position))
                            }
                        }
                    }
                },
            content = content,
        )
    }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null

    fun toWindow(local: Offset): Offset = coordinates?.takeIf { it.isAttached }?.localToWindow(local) ?: local
}

/** مكان العنصر على الشباك وقت السؤال (بعد أي حركة أو تمرير) — `null` لو اتشال. */
internal fun LayoutCoordinates.liveBounds(): () -> Rect? = { if (isAttached) boundsInWindow() else null }

/**
 * منطقة من مناطق الاختيار [choice] (اللوحة نفسها **و** الزرار اللي بيفتحها/يقفلها — عشان ضغطته ما تتحسبش «برّه» وتفتحه تاني).
 * `null` ⇒ ولا حاجة (الاختيار مقفول).
 */
fun Modifier.choiceArea(choice: OpenChoices.Choice?): Modifier =
    if (choice == null) this else this then AreaElement(AreaTarget.OfChoice(choice))

/** خانة كتابة: الضغط جواها ما بيقفلش الكيبورد (`TextInput` بيحطها لوحده · المحادثة بتحطها على سطر الكتابة كله بزراره). */
fun Modifier.keepsKeyboard(): Modifier = this then AreaElement(AreaTarget.Keeper)

private sealed interface AreaTarget {
    data class OfChoice(val choice: OpenChoices.Choice) : AreaTarget
    data object Keeper : AreaTarget
}

private data class AreaElement(val target: AreaTarget) : ModifierNodeElement<AreaNode>() {
    override fun create() = AreaNode(target)
    override fun update(node: AreaNode) = node.retarget(target)
}

private class AreaNode(var target: AreaTarget) : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    /** سجل خانات الكتابة (من الجذر) — بيتاخد لما العنصر يتركّب، عشان يتشال منه بعد ما يتفك. */
    private var keepers: OpenChoices? = null
    private var placed: LayoutCoordinates? = null

    override fun onAttach() {
        keepers = currentValueOf(LocalOpenChoices)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (placed === coordinates) return
        placed = coordinates
        register(coordinates.liveBounds())
    }

    fun retarget(next: AreaTarget) {
        if (next == target) return
        register(null)
        target = next
        placed?.let { register(it.liveBounds()) }
    }

    override fun onDetach() {
        register(null)
        placed = null
    }

    private fun register(bounds: (() -> Rect?)?) {
        when (val t = target) {
            is AreaTarget.OfChoice -> t.choice.owner.area(t.choice, this, bounds)
            AreaTarget.Keeper -> keepers?.keeper(this, bounds)
        }
    }
}
