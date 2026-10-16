package app.masroufy.ui.screens.home.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.masroufy.core.CharacterLook
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.HomeCardKind
import app.masroufy.core.PeopleTotal
import app.masroufy.core.periodForDate
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.screens.home.ReadState
import app.masroufy.ui.screens.home.readState
import app.masroufy.usecase.DailyAllowanceView
import app.masroufy.usecase.GoalRow
import app.masroufy.usecase.HomeScreenData
import app.masroufy.usecase.LoadHomeScreenRequest

/**
 * قراية الكروت الظاهرة بس (المخفي ما بيتقراش): كل قراية لوحدها — فشل واحدة ⇒ الكارت ده بس «تعذّر التحميل»، مش الكروت كلها.
 * [month] = `LoadHomeScreen` مرة واحدة لـ«شخصيتك» و«أكتر حاجة صرفت فيها» و«محتاجة مراجعة» و«آخر العمليات».
 */
internal data class HomeCardsData(
    val daily: ReadState<DailyAllowanceView> = ReadState.Loading,
    val month: ReadState<HomeScreenData> = ReadState.Loading,
    val goals: ReadState<List<GoalRow>> = ReadState.Loading,
    /** null = مفيش ديون في البلد دي (صفر محسوب: «ليك 0 · عليك 0»). */
    val people: ReadState<PeopleTotal?> = ReadState.Loading,
    /** «شخصيتك» المحفوظة على الملف (راس «شخصيتك بتكلمك») — null = لسه ما اتبنتش أو الملف ما اتقراش ⇒ الشكل المحايد. */
    val character: CharacterLook? = null,
)

private val NEEDS_MONTH = setOf(HomeCardKind.BUDDY, HomeCardKind.TOP, HomeCardKind.REVIEW, HomeCardKind.RECENT)

@Composable
internal fun rememberHomeCardsData(visible: List<HomeCardKind>?): HomeCardsData {
    val deps = LocalSpace.current
    val changes = LocalDataChanges.current
    var data by remember(deps) { mutableStateOf(HomeCardsData()) }
    val want = visible?.toSet()
    LaunchedEffect(deps, changes.version, want) {
        if (want == null) return@LaunchedEffect
        val today = deps.shell.today()
        val cards = deps.home.cards
        val profile = runCatching { deps.home.profile.load() }.getOrNull()
        val payday = profile?.payday ?: DEFAULT_PAYDAY
        data = data.copy(character = profile?.character)
        if (HomeCardKind.DAILY in want) data = data.copy(daily = readState { cards.daily.load(today) })
        if (want.any { it in NEEDS_MONTH }) {
            data = data.copy(month = readState { deps.home.homeScreen.load(LoadHomeScreenRequest(periodForDate(today, payday), today, payday, includeHistory = false)) })
        }
        if (HomeCardKind.GOALS in want) data = data.copy(goals = readState { cards.goals.load(today) })
        if (HomeCardKind.PEOPLE in want) {
            data = data.copy(people = readState { deps.people.overview.forSpace(today, deps.space.id).totals.firstOrNull { it.spaceId == deps.space.id } })
        }
    }
    return data
}
