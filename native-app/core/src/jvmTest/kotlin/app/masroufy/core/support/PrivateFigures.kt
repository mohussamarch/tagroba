package app.masroufy.core

import java.io.File

/**
 * أرقام المالك المستهدفة لاختبارات الكشف الحقيقي (OVERRIDES §120-1): مش مكتوبة في الكود (المستودع عام) —
 * بتتقري من `PRIVATE-FIGURES.md` (مستبعد من Git)، سطور `مفتاح=قيمة`. الـGradle بيمرر مكانه، ومن غيره الاختبار ما بيشتغلش.
 */
internal object PrivateFigures {
    private val values: Map<String, String> by lazy {
        val path = System.getProperty("masroufy.privateFigures") ?: error("PRIVATE-FIGURES.md مش متحدد")
        parseFigures(File(path).readText(Charsets.UTF_8))
    }

    operator fun get(key: String): String = values[key] ?: error("«$key» مش موجود في PRIVATE-FIGURES.md")
    fun int(key: String): Int = get(key).toInt()
}

/** سطور `مفتاح=قيمة` (حروف إنجليزي بس في المفتاح) — أي سطر تاني بيتساب. */
internal fun parseFigures(text: String): Map<String, String> = text.lineSequence()
    .map { it.trim() }
    .mapNotNull { Regex("^([A-Za-z][A-Za-z0-9]*)=(.+)$").matchEntire(it) }
    .associate { it.groupValues[1] to it.groupValues[2].trim() }
