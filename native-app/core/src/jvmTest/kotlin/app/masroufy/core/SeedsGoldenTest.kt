package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** المراجع الأولية بنفس المعرّفات بالظبط + كشف نوع الملف (native-app/golden/seeds.json). */
class SeedsGoldenTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("seeds", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private val root = File(System.getProperty("golden.dir")).parentFile.parentFile

    private fun readJson(path: String) = kotlinx.serialization.json.Json.parseToJsonElement(File(root, path).readText())
    private fun JsonElement.opt(name: String) = jsonObject[name]?.takeIf { it !is JsonNull }?.str

    private fun tree(e: JsonElement) = RawCategoryTree(
        e.field("groups").jsonArray.map { g ->
            RawGroup(
                g.field("key").str,
                g.field("mains").jsonArray.map { m ->
                    val noCar = m.jsonObject["noCar"]
                    RawMain(
                        m.field("name").str, m.field("icon").str, m.field("h").jsonPrimitive.double, m.field("s").jsonPrimitive.double, m.field("l").jsonPrimitive.double,
                        m.jsonObject["from"]?.jsonArray?.map { it.str }.orEmpty(), m.opt("requires"), noCar?.opt("name"), noCar?.opt("icon"),
                        m.field("subs").jsonArray.map { s -> val a = s.jsonArray; RawSub(a[0].str, a[1].str, a.getOrNull(2)?.str) },
                    )
                },
            )
        },
        e.jsonObject["ruleWordOverrides"]?.jsonObject?.mapValues { (_, v) -> v.jsonArray.map { it.str } }.orEmpty(),
    )
    private val appTree by lazy { tree(readJson("src/infrastructure/import/categoryTree.json")) }
    private fun Category.toJson() = JsonObject(
        buildMap {
            put("id", json(id)); put("parentId", nullable(parentId)); put("name", json(name)); put("iconKey", json(iconKey))
            put("lightColor", json(lightColor)); put("darkColor", json(darkColor)); put("active", json(active)); put("order", json(order))
            groupKey?.let { put("groupKey", json(it)) }; requires?.let { put("requires", json(it)) }
            noCarName?.let { put("noCarName", json(it)) }; noCarIconKey?.let { put("noCarIconKey", json(it)) }
        },
    )
    private fun BuiltCategoryTree.toJson() = obj(
        "categories" to categories.map { it.toJson() },
        "aliases" to aliases.map { (k, v) -> listOf(k, v) },
        "wordOverrides" to wordOverrides.map { (k, v) -> listOf(k, v) },
    )
    private fun rules(e: JsonElement) = e.jsonArray.map { RawRule(it.opt("word"), it.opt("cat")) }
    private fun merchants(e: JsonElement) = e.jsonArray.map { RawMerchant(it.opt("name"), it.opt("cat"), it.opt("confidence")) }
    private fun LoadedReferences.toJson() = obj(
        "rules" to rules.map { r -> obj("id" to r.id, "priority" to r.priority, "matchText" to r.matchText, "matchMode" to r.matchMode.wire, "categoryId" to r.categoryId, "enabled" to r.enabled) },
        "merchants" to merchants.map { m -> obj("id" to m.id, "displayName" to m.displayName, "normalizedName" to m.normalizedName, "verifiedCategoryId" to m.verifiedCategoryId) },
        "unknownCategoryNames" to unknownCategoryNames, "unverifiedMerchantCount" to unverifiedMerchantCount,
    )

    @Test fun categoryTree() {
        check("buildCategoryTree") { if (it is JsonArray || it.jsonPrimitiveOrNull() != null) buildCategoryTree(appTree).toJson() else buildCategoryTree(tree(it.field("raw"))).toJson() }
    }

    @Test fun references() {
        val built = buildCategoryTree(appTree)
        val rawRules = rules(readJson("design-source/masroofi-claude-code/fixtures/rule-reference.json"))
        val rawMerchants = merchants(readJson("design-source/masroofi-claude-code/fixtures/merchant-reference.json"))
        check("loadReferences") {
            when (it.str) {
                "with-tree" -> loadReferences(rawRules, rawMerchants, built.categories, built).toJson()
                "no-tree" -> loadReferences(rawRules, rawMerchants, built.categories).toJson()
                else -> loadReferences(
                    listOf(RawRule(" ", "x"), RawRule("A", " "), RawRule("B", "مش موجود")),
                    listOf(RawMerchant(" ", "x", "مؤكد"), RawMerchant("dup", "سفر", "مؤكد"), RawMerchant("DUP", "x", "مؤكد"), RawMerchant("y", "يحتاج تأكيد", "مؤكد"), RawMerchant("z", "مش موجود", " مؤكد ")),
                    built.categories, built,
                ).toJson()
            }
        }
    }

    /** شجرة مصر (§64) = شجرة التطبيق الحقيقية + الفروق — بتتبني، والقواعد بتلاقي تصنيفاتها زي السعودية بالظبط. */
    @Test fun egyptTreeFromTheRealAppTree() {
        val saudi = buildCountryCategoryTree(appTree, SAUDI_PACK)
        val egypt = buildCountryCategoryTree(appTree, EGYPT_PACK)
        kotlin.test.assertEquals(buildCategoryTree(appTree), saudi)
        kotlin.test.assertEquals(saudi.categories.size + 1, egypt.categories.size, "فرعي واحد زيادة («سايس»)")
        for (name in listOf("باركنج", "سايس", "موبايل")) kotlin.test.assertEquals(1, egypt.categories.count { it.name == name }, name)
        for (name in listOf("مواقف وسايس", "جوال")) kotlin.test.assertEquals(0, egypt.categories.count { it.name == name }, name)
        val rawRules = rules(readJson("design-source/masroofi-claude-code/fixtures/rule-reference.json"))
        val rawMerchants = merchants(readJson("design-source/masroofi-claude-code/fixtures/merchant-reference.json"))
        val inSaudi = loadReferences(rawRules, rawMerchants, saudi.categories, saudi)
        val inEgypt = loadReferences(rawRules, rawMerchants, egypt.categories, egypt)
        kotlin.test.assertEquals(inSaudi.unknownCategoryNames, inEgypt.unknownCategoryNames, "ولا قاعدة ضاعت بسبب تغيير الاسم")
        kotlin.test.assertEquals(inSaudi.rules.size, inEgypt.rules.size)
    }

    @Test fun files() {
        check("guessSourceType") { guessSourceType(it.str) }
        check("inspectFile") { val c = inspectFile(it.field("name").str, it.field("content").str); obj("kind" to c.kind, "ok" to c.ok, "message" to c.message).let { o -> JsonObject(o + ("message" to nullable(c.message))) } }
    }

    private fun JsonElement.jsonPrimitiveOrNull() = (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }
}
