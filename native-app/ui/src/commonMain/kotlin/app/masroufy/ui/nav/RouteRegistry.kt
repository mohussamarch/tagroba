package app.masroufy.ui.nav

import app.masroufy.core.UiKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.masroufy.core.TextKey
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.screens.common.PendingScreen
import app.masroufy.ui.text.t
import kotlin.reflect.KClass

/**
 * جدول الشاشات: كل منطقة بتسجّل شاشاتها **من ملفها** (`screens/<المنطقة>/<Area>Routes.kt` — دالة `RouteRegistry.register<Area>()`)،
 * فالمناطق بتتبني بالتوازي من غير ما حد يلمس ملف التاني. القايمة الثابتة للمناطق في `screens/Areas.kt`.
 *
 * ```
 * // screens/people/PeopleRoutes.kt  (المسار نفسه متعرّف هناك من الأول — PersonProfileRoute(personId))
 * fun RouteRegistry.registerPeople() {
 *     tabRoot(Tab.PEOPLE) { PeopleScreen() }
 *     screen<PersonProfileRoute> { route -> PersonProfileScreen(route.personId) }
 *     sheet<AddPersonSheetRoute> { route, close -> AddPersonSheet(route, close) }   // جوه: RouteSheet(title, close) { … }
 *     slot(Slots.SOMETHING) { … }                                                  // قطعة بتتعرض جوه شاشة منطقة تانية
 * }
 * ```
 * - شاشة مش متسجلة ⇒ `PendingScreen` (رجوع + «قيد البناء») بدل ما التطبيق يقع.
 * - لوحة مش متسجلة ⇒ لوحة فيها «قيد البناء» (الزرار اللي فتحها ما يبقاش ميت من غير سبب).
 * - قطعة ([slot]) مش متسجلة ⇒ «قيد البناء» مكانها.
 */
class RouteRegistry {
    @PublishedApi internal val screens = HashMap<KClass<out Route>, @Composable (Route) -> Unit>()
    @PublishedApi internal val sheets = HashMap<KClass<out SheetRoute>, @Composable (SheetRoute, () -> Unit) -> Unit>()
    private val roots = HashMap<Tab, @Composable () -> Unit>()
    private val slots = HashMap<String, @Composable () -> Unit>()

    /** جذر تبويب (الشاشة اللي تحت شريط التنقل). */
    fun tabRoot(tab: Tab, content: @Composable () -> Unit) {
        check(tab !in roots) { "التبويب ${tab.name} متسجّل مرتين" }
        roots[tab] = content
    }

    /** شاشة داخلية بنوعها. */
    inline fun <reified R : Route> screen(noinline content: @Composable (R) -> Unit) {
        check(R::class !in screens) { "الشاشة ${R::class.simpleName} متسجّلة مرتين" }
        // غلاف مش cast: الدالة المركّبة على JVM ليها شكل تاني (Composer) فالـcast بيوقع وقت التشغيل
        screens[R::class] = { route -> content(route as R) }
    }

    /**
     * لوحة بتتفتح بـ`navigator.open(…)` من أي منطقة — [content] بياخد اللوحة ودالة القفل، وبيرسم `RouteSheet(title, close) { … }`
     * (بيقفل بحركة الخروج قبل ما يشيلها من الرصة). اللوحة الخاصة بشاشة واحدة ما تحتاجش ده: `Sheet(visible, …)` جوه الشاشة على طول.
     */
    inline fun <reified R : SheetRoute> sheet(noinline content: @Composable (R, close: () -> Unit) -> Unit) {
        check(R::class !in sheets) { "اللوحة ${R::class.simpleName} متسجّلة مرتين" }
        sheets[R::class] = { route, close -> content(route as R, close) }
    }

    /**
     * قطعة بتتعرض **جوه شاشة منطقة تانية** باسمها ([Slots]) — مثلًا خانة «المستحقات» جوه مبدّل «العمليات»: منطقة المستحقات بتسجّلها،
     * وتبويب العمليات بيرسمها بـ`LocalRegistry.current.Slot(Slots.DUES)` من غير ما يعرف كودها.
     */
    fun slot(name: String, content: @Composable () -> Unit) {
        check(name !in slots) { "القطعة $name متسجّلة مرتين" }
        slots[name] = content
    }

    fun has(route: Route): Boolean = if (route is SheetRoute) route::class in sheets else route::class in screens

    fun hasRoot(tab: Tab): Boolean = tab in roots

    fun hasSlot(name: String): Boolean = name in slots

    @Composable
    fun Root(tab: Tab) {
        val content = roots[tab]
        if (content == null) PendingScreen(tab.boardName, showBack = false) else content()
    }

    @Composable
    fun Screen(route: Route) {
        val content = screens[route::class]
        if (content == null) PendingScreen(route.name) else content(route)
    }

    @Composable
    fun Sheet(route: SheetRoute, close: () -> Unit) {
        val content = sheets[route::class]
        if (content != null) content(route, close)
        else RouteSheet(title = route.name, close = close) { EmptyState(t(UiKey.SHELL_PENDING_SCREEN)) }
    }

    @Composable
    fun Slot(name: String) {
        val content = slots[name]
        if (content == null) EmptyState(t(UiKey.SHELL_PENDING_SCREEN)) else content()
    }
}

/** أسماء القطع المشتركة بين المناطق ([RouteRegistry.slot]). قطعة جديدة = سطر هنا (قرار مكتوب) + تسجيلها من منطقتها. */
object Slots {
    /** خانة «المستحقات» (تالت خانة في مبدّل «العمليات») — بتسجّلها منطقة المستحقات، وتبويب العمليات بيرسمها. */
    const val DUES = "Dues"

    /** خانة «الميزانيات» (تاني خانة في مبدّل «العمليات») — بتسجّلها منطقة الميزانيات (`screens/budgets`)، وتبويب العمليات بيرسمها (ARCHITECTURE §31.32). */
    const val BUDGETS = "Budgets"
}

/** الجدول للشاشات اللي محتاجة ترسم قطعة منطقة تانية ([RouteRegistry.Slot]). */
val LocalRegistry = staticCompositionLocalOf<RouteRegistry> { error("RouteRegistry مش متقدّم — الشاشة لازم تبقى جوه AppShell") }
