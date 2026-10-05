package app.masroufy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** شجرة التطبيق الحقيقية وملفات المراجع المرفقة معاه (نفس اللي بيقراها `SeedsGoldenTest`) — بيانات عامة في المستودع، مش بيانات المالك. */
object RealSeeds {
    private val root = File(System.getProperty("golden.dir")).parentFile.parentFile

    private fun read(path: String) = Json.parseToJsonElement(File(root, path).readText())

    private fun JsonElement.opt(name: String) = jsonObject[name]?.takeIf { it !is JsonNull }?.str

    val tree: RawCategoryTree by lazy {
        val e = read("src/infrastructure/import/categoryTree.json")
        RawCategoryTree(
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
    }

    val rules: List<RawRule> by lazy { read("design-source/masroofi-claude-code/fixtures/rule-reference.json").jsonArray.map { RawRule(it.opt("word"), it.opt("cat")) } }

    val merchants: List<RawMerchant> by lazy {
        read("design-source/masroofi-claude-code/fixtures/merchant-reference.json").jsonArray.map { RawMerchant(it.opt("name"), it.opt("cat"), it.opt("confidence")) }
    }
}
