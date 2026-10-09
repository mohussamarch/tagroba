package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «الاشتراكات والفواتير» (`Subscriptions`): مرشح مكتشف محتاج تأكيد (نعم ⇒ `ManageRecurring.save` · لا ⇒ يتشال) · المؤكدين بالترتيب
 * (الاسم · النوع والموعد · متأخرة/قريبة/قادمة/متوقف · المتوقع والدورة) ⇒ تفاصيل الاشتراك.
 */
@Composable
fun SubscriptionsScreen() {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = space.shell.today()
    val load = rememberLoad(deps) { deps.recurring.load(today) }
    DuesScaffold(t(TextKey.SUBS_TITLE)) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(90, 260) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val ui = subscriptionsUi(s.value, today, dismissed)
                if (ui.empty) {
                    item { EmptyState(t(TextKey.SUBS_EMPTY_TITLE), t(TextKey.SUBS_EMPTY_BODY)) }
                    return@DuesScaffold
                }
                ui.candidate?.let { c ->
                    item(key = "cand-${c.key}") {
                        CandidateCard(c, error, onYes = {
                            scope.launch {
                                try {
                                    deps.recurring.save(c.source.confirmInput())
                                    toaster.show(t(TextKey.SUBS_CAND_ADDED, c.name))
                                    error = null
                                    DuesChanges.bump()
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    error = e.message
                                }
                            }
                        }, onNo = { dismissed = dismissed + c.key; error = null })
                    }
                }
                if (ui.rows.isNotEmpty()) item(key = "rows") {
                    CardList {
                        ui.rows.forEachIndexed { i, r ->
                            if (i > 0) Divider()
                            SubRow(r) { nav.push(SubscriptionDetailRoute(r.itemId)) }
                        }
                    }
                }
                item(key = "note") { BasicText(t(TextKey.SUBS_NOTE), style = Type.caption().copy(color = Ink.muted)) }
            }
        }
    }
}

@Composable
private fun CandidateCard(c: CandidateUi, error: String?, onYes: () -> Unit, onNo: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(TextKey.SUBS_CAND_TITLE), style = Type.captionBold().copy(color = Ink.focus))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(c.name, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(c.why, style = Type.caption().copy(color = Ink.muted))
                }
                AmountText(c.amountMinor, c.currency)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(t(TextKey.SUBS_CAND_YES), onYes, Modifier.weight(2f), height = 44.dp)
                TonalButton(t(TextKey.DUES_NO), onNo, Modifier.weight(1f), height = 44.dp)
            }
            error?.let { FieldError(it) }
        }
    }
}

@Composable
private fun SubRow(r: SubRowUi, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).pressScale(press).tap(press, label = r.name, onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(r.name, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(r.sub, style = Type.caption().copy(color = Ink.muted), maxLines = 1)
            StatusChip(r.chipText, r.chip)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AmountText(r.amountMinor, r.currency, showCurrency = false)
            BasicText(r.cycle, style = Type.of(11).copy(color = Ink.muted))
        }
    }
}
