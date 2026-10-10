package app.masroufy.ui.screens.imports

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
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
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalPermissions
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.more.NotificationSettingsRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** لوحة المرسل: تعديل محفظة بنك موجود أو إضافة مرسل جديد. */
sealed interface SenderSheet {
    data class Edit(val sender: SenderUi) : SenderSheet

    data object Add : SenderSheet
}

/**
 * «إعداد القراءة» (`BankSmsSettings` + `SmsPermission`): تنبيه لو الإذن اتسحب · القراءة التلقائية (تشغيل/إيقاف بزرار واضح §17) · البنوك المرسلة
 * ومحفظة كل بنك (مرة واحدة — §72) · إضافة مرسل (أقصى ١٠) · إشعارات رسائل البنك. الإذن: شرح (`SmsPermission`) ⇒ نافذة النظام.
 */
@Composable
fun BankSmsSettingsScreen() {
    val deps = LocalSpace.current.imports
    val sms = deps.sms
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val permissions = LocalPermissions.current
    val scope = rememberCoroutineScope()
    var ui by remember(deps) { mutableStateOf<SmsSettingsUi?>(null) }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    var reload by remember(deps) { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<SenderSheet?>(null) }
    var explain by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var afterGrant by remember { mutableStateOf<(suspend () -> Unit)?>(null) }

    LaunchedEffect(deps, reload) {
        wallets = runCatching { deps.wallets() }.getOrDefault(wallets)
        ui = smsSettingsUi(sms?.let { runCatching { it.overview(record = false) }.getOrNull() }, wallets)
    }

    fun act(done: TextRef?, vararg args: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                if (done != null) toaster.show(t(done, *args), dark = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toaster.show(e.message ?: t(UiKey.IMPORTS_LOAD_FAILED), dark = true)
            }
            reload++
        }
    }

    /** الإذن قبل أي تشغيل: موجود ⇒ على طول، وإلا شرح `SmsPermission` الأول ثم نافذة النظام. */
    fun withPermission(block: suspend () -> Unit) {
        if (permissions.hasSms()) act(null) { block() } else { afterGrant = block; denied = false; explain = true }
    }

    InnerScaffold(t(UiKey.SMS_SETTINGS_TITLE)) {
        val u = ui
        if (u == null) {
            item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }
            return@InnerScaffold
        }
        if (!u.available) {
            item(key = "ios") {
                TintedPanel(PanelTone.QUIET) {
                    PanelTitle(t(UiKey.BANK_SMS_STATUS_IOS), PanelTone.QUIET)
                    BasicText(t(UiKey.BANK_SMS_STATUS_IOS_BODY), style = Type.of(13).copy(color = Ink.muted))
                    QuietButton(t(UiKey.BANK_SMS_PASTE), { nav.push(SmsPasteRoute) }, onTint = true, height = 44.dp)
                }
            }
            return@InnerScaffold
        }
        if (u.permissionLost) {
            item(key = "perm") {
                TintedPanel(PanelTone.AMBER) {
                    PanelTitle(t(UiKey.SMS_SETTINGS_PERM_TITLE), PanelTone.AMBER)
                    BasicText(t(UiKey.SMS_SETTINGS_PERM_BODY), style = Type.of(13).copy(color = Ink.focus))
                    QuietButton(t(UiKey.SMS_SETTINGS_PERM_BUTTON), { withPermission { toaster.show(t(UiKey.SMS_SETTINGS_PERM_BACK), dark = true) } }, onTint = true, height = 44.dp)
                }
            }
        }
        item(key = "auto") { AutoCard(u, onStop = { act(UiKey.SMS_SETTINGS_STOPPED) { sms?.disable() } }, onStart = {
            if (u.senders.isEmpty()) sheet = SenderSheet.Add
            else withPermission { sms?.enable(u.senders.map { it.sender }); toaster.show(t(UiKey.SMS_SETTINGS_STARTED), dark = true) }
        }) }
        item(key = "senders") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CountTitle(t(UiKey.SMS_SETTINGS_SENDERS), t(UiKey.SMS_SETTINGS_SENDERS_COUNT, sentenceNumber(u.senders.size), sentenceNumber(SmsSettingsUi.MAX_SENDERS)))
                if (u.senders.isNotEmpty()) {
                    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        u.senders.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            SenderRow(s) { sheet = SenderSheet.Edit(s) }
                        }
                    }
                }
                TonalButton(t(UiKey.SMS_SETTINGS_ADD), { sheet = SenderSheet.Add }, Modifier.fillMaxWidth(), enabled = !u.full)
                BasicText(t(UiKey.SMS_SETTINGS_RULES), style = Type.of(12, lineHeight = 1.7).copy(color = Ink.muted))
            }
        }
        item(key = "notif") { LinkRow(t(UiKey.SMS_SETTINGS_NOTIF_LINK), { nav.push(NotificationSettingsRoute) }) }
    }

    SenderSheetView(
        sheet = sheet,
        wallets = wallets.filter { it.kind != "cash" },
        isLast = (ui?.senders?.size ?: 0) <= 1,
        onDismiss = { sheet = null },
        onSave = { name, wallet ->
            val current = ui?.senders?.map { it.sender }.orEmpty()
            when (val s = sheet) {
                is SenderSheet.Edit -> act(UiKey.SMS_SETTINGS_LINKED, s.sender.sender, wallet.name) { sms?.chooseWallet(s.sender.sender, wallet.id) }
                SenderSheet.Add -> withPermission {
                    sms?.enable(sendersWith(current, name))
                    sms?.chooseWallet(name.trim(), wallet.id)
                    toaster.show(t(UiKey.SMS_SETTINGS_ADDED, name.trim()), dark = true)
                }
                null -> Unit
            }
            sheet = null
        },
        onUnlink = { s -> sheet = null; act(UiKey.SMS_SETTINGS_UNLINKED) { sms?.chooseWallet(s.sender, null) } },
        onRemove = { s ->
            sheet = null
            act(UiKey.SMS_SETTINGS_REMOVED, s.sender) { sms?.enable(sendersWithout(ui?.senders?.map { it.sender }.orEmpty(), s.sender)) }
        },
    )
    SmsPermissionSheet(
        visible = explain,
        denied = denied,
        onDismiss = { explain = false },
        onAllow = {
            scope.launch {
                if (permissions.requestSms()) {
                    explain = false
                    afterGrant?.invoke()
                    afterGrant = null
                    reload++
                } else {
                    denied = true
                }
            }
        },
        onPaste = { explain = false; nav.push(SmsPasteRoute) },
    )
}

@Composable
private fun AutoCard(u: SmsSettingsUi, onStop: () -> Unit, onStart: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.SMS_SETTINGS_AUTO_TITLE), Modifier.weight(1f), style = Type.of(16, FontWeight.Bold))
                Tag(t(if (u.reading) UiKey.SMS_SETTINGS_ON else UiKey.SMS_SETTINGS_OFF), if (u.reading) TagTone.NEW else TagTone.AMBER)
            }
            val body = when {
                u.permissionLost -> UiKey.SMS_SETTINGS_BODY_PERM
                u.enabled -> UiKey.SMS_SETTINGS_BODY_ON
                else -> UiKey.SMS_SETTINGS_BODY_OFF
            }
            BasicText(t(body), style = Type.of(13, lineHeight = 1.7).copy(color = Ink.muted))
            if (u.enabled && !u.permissionLost) QuietButton(t(UiKey.SMS_SETTINGS_STOP), onStop, Modifier.fillMaxWidth(), danger = true)
            if (!u.enabled) PrimaryButton(t(UiKey.SMS_SETTINGS_START), onStart, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (u.permissionLost) Ink.focus else Ink.income, 8.dp)
                BasicText(t(if (u.permissionLost) UiKey.SMS_SETTINGS_PERM_LINE_OFF else UiKey.SMS_SETTINGS_PERM_LINE_ON), style = Type.caption().copy(color = Ink.muted))
            }
        }
    }
}

@Composable
private fun SenderRow(s: SenderUi, onClick: () -> Unit) {
    val waiting = s.walletId == null
    val sub = when {
        !waiting -> t(UiKey.SMS_SETTINGS_RECORDS_IN, s.walletName ?: t(TextKey.NOT_AVAILABLE))
        s.waiting > 0 -> t(UiKey.SMS_SETTINGS_WAITING_WALLET, msgsCount(s.waiting))
        else -> t(UiKey.SMS_SETTINGS_NO_WALLET)
    }
    ListRow(
        title = s.sender,
        subtitle = sub,
        leading = {
            IconTile(if (waiting) Ink.focus else Ink.primary) { LucideIcon(Lucide.BUILDING_2, size = 18.dp, tint = if (waiting) Ink.focus else Ink.primary) }
        },
        onClick = onClick,
        padding = PaddingValues(8.dp),
    )
}
