package app.masroufy.ui.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** كل أيقونة بتتقري مسارات سليمة (أول تشغيل على المحاكي وقع من سطر `line` اتحوّل غلط — «Unknown command»). */
class LucideTest {
    @Test fun everyIconParses() {
        for (icon in Lucide.all) {
            assertTrue(icon.paths.isNotEmpty(), icon.name)
            assertTrue(icon.paths.none { "undefined" in it || "NaN" in it }, icon.name)
            val v = icon.vector
            assertEquals(24f, v.viewportWidth, icon.name)
        }
    }

    @Test fun theShellIconsExist() {
        val needed = setOf("HOUSE", "LIST", "USERS", "TRENDING_UP", "PLUS", "SETTINGS", "BELL", "MIC", "SPARKLES", "CALENDAR", "BANKNOTE", "X", "CHEVRON_LEFT", "CHEVRON_RIGHT")
        assertTrue(needed.all { n -> Lucide.all.any { it.name == n } })
    }
}
