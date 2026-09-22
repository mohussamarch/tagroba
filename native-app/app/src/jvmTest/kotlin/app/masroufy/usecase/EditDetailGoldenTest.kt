package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Tag
import app.masroufy.core.TransactionTag
import app.masroufy.core.Wallet
import app.masroufy.core.buildPeriod
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
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

/** تعديل العملية + ملخص الكاش + مطابقة الرصيد على `editDetail.json`. */
class EditDetailGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str, parentId = e.optStr("parentId"), name = e.field("name").str,
        iconKey = e.field("iconKey").str, lightColor = e.field("lightColor").str, darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean, order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"), requires = e.optStr("requires"),
    )

    private fun tag(e: JsonElement) = Tag(e.field("id").str, e.field("normalizedName").str, e.field("displayName").str)

    private fun tagJson(t: Tag) = JsonObject(
        mapOf("id" to json(t.id), "normalizedName" to json(t.normalizedName), "displayName" to json(t.displayName)),
    )

    private fun link(e: JsonElement) = TransactionTag(e.field("id").str, e.field("transactionId").str, e.field("tagId").str)

    private fun linkJson(l: TransactionTag) = JsonObject(
        mapOf("id" to json(l.id), "transactionId" to json(l.transactionId), "tagId" to json(l.tagId)),
    )

    private fun wallet(e: JsonElement) = Wallet(
        id = e.field("id").str, name = e.field("name").str, currency = Currency.valueOf(e.field("currency").str),
        kind = e.field("kind").str, openingBalanceMinor = e.field("openingBalanceMinor").jsonPrimitive.content.toLong(),
        openingAt = e.field("openingAt").str, accountLast4 = e.optStr("accountLast4"),
    )

    @Test
    fun editTransaction() {
        Golden.check("editDetail", "editTransaction") { input ->
            val seed = input.field("seed")
            val seedTxns = EntityJson.transactions(seed.field("transactions"))
            val txns = MemoryTransactionRepository(seedTxns)
            val tags = MemoryTagRepository(seed.field("tags").jsonArray.map(::tag))
            val links = MemoryTransactionTagRepository(seed.field("links").jsonArray.map(::link))
            val allocations = MemoryAllocationRepository(EntityJson.allocations(seed.field("allocations")))
            val settlements = MemorySettlementRepository(EntityJson.settlements(seed.field("settlements")))
            val edit = EditTransaction(
                EditTransactionDeps(
                    txns = txns,
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                    tags = tags, transactionTags = links,
                    uow = MemoryUnitOfWork(listOf(txns)),
                    ids = SequentialIdGenerator(),
                    clock = FixedClock("2026-09-22T10:00:00.000Z"),
                    allocations = allocations, settlements = settlements,
                ),
            )
            val action = input.field("action")

            runBlocking {
                suspend fun stored() = mapOf(
                    "storedTransactions" to JsonArray(txns.all().map { EntityJson.transactionJson(it) }),
                    "storedTags" to JsonArray(tags.listAll().map(::tagJson)),
                    "storedLinks" to JsonArray(links.listByTransactionIds(seedTxns.map { it.id }).map(::linkJson)),
                )
                when (action.field("kind").str) {
                    "load" -> {
                        val detail = edit.load(action.field("transactionId").str)
                        JsonObject(mapOf("transactionId" to json(detail.transaction.id), "tags" to JsonArray(detail.tags.map(::tagJson))))
                    }
                    "setCategory" -> {
                        edit.setCategory(action.field("transactionId").str, action.optStr("categoryId"))
                        JsonObject(stored())
                    }
                    "setAmount" -> {
                        edit.setAmount(action.field("transactionId").str, action.field("amountMinor").jsonPrimitive.content.toLong())
                        JsonObject(stored())
                    }
                    "setNote" -> {
                        edit.setNote(action.field("transactionId").str, action.field("note").str)
                        JsonObject(stored())
                    }
                    "setCashTag" -> {
                        edit.setCashTag(action.field("transactionId").str, action.field("value").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                    "setExcluded" -> {
                        edit.setExcludedFromBudget(action.field("transactionId").str, action.field("value").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                    "addTag" -> {
                        val created = edit.addTag(action.field("transactionId").str, action.field("displayName").str)
                        JsonObject(mapOf("created" to tagJson(created)) + stored())
                    }
                    "removeTag" -> {
                        edit.removeTag(action.field("transactionId").str, action.field("tagId").str)
                        JsonObject(stored())
                    }
                    "listTags" -> JsonObject(mapOf("tags" to JsonArray(edit.listTags().map(::tagJson))))
                    else -> {
                        val map = edit.tagsFor(action.field("transactionIds").jsonArray.map { it.str })
                        JsonObject(
                            mapOf(
                                "byTransaction" to JsonObject(map.mapValues { (_, v) -> JsonArray(v.map { json(it.id) }) }),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun loadCashSummary() {
        Golden.check("editDetail", "loadCashSummary") { input ->
            val seed = input.field("seed")
            val options = input.field("options")
            val load = LoadCashSummary(
                LoadCashSummaryDeps(
                    wallets = MemoryWalletRepository(seed.field("wallets").jsonArray.map(::wallet)),
                    txns = MemoryTransactionRepository(EntityJson.transactions(seed.field("transactions"))),
                    allocations = MemoryAllocationRepository(EntityJson.allocations(seed.field("allocations"))),
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                ),
            )
            val period = buildPeriod(options.field("year").jsonPrimitive.int, options.field("month").jsonPrimitive.int, options.field("payday").jsonPrimitive.int)
            runBlocking {
                val summary = load.load(period, options.field("today").str)
                JsonObject(
                    mapOf(
                        "summary" to if (summary == null) {
                            JsonNull
                        } else {
                            JsonObject(
                                mapOf(
                                    "walletId" to json(summary.wallet.id),
                                    "balanceMinor" to json(summary.balanceMinor),
                                    "inSinceOpeningMinor" to json(summary.inSinceOpeningMinor),
                                    "outSinceOpeningMinor" to json(summary.outSinceOpeningMinor),
                                    "spentInPeriodMinor" to json(summary.spentInPeriodMinor),
                                    "periodTransactionIds" to JsonArray(summary.periodTransactions.map { json(it.id) }),
                                ),
                            )
                        },
                    ),
                )
            }
        }
    }

    @Test
    fun reconcileBalance() {
        Golden.check("editDetail", "reconcileBalance") { input ->
            val seed = input.field("seed")
            val options = input.field("options")
            val reconcile = ReconcileBalance(
                ReconcileDeps(
                    txns = MemoryTransactionRepository(EntityJson.transactions(seed.field("transactions"))),
                    wallets = MemoryWalletRepository(seed.field("wallets").jsonArray.map(::wallet)),
                ),
            )
            runBlocking {
                val o = reconcile.run(options.field("walletId").str, options.field("until").str, options.field("payday").jsonPrimitive.int)
                JsonObject(
                    mapOf(
                        "walletId" to json(o.wallet.id),
                        "result" to JsonObject(
                            mapOf(
                                "openingMinor" to json(o.result.openingMinor),
                                "openingAt" to json(o.result.openingAt),
                                "closingMinor" to json(o.result.closingMinor),
                                "closingAt" to nullable(o.result.closingAt),
                                "totalDebitMinor" to json(o.result.totalDebitMinor),
                                "totalCreditMinor" to json(o.result.totalCreditMinor),
                                "movementCount" to json(o.result.movementCount),
                                // `reference` و`label` الغايبين بيتشالوا زي جافاسكربت (undefined بيسقط من JSON)
                                "mismatches" to JsonArray(
                                    o.result.mismatches.map { m ->
                                        EntityJson.obj(
                                            "index" to m.index, "date" to m.date,
                                            "computedMinor" to m.computedMinor, "statedMinor" to m.statedMinor,
                                            "differenceMinor" to m.differenceMinor, "reference" to m.reference,
                                            "label" to m.label, "sameDayCount" to m.sameDayCount,
                                        )
                                    },
                                ),
                                "checkedCount" to json(o.result.checkedCount),
                                "ambiguityNote" to nullable(o.result.ambiguityNote),
                            ),
                        ),
                        "withoutStatedBalance" to json(o.withoutStatedBalance),
                        "unassignedCount" to json(o.unassignedCount),
                        "periodsRead" to json(o.periodsRead),
                    ),
                )
            }
        }
    }
}
