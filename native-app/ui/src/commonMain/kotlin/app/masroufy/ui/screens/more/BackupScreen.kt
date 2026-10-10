package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.periodForDate
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.countryLabel
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * النسخة الاحتياطية والتصدير (`Backup` — spec/03 · §12 · §41.1 · §49): **النسخة الشاملة** ملف واحد للحساب كله (`FullBackup.create`) بيتحفظ
 * بنافذة النظام · **الاسترجاع بالدمج** (ملف ⇐ `RestorePreview`، ومفيش حرف بيتكتب قبل التأكيد) · **تصدير العمليات** CSV من تاريخ لتاريخ بالظبط
 * (`ExportCsv`). المحفوظ اتأكد بعدد البايتات اللي اتكتبت (زي `saveVerifiedBackup`).
 */
@Composable
fun BackupScreen() {
    val deps = LocalSpace.current
    val more = deps.more
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = deps.shell.today()
    var payday by remember(deps) { mutableIntStateOf(28) }
    var making by remember { mutableStateOf(false) }
    var made by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var from by remember(deps) { mutableStateOf(today) }
    var to by remember(deps) { mutableStateOf(today) }
    var exporting by remember { mutableStateOf(false) }
    var expected by remember { mutableStateOf(0L) }
    LaunchedEffect(deps) {
        payday = runCatching { more.profile.load().payday }.getOrDefault(payday)
        from = periodForDate(today, payday).start
    }
    val saveJson = rememberFileSaver("application/json") { r ->
        making = false
        when (r) {
            is FileSaveResult.Saved -> if (r.bytes == expected) { made = true; backupError = null; toaster.show(t(UiKey.BAK_SAVED), dark = true) } else backupError = t(UiKey.BAK_PARTIAL)
            is FileSaveResult.Failed -> backupError = t(UiKey.BAK_FAILED)
            FileSaveResult.Unavailable -> backupError = t(UiKey.BAK_NO_FILES)
            FileSaveResult.Cancelled -> Unit
        }
    }
    val saveCsv = rememberFileSaver("text/csv") { r ->
        exporting = false
        when (r) {
            is FileSaveResult.Saved -> toaster.show(t(UiKey.BAK_EXPORTED, exportFileName(from, to)), dark = true)
            is FileSaveResult.Failed -> toaster.show(t(UiKey.BAK_FAILED), dark = true)
            FileSaveResult.Unavailable -> toaster.show(t(UiKey.BAK_NO_FILES), dark = true)
            FileSaveResult.Cancelled -> Unit
        }
    }
    val pick = rememberFilePicker { r ->
        when (r) {
            is FilePickResult.Picked -> nav.push(RestorePreviewRoute(r.name, r.text))
            is FilePickResult.Failed -> toaster.show(t(UiKey.BAK_PICK_FAILED), dark = true)
            FilePickResult.Unavailable -> toaster.show(t(UiKey.BAK_NO_FILES), dark = true)
            FilePickResult.Cancelled -> Unit
        }
    }
    val backup = more.fullBackup
    InnerScaffold(t(UiKey.BAK_TITLE)) {
        item(key = "full") {
            BackupCard(MoreIcons.DATABASE_BACKUP, t(UiKey.BAK_FULL_TITLE), t(UiKey.BAK_FULL_BODY)) {
                if (made) BasicText(t(UiKey.BAK_LAST_NOW), style = Type.of(13, FontWeight.Bold).copy(color = Ink.income))
                backupError?.let { WarnBox(null, it) }
                if (backup == null) NotYetLine(t(UiKey.BAK_NOT_WIRED))
                PrimaryButton(
                    t(
                        when {
                            making -> UiKey.BAK_MAKING
                            backupError != null -> UiKey.MORE_RETRY
                            made -> UiKey.BAK_MAKE_ANOTHER
                            else -> UiKey.BAK_MAKE
                        },
                    ),
                    onClick = {
                        val b = backup ?: return@PrimaryButton
                        making = true
                        backupError = null
                        scope.launch {
                            runCatching { b.create(more.nowIso()).toJsonText() }
                                .onSuccess { text -> expected = text.encodeToByteArray().size.toLong(); saveJson(backupFileName(today), text) }
                                .onFailure { making = false; backupError = it.message ?: t(UiKey.BAK_FAILED) }
                        }
                    },
                    enabled = backup != null, loading = making, modifier = Modifier.fillMaxWidth(),
                )
                BasicText(t(UiKey.BAK_SCOPE_NOTE), style = Type.caption().copy(color = Ink.muted))
            }
        }
        item(key = "restore") {
            BackupCard(Lucide.CLOUD_UPLOAD, t(UiKey.BAK_RESTORE_TITLE), t(UiKey.BAK_RESTORE_BODY)) {
                TonalButton(t(UiKey.BAK_PICK), onClick = pick, enabled = backup != null, modifier = Modifier.fillMaxWidth())
            }
        }
        item(key = "csv") {
            BackupCard(MoreIcons.TABLE, t(UiKey.BAK_CSV_TITLE), t(UiKey.BAK_CSV_BODY, countryLabel(deps.space))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(from, { from = it.trim() }, Modifier.weight(1f), label = t(UiKey.BAK_FROM), ltr = true)
                    TextInput(to, { to = it.trim() }, Modifier.weight(1f), label = t(UiKey.BAK_TO), ltr = true)
                }
                val range = exportRange(from, to, today)
                if (range.ok) BasicText(range.line, style = Type.of(12, FontWeight.Bold).copy(color = Ink.muted)) else FieldError(range.line)
                TonalButton(
                    t(UiKey.BAK_EXPORT),
                    onClick = {
                        exporting = true
                        scope.launch {
                            runCatching { more.exportCsv.export(from, to, payday) }
                                .onSuccess { saveCsv(exportFileName(from, to), it) }
                                .onFailure { exporting = false; toaster.show(it.message ?: t(UiKey.BAK_FAILED), dark = true) }
                        }
                    },
                    enabled = range.ok && !exporting, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BackupCard(icon: Lucide, title: String, body: String, content: @Composable () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Ink.primary, size = 40.dp, radius = 13.dp) { LucideIcon(icon, size = 20.dp, tint = Ink.primary) }
                BasicText(title, Modifier.weight(1f), style = Type.of(16, FontWeight.Bold))
            }
            BasicText(body, style = Type.of(13).copy(color = Ink.muted))
            content()
        }
    }
}
