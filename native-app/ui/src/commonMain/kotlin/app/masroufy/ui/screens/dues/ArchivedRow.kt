package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ArchivedItem
import app.masroufy.usecase.EntityArchiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * r06-archive (OVERRIDES §118-ب-1): سطر «المؤرشف (N)» صغير تحت القايمة — نفس فكرة «الحسابات المخفية» (مجموعة لوحدها تحت، وكل واحد
 * فيها ليه زرار يرجّعه). الدوسة بتفتح الأسامي، و«رجّعه» بينادي [EntityArchiver.setArchived] ⇒ رسالة ⇒ [onChanged] (القايمة بتتقري تاني).
 * مفيش مؤرشف أو مش متوصل ⇒ مفيش سطر. الشاشة ما بتكتبش ولا بتحسب حاجة.
 */
internal fun LazyListScope.archivedSection(items: List<ArchivedItem>, archiver: EntityArchiver?, onChanged: () -> Unit) {
    if (items.isEmpty() || archiver == null) return
    item(key = "archived") { ArchivedRow(items, archiver, onChanged) }
}

@Composable
internal fun ArchivedRow(items: List<ArchivedItem>, archiver: EntityArchiver?, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty() || archiver == null) return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val title = t(R06Key.DEL_ARCHIVED_ROW, "${items.size}")
    FloatingCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp), onClick = { open = !open }, clickLabel = title) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(title, style = Type.caption().copy(color = Ink.muted))
            if (open) for (a in items) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(a.name, style = Type.of(14), modifier = Modifier.weight(1f))
                    TonalButton(t(R06Key.DEL_UNARCHIVE), {
                        scope.launch {
                            try {
                                val done = archiver.setArchived(a.id, false)
                                toaster.show(t(R06Key.DEL_UNARCHIVED, done?.name ?: a.name))
                                onChanged()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                toaster.show(e.message ?: t(UiKey.SHELL_LOAD_FAILED))
                            }
                        }
                    }, height = 36.dp, muted = true)
                }
            }
        }
    }
}
