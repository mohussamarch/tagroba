package app.masroufy.ui.screens.home.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.HOME_CARDS_DEFAULT_HIDDEN
import app.masroufy.core.HOME_CARDS_DEFAULT_ORDER
import app.masroufy.core.HomeCardKind
import app.masroufy.core.HomeCardsLayout
import app.masroufy.core.UiKey
import app.masroufy.core.defaultHomeCards
import app.masroufy.core.moveHomeCard
import app.masroufy.core.toggleHomeCard
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.screens.budgets.Switch
import app.masroufy.ui.screens.home.UpcomingSection
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * كروت الرئيسية (اختيار المالك 2026-10-15 — لوحة `HomeCards`): الكروت الظاهرة بترتيبك، وتحتها «عدّل الكروت» ⇒ كل كارت سطر فيه سهم لفوق
 * وسهم لتحت ومفتاح ظاهر/مخفي · «تمام» بيحفظ في الحساب · «الترتيب الأصلي» بيرجّع زي أول مرة (ومع «تمام» بيمسح المحفوظ).
 * الترتيب نفسه في `core/HomeCards.kt` والحفظ في `ManageHomeCards` — هنا العرض بس.
 */
@Composable
fun HomeCards() {
    val deps = LocalSpace.current
    val manage = deps.home.cards.layout
    val scope = rememberCoroutineScope()
    var layout by remember(deps) { mutableStateOf<HomeCardsLayout?>(null) }
    // مش null = وضع التعديل (نسخة بتتعدل لحد «تمام»)
    var draft by remember(deps) { mutableStateOf<HomeCardsLayout?>(null) }
    var saveFailed by remember(deps) { mutableStateOf(false) }
    // القراية فشلت ⇒ الترتيب الأول (الكروت أهم من الترتيب) — والحفظ بعدها بيكتب فوقه عادي
    LaunchedEffect(deps) { layout = runCatching { manage.layout() }.getOrElse { defaultHomeCards() } }
    val data = rememberHomeCardsData(layout?.visible)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val shown = layout
        val editing = draft
        when {
            shown == null -> Skeleton(Modifier.fillMaxWidth().height(190.dp))
            editing != null -> HomeCardsEditor(
                editing,
                saveFailed,
                onChange = { draft = it },
                onReset = { draft = defaultHomeCards() },
                onDone = {
                    scope.launch {
                        val isDefault = editing.order == HOME_CARDS_DEFAULT_ORDER && editing.hidden == HOME_CARDS_DEFAULT_HIDDEN
                        runCatching { if (isDefault) manage.reset() else manage.save(editing.order, editing.hidden) }
                            .onSuccess { layout = it; draft = null; saveFailed = false }
                            .onFailure { saveFailed = true }
                    }
                },
            )
            else -> {
                shown.visible.forEach { kind -> key(kind) { HomeCard(kind, data) } }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TonalButton(t(UiKey.HOME_CARDS_EDIT), onClick = { draft = shown; saveFailed = false })
                }
            }
        }
    }
}

@Composable
private fun HomeCard(kind: HomeCardKind, data: HomeCardsData) {
    when (kind) {
        HomeCardKind.DAILY -> DailyCard(data.daily)
        HomeCardKind.BUDDY -> BuddyCard(data.month)
        HomeCardKind.UPCOMING -> UpcomingSection()
        HomeCardKind.TOP -> TopCard(data.month)
        HomeCardKind.GOALS -> GoalsCard(data.goals)
        HomeCardKind.REVIEW -> ReviewCard(data.month)
        HomeCardKind.RECENT -> RecentCard(data.month)
        HomeCardKind.PEOPLE -> PeopleCard(data.people)
        HomeCardKind.INVEST -> InvestCard()
    }
}

/** اسم الكارت في «عدّل الكروت» (نفس عنوانه في الرئيسية). */
@Composable
internal fun cardName(kind: HomeCardKind): String = t(
    when (kind) {
        HomeCardKind.DAILY -> UiKey.HOME_CARD_DAILY
        HomeCardKind.BUDDY -> UiKey.HOME_CARD_BUDDY
        HomeCardKind.UPCOMING -> UiKey.UPCOMING_TITLE
        HomeCardKind.TOP -> UiKey.HOME_CARD_TOP
        HomeCardKind.GOALS -> UiKey.HOME_CARD_GOALS
        HomeCardKind.REVIEW -> UiKey.HOME_CARD_REVIEW
        HomeCardKind.RECENT -> UiKey.HOME_CARD_RECENT
        HomeCardKind.PEOPLE -> UiKey.HOME_CARD_PEOPLE
        HomeCardKind.INVEST -> UiKey.HOME_CARD_INVEST
    },
)

/** «عدّل الكروت»: سطر لكل كارت (الاسم وحالته · سهم لفوق · سهم لتحت · مفتاح) ثم «تمام» و«الترتيب الأصلي». */
@Composable
private fun HomeCardsEditor(
    layout: HomeCardsLayout,
    saveFailed: Boolean,
    onChange: (HomeCardsLayout) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(t(UiKey.HOME_CARDS_EDIT_TITLE), style = Type.section())
            BasicText(t(UiKey.HOME_CARDS_EDIT_SUB), style = Type.caption().copy(color = Ink.muted))
        }
        Column(Modifier.fillMaxWidth()) {
            layout.order.forEachIndexed { i, kind ->
                key(kind) {
                    val name = cardName(kind)
                    val on = kind !in layout.hidden
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(name, style = Type.bodyBold().copy(color = if (on) Ink.text else Ink.muted), maxLines = 1)
                            BasicText(t(if (on) UiKey.HOME_CARDS_SHOWN else UiKey.HOME_CARDS_HIDDEN), style = Type.caption().copy(color = Ink.muted))
                        }
                        IconButton44(Lucide.CHEVRON_UP, t(UiKey.HOME_CARDS_UP, name), onClick = { onChange(moveHomeCard(layout, kind, -1)) }, enabled = i > 0)
                        IconButton44(Lucide.CHEVRON_DOWN, t(UiKey.HOME_CARDS_DOWN, name), onClick = { onChange(moveHomeCard(layout, kind, 1)) }, enabled = i < layout.order.size - 1)
                        Switch(on, t(UiKey.HOME_CARDS_SHOW, name), onToggle = { onChange(toggleHomeCard(layout, kind)) })
                    }
                }
            }
        }
        if (saveFailed) BasicText(t(UiKey.HOME_CARDS_SAVE_FAILED), style = Type.caption().copy(color = Ink.expense))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(t(UiKey.HOME_CARDS_DONE), onClick = onDone, modifier = Modifier.weight(1f))
            SecondaryButton(t(UiKey.HOME_CARDS_RESET), onClick = onReset)
        }
    }
}
