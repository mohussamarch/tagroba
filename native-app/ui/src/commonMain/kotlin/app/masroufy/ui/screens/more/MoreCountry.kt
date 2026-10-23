package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.R06Key
import app.masroufy.core.Space
import app.masroufy.core.UiKey
import app.masroufy.core.bothCountriesActive
import app.masroufy.ui.app.CountryMark
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.overlay.QuickMenu
import app.masroufy.ui.overlay.QuickMenuHost
import app.masroufy.ui.overlay.QuickTarget
import app.masroufy.ui.shell.countryLabel
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Type

/**
 * علم البلد جنب كارت الملف في «المزيد» (OVERRIDES §116-2 · `More.dc.html` فرع التصميم c37ce76a) — بدل سطر «البلدان».
 * - **الضغطة** على العلم ⇒ تبديل للبلد التانية (نفس `ShellDeps.switchSpace` بتاع شارة الرئيسية).
 * - **الضغطة الطويلة** (على العلم أو على كارت الملف) ⇒ «القايمة السريعة» (الاسم الثابت عند المالك — نفس `QuickMenu`/`LiquidMenu` بتاع العمليات):
 *   «+ ضيف بلد» · «أرشف <البلد>» (نفس لوحات وحالات استخدام [SpacesScreen]) · «إدارة البلدان» (`SpacesRoute` — الطريق الوحيد ليها من «المزيد»).
 * - بلد واحدة ⇒ **مفيش علم** (نفس قاعدة `CountriesInfo`: بلد واحدة = ولا علامة)؛ القايمة السريعة بتفضل على كارت الملف عشان «ضيف بلد».
 */

/** البلد اللي العلم بيبدّل لها — null ⇒ العلم مش ظاهر (بلد واحدة شغالة). دالة نقية. */
fun swapTarget(active: Space, open: List<Space>): Space? =
    if (bothCountriesActive(open)) open.firstOrNull { it.id != active.id && !it.archived } else null

enum class CountryAct { ADD, ARCHIVE, MANAGE }

data class CountryMenuItem(val act: CountryAct, val space: Space? = null)

/** بنود القايمة السريعة: «ضيف بلد» دايمًا · «أرشف» للبلد اللي تتأرشف (السعودية ما بتتأرشفش — §64-3) · «إدارة البلدان». */
fun countryMenu(open: List<Space>): List<CountryMenuItem> = listOfNotNull(
    CountryMenuItem(CountryAct.ADD),
    open.firstOrNull { it.id != DEFAULT_SPACE_ID && !it.archived }?.let { CountryMenuItem(CountryAct.ARCHIVE, it) },
    CountryMenuItem(CountryAct.MANAGE),
)

/** حالة القايمة السريعة واللوحات — بتتعمل في [MoreScreen] وتترسم برّه القايمة الطويلة ([CountryHosts]). */
@Stable
class CountryMenuState(val quick: QuickMenu) {
    var sheet: SpaceSheet? by mutableStateOf(null)
    var busy: Boolean by mutableStateOf(false)
}

@Composable
fun rememberCountryMenu(): CountryMenuState = remember { CountryMenuState(QuickMenu()) }

/** صف الملف: كارت الاسم (ضغطة ⇒ «ملفك») + العلم على شماله لو البلدين شغالين. */
@Composable
internal fun MeRow(
    me: MeInfo?,
    view: MoreView,
    active: Space,
    open: List<Space>,
    state: CountryMenuState,
    menuItems: () -> List<MenuItem>,
    onProfile: () -> Unit,
    onSwap: (Space) -> Unit,
) {
    val target = swapTarget(active, open)
    val title = t(R06Key.MR_QUICK_TITLE)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickTarget(state.quick, title, menuItems, Modifier.weight(1f).fillMaxHeight(), subtitle = view.spaceLine) { long ->
            MeCardBody(me, view, onLongClick = long, onClick = state.quick.guardTap(onProfile))
        }
        if (target != null) QuickTarget(state.quick, title, menuItems, Modifier.width(72.dp).fillMaxHeight(), subtitle = view.spaceLine) { long ->
            FlagTile(active, target, onLongClick = long, onClick = state.quick.guardTap { onSwap(target) })
        }
    }
}

/** مربع العلم (72 عرض · زاوية 22 — زي النموذج): علم البلد الحالية كبير واسمها تحته. */
@Composable
private fun FlagTile(active: Space, target: Space, onLongClick: () -> Unit, onClick: () -> Unit) {
    FloatingCard(
        Modifier.fillMaxWidth().fillMaxHeight(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(vertical = 12.dp),
        onClick = onClick, onLongClick = onLongClick, clickLabel = t(R06Key.MR_SWAP_TO, countryLabel(target)),
    ) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            // `CountryMark` ثابت 16dp (علامة الكروت) ⇒ مكبّر هنا بدل رسمة جديدة
            CountryMark(active.countryCode, Modifier.scale(2.1f))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BasicText(countryLabel(active), style = Type.of(12, FontWeight.Bold))
        }
    }
}

/** بنود القايمة كـ`MenuItem` (الأفعال من الشاشة). */
internal fun countryMenuItems(open: List<Space>, onAdd: () -> Unit, onArchive: (Space) -> Unit, onManage: () -> Unit): List<MenuItem> =
    countryMenu(open).map { m ->
        when (m.act) {
            CountryAct.ADD -> MenuItem(t(R06Key.MR_ADD_COUNTRY), Lucide.PLUS, onClick = onAdd)
            CountryAct.ARCHIVE -> MenuItem(t(R06Key.MR_ARCHIVE_COUNTRY, countryLabel(m.space!!)), onClick = { onArchive(m.space) })
            CountryAct.MANAGE -> MenuItem(t(UiKey.SPACE_MANAGE), Lucide.GLOBE, onClick = onManage)
        }
    }

/** القايمة واللوحات (نفس `SpacesSheet` بتاع «البلدان») — مرة واحدة برّه القايمة. */
@Composable
internal fun CountryHosts(
    state: CountryMenuState,
    canAdmin: Boolean,
    open: List<Space>,
    archived: List<Space>,
    onCreate: (String) -> Unit,
    onArchive: (Space) -> Unit,
) {
    QuickMenuHost(state.quick)
    SpacesSheet(state.sheet, canAdmin, state.busy, list = open, archived = archived, onClose = { state.sheet = null }, onCreate = onCreate, onArchive = onArchive)
}
