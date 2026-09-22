package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Merchant
import app.masroufy.core.Tag
import app.masroufy.core.TransactionTag
import app.masroufy.core.buildPeriod
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
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

/**
 * شاشة العمليات — تاني حالة استخدام بتتطابق مع التطبيق الحالي بملف مرجع (`transactions.json`).
 * الحقل اللي قيمته `null` بيفضل موجود عشان «غير متاح» تتقارن، وخرائط الأسماء بتتقارن
 * كمفاتيح (الترتيب جوه قايمة الأسماء هو المهم).
 */
class TransactionsGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? =
        jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str,
        parentId = e.optStr("parentId"),
        name = e.field("name").str,
        iconKey = e.field("iconKey").str,
        lightColor = e.field("lightColor").str,
        darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean,
        order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"),
        requires = e.optStr("requires"),
    )

    private fun merchant(e: JsonElement) = Merchant(
        id = e.field("id").str,
        displayName = e.field("displayName").str,
        normalizedName = e.field("normalizedName").str,
        aliases = e.jsonObject["aliases"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.str },
        logoAsset = e.optStr("logoAsset"),
        logoSource = e.optStr("logoSource"),
        verifiedCategoryId = e.optStr("verifiedCategoryId"),
    )

    private fun tag(e: JsonElement) = Tag(
        id = e.field("id").str,
        normalizedName = e.field("normalizedName").str,
        displayName = e.field("displayName").str,
    )

    private fun link(e: JsonElement) = TransactionTag(
        id = e.field("id").str,
        transactionId = e.field("transactionId").str,
        tagId = e.field("tagId").str,
    )

    private fun namesJson(map: Map<String, List<String>>) =
        JsonObject(map.mapValues { (_, names) -> JsonArray(names.map(::json)) })

    @Test
    fun loadTransactionsScreen() {
        Golden.check("transactions", "loadTransactionsScreen") { input ->
            val options = input.field("options")
            val withOptional = options.field("withOptional").jsonPrimitive.boolean
            val deps = LoadTransactionsScreenDeps(
                txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions"))),
                categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                allocations = MemoryAllocationRepository(EntityJson.allocations(input.field("allocations"))),
                merchants = if (withOptional) MemoryMerchantRepository(input.field("merchants").jsonArray.map(::merchant)) else null,
                tags = if (withOptional) MemoryTagRepository(input.field("tags").jsonArray.map(::tag)) else null,
                transactionTags = if (withOptional) {
                    MemoryTransactionTagRepository(input.field("transactionTags").jsonArray.map(::link))
                } else {
                    null
                },
            )
            val payday = options.field("payday").jsonPrimitive.int
            val request = if (options.field("usePeriod").jsonPrimitive.boolean) {
                LoadTransactionsScreenRequest(
                    period = buildPeriod(options.field("year").jsonPrimitive.int, options.field("month").jsonPrimitive.int, payday),
                    today = options.field("today").str,
                    payday = payday,
                )
            } else {
                LoadTransactionsScreenRequest(today = options.field("today").str, payday = payday)
            }

            val data = runBlocking { LoadTransactionsScreen(deps).load(request) }
            JsonObject(
                mapOf(
                    "periodKey" to json(data.period.key),
                    "periodRange" to json(data.periodRange),
                    "transactionIds" to JsonArray(data.transactions.map { json(it.id) }),
                    "categoryIds" to JsonArray(data.categories.map { json(it.id) }),
                    "tagNamesByTransaction" to namesJson(data.tagNamesByTransaction),
                    "merchantNamesByTransaction" to namesJson(data.merchantNamesByTransaction),
                    "incomeMinor" to nullable(data.incomeMinor),
                    "expenseMinor" to nullable(data.expenseMinor),
                    "remainingMinor" to nullable(data.remainingMinor),
                    "savingsRatePercent" to nullable(data.savingsRatePercent),
                    "unclassifiedCount" to json(data.unclassifiedCount),
                    "estimatedCount" to json(data.estimatedCount),
                    "needsReviewCount" to json(data.needsReviewCount),
                    "totalCount" to json(data.totalCount),
                ),
            )
        }
    }
}
