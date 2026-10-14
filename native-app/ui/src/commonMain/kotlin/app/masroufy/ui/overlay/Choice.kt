package app.masroufy.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.masroufy.ui.components.LocalOpenChoices
import app.masroufy.ui.components.OpenChoices

/**
 * اختيار مؤقت مفتوح على الشاشة (شبكة تاريخ · شرايح اختيار · وضع تعديل · تأكيد صغير — HANDOVER §6.5 بند 11): طول ما [open] ⇒
 * متسجّل في الآلية الواحدة (`OpenChoices` · `OutsideTapRoot`):
 * - ضغطة برّه مناطقه (`Modifier.choiceArea(choice)` على اللوحة **وعلى الزرار اللي فتحها**) ⇒ [onClose].
 * - **زرار الرجوع** ⇒ [onClose] قبل ما يرجع للشاشة اللي قبلها (خانة رجوع فاضية في طبقة اللوحات — نفس ترتيب اللوحات: اللي جوه لوحة بيتقفل قبلها).
 * - [closeOnScroll] ⇒ السحب اللي بيبدأ برّاه كمان بيقفله (أوضاع التعديل) — اللوحات اللي جوه المحتوى لأ.
 * [onClose] **ما بيحفظش حاجة**: بيقفل ويرجّع أي قيمة ما اتأكدتش. بيرجّع `null` وهو مقفول (`choiceArea(null)` ما بتعملش حاجة).
 */
@Composable
fun rememberChoice(open: Boolean, onClose: () -> Unit, closeOnScroll: Boolean = false): OpenChoices.Choice? {
    val registry = LocalOpenChoices.current
    val host = LocalOverlayHost.current
    val close by rememberUpdatedState(onClose)
    if (!open) return null
    val slot = remember(registry, host, closeOnScroll) { ChoiceSlot((registry ?: OpenChoices()).prepare({ close() }, closeOnScroll), host) }
    return slot.choice
}

/** بيسجّل الاختيار لما يترسم فعلًا، ويشيله (هو وخانة الرجوع بتاعته) لما يتقفل أو الشاشة تتشال. */
internal class ChoiceSlot(val choice: OpenChoices.Choice, private val host: OverlayHost?) : RememberObserver {
    private var entry: OverlayHost.Entry? = null

    override fun onRemembered() {
        val owner = choice.owner
        owner.register(choice)
        val h = host ?: return
        val e = h.newEntry().also { it.onBack = choice.onClose }
        entry = e
        owner.backEntries += e
        h.entries += e
        // لوحة حقيقية اتفتحت بعده (مش خانة رجوع لاختيار تاني) ⇒ الضغط جواها مش «برّه»
        choice.covered = {
            val i = h.entries.indexOf(e)
            i >= 0 && (i + 1 until h.entries.size).any { h.entries[it] !in owner.backEntries }
        }
    }

    override fun onForgotten() = release()

    override fun onAbandoned() = release()

    private fun release() {
        val owner = choice.owner
        owner.closed(choice)
        entry?.let { e ->
            host?.entries?.remove(e)
            owner.backEntries -= e
        }
        entry = null
    }
}
