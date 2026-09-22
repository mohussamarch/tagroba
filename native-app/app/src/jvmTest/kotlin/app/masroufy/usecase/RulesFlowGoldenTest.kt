package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Merchant
import app.masroufy.core.RecurringCandidate
import app.masroufy.core.RecurringItem
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test

/** قواعد التصنيف والتجار + الاشتراكات على `rulesFlow.json`. */
class RulesFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str, parentId = e.optStr("parentId"), name = e.field("name").str,
        iconKey = e.field("iconKey").str, lightColor = e.field("lightColor").str, darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean, order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"), requires = e.optStr("requires"),
    )

    private fun merchant(e: JsonElement) = Merchant(
        id = e.field("id").str, displayName = e.field("displayName").str, normalizedName = e.field("normalizedName").str,
        aliases = e.jsonObject["aliases"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.str },
        verifiedCategoryId = e.optStr("verifiedCategoryId"),
    )

    private fun merchantJson(m: Merchant) = EntityJson.obj(
        "id" to m.id, "displayName" to m.displayName, "normalizedName" to m.normalizedName,
        "aliases" to m.aliases?.let { list -> JsonArray(list.map(::json)) },
        "logoAsset" to m.logoAsset, "logoSource" to m.logoSource, "verifiedCategoryId" to m.verifiedCategoryId,
    )

    private fun rule(e: JsonElement) = ClassificationRule(
        id = e.field("id").str, priority = e.field("priority").jsonPrimitive.int, matchText = e.field("matchText").str,
        matchMode = RuleMatchMode.fromWire(e.field("matchMode").str), categoryId = e.field("categoryId").str,
        enabled = e.field("enabled").jsonPrimitive.boolean,
    )

    private fun ruleJson(r: ClassificationRule) = JsonObject(
        mapOf(
            "id" to json(r.id), "priority" to json(r.priority), "matchText" to json(r.matchText),
            "matchMode" to json(r.matchMode.wire), "categoryId" to json(r.categoryId), "enabled" to json(r.enabled),
        ),
    )

    private fun recurringItem(e: JsonElement) = RecurringItem(
        id = e.field("id").str, name = e.field("name").str, merchantKey = e.field("merchantKey").str,
        kind = e.field("kind").str, cycleMonths = e.field("cycleMonths").jsonPrimitive.int,
        expectedMinor = e.field("expectedMinor").jsonPrimitive.long, currency = Currency.valueOf(e.field("currency").str),
        nextDueAt = e.field("nextDueAt").str, active = e.field("active").jsonPrimitive.boolean,
        confirmed = e.field("confirmed").jsonPrimitive.boolean,
    )

    private fun itemJson(i: RecurringItem) = JsonObject(
        mapOf(
            "id" to json(i.id), "name" to json(i.name), "merchantKey" to json(i.merchantKey), "kind" to json(i.kind),
            "cycleMonths" to json(i.cycleMonths), "expectedMinor" to json(i.expectedMinor), "currency" to json(i.currency.name),
            "nextDueAt" to json(i.nextDueAt), "active" to json(i.active), "confirmed" to json(i.confirmed),
        ),
    )

    private fun candidateJson(c: RecurringCandidate) = JsonObject(
        mapOf(
            "name" to json(c.name), "merchantKey" to json(c.merchantKey), "currency" to json(c.currency.name),
            "expectedMinor" to json(c.expectedMinor), "nextDueAt" to json(c.nextDueAt), "cycleMonths" to json(c.cycleMonths),
            "transactionIds" to json(c.transactionIds), "reason" to json(c.reason),
        ),
    )

    @Test
    fun manageRules() {
        Golden.check("rulesFlow", "manageRules") { input ->
            val rules = MemoryRuleRepository(input.field("rules").jsonArray.map(::rule))
            val merchants = MemoryMerchantRepository(input.field("merchants").jsonArray.map(::merchant))
            val manage = ManageRules(
                ManageRulesDeps(
                    rules = rules, merchants = merchants,
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                    ids = SequentialIdGenerator(),
                ),
            )
            val action = input.field("action")

            runBlocking {
                suspend fun stored() = mapOf(
                    "storedRules" to JsonArray(rules.listAll().map(::ruleJson)),
                    "storedMerchants" to JsonArray(merchants.listAll().map(::merchantJson)),
                )
                when (action.field("kind").str) {
                    "listRules" -> JsonObject(
                        mapOf(
                            "rows" to JsonArray(
                                manage.listRules().map {
                                    JsonObject(
                                        mapOf("rule" to ruleJson(it.rule), "categoryName" to json(it.categoryName), "categoryMissing" to json(it.categoryMissing)),
                                    )
                                },
                            ),
                        ),
                    )
                    "listMerchants" -> JsonObject(
                        mapOf(
                            "rows" to JsonArray(
                                manage.listMerchants().map {
                                    JsonObject(mapOf("merchant" to merchantJson(it.merchant), "verifiedCategoryName" to nullable(it.verifiedCategoryName)))
                                },
                            ),
                        ),
                    )
                    "addRule" -> {
                        val created = manage.addRule(
                            matchText = action.field("matchText").str,
                            matchMode = RuleMatchMode.fromWire(action.field("matchMode").str),
                            categoryId = action.field("categoryId").str,
                            priority = action.jsonObject["priority"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int,
                        )
                        JsonObject(mapOf("created" to ruleJson(created)) + stored())
                    }
                    "updateRule" -> {
                        val patch = action.field("patch")
                        manage.updateRule(
                            action.field("id").str,
                            RulePatch(
                                priority = patch.jsonObject["priority"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int,
                                matchText = patch.optStr("matchText"),
                                matchMode = patch.optStr("matchMode")?.let(RuleMatchMode::fromWire),
                                categoryId = patch.optStr("categoryId"),
                                enabled = patch.jsonObject["enabled"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean,
                            ),
                        )
                        JsonObject(stored())
                    }
                    "setRuleEnabled" -> {
                        manage.setRuleEnabled(action.field("id").str, action.field("enabled").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                    "setMerchantCategory" -> {
                        manage.setMerchantCategory(action.field("merchantId").str, action.optStr("categoryId"))
                        JsonObject(stored())
                    }
                    "renameMerchant" -> {
                        manage.renameMerchant(action.field("merchantId").str, action.field("displayName").str)
                        JsonObject(stored())
                    }
                    else -> {
                        manage.addAlias(action.field("merchantId").str, action.field("raw").str)
                        JsonObject(stored())
                    }
                }
            }
        }
    }

    @Test
    fun manageRecurring() {
        Golden.check("rulesFlow", "manageRecurring") { input ->
            val items = MemoryRecurringRepository(input.field("items").jsonArray.map(::recurringItem))
            val manage = ManageRecurring(
                ManageRecurringDeps(
                    items = items,
                    txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions"))),
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                    ids = SequentialIdGenerator(),
                ),
            )
            val action = input.field("action")

            runBlocking {
                if (action.field("kind").str == "load") {
                    val view = manage.load(action.field("today").str)
                    JsonObject(
                        mapOf(
                            "view" to JsonObject(
                                mapOf(
                                    "from" to json(view.from),
                                    "to" to json(view.to),
                                    "choices" to JsonArray(view.choices.map { JsonObject(mapOf("key" to json(it.key), "name" to json(it.name))) }),
                                    "candidates" to JsonArray(view.candidates.map(::candidateJson)),
                                    "items" to JsonArray(
                                        view.items.map {
                                            JsonObject(
                                                mapOf(
                                                    "item" to itemJson(it.item),
                                                    "paidMinor" to nullable(it.paidMinor),
                                                    "paidCount" to json(it.paidCount),
                                                    "annualMinor" to json(it.annualMinor),
                                                    "overdue" to json(it.overdue),
                                                ),
                                            )
                                        },
                                    ),
                                ),
                            ),
                        ),
                    )
                } else {
                    val i = action.field("input")
                    val saved = manage.save(
                        RecurringSaveInput(
                            id = i.optStr("id"),
                            name = i.field("name").str,
                            merchantKey = i.field("merchantKey").str,
                            kind = i.field("kind").str,
                            cycleMonths = i.field("cycleMonths").jsonPrimitive.int,
                            expectedMinor = i.field("expectedMinor").jsonPrimitive.long,
                            currency = Currency.valueOf(i.field("currency").str),
                            nextDueAt = i.field("nextDueAt").str,
                            active = i.field("active").jsonPrimitive.boolean,
                        ),
                    )
                    JsonObject(
                        mapOf(
                            "saved" to itemJson(saved),
                            "storedItems" to JsonArray(items.listAll().map(::itemJson)),
                        ),
                    )
                }
            }
        }
    }
}
