package app.masroufy.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * r06-polish (المظهر الليلي — الجزء الأول): عدد الألوان الثابتة `Color(0x…)` في الشاشات (برّه `theme/`) **بينزل بس**.
 * لون ثابت ما بيتبدلش مع الغامق ⇒ كل لون جديد يتعمل في `Tokens.kt` (`Ink` · `CategoryInk`). لما تبدّل ألوان، نزّل [BUDGET] للعدد الجديد.
 * قبل الجولة: 237 · بعدها: 166.
 */
class HardcodedColorBudgetTest {
    private val budget = BUDGET

    @Test
    fun hardcodedColorsOutsideThemeOnlyGoDown() {
        val root = listOf("src/commonMain/kotlin/app/masroufy/ui", "ui/src/commonMain/kotlin/app/masroufy/ui").map(::File).first { it.isDirectory }
        val literal = Regex("""Color\(0x""")
        val perFile = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "theme" }
            .associate { it.relativeTo(root).path to literal.findAll(it.readText()).count() }
            .filterValues { it > 0 }
        val total = perFile.values.sum()
        val top = perFile.entries.sortedByDescending { it.value }.take(10).joinToString { "${it.key}=${it.value}" }
        assertTrue(total <= budget, "ألوان ثابتة برّه theme/: $total > $budget — استعمل Ink/CategoryInk. الأكتر: $top")
        assertTrue(total > 0 || budget == 0, "العدد نزل لـ$total — نزّل BUDGET")
    }

    companion object {
        const val BUDGET = 166
    }
}
