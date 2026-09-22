package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.Wallet
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
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
import kotlin.test.Test

/** تحديد النوع + التصنيف الجماعي + الإضافة اليدوية على `txnEdit.json`. */
class TxnEditGoldenTest {
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

    private fun rule(e: JsonElement) = ClassificationRule(
        id = e.field("id").str, priority = e.field("priority").jsonPrimitive.int, matchText = e.field("matchText").str,
        matchMode = RuleMatchMode.fromWire(e.field("matchMode").str), categoryId = e.field("categoryId").str,
        enabled = e.field("enabled").jsonPrimitive.boolean,
    )

    private fun wallet(e: JsonElement) = Wallet(
        id = e.field("id").str, name = e.field("name").str, currency = Currency.valueOf(e.field("currency").str),
        kind = e.field("kind").str, openingBalanceMinor = e.field("openingBalanceMinor").jsonPrimitive.content.toLong(),
        openingAt = e.field("openingAt").str, accountLast4 = e.optStr("accountLast4"),
    )

    private fun suggestionJson(line: SuggestionLine) = JsonObject(
        mapOf(
            "transactionId" to json(line.transaction.id),
            "kind" to (line.suggestion.kind?.let { json(it.wire) } ?: JsonNull),
            "confidence" to json(line.suggestion.confidence.wire),
            "reason" to json(line.suggestion.reason),
            "alternatives" to JsonArray(line.suggestion.alternatives.map { json(it.wire) }),
        ),
    )

    private fun reportJson(r: CategorizationReport) = JsonObject(
        mapOf(
            "changed" to JsonArray(
                r.changed.map {
                    EntityJson.obj(
                        "transactionId" to it.transactionId, "fromCategoryId" to it.fromCategoryId,
                        "toCategoryId" to it.toCategoryId, "reason" to it.reason, "source" to it.source,
                    )
                },
            ),
            "skippedConfirmed" to json(r.skippedConfirmed),
            "stillNeedsReview" to json(r.stillNeedsReview),
        ),
    )

    private fun stored(txns: MemoryTransactionRepository) = JsonArray(txns.all().map { EntityJson.transactionJson(it) })

    @Test
    fun setEconomicKind() {
        Golden.check("txnEdit", "setEconomicKind") { input ->
            val seed = EntityJson.transactions(input.field("seed"))
            val txns = MemoryTransactionRepository(seed)
            val useCase = SetEconomicKind(
                SetEconomicKindDeps(
                    txns = txns,
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                    uow = MemoryUnitOfWork(listOf(txns)),
                    clock = FixedClock("2026-09-22T10:00:00.000Z"),
                ),
            )
            val action = input.field("action")
            runBlocking {
                when (action.field("kind").str) {
                    "summarize" -> {
                        val s = useCase.summarize(seed)
                        JsonObject(
                            mapOf(
                                "confirmable" to JsonArray(s.confirmable.map(::suggestionJson)),
                                "needsLook" to JsonArray(s.needsLook.map(::suggestionJson)),
                                "ambiguous" to JsonArray(s.ambiguous.map(::suggestionJson)),
                                "alreadySet" to json(s.alreadySet),
                            ),
                        )
                    }
                    "setOne" -> {
                        useCase.setOne(action.field("transactionId").str, EconomicKind.fromWire(action.field("economicKind").str))
                        JsonObject(mapOf("stored" to stored(txns)))
                    }
                    else -> {
                        val result = useCase.confirmBulk(seed, action.field("selectedIds").jsonArray.map { it.str })
                        JsonObject(
                            mapOf(
                                "result" to JsonObject(mapOf("applied" to json(result.applied), "skipped" to json(result.skipped))),
                                "stored" to stored(txns),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun categorizeTransactions() {
        Golden.check("txnEdit", "categorizeTransactions") { input ->
            val seed = EntityJson.transactions(input.field("seed"))
            val references = input.field("references")
            val txns = MemoryTransactionRepository(seed)
            val useCase = CategorizeTransactions(
                CategorizeTransactionsDeps(
                    txns = txns,
                    merchants = MemoryMerchantRepository(references.field("merchants").jsonArray.map(::merchant)),
                    categories = MemoryCategoryRepository(references.field("categories").jsonArray.map(::category)),
                    rules = MemoryRuleRepository(references.field("rules").jsonArray.map(::rule)),
                    uow = MemoryUnitOfWork(listOf(txns)),
                    clock = FixedClock("2026-09-22T10:00:00.000Z"),
                ),
            )
            val action = input.field("action")
            runBlocking {
                when (action.field("kind").str) {
                    "plan" -> JsonObject(mapOf("report" to reportJson(useCase.plan(seed))))
                    "apply" -> JsonObject(mapOf("report" to reportJson(useCase.apply(seed)), "stored" to stored(txns)))
                    else -> {
                        useCase.confirm(action.field("transactionId").str, action.field("categoryId").str)
                        JsonObject(mapOf("stored" to stored(txns)))
                    }
                }
            }
        }
    }

    @Test
    fun addTransaction() {
        Golden.check("txnEdit", "addTransaction") { input ->
            val txns = MemoryTransactionRepository()
            val useCase = AddTransaction(
                AddTransactionDeps(
                    txns = txns,
                    wallets = MemoryWalletRepository(input.field("wallets").jsonArray.map(::wallet)),
                    ids = SequentialIdGenerator(),
                    clock = FixedClock("2026-09-22T10:00:00.000Z"),
                ),
            )
            val i = input.field("input")
            val request = NewTransactionInput(
                amountMinor = i.field("amountMinor").jsonPrimitive.content.toLong(),
                currency = i.optStr("currency")?.let(Currency::valueOf),
                occurredAt = i.field("occurredAt").str,
                walletId = i.field("walletId").str,
                transferToWalletId = i.optStr("transferToWalletId"),
                economicKind = EconomicKind.fromWire(i.field("economicKind").str),
                merchantName = i.field("merchantName").str,
                categoryId = i.optStr("categoryId"),
                note = i.optStr("note"),
                isCashTagged = i.jsonObject["isCashTagged"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean,
                excludedFromBudget = i.jsonObject["excludedFromBudget"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean,
            )
            runBlocking {
                val created = useCase.add(request)
                JsonObject(mapOf("created" to EntityJson.transactionJson(created), "stored" to stored(txns)))
            }
        }
    }
}
