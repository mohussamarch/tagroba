package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.ProfileCheck
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «كمّل ملفك» (`ProfileQuestion`): سؤال واحد بس — مكان الرسمة (١٢٠×٨٠ «رسمة بخط واحد» لحد ما المالك يرسمها) · السؤال 22 · الاختيارات صفوف 52 ·
 * «ليس الآن». الاختيار بيتحفظ على طول (`ManageProfile.saveWithQuestions`) وتحت بتطلع جملة باللي اتغير + «العودة إلى الرئيسية» (و«السؤال التالي» لسؤال المتابعة بس).
 * ⚠️ رأس الصفحة في النموذج فيه دايرة «ملفك X٪» — النسبة مالهاش حسبة لسه ⇒ «كمّل ملفك» من غير رقم.
 */
@Composable
fun ProfileQuestionScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var profile by remember(deps) { mutableStateOf<UserProfile?>(null) }
    var failed by remember(deps) { mutableStateOf(false) }
    var card by remember(deps) { mutableStateOf<ProfileCard?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // «عندي سيارة» وعنده شغل شغال ⇒ «هل تذهب بها إلى العمل؟» هو السؤال الجاي (§64 — `IncomeFollowUp.CarToWork`)
    var carToWork by remember { mutableStateOf(false) }
    LaunchedEffect(deps) {
        runCatching { deps.home.profile.load() }.onSuccess { profile = it; card = nextProfileCard(it) }.onFailure { failed = true }
    }
    fun answer(c: ProfileCard, option: ProfileOption) {
        val p = profile ?: return
        if (saving) return
        saving = true
        picked = option.id
        error = null
        scope.launch {
            runCatching {
                if (c == ProfileCard.CAR_TO_WORK) {
                    val check = deps.home.profile.answerCarToWork(option.id == "yes")
                    check to emptyList<IncomeFollowUp>()
                } else {
                    val saved = deps.home.profile.saveWithQuestions(applyAnswer(p, c, option.id))
                    saved.check to saved.followUps
                }
            }.onSuccess { (check, followUps) ->
                when (check) {
                    is ProfileCheck.Ok -> {
                        profile = check.profile
                        reply = t(option.reply)
                        carToWork = IncomeFollowUp.CarToWork in followUps
                    }
                    is ProfileCheck.Invalid -> error = check.message
                }
            }.onFailure { error = t(UiKey.PROFILE_QUESTION_SAVE_FAILED) }
            saving = false
        }
    }
    InnerScaffold(t(UiKey.PROFILE_QUESTION_TITLE)) {
        val c = card
        when {
            failed -> item(key = "failed") { EmptyState(t(UiKey.SHELL_LOAD_FAILED)) }
            profile == null -> item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(260.dp)) }
            c == null -> item(key = "done") {
                EmptyState(t(UiKey.PROFILE_QUESTION_NONE_TITLE), t(UiKey.PROFILE_QUESTION_NONE_BODY), action = {
                    TonalButton(t(UiKey.PROFILE_QUESTION_BACK_HOME), onClick = { nav.pop() })
                })
            }
            else -> {
                item(key = "lead") { LeadLine() }
                item(key = "card-${c.name}") { QuestionCard(c, picked, enabled = reply == null && !saving) { answer(c, it) } }
                error?.let { msg -> item(key = "error") { FieldError(msg) } }
                val r = reply
                if (r != null) item(key = "reply") {
                    // §63: سؤال واحد بس كل مرة ⇒ بعد الإجابة «العودة إلى الرئيسية» (النموذج) — إلا سؤال المتابعة اللي المنطق نفسه طلّعه
                    // («عندي سيارة» وعنده شغل ⇒ «هل تذهب بها إلى العمل؟» — جزء من نفس الإجابة)
                    val next = if (carToWork) ProfileCard.CAR_TO_WORK else null
                    ReplyCard(r, hasNext = next != null, onNext = {
                        card = next
                        carToWork = false
                        picked = null
                        reply = null
                    }, onHome = { nav.pop() })
                } else item(key = "later") {
                    TonalButton(t(UiKey.PROFILE_QUESTION_LATER), onClick = { nav.pop() }, modifier = Modifier.fillMaxWidth(), muted = true)
                }
            }
        }
    }
}

@Composable
private fun LeadLine() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Ink.primary, size = 44.dp, radius = 22.dp) { LucideIcon(Lucide.USER, size = 22.dp, tint = Ink.primary) }
        Column {
            BasicText(t(UiKey.HOME_PROFILE_TITLE), style = Type.bodyBold())
            BasicText(t(UiKey.PROFILE_QUESTION_LEAD), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun QuestionCard(card: ProfileCard, picked: String?, enabled: Boolean, onPick: (ProfileOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // مكان الرسمة (بخط واحد — المالك بيرسمها)، جواها رمز البطاقة لحد ما تترسم
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(Ink.selected.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(card.icon, size = 48.dp, tint = Ink.primary) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(t(card.title), style = Type.of(22, FontWeight.Bold, 1.4))
            card.sub?.let { BasicText(t(it), style = Type.of(14).copy(color = Ink.muted)) }
        }
        for (o in optionsOf(card)) OptionRow(t(o.label), picked == o.id, enabled) { onPick(o) }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    val surface = if (selected) Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary)
    else Modifier.background(Ink.onPrimary).insetRing(shape, 1.dp, Ink.fieldEdge)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).pressScale(press, enabled).clip(shape).then(surface)
            .tap(press, enabled, role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = Type.of(16, FontWeight.Bold))
        if (selected) LucideIcon(Lucide.CHECK, size = 20.dp, tint = Ink.primary)
    }
}

@Composable
private fun ReplyCard(text: String, hasNext: Boolean, onNext: () -> Unit, onHome: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.CHECK, size = 22.dp, tint = Ink.income)
                Column(Modifier.weight(1f)) {
                    BasicText(t(UiKey.PROFILE_QUESTION_SAVED_TITLE), style = Type.bodyBold())
                    BasicText(text, style = Type.of(13).copy(color = Ink.soft))
                }
            }
            if (hasNext) PrimaryButton(t(UiKey.PROFILE_QUESTION_NEXT), onClick = onNext, modifier = Modifier.fillMaxWidth())
            TonalButton(t(UiKey.PROFILE_QUESTION_BACK_HOME), onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
    }
}
