package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.port.SavedBackupFile
import kotlinx.coroutines.launch

/** الحجم بالكيلوبايت (تقريب لفوق) — عرض بس. */
internal fun backupSizeLabel(bytes: Long): String = ((bytes + 1023) / 1024).coerceAtLeast(1).toString()

/**
 * r06-links2 (§118-ج): «النسخ المحفوظة على الجوال» — نسخ ما قبل الصيانة في المجلد الخاص (`noBackupFilesDir/backups`). كل نسخة
 * بـ«امسح» وتأكيد صريح إن **المسح مالوش تراجع** (ملف). `ManageSavedBackups` — الشاشة ما بتلمسش ملفات.
 */
@Composable
internal fun SavedBackupsCard() {
    val manage = LocalSpace.current.shell.savedBackups ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<SavedBackupFile>?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { files = runCatching { manage.list() }.getOrDefault(emptyList()) }
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(R06Key.L2_BK_TITLE), style = Type.of(14, FontWeight.Bold))
            val list = files.orEmpty()
            if (files != null && list.isEmpty()) BasicText(t(R06Key.L2_BK_EMPTY), style = Type.caption().copy(color = Ink.muted))
            list.forEach { f ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(f.name, style = Type.of(12, FontWeight.Bold), maxLines = 1)
                        BasicText(t(R06Key.L2_BK_SIZE, backupSizeLabel(f.bytes)), style = Type.caption().copy(color = Ink.muted))
                    }
                    TonalButton(t(R06Key.DEL_ACTION), { asking = f.name }, height = 40.dp, muted = true)
                }
                if (asking == f.name) WarnBox(t(R06Key.L2_BK_CONFIRM_TITLE), t(R06Key.L2_BK_CONFIRM_BODY)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalButton(t(R06Key.DEL_CONFIRM), {
                            scope.launch {
                                val ok = runCatching { manage.delete(f.name) }.getOrDefault(false)
                                toaster.show(t(if (ok) R06Key.L2_BK_DONE else R06Key.L2_BK_FAILED))
                                asking = null
                                tick++
                            }
                        }, Modifier.weight(1f))
                        TonalButton(t(R06Key.L2_BK_KEEP), { asking = null }, Modifier.weight(1f), muted = true)
                    }
                }
            }
        }
    }
}
