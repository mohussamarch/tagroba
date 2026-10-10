package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.countryLabel
import app.masroufy.core.countryPack
import app.masroufy.core.currencyName
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.LensOnLight
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.countryMark
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «البلدان» (`Spaces` — §41 · §64): لكل بلد حساب منفصل. الشغالة (والحالية عليها «الحالية») · المؤرشفة · «أضف بلدًا» · التحويل لنفسك ·
 * المشترك بين البلدان. التبديل على الجهاز بس (`ShellDeps.switchSpace`). الإنشاء والأرشفة نقطة ربط ([SpacesAdmin] — `ManageSpaces`
 * مش متجمّع على الجوال) ⇒ الزرار بيقول «غير متاح بعد» بدل ما يعمل نص بلد.
 */
@Composable
fun SpacesScreen() {
    val deps = LocalSpace.current
    val admin = deps.more.spacesAdmin
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var open by remember(deps) { mutableStateOf<List<SpaceCard>?>(null) }
    var archived by remember(deps) { mutableStateOf<List<Space>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SpaceSheet?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        open = runCatching { deps.more.spaces() }.getOrNull() ?: emptyList()
        archived = runCatching { admin?.archived() }.getOrNull().orEmpty()
    }

    fun act(block: suspend () -> String?) {
        scope.launch {
            val done = runCatching { block() }
            failed = done.isFailure
            sheet = null
            done.getOrNull()?.let { toaster.show(it, dark = true) }
            tick++
        }
    }

    InnerScaffold(t(TextKey.SPC_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.SPC_INTRO), Modifier.padding(horizontal = 4.dp), style = Type.of(13).copy(color = Ink.muted)) }
        if (failed) item(key = "err") { WarnBox(t(TextKey.SPC_ERR_TITLE), t(TextKey.SPC_ERR_BODY)) }
        val list = open
        if (list == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(260.dp)) }
            return@InnerScaffold
        }
        for (section in spaceSections(list, archived)) item(key = section.title.name) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(section.title, sentenceNumber(section.count)), style = Type.of(15, FontWeight.Bold))
                for (c in section.cards) SpaceCardBox(c, admin != null,
                    onSwitch = { act { if (deps.shell.switchSpace(c.space.id)) t(TextKey.SPACE_SWITCHED, countryLabel(c.space.countryCode)) else null } },
                    onArchive = { sheet = SpaceSheet.Archive(c.space, c.active) },
                    onUnarchive = { act { admin?.unarchive(c.space.id); t(TextKey.SPC_UNARCHIVED, countryLabel(c.space.countryCode)) } },
                )
            }
        }
        item(key = "add") { TonalButton(t(TextKey.SPC_ADD), onClick = { sheet = SpaceSheet.Add }, modifier = Modifier.fillMaxWidth()) }
        item(key = "transfer") { MenuRowCard(t(TextKey.SPC_TRANSFER)) { nav.push(SpaceTransferLink) } }
        item(key = "shared") { NoteBox(t(TextKey.SPC_SHARED_BODY), title = t(TextKey.SPC_SHARED_TITLE)) }
    }

    SpacesSheet(
        sheet, admin != null, list = open.orEmpty().map { it.space }, archived = archived, onClose = { sheet = null },
        onCreate = { code -> act { admin?.create(code); t(TextKey.SPC_CREATED, countryLabel(code)) } },
        onArchive = { s -> act { admin?.archive(s.id); t(TextKey.SPC_ARCHIVED_DONE, countryLabel(s.countryCode)) } },
    )
}

sealed interface SpaceSheet {
    data object Add : SpaceSheet

    data class Archive(val space: Space, val wasActive: Boolean) : SpaceSheet
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceCardBox(c: SpaceCardView, canAdmin: Boolean, onSwitch: () -> Unit, onArchive: () -> Unit, onUnarchive: () -> Unit) {
    val name = countryLabel(c.space.countryCode)
    val shape = RoundedCornerShape(22.dp)
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LensOnLight(Modifier.size(46.dp).alpha(if (c.archived) 0.6f else 1f), shape = RoundedCornerShape(16.dp)) {
                    BasicText(countryMark(name), style = Type.of(18, FontWeight.Bold).copy(color = if (c.archived) Ink.muted else Ink.primary))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(name, style = Type.of(17, FontWeight.Bold))
                        if (c.active) Badge(t(TextKey.SPACE_ACTIVE), BadgeKind.INFO)
                    }
                    BasicText(t(TextKey.SPACE_SUB, currencyName(c.space.currency), currencySymbol(c.space.currency)), style = Type.of(13).copy(color = Ink.muted))
                    if (c.meta.isNotEmpty()) BasicText(c.meta, style = Type.caption().copy(color = Ink.muted))
                }
            }
            val acts = c.canSwitch || (c.canArchive && canAdmin) || (c.archived && canAdmin)
            if (acts) {
                RowRule()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.archived) PrimaryButton(t(TextKey.SPC_UNARCHIVE), onClick = onUnarchive, height = 44.dp)
                    if (c.canSwitch) PrimaryButton(t(TextKey.SPC_SWITCH), onClick = onSwitch, height = 44.dp)
                    if (c.canArchive && canAdmin) TonalButton(t(TextKey.SPC_ARCHIVE), onClick = onArchive, height = 44.dp)
                }
            }
            if (c.isDefault) BasicText(t(TextKey.SPC_DEFAULT_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
    }
    if (c.archived) Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0x8CFFFFFF)).insetRing(shape, 1.dp, Color(0xB3CCD8CC)).padding(horizontal = 16.dp, vertical = 14.dp),
    ) { body() }
    else FloatingCard(Modifier.fillMaxWidth().then(if (c.active) Modifier.insetRing(shape, 1.5.dp, Color(0x7308634F)) else Modifier), shape = shape, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { body() }
}

/** صف عائم لوحده («التحويل لنفسك بين البلدين ‹»). */
@Composable
private fun MenuRowCard(label: String, onClick: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        MenuRow(null, label, null, last = true, onClick = onClick)
    }
}

@Composable
private fun SpacesSheet(
    sheet: SpaceSheet?,
    canAdmin: Boolean,
    list: List<Space>,
    archived: List<Space>,
    onClose: () -> Unit,
    onCreate: (String) -> Unit,
    onArchive: (Space) -> Unit,
) {
    var shown by remember { mutableStateOf<SpaceSheet?>(null) }
    if (sheet != null) shown = sheet
    val current = shown ?: return
    var pick by remember(current) { mutableStateOf<String?>(null) }
    val title = when (current) {
        SpaceSheet.Add -> t(TextKey.SPC_ADD)
        is SpaceSheet.Archive -> t(TextKey.SPC_ARCHIVE_Q, countryLabel(current.space.countryCode))
    }
    Sheet(sheet != null, onClose, title = title, closeLabel = t(TextKey.SHELL_CLOSE), corner = 28.dp, spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        when (current) {
            SpaceSheet.Add -> {
                BasicText(t(TextKey.SPC_ADD_BODY), style = Type.of(13).copy(color = Ink.muted))
                for (o in countryOptions(list, archived)) CountryRow(o, pick == o.countryCode) { if (o.state == CountryState.AVAILABLE) pick = o.countryCode }
                BasicText(t(TextKey.SPC_SUPPORTED), style = Type.caption().copy(color = Ink.muted))
                if (!canAdmin) NotYetLine(t(TextKey.SPC_ADMIN_NOT_YET))
                val code = pick
                TwoButtons(
                    if (code != null) t(TextKey.SPC_CREATE_NAMED, countryLabel(code)) else t(TextKey.SPC_CREATE),
                    enabled = canAdmin && code != null, onFirst = { code?.let(onCreate) }, onCancel = onClose,
                )
            }
            is SpaceSheet.Archive -> {
                val body = t(TextKey.SPC_ARCHIVE_BODY) + if (current.wasActive) " " + t(TextKey.SPC_ARCHIVE_BACK_TO_SA) else ""
                BasicText(body, style = Type.of(13).copy(color = Ink.muted))
                TwoButtons(t(TextKey.SPC_ARCHIVE), enabled = canAdmin, onFirst = { onArchive(current.space) }, onCancel = onClose)
            }
        }
    }
}

@Composable
private fun CountryRow(o: CountryOption, on: Boolean, onPick: () -> Unit) {
    val press = rememberPress()
    val off = o.state != CountryState.AVAILABLE
    val shape = RoundedCornerShape(16.dp)
    val name = countryLabel(o.countryCode)
    val sub = when (o.state) {
        CountryState.TAKEN -> t(TextKey.SPC_TAKEN, name)
        CountryState.ARCHIVED -> t(TextKey.SPC_TAKEN_ARCHIVED)
        CountryState.AVAILABLE -> countryPack(o.countryCode).currency.let { t(TextKey.SPACE_SUB, currencyName(it), currencySymbol(it)) }
    }
    Column(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).alpha(if (off) 0.55f else 1f).pressScale(press, !off).clip(shape)
            .background(if (on) Ink.selected else Color(0x0A193D33)).then(if (on) Modifier.insetRing(shape, 1.5.dp, Ink.primary) else Modifier)
            .tap(press, !off, role = Role.RadioButton, onClick = onPick).semantics { selected = on }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(name, style = Type.of(15, FontWeight.Bold))
        BasicText(sub, style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
private fun TwoButtons(first: String, enabled: Boolean, onFirst: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(first, onClick = onFirst, enabled = enabled, modifier = Modifier.weight(1f))
        TonalButton(t(TextKey.MORE_CANCEL), onClick = onCancel, modifier = Modifier.weight(1f), muted = true)
    }
}
