package app.masroufy.ui.screens.home.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Direction
import app.masroufy.core.PeopleTotal
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.home.ReadState
import app.masroufy.ui.screens.home.transactionTitle
import app.masroufy.ui.screens.more.AccountCharacterRoute
import app.masroufy.ui.screens.operations.ReviewQueueRoute
import app.masroufy.ui.character.CharacterArt
import app.masroufy.ui.character.NeutralFigure
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import androidx.compose.foundation.layout.size
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.HomeScreenData

/**
 * باقي كروت الرئيسية (لوحات `HomeBuddy` و`HomeQuick` في النموذج): «شخصيتك بتكلمك» · «محتاجة مراجعة» · «آخر العمليات» · «ليك وعليك» ·
 * «استثماراتك». الأرقام من `LoadHomeScreen` و`LoadPeopleOverview` — مفيش جملة برقم مخترع (CLAUDE.md #10).
 */

/** عدد «آخر العمليات» في الكارت (زي النموذج). */
private const val RECENT_ON_HOME = 3

private val bubble = Color(0xFFEAF3EC)

/**
 * «شخصيتك بتكلمك»: راس «شخصيتك» (`CharacterArt` · `CharacterLayer.HEAD`) من الملف — ولو لسه ما اتبنتش الشكل المحايد (`NeutralFigure`).
 * الضغط ⇒ «ملفك» و«ابنِ شخصيتك» فوقها (`AccountCharacterRoute`).
 * الجملة = توقع صرف الشهر (`HomeScreenData.forecast` — نفس الرئيسية)، ولو البيانات ما تكفيش «لسه بدري».
 */
@Composable
internal fun BuddyCard(state: ReadState<HomeScreenData>, character: CharacterLook?) {
    val nav = LocalNavigator.current
    val currency = LocalSpace.current.space.currency
    val say = (state as? ReadState.Ready)?.value?.forecast?.projectedMinor?.let { t(UiKey.HOME_BUDDY_FORECAST, amountLabel(it, currency)) }
        ?: t(UiKey.HOME_BUDDY_EARLY)
    FloatingCard(Modifier.fillMaxWidth(), onClick = { nav.push(AccountCharacterRoute) }, clickLabel = t(UiKey.HOME_BUDDY_OPEN)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (character != null) CharacterArt(character, CharacterLayer.HEAD, Modifier.size(64.dp)) else NeutralFigure(Modifier.size(64.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).background(bubble).padding(horizontal = 12.dp, vertical = 10.dp)) {
                BasicText(say, style = Type.of(13, lineHeight = 1.6).copy(color = Ink.text))
            }
        }
    }
}

@Composable
internal fun ReviewCard(state: ReadState<HomeScreenData>) {
    val nav = LocalNavigator.current
    val count = (state as? ReadState.Ready)?.value?.needsReviewCount
    val sub = when {
        count == null -> null
        count == 0 -> t(UiKey.HOME_REVIEW_NONE)
        else -> t(UiKey.HOME_REVIEW_COUNT, count.toString())
    }
    CardShell(state, Lucide.CHECK, Ink.focus, t(UiKey.HOME_CARD_REVIEW), sub, onClick = { nav.push(ReviewQueueRoute) }, loadingHeight = 78) { _ -> }
}

@Composable
internal fun RecentCard(state: ReadState<HomeScreenData>) {
    val nav = LocalNavigator.current
    CardShell(state, Lucide.LIST, Ink.muted, t(UiKey.HOME_CARD_RECENT), t(UiKey.HOME_RECENT_SUB), onClick = { nav.switchTab(Tab.OPERATIONS) }) { data ->
        val rows = data.latest.take(RECENT_ON_HOME)
        if (rows.isEmpty()) BasicText(t(UiKey.HOME_EMPTY_TITLE), style = Type.caption().copy(color = Ink.muted))
        rows.forEach { tx ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(transactionTitle(tx), style = Type.of(13), modifier = Modifier.weight(1f), maxLines = 1)
                val tone = if (tx.observedDirection == Direction.IN) AmountTone.INCOME else AmountTone.EXPENSE
                AmountText(tx.amountMinor, tx.currency, tone = tone, size = 13)
            }
        }
    }
}

@Composable
internal fun PeopleCard(state: ReadState<PeopleTotal?>) {
    val nav = LocalNavigator.current
    val currency = LocalSpace.current.space.currency
    CardShell(state, Lucide.USERS, Ink.transfer, t(UiKey.HOME_CARD_PEOPLE), t(UiKey.HOME_PEOPLE_SUB), onClick = { nav.switchTab(Tab.PEOPLE) }) { total ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Side(t(UiKey.HOME_PEOPLE_OWED), total?.owedToYouMinor ?: 0L, total?.currency ?: currency, Ink.income, Modifier.weight(1f))
            Side(t(UiKey.HOME_PEOPLE_OWE), total?.youOweMinor ?: 0L, total?.currency ?: currency, Ink.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Side(label: String, minor: Long, currency: app.masroufy.core.Currency, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x0A193D33)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.caption().copy(color = Ink.muted))
        AmountText(minor, currency, size = 18, color = color)
    }
}

/**
 * «استثماراتك النهارده» (مواصفة النقل §1.14 — `LoadInvestToday`): التغيّر عن آخر يوم اتشاف لنفس الحاجات (+1.2% و«+M منذ أمس» أو «منذ
 * تاريخ»). مفيش يوم قبل كده أو القراية فشلت ⇒ «غير متاح» (مش رقم مخترع). الضغط بيفتح الاستثمار. بيقرا لوحده (الكارت مخفي افتراضيًا §96-2).
 */
@Composable
internal fun InvestCard() {
    val nav = LocalNavigator.current
    val deps = LocalSpace.current
    val changes = app.masroufy.ui.app.LocalDataChanges.current
    var view by androidx.compose.runtime.remember(deps) { androidx.compose.runtime.mutableStateOf<ReadState<app.masroufy.usecase.InvestTodayView?>>(ReadState.Loading) }
    androidx.compose.runtime.LaunchedEffect(deps, changes.version) {
        view = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { deps.home.cards.investToday.load(deps.space.id, deps.shell.today()) }.fold({ ReadState.Ready(it) }, { ReadState.Failed })
        }
    }
    FloatingCard(Modifier.fillMaxWidth(), onClick = { nav.switchTab(Tab.INVESTMENT) }, clickLabel = t(UiKey.HOME_CARD_INVEST)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHead(Lucide.TRENDING_UP, Ink.income, t(UiKey.HOME_CARD_INVEST), t(UiKey.HOME_INVEST_SUB))
            val v = (view as? ReadState.Ready)?.value
            if (v == null) {
                if (view != ReadState.Loading) BasicText(t(UiKey.HOME_INVEST_NA), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
            } else {
                InvestTodayLine(v)
            }
        }
    }
}

/** سطر «+1.2%  +M من امبارح» (`HomeQuick` kind=invest) — منفصل عشان اختبار الرسم. */
@Composable
internal fun InvestTodayLine(v: app.masroufy.usecase.InvestTodayView) {
    val ink = if (v.gainMinor < 0) Ink.expense else Ink.income
    val sign = if (v.gainMinor > 0) "+" else ""
    val amount = sign + amountLabel(v.gainMinor, v.currency, AmountTone.PLAIN, true)
    val since = v.sinceDate?.let { t(app.masroufy.core.InvestInsightKey.HOME_SINCE_DATE, amount, app.masroufy.core.dayMonth(it)) }
        ?: t(app.masroufy.core.InvestInsightKey.HOME_SINCE_YESTERDAY, amount)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) { // `HomeQuick` kind=invest: 28 عريض + 13
        BasicText("⁦" + pctTenths(v.pctBp) + "⁩", style = Type.of(28, FontWeight.Bold, 1.2).copy(color = ink))
        BasicText(since, Modifier.padding(bottom = 5.dp), style = Type.of(13).copy(color = Ink.soft))
    }
}

/** «+1.2%» من نقاط الأساس (عرض بس: عُشر بالتقريب النص لبعيد عن الصفر). */
internal fun pctTenths(bp: Int): String {
    val abs = if (bp < 0) -bp else bp
    val tenths = (abs + 5) / 10
    val sign = if (bp > 0) "+" else if (bp < 0) "−" else ""
    return "$sign${tenths / 10}.${tenths % 10}%"
}
