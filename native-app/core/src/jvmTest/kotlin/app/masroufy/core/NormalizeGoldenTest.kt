package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** `normalize.ts` — نفس الطبعنة بالحرف (native-app/golden/normalize.json). */
class NormalizeGoldenTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun check(fn: String, run: (kotlinx.serialization.json.JsonElement) -> Any?) =
        Golden.check("normalize", fn) { json(run(it)) }

    @Test fun latinizeDigits() { check("latinizeDigits") { latinizeDigits(it.str) } }
    @Test fun normalizeText() { check("normalizeText") { normalizeText(it.str) } }
    @Test fun normalizeCompact() { check("normalizeCompact") { normalizeCompact(it.str) } }

    @Test fun normalizedContains() {
        check("normalizedContains") { normalizedContains(it.field("haystack").str, it.field("needle").str) }
    }
}
