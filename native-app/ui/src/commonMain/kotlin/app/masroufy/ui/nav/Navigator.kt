package app.masroufy.ui.nav

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.masroufy.core.TextKey
import app.masroufy.ui.icons.Lucide

/**
 * التنقل **من غير مكتبة تنقل** (ARCHITECTURE §31.31) — نفس قواعد النموذج (`App.dc.html`):
 * - **تبويب** (الرئيسية · العمليات · الأشخاص · الاستثمار) = تبديل سريع، والرجوع بيتمسح. الضغط على نفس التبويب وإنت عليه = ولا حاجة.
 * - **شاشة داخلية** ([push]) = بتدخل من الشمال (في العربي) فوق التبويب ومن غير شريط التنقل. **الرجوع** ([pop]) = بتخرج.
 * - **لوحة** ([open] بـ[SheetRoute]) = بتطلع من تحت فوق أي حاجة. زرار الرجوع بيقفل اللوحة الأول.
 * - حالة كل شاشة **بتتحفظ** لما تسيبها وترجعلها، على أي عمق، ومنها التبويبات الأربعة (OVERRIDES §85): الخانات والتبويب الفرعي والفلتر
 *   والمفتوح بـ`rememberSaveable` (`AppShell` — `SaveableStateHolder` بمفتاح الخانة)، والتمرير في [scrolls] (`EntryScroll.kt` — بيرجّعه
 *   حتى لو المحتوى لسه بيحمّل).
 * زرار الرجوع ([back]): لوحة ⇒ شاشة داخلية ⇒ تبويب غير الرئيسية ⇒ الرئيسية ⇒ `false` (التطبيق يتقفل).
 */
interface Route {
    /** اسم اللوحة في النموذج = اسم الشاشة في كوتلن (KOTLIN-MAP §1 — `SCREENS.md`). */
    val name: String
}

/** لوحة بتطلع من تحت وبتتفتح من أكتر من مكان (`navigator.open(…)`). اللوحة الخاصة بشاشة واحدة بتتكتب جوه الشاشة بـ`Sheet` على طول. */
interface SheetRoute : Route

enum class Tab(val label: TextRef, val icon: Lucide, val boardName: String) {
    HOME(UiKey.TAB_HOME, Lucide.HOUSE, "Main"),
    OPERATIONS(UiKey.TAB_OPERATIONS, Lucide.LIST, "Operations"),
    PEOPLE(UiKey.TAB_PEOPLE, Lucide.USERS, "People"),
    INVESTMENT(UiKey.TAB_INVESTMENT, Lucide.TRENDING_UP, "Investment"),
}

/** نوع الحركة لآخر تغيير (`AppShell` بيرسم بيها): تبويب · دخول · رجوع. */
enum class NavMotion { NONE, TAB, PUSH, POP }

/** سطر في الرصة — [id] ثابت للسطر ده بالذات (حالة الشاشة بتتحفظ بيه). */
data class StackEntry(val id: Long, val route: Route)

@Stable
class Navigator(start: Tab = Tab.HOME) {
    var tab: Tab by mutableStateOf(start)
        private set

    private val screens = mutableStateListOf<StackEntry>()
    private val openSheets = mutableStateListOf<StackEntry>()
    private var nextId = 1L

    var motion: NavMotion by mutableStateOf(NavMotion.NONE)
        private set

    /** الشاشات الداخلية فوق التبويب (الأقدم الأول). */
    val stack: List<StackEntry> get() = screens

    /** اللوحات المفتوحة من [open] (الأقدم الأول). */
    val sheets: List<StackEntry> get() = openSheets

    /** الشاشة الظاهرة (null = التبويب نفسه). */
    val top: StackEntry? get() = screens.lastOrNull()

    /** على التبويب نفسه ⇒ شريط التنقل وشريط السؤال ظاهرين. */
    val atTabRoot: Boolean get() = screens.isEmpty()

    /** مفتاح الشاشة الظاهرة — لحفظ حالتها. */
    val visibleKey: String get() = top?.let { keyOf(it) } ?: "tab-${tab.name}"

    /**
     * مكان التمرير لكل خانة في السجل (OVERRIDES §85): بيتكتب وإنت خارج من الشاشة، وبيرجع مع الرجوع على أي عمق، والتبويبات الأربعة كل واحد
     * بمكانه. الخانة اللي بتخرج من السجل مكانها بيتمسح معاها (`ScrollMemory.kt` · `EntryScroll.kt`).
     */
    val scrolls = ScrollMemory(::isLive)

    /** الخانة دي لسه في السجل؟ (التبويبات الأربعة دايمًا.) */
    fun isLive(key: String): Boolean = key.startsWith("tab-") || screens.any { keyOf(it) == key }

    private fun keyOf(e: StackEntry) = "s${e.id}"

    private fun entry(route: Route) = StackEntry(nextId++, route)

    /** يفتح شاشة داخلية (أو لوحة لو [route] لوحة). */
    fun push(route: Route) {
        app.masroufy.perf.PerfTrace.log("nav push ${route.name}")
        if (route is SheetRoute) {
            open(route)
            return
        }
        screens += entry(route)
        motion = NavMotion.PUSH
    }

    /** يقفل الشاشة الظاهرة. `false` = مفيش شاشة داخلية (إنت على التبويب). */
    fun pop(): Boolean {
        if (screens.isEmpty()) return false
        app.masroufy.perf.PerfTrace.log("nav pop")
        screens.removeAt(screens.lastIndex)
        scrolls.prune()
        motion = NavMotion.POP
        return true
    }

    /** يرجع لحد أقرب شاشة اسمها [name] (لو موجودة)، وإلا ولا حاجة. */
    fun popTo(name: String): Boolean {
        val i = screens.indexOfLast { it.route.name == name }
        if (i < 0 || i == screens.lastIndex) return false
        while (screens.lastIndex > i) screens.removeAt(screens.lastIndex)
        scrolls.prune()
        motion = NavMotion.POP
        return true
    }

    /** يبدّل الشاشة الظاهرة بغيرها من غير ما الرجوع يرجعلها (مثلًا بعد الحفظ). */
    fun replace(route: Route) {
        if (screens.isNotEmpty()) screens.removeAt(screens.lastIndex)
        screens += entry(route)
        scrolls.prune()
        motion = NavMotion.PUSH
    }

    /**
     * تبديل تبويب: الشاشات الداخلية بتتمسح (زي النموذج)، و**كل تبويب بيرجع لمكانه** (التمرير وحالته — §85). نفس التبويب ومفيش شاشة فوقه
     * ⇒ ولا حاجة.
     */
    fun switchTab(target: Tab) {
        if (target == tab && screens.isEmpty()) return
        app.masroufy.perf.PerfTrace.log("nav tab $target")
        tab = target
        screens.clear()
        scrolls.prune()
        motion = NavMotion.TAB
    }

    fun open(sheet: SheetRoute) {
        openSheets += entry(sheet)
    }

    /** يقفل لوحة بعينها (أو آخر واحدة لو [sheet] = null). */
    fun close(sheet: SheetRoute? = null): Boolean {
        val i = if (sheet == null) openSheets.lastIndex else openSheets.indexOfLast { it.route == sheet }
        if (i < 0) return false
        openSheets.removeAt(i)
        return true
    }

    /** r041b P2: شاشات بتمسك زرار الرجوع بنفسها وهي فوق (أول تشغيل ⇒ الكارت اللي قبله) — بالاسم، ومن غيرها [pop] عادي. */
    private val backHolders = mutableMapOf<String, () -> Boolean>()

    /** [handler] = null بيشيل الإمساك. الرد true = الشاشة عملت حاجة · false = سيب الجهاز يقفل التطبيق (من غير ما الشاشة تتشال). */
    fun holdBack(route: Route, handler: (() -> Boolean)?) {
        if (handler == null) backHolders.remove(route.name) else backHolders[route.name] = handler
    }

    /** زرار الرجوع. `false` = الرئيسية من غير حاجة مفتوحة (أو شاشة ماسكة الرجوع قالت «سيبه») ⇒ التطبيق نفسه يتقفل. */
    fun back(): Boolean = when {
        openSheets.isNotEmpty() -> close()
        screens.isNotEmpty() -> backHolders[screens.last().route.name]?.invoke() ?: pop()
        tab != Tab.HOME -> {
            switchTab(Tab.HOME)
            true
        }
        else -> false
    }

    /** بداية جديدة (بعد الدخول أو تبديل البلد): الرئيسية من غير أي شاشة أو لوحة. */
    fun reset(target: Tab = Tab.HOME) {
        openSheets.clear()
        screens.clear()
        scrolls.clear()
        tab = target
        motion = NavMotion.NONE
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator مش متقدّم — الشاشة لازم تبقى جوه AppShell") }
