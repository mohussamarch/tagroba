package app.masroufy.seed

import app.masroufy.core.COUNTRY_PACKS
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * تنبيه التصنيف (OVERRIDES §113 · §114-2): المهمة الآلية في المستودع الخاص بتقارن `sharedMerchants` بالقواعد الجاهزة — من
 * `tools/feedback-action/builtin-rules.json` (معرّف القاعدة · كلمتها · تصنيفها في كل بلد · أسامي التصنيفات). الملف ده لازم يفضل نفس
 * القواعد المضمّنة في التطبيق (`BundledSeeds`). بعد أي تغيير في القواعد: `MASROUFY_WRITE_RULES=1` على نفس الاختبار بيكتبه من جديد.
 */
class BuiltInRulesExportTest {
    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return dir ?: fail("ما لقيناش جذر المشروع (settings.gradle.kts)")
    }

    private fun render(): String = runBlocking {
        val packs = COUNTRY_PACKS.values.sortedBy { it.code }
        val loaded = packs.associate { p -> p.code.lowercase() to BundledSeeds.load(p) }
        val ids = loaded.values.flatMap { s -> s.rules.map { it.id } }.toSortedSet()
        val obj = buildJsonObject {
            put("version", 1)
            put("note", "Generated from the app's built-in rules (BuiltInRulesExportTest). Do not edit by hand.")
            putJsonArray("rules") {
                for (id in ids) addJsonObject {
                    put("id", id)
                    val any = loaded.values.firstNotNullOf { s -> s.rules.firstOrNull { it.id == id } }
                    put("word", any.matchText)
                    putJsonObject("category") {
                        for ((country, s) in loaded) s.rules.firstOrNull { it.id == id }?.let { put(country, it.categoryId) }
                    }
                }
            }
            putJsonObject("categoryNames") {
                val names = sortedMapOf<String, String>()
                for (s in loaded.values) for (c in s.categories) names.putIfAbsent(c.id, c.name)
                for ((id, name) in names) put(id, JsonPrimitive(name))
            }
            putJsonArray("countries") { for (c in loaded.keys) add(c) }
        }
        Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj) + "\n"
    }

    @Test fun feedbackActionRulesFileMatchesTheApp() {
        val file = File(root().parentFile, "tools/feedback-action/builtin-rules.json")
        val now = render()
        if (System.getenv("MASROUFY_WRITE_RULES") == "1") {
            file.parentFile.mkdirs()
            file.writeText(now, Charsets.UTF_8)
        }
        if (!file.isFile) fail("مفيش ${file.path} — شغّل الاختبار ده بـMASROUFY_WRITE_RULES=1")
        assertEquals(now, file.readText(Charsets.UTF_8).replace("\r\n", "\n"), "القواعد الجاهزة اتغيرت — اكتب الملف تاني بـMASROUFY_WRITE_RULES=1")
    }
}
