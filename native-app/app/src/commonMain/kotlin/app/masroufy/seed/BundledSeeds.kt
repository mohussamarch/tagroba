package app.masroufy.seed

import app.masroufy.core.CountryPack
import app.masroufy.core.RawCategoryTree
import app.masroufy.core.RawGroup
import app.masroufy.core.RawMain
import app.masroufy.core.RawRule
import app.masroufy.core.RawSub
import app.masroufy.core.buildCountryCategoryTree
import app.masroufy.core.loadReferences
import app.masroufy.port.SeedSource
import app.masroufy.port.SpaceSeedSourceLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * المراجع الأولية لحساب أو بلد جديدة **من جوه التطبيق** (من غير نت ولا ملفات على الجهاز): شجرة التصنيفات + فروق البلد
 * ([buildCountryCategoryTree]) وقواعد التصنيف العامة. **التجار ما بيتزرعوش** (مشتركين على مستوى الحساب وبيتعلموا من الاستعمال — زي `ManageSpaces`).
 * نفس قراية `RealSeeds` في الاختبارات بالظبط، والاختبار بيتأكد إن النسخة المضمّنة = ملفات المستودع.
 */
object BundledSeeds : SpaceSeedSourceLoader {
    private val tree: RawCategoryTree by lazy { parseCategoryTree(CATEGORY_TREE_JSON) }
    private val rules: List<RawRule> by lazy {
        Json.parseToJsonElement(RULE_REFERENCE_JSON).jsonArray.map { RawRule(it.opt("word"), it.opt("cat")) }
    }

    override suspend fun load(pack: CountryPack): SeedSource {
        val built = buildCountryCategoryTree(tree, pack)
        return SeedSource(built.categories, loadReferences(rules, emptyList(), built.categories, built).rules, emptyList())
    }
}

private fun JsonElement.field(name: String): JsonElement = jsonObject[name] ?: JsonNull

private fun JsonElement.opt(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

private fun JsonElement.str(): String = jsonPrimitive.content

internal fun parseCategoryTree(text: String): RawCategoryTree {
    val e = Json.parseToJsonElement(text)
    return RawCategoryTree(
        e.field("groups").jsonArray.map { g ->
            RawGroup(
                g.field("key").str(),
                g.field("mains").jsonArray.map { m ->
                    val noCar = m.jsonObject["noCar"]?.takeIf { it !is JsonNull }
                    RawMain(
                        m.field("name").str(), m.field("icon").str(),
                        m.field("h").str().toDouble(), m.field("s").str().toDouble(), m.field("l").str().toDouble(),
                        m.jsonObject["from"]?.jsonArray?.map { it.str() }.orEmpty(), m.opt("requires"), noCar?.opt("name"), noCar?.opt("icon"),
                        m.field("subs").jsonArray.map { s -> val a = s.jsonArray; RawSub(a[0].str(), a[1].str(), a.getOrNull(2)?.str()) },
                    )
                },
            )
        },
        e.jsonObject["ruleWordOverrides"]?.jsonObject?.mapValues { (_, v) -> v.jsonArray.map { it.str() } }.orEmpty(),
    )
}
