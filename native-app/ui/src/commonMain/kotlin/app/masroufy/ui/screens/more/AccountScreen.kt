package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.ProfileCheck
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.auth.ResetMode
import app.masroufy.ui.screens.auth.ResetPasswordSheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «ملفك» (`Account` — من دايرتك في الرئيسية وكارت «المزيد»): الدايرة والاسم والإيميل · «ملفك ٪» (الحسبة لسه مالهاش منطق ⇒ «غير متاح بعد»)
 * · «غيّر شكلك» · عنك · الدخل · حياتك · المستحقات في الميزانية · الحساب (الإيميل للعرض · تغيير كلمة السر بلوحة الرابط · الخروج).
 * كل تعديل بيعدّي على `ManageProfile.saveWithQuestions` («هل تذهب بها للدوام؟» بيطلع منه هو — §64).
 */
@Composable
fun AccountScreen() {
    val deps = LocalSpace.current
    val more = deps.more
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var profile by remember(deps) { mutableStateOf<UserProfile?>(null) }
    var me by remember(deps) { mutableStateOf<MeInfo?>(null) }
    var range by remember(deps) { mutableStateOf<SalaryRange?>(null) }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountEdit?>(null) }
    LaunchedEffect(deps) {
        profile = runCatching { more.profile.load() }.getOrNull()
        me = runCatching { deps.shell.me() }.getOrNull()
        range = runCatching { more.salaryRange?.current() }.getOrNull()
    }

    /** حفظ ⇒ الملف الجديد على الشاشة + «هل تذهب بها للدوام؟» لو طلع. */
    fun save(updated: UserProfile, done: String? = null) {
        scope.launch {
            val saved = runCatching { more.profile.saveWithQuestions(updated) }.getOrNull()
            when (val check = saved?.check) {
                is ProfileCheck.Ok -> {
                    failed = false
                    profile = check.profile
                    editing = if (saved.followUps.any { it is app.masroufy.core.IncomeFollowUp.CarToWork }) AccountEdit.CarToWork else null
                    if (done != null) toaster.show(done, dark = true)
                }
                is ProfileCheck.Invalid -> editing = editing?.withError(check.message)
                null -> {
                    failed = true
                    editing = null
                }
            }
        }
    }

    InnerScaffold(t(UiKey.ACC_TITLE)) {
        if (failed) item(key = "err") { WarnBox(t(UiKey.ACC_ERR_TITLE), t(UiKey.ACC_ERR_BODY)) }
        val p = profile
        if (p == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }
            return@InnerScaffold
        }
        item(key = "head") { AccountHead(p, me, more.profile.email()) }
        for (g in accountGroups(p, deps.space.currency, range)) item(key = g.title.name) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupTitle(t(g.title))
                if (g.note != null) BasicText(t(g.note), Modifier.padding(horizontal = 4.dp), style = Type.caption().copy(color = Ink.muted))
                GroupCard {
                    g.rows.forEachIndexed { i, r -> AnswerRow(r, last = i == g.rows.lastIndex) { editing = AccountEdit.Field(r.field, draftOf(p, r.field, range)) } }
                }
            }
        }
        item(key = "dues") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupTitle(t(UiKey.ACC_GROUP_BUDGET))
                FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(t(UiKey.ACC_DUES_TITLE), style = Type.of(15, FontWeight.Bold))
                            BasicText(t(duesDescription(p)), style = Type.caption().copy(color = Ink.muted))
                        }
                        ToggleSwitch(duesOn(p), t(UiKey.ACC_DUES_TITLE)) { save(p.copy(duesInBudget = !duesOn(p))) }
                    }
                }
            }
        }
        item(key = "account") { AccountActions(more.profile.email()) { editing = AccountEdit.SignOut } }
    }

    AccountSheet(
        edit = editing, rangeEditable = more.salaryRange != null,
        onDraft = { editing = it }, onClose = { editing = null },
        onSave = { field, draft -> profile?.let { p -> applyAnswer(p, field, draft)?.let { save(it, paydayToast(p, it)) } } },
        onRange = { r -> scope.launch { runCatching { more.salaryRange?.choose(r) }; range = r; editing = null } },
        onCarToWork = { yes ->
            scope.launch {
                when (val check = runCatching { more.profile.answerCarToWork(yes) }.getOrNull()) {
                    is ProfileCheck.Ok -> { profile = check.profile; if (yes) toaster.show(t(UiKey.ACC_CAR_TO_WORK_SAVED), dark = true) }
                    else -> failed = true
                }
                editing = null
            }
        },
    )
}

/** «أصبح شهرك المالي يبدأ يوم N» لما يوم الراتب يتغير بس. */
private fun paydayToast(before: UserProfile, after: UserProfile): String? =
    if (before.payday != after.payday) t(UiKey.ACC_PAYDAY_SAVED, app.masroufy.core.sentenceNumber(after.payday)) else null

private fun draftOf(p: UserProfile, field: AccountField, range: SalaryRange?): Any? = when (field) {
    AccountField.NAME -> p.displayName.orEmpty()
    AccountField.GENDER -> p.gender
    AccountField.DEPENDENTS -> p.dependentKinds.orEmpty()
    AccountField.SALARY -> range
    AccountField.PAYDAY -> p.payday
    AccountField.CAR -> p.hasCar
    AccountField.RENTER -> p.renter
    AccountField.MAID -> p.domesticWorker
    AccountField.BUSINESS -> p.business
}

@Composable
private fun AccountHead(p: UserProfile, me: MeInfo?, email: String?) {
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MeAvatar(64.dp, me?.profilePercent, look = me?.lookIndex ?: 1)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(p.displayName?.takeIf { it.isNotBlank() } ?: t(UiKey.ACC_NO_NAME), style = Type.of(18, FontWeight.Bold))
                    if (email != null) BasicText(email, style = Type.of(13).copy(color = Ink.muted, textAlign = TextAlign.End, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                }
            }
            // نسبة «ملفك ٪» مالهاش حسبة في كوتلن لسه (OVERRIDES §76 «ناقص») ⇒ من غير شريط ولا رقم
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.ACC_PERCENT_LABEL), style = Type.of(13, FontWeight.Bold))
                if (me?.profilePercent == null) NotYetBadge() else BasicText(app.masroufy.core.sentenceNumber(me.profilePercent) + "٪", style = Type.of(13, FontWeight.Bold))
            }
            LookButton(me?.lookIndex ?: 1)
            BasicText(t(UiKey.ACC_LOOK_SOON), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
        }
    }
}

/** صف سؤال: الاسم (+ سطر تحته) والقيمة شمال، أو شارة «لم تُجب بعد» الكهرمانية. */
@Composable
private fun AnswerRow(r: AccountRow, last: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).clip(RoundedCornerShape(16.dp))
                .tap(press, label = t(r.label), onClick = onClick).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(t(r.label), style = Type.of(15, FontWeight.Medium))
                if (r.hint != null) BasicText(t(r.hint), style = Type.caption().copy(color = Ink.muted))
            }
            if (r.value != null) BasicText(r.value, Modifier.widthIn(max = 150.dp), style = Type.of(14, FontWeight.Bold).copy(textAlign = TextAlign.End))
            else Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Ink.alertBg).padding(horizontal = 10.dp, vertical = 3.dp)) {
                BasicText(t(UiKey.ACC_NOT_ANSWERED), style = Type.of(12, FontWeight.Bold).copy(color = Ink.focus))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
        if (!last) RowRule()
    }
}

@Composable
private fun AccountActions(email: String?, onSignOut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle(t(UiKey.ACC_GROUP_ACCOUNT))
        GroupCard {
            FactRow(t(UiKey.ACC_EMAIL), email ?: "", last = false, muted = true, valueContent = {
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(email ?: t(TextKey.NOT_AVAILABLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                }
            })
            BasicText(t(UiKey.ACC_EMAIL_HINT), Modifier.padding(start = 8.dp, bottom = 6.dp), style = Type.caption().copy(color = Ink.muted))
            RowRule()
            ResetPasswordSheet(prefill = email.orEmpty(), mode = ResetMode.CHANGE)
            RowRule()
            val press = rememberPress()
            Box(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).tap(press, onClick = onSignOut).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) { BasicText(t(UiKey.ACC_SIGN_OUT), style = Type.of(15, FontWeight.Bold).copy(color = Ink.expense)) }
        }
    }
}

/** «غيّر شكلك» + لوحته (`LookSheet`). */
@Composable
private fun LookButton(current: Int) {
    var open by remember { mutableStateOf(false) }
    TonalButton(t(UiKey.LOOK_OPEN), onClick = { open = true }, modifier = Modifier.fillMaxWidth(), height = 44.dp)
    LookSheet(open, current) { open = false }
}
