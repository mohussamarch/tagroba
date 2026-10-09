package app.masroufy.ui.nav

import androidx.compose.runtime.Composable
import app.masroufy.ui.screens.common.PendingScreen
import kotlin.reflect.KClass

/**
 * جدول الشاشات: كل منطقة بتسجّل شاشاتها **من ملفها** (`screens/<المنطقة>/<Area>Routes.kt` — دالة `RouteRegistry.register<Area>()`)،
 * فالمناطق بتتبني بالتوازي من غير ما حد يلمس ملف التاني. القايمة الثابتة للمناطق في `screens/Areas.kt`.
 *
 * ```
 * // screens/people/PeopleRoutes.kt
 * data class PersonProfileRoute(val personId: String) : Route { override val name = "PersonProfile" }
 * fun RouteRegistry.registerPeople() {
 *     tabRoot(Tab.PEOPLE) { PeopleScreen() }
 *     screen<PersonProfileRoute> { route -> PersonProfileScreen(route.personId) }
 * }
 * ```
 * شاشة مش متسجلة ⇒ `PendingScreen` (رجوع + «قيد البناء») بدل ما التطبيق يقع.
 */
class RouteRegistry {
    @PublishedApi internal val screens = HashMap<KClass<out Route>, @Composable (Route) -> Unit>()
    @PublishedApi internal val sheets = HashMap<KClass<out SheetRoute>, @Composable (SheetRoute, () -> Unit) -> Unit>()
    private val roots = HashMap<Tab, @Composable () -> Unit>()

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

    /** لوحة بتتفتح بـ`navigator.open(…)` — [content] بياخد اللوحة ودالة القفل. */
    inline fun <reified R : SheetRoute> sheet(noinline content: @Composable (R, close: () -> Unit) -> Unit) {
        check(R::class !in sheets) { "اللوحة ${R::class.simpleName} متسجّلة مرتين" }
        sheets[R::class] = { route, close -> content(route as R, close) }
    }

    fun has(route: Route): Boolean = if (route is SheetRoute) route::class in sheets else route::class in screens

    fun hasRoot(tab: Tab): Boolean = tab in roots

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
        sheets[route::class]?.invoke(route, close)
    }
}
