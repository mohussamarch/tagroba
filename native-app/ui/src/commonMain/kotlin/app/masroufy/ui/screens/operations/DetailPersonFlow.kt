package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ManagePersonLinks
import app.masroufy.usecase.TxnPersonStatus

/**
 * «الشخص» في تفاصيل العملية (جولة r05): حالة الربط (`ManagePersonLinks.statusOf` — بعد «سجّل تحويلاته لوحده» للعملية دي) · لوحة «مع مين؟» ·
 * شريط «تراجع» 8 ثواني بعد الربط · بانر «محتاجة تربطها بشخص» لو التصنيف معناه فلوس مع شخص ومفيش ربط.
 */
@Stable
class DetailPersonFlow {
    var status by mutableStateOf<TxnPersonStatus?>(null)
    var target by mutableStateOf<MeaningTarget?>(null)
    var undo by mutableStateOf<PersonLinked?>(null)

    fun ask(v: DetailView) {
        target = MeaningTarget(v.id, v.transaction.observedDirection, v.title, v.amountMinor, v.currency)
    }

    /** بعد ما التصنيف يتحفظ: لو بقى «فلوس مع شخص» والعملية مش مربوطة ⇒ «مع مين؟» على طول (طريق المالك نفسه). */
    suspend fun afterCategory(links: ManagePersonLinks?, v: DetailView) {
        val s = links?.let { attempt { it.statusOf(v.id) } } ?: return
        status = s
        if (s.needsPerson) ask(v)
    }
}

@Composable
internal fun rememberDetailPersonFlow(transactionId: Id, reload: Int): DetailPersonFlow {
    val deps = LocalSpace.current.operations
    val flow = remember(transactionId) { DetailPersonFlow() }
    LaunchedEffect(deps, transactionId, reload) {
        val links = deps.personLinks ?: return@LaunchedEffect
        attempt { links.accounts.applyAuto(listOf(transactionId)) }
        flow.status = attempt { links.statusOf(transactionId) }
    }
    return flow
}

/** شريط «تراجع» بعد الربط · بانر «محتاجة تربطها بشخص». */
internal fun LazyListScope.personFlowItems(flow: DetailPersonFlow, v: DetailView, window: UndoWindow?, onUndo: (PersonLinked) -> Unit) {
    flow.undo?.let { u -> item(key = "person-undo") { UndoStrip(u.text, window) { onUndo(u) } } }
    if (flow.status?.needsPerson == true && flow.undo == null) item(key = "needs-person") {
        NeedsPersonBanner(v.category?.name.orEmpty()) { flow.ask(v) }
    }
}

@Composable
private fun NeedsPersonBanner(category: String, onLink: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Color(0x14C7861F)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(Lucide.USERS, size = 18.dp, tint = Ink.focus)
            BasicText(t(UiKey.PLINK_NEEDS_TITLE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
        }
        BasicText(t(UiKey.PLINK_NEEDS_BODY, category), style = Type.of(13))
        PrimaryButton(t(UiKey.PLINK_NEEDS_ACTION), onClick = onLink, modifier = Modifier.fillMaxWidth())
    }
}
