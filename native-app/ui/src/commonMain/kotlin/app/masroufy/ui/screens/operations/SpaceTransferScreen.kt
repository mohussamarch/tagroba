package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.more.SpacesRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * تحويلاتك بين البلدين (`SpaceTransfer` — من «البلدان»): الأزواج (الطالعة من بلد والداخلة في التانية) — لا مصروف ولا دخل، وكل رجل بعملتها، والسعر
 * للعرض بس. «سجّل تحويلًا» (`recordNew` — الزوج ورجليه مع بعض أو ولا حاجة) · «فك الربط» (`unlink` — العمليتين ما بيتمسحوش).
 * الحالات: بيحمّل · خطأ · فاضي · غير متاح (بلد واحدة بس).
 * ⚠️ «اربط عمليتين» مقفول: مفيش حالة استخدام لقايمة العمليات المرشحة في البلدين لسه (`linkExisting` نفسها موجودة).
 */
@Composable
fun SpaceTransferScreen() {
    val space = LocalSpace.current
    val deps = space.operations
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var books by remember(deps) { mutableStateOf<List<SpaceWallets>?>(null) }
    var pairs by remember(deps) { mutableStateOf<List<PairView>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var unlinking by remember { mutableStateOf<PairView?>(null) }
    LaunchedEffect(deps, reload) {
        books = attempt { deps.spaceBooks() }
        val list = attempt { deps.spaceTransfers.list() }
        failed = list == null || books == null
        pairs = list?.let { pairViews(it, books.orEmpty().map { b -> b.space }) }
    }
    val b = books
    val p = pairs
    InnerScaffold(t(TextKey.SPACE_TRANSFER_SCREEN_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        when {
            failed -> item(key = "error") {
                ErrorBanner(t(TextKey.SPACE_TRANSFER_SCREEN_ERROR), t(TextKey.OPERATIONS_ERROR_BODY), t(TextKey.SHELL_RETRY), { reload++ })
            }
            b == null || p == null -> item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(2) { Skeleton(Modifier.fillMaxWidth().height(170.dp)) } }
            }
            b.size < 2 -> item(key = "na") {
                FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_NA_TITLE), style = Type.of(15, FontWeight.Bold))
                    BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_NA_BODY), Modifier.padding(vertical = 8.dp), style = Type.of(13).copy(color = Ink.muted))
                    TonalButton(t(TextKey.SPACE_TRANSFER_SCREEN_ADD_SPACE), { nav.push(SpacesRoute) }, Modifier.fillMaxWidth())
                }
            }
            else -> {
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(t(TextKey.SPACE_TRANSFER_SCREEN_NEW), onClick = { adding = true }, modifier = Modifier.weight(1f))
                            TonalButton(t(TextKey.SPACE_TRANSFER_SCREEN_LINK), {}, Modifier.weight(1f), enabled = false)
                        }
                        BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_LINK_SOON), style = Type.caption().copy(color = Ink.muted))
                    }
                }
                if (p.isEmpty()) item(key = "empty") { EmptyState(t(TextKey.SPACE_TRANSFER_SCREEN_EMPTY), t(TextKey.SPACE_TRANSFER_SCREEN_EMPTY_BODY)) }
                for (pair in p) item(key = "pair-${pair.id}") { PairCard(pair) { unlinking = pair } }
                if (p.isNotEmpty()) item(key = "lock") { BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_LOCK_NOTE), Modifier.padding(horizontal = 4.dp), style = Type.of(12).copy(color = Ink.muted)) }
            }
        }
    }
    NewSpaceTransferSheet(adding, b.orEmpty(), space.space.id, onDismiss = { adding = false }) {
        adding = false
        toaster.show(t(TextKey.SPACE_TRANSFER_SCREEN_SAVED))
        reload++
    }
    UnlinkSheet(unlinking, onDismiss = { unlinking = null }) { id ->
        scope.launch {
            val err = failureOf { deps.spaceTransfers.unlink(id) }
            toaster.show(err ?: t(TextKey.SPACE_TRANSFER_SCREEN_UNLINKED))
            unlinking = null
            reload++
        }
    }
}

/** كارت الزوج: «من السعودية إلى مصر» + «لا مصروف ولا دخل» · الرجلين · السعر · ملاحظتك · «فك الربط». */
@Composable
private fun PairCard(p: PairView, onUnlink: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(p.route, style = Type.of(15, FontWeight.Bold))
                StatusChip(t(TextKey.SPACE_TRANSFER_SCREEN_NOT_SPEND), ChipInk.blue, ChipInk.blueBg)
            }
            for (leg in p.legs) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(ChipInk.blueBg), contentAlignment = Alignment.Center) {
                    BasicText(leg.mark, style = Type.of(14, FontWeight.Bold).copy(color = Ink.transfer))
                }
                BasicText(leg.title, Modifier.weight(1f), style = Type.of(14, FontWeight.Bold))
                AmountText(leg.amountMinor, leg.currency, tone = if (leg.out) AmountTone.EXPENSE else AmountTone.INCOME, color = Ink.transfer)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0A193D33)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_RATE), style = Type.caption().copy(color = Ink.muted))
                BasicText(p.rate, style = Type.of(13, FontWeight.Bold))
            }
            p.note?.let { BasicText(it, style = Type.caption().copy(color = Ink.muted)) }
            TonalButton(t(TextKey.SPACE_TRANSFER_SCREEN_UNLINK), onUnlink, height = 44.dp)
        }
    }
}

/** «فك ربط التحويل؟» — العمليتين ما بيتمسحوش، ونوع كل واحدة بيرجع «غير محدد» وتظهر في المراجعات. */
@Composable
private fun UnlinkSheet(pair: PairView?, onDismiss: () -> Unit, onConfirm: (Id) -> Unit) {
    Sheet(pair != null, onDismiss, title = t(TextKey.SPACE_TRANSFER_SCREEN_UNLINK_TITLE), closeLabel = t(TextKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_UNLINK_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(TextKey.SPACE_TRANSFER_SCREEN_UNLINK_BODY), style = Type.of(13).copy(color = Ink.muted))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(TextKey.SPACE_TRANSFER_SCREEN_UNLINK), onClick = { pair?.let { onConfirm(it.id) } }, modifier = Modifier.weight(1.4f))
            TonalButton(t(TextKey.SPACE_TRANSFER_SCREEN_CANCEL), onDismiss, Modifier.weight(1f), muted = true)
        }
    }
}

/** خطأ ظاهر جنب الزرار (مستخدم في لوحة التحويل الجديد). */
@Composable
internal fun SheetError(text: String?) {
    if (text != null) FieldError(text)
}
