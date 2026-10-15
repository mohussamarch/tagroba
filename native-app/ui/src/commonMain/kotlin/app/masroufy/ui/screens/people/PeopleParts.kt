package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type

/** قطع تبويب «الأشخاص» حوالين المسرح: «لك» و«عليك» · هيكل التحميل · «لسه مفيش حد» · كارت الخطأ (من `PeopleScreen` — حد الـ300 سطر). */

@Composable
internal fun Boxes(ui: PeopleTabUi, nav: app.masroufy.ui.nav.Navigator, modifier: Modifier, enabled: Boolean) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideBox(t(UiKey.PEOPLE_OWED_BOX), ui.owed, app.masroufy.ui.components.AmountTone.INCOME, t(UiKey.PEOPLE_OWED_COUNT, countOf(ui.owedPeople, Noun.PEOPLE)), enabled, Modifier.weight(1f)) { nav.push(OwedToYouRoute) }
        val oweCount = if (ui.owePeople == 2) t(UiKey.PEOPLE_OWE_TWO) else t(UiKey.PEOPLE_OWE_COUNT, countOf(ui.owePeople, Noun.PEOPLE))
        SideBox(t(UiKey.PEOPLE_OWE_BOX), ui.owe, app.masroufy.ui.components.AmountTone.EXPENSE, oweCount, enabled, Modifier.weight(1f)) { nav.push(YouOweRoute) }
    }
}

@Composable
private fun SideBox(title: String, lines: List<MoneyLine>?, tone: app.masroufy.ui.components.AmountTone, count: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FloatingCard(modifier, onClick = onClick, enabled = enabled, clickLabel = title) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Type.bodyBold())
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
        if (lines == null) AmountText(null, LocalSpace.current.space.currency, Modifier.fillMaxWidth(), size = 22)
        else for (l in lines) AmountText(l.minor, l.currency, Modifier.fillMaxWidth(), size = 22, color = tone.color)
        BasicText(count, style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
internal fun LoadingStage(top: Float) {
    Column(Modifier.padding(horizontal = Space.gutter).offset(y = (top + 82f).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(STAGE_H.dp), radius = 28.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { repeat(5) { Skeleton(Modifier.size(56.dp), radius = 28.dp) } }
    }
}

@Composable
internal fun EmptyPeople(add: () -> Unit, modifier: Modifier) {
    EmptyState(
        t(UiKey.PEOPLE_EMPTY_TITLE), t(UiKey.PEOPLE_EMPTY_BODY), modifier.padding(horizontal = Space.gutter),
        action = { PrimaryButton(t(UiKey.PEOPLE_ADD_PERSON), onClick = add, modifier = Modifier.padding(top = 8.dp)) },
    )
}

@Composable
internal fun ErrorCard(onRetry: () -> Unit, modifier: Modifier = Modifier, title: String = t(UiKey.PEOPLE_ERROR_TITLE), body: String = t(UiKey.PEOPLE_ERROR_BODY)) {
    Row(
        modifier.fillMaxWidth().background(Ink.alertBg, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
            if (body.isNotBlank()) BasicText(body, style = Type.caption().copy(color = Ink.focus))
        }
        SecondaryButton(t(UiKey.PPL_RETRY), onClick = onRetry)
    }
}
