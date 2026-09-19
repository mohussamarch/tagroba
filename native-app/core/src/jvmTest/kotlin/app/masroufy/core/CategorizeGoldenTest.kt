package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** التصنيف بالأولوية والتجار والقواعد و«افتكر المحل» (native-app/golden/categorize.json). */
class CategorizeGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("categorize", fn) { json(run(it)) }
    private fun JsonElement.opt(name: String) = jsonObject[name]?.takeIf { it !is JsonNull }?.str

    private fun merchants(e: JsonElement) = e.jsonArray.map {
        Merchant(
            it.field("id").str, it.field("displayName").str, it.field("normalizedName").str,
            aliases = it.jsonObject["aliases"]?.jsonArray?.map { a -> a.str }, verifiedCategoryId = it.opt("verifiedCategoryId"),
        )
    }

    private fun rules(e: JsonElement) = e.jsonArray.map {
        ClassificationRule(
            it.field("id").str, it.field("priority").jsonPrimitive.int, it.field("matchText").str,
            RuleMatchMode.fromWire(it.field("matchMode").str), it.field("categoryId").str, it.field("enabled").jsonPrimitive.boolean,
        )
    }

    private fun Merchant.toJson() = obj(
        "id" to id, "displayName" to displayName, "normalizedName" to normalizedName, "aliases" to aliases, "verifiedCategoryId" to verifiedCategoryId,
    )

    @Test fun prepareRulesAndIndex() {
        // الترتيب والفهرس اتقاسوا على نفس القواعد والتجار اللي في حالات categorize
        val first = Golden.cases("categorize", "categorize").first()["in"]!!
        Golden.check("categorize", "prepareRules") { json(prepareRules(rules(first.field("rules"))).map { r -> r.id }) }
        Golden.check("categorize", "merchantIndex") { obj(*merchantIndex(merchants(first.field("merchants"))).map { (k, m) -> k to m.id }.toTypedArray()) }
    }

    @Test fun matchesText() {
        check("matchesText") { matchesText(it.field("needle").str, RuleMatchMode.fromWire(it.field("mode").str), it.field("haystack").str) }
    }

    @Test fun categorize() {
        check("categorize") {
            val input = it.field("input")
            val deps = CategorizeDeps(
                merchantIndex(merchants(it.field("merchants"))),
                prepareRules(rules(it.field("rules"))),
                it.field("categoryNames").jsonObject.mapValues { (_, v) -> v.str },
            )
            val r = categorize(
                CategorizationInput(
                    currentConfirmed = input.field("currentConfirmed").jsonPrimitive.boolean, currentCategoryId = input.opt("currentCategoryId"),
                    merchantName = input.opt("merchantName"), description = input.opt("description"), sourceCategory = input.opt("sourceCategory"),
                ),
                deps,
            )
            obj("categoryId" to r.categoryId, "source" to r.source.wire, "reviewState" to r.reviewState.wire, "reason" to r.reason, "matchedBy" to r.matchedBy)
        }
    }

    @Test fun rememberMerchant() {
        check("rememberMerchant") {
            rememberMerchant(merchants(it.field("merchants")), it.field("name").str, it.field("categoryId").str, it.field("newId").str)?.toJson()
        }
    }
}
