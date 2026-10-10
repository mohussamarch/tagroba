package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.UserProfile
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LockChange
import kotlinx.coroutines.launch

/**
 * القفل واللغة والمحتوى الإسلامي (`AppSettings` = LockSettings · LanguageSettings · IslamicContentSettings — KOTLIN-MAP §١).
 * - **القفل** (`AppLock`): التشغيل والإيقاف **الاتنين بتأكيد الجهاز** (نافذة النظام نفسها — البصمة أو رمز الجوال)، والرفض جنب المفتاح بإعادة.
 * - **اللغة**: الاختيار بيتحفظ عن طريق [LanguageSetting] (لسه مالوش مكان ⇒ «غير متاح بعد»). العربي فصحى للسعودية ومصري لمصر لوحده (§66).
 * - **المحتوى الإسلامي**: `ManageProfile.save(islamicContentVisible)` — إخفاء بس، مفيش حاجة بتتمسح.
 */
@Composable
fun AppSettingsScreen() {
    val deps = LocalSpace.current
    val lock = LocalApp.current.lock
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var lockOn by remember { mutableStateOf(lock?.isEnabled() == true) }
    var lockError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var profile by remember(deps) { mutableStateOf<UserProfile?>(null) }
    LaunchedEffect(deps) { profile = runCatching { deps.more.profile.load() }.getOrNull() }
    val available = lock != null && lock.supported

    fun toggleLock() {
        val l = lock ?: return
        if (busy) return
        busy = true
        val want = !lockOn
        scope.launch {
            when (val r = if (want) l.enable() else l.disable()) {
                LockChange.Ok -> {
                    lockOn = want
                    lockError = null
                    toaster.show(t(if (want) TextKey.APPS_LOCK_ON_DONE else TextKey.APPS_LOCK_OFF_DONE), dark = true)
                }
                is LockChange.Refused -> lockError = r.message
            }
            busy = false
        }
    }

    InnerScaffold(t(TextKey.APPS_TITLE)) {
        item(key = "lock") {
            Section(t(TextKey.APPS_LOCK_HEAD)) {
                SwitchHeader(Lucide.FINGERPRINT, t(TextKey.APPS_LOCK_TITLE), t(TextKey.APPS_LOCK_DESC), lockOn, enabled = available && !busy) { toggleLock() }
                if (lockOn && available) Facts(listOf(TextKey.APPS_LOCK_FACT_1, TextKey.APPS_LOCK_FACT_2, TextKey.APPS_LOCK_FACT_3))
                if (!available) WarnBox(null, t(TextKey.APPS_LOCK_NA))
                lockError?.let { msg -> WarnBox(null, msg) { TonalButton(t(TextKey.MORE_RETRY), onClick = { toggleLock() }, height = 44.dp) } }
            }
        }
        item(key = "lang") { LanguageSection() }
        item(key = "islamic") {
            val p = profile
            val on = p?.islamicContentVisible != false
            Section(t(TextKey.APPS_ISL_HEAD)) {
                SwitchHeader(Lucide.MOON, t(TextKey.APPS_ISL_TITLE), t(if (on) TextKey.APPS_ISL_SHOWN else TextKey.APPS_ISL_HIDDEN), on, enabled = p != null) {
                    val now = p ?: return@SwitchHeader
                    scope.launch {
                        val saved = runCatching { deps.more.profile.save(now.copy(islamicContentVisible = !on)) }.getOrNull()
                        if (saved is app.masroufy.core.ProfileCheck.Ok) {
                            profile = saved.profile
                            toaster.show(t(if (on) TextKey.APPS_ISL_HIDDEN_DONE else TextKey.APPS_ISL_SHOWN_DONE), dark = true)
                        } else toaster.show(t(TextKey.MORE_SAVE_FAILED), dark = true)
                    }
                }
                RowRuleSpaced()
                BasicText(t(if (on) TextKey.APPS_ISL_LIST_ON else TextKey.APPS_ISL_LIST_OFF), style = Type.of(12, FontWeight.Bold).copy(color = Ink.muted))
                for (k in listOf(TextKey.APPS_ISL_ZAKAT, TextKey.APPS_ISL_CALENDAR)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Ink.income else Color(0x40193D33)))
                    BasicText(t(k), style = Type.of(13).copy(color = if (on) Ink.text else Ink.muted, textDecoration = if (on) null else TextDecoration.LineThrough))
                }
                BasicText(t(TextKey.APPS_ISL_NOTE), style = Type.caption().copy(color = Ink.muted))
            }
        }
    }
}

@Composable
private fun LanguageSection() {
    val hook = LocalSpace.current.more.appLanguage
    val toaster = LocalToaster.current
    var lang by remember { mutableStateOf(hook?.current() ?: Texts.language) }
    val variant = t(if (Texts.arabicVariant == ArabicVariant.EGYPTIAN) TextKey.APPS_LANG_EGYPTIAN else TextKey.APPS_LANG_MSA)
    Section(t(TextKey.APPS_LANG_HEAD), horizontal = 16.dp) {
        LangRow(t(TextKey.APPS_LANG_AR), null, t(TextKey.APPS_LANG_AR_DESC, variant), lang == Language.AR, hook != null) {
            if (lang != Language.AR) { hook?.choose(Language.AR); lang = Language.AR; toaster.show(t(TextKey.APPS_LANG_NEXT_OPEN), dark = true) }
        }
        RowRule()
        LangRow(t(TextKey.APPS_LANG_EN), t(TextKey.APPS_LANG_EN_ALT), t(TextKey.APPS_LANG_EN_DESC), lang == Language.EN, hook != null) {
            if (lang != Language.EN) { hook?.choose(Language.EN); lang = Language.EN; toaster.show(t(TextKey.APPS_LANG_NEXT_OPEN), dark = true) }
        }
        RowRule()
        BasicText(t(if (lang == Language.EN) TextKey.APPS_DIR_LTR else TextKey.APPS_DIR_RTL), Modifier.padding(vertical = 12.dp), style = Type.caption().copy(color = Ink.muted))
        if (hook == null) NotYetLine(t(TextKey.APPS_LANG_NOT_YET), Modifier.padding(bottom = 12.dp))
    }
}

@Composable
private fun LangRow(name: String, alt: String?, desc: String, on: Boolean, enabled: Boolean, onPick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).pressScale(press, enabled).tap(press, enabled, role = Role.RadioButton, onClick = onPick)
            .semantics { selected = on }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).insetRing(CircleShape, 1.5.dp, if (on) Ink.primary else Color(0x4D193D33)), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(12.dp).clip(CircleShape).background(Ink.primary))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(name, style = Type.of(15, FontWeight.Bold))
                if (alt != null) BasicText(alt, style = Type.caption().copy(color = Ink.muted))
            }
            BasicText(desc, style = Type.caption().copy(color = Ink.muted))
        }
    }
}

/** عنوان 13 رمادي + كارت فيه المحتوى (`padding 14 16`). */
@Composable
private fun Section(title: String, horizontal: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle(title)
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = horizontal, vertical = 14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** رأس الكارت: أيقونة 36 · العنوان والوصف · المفتاح. */
@Composable
private fun SwitchHeader(icon: Lucide, title: String, desc: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Ink.primary) { LucideIcon(icon, size = 20.dp, tint = Ink.primary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.of(15, FontWeight.Bold))
            BasicText(desc, style = Type.caption().copy(color = Ink.muted))
        }
        ToggleSwitch(checked, title, enabled, onToggle)
    }
}

@Composable
private fun Facts(keys: List<TextKey>) {
    RowRuleSpaced()
    for (k in keys) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        LucideIcon(Lucide.CHECK, size = 16.dp, tint = Ink.income)
        BasicText(t(k), style = Type.of(13))
    }
}

@Composable
private fun RowRuleSpaced() = RowRule()
