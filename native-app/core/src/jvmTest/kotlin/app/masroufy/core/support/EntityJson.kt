package app.masroufy.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** تحويل كيانات ملفات المرجع (JSON بأسماء التخزين) لكيانات كوتلن وبالعكس — للاختبارات بس. */
object EntityJson {
    private fun JsonElement.opt(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
    private fun JsonElement.optLong(name: String): Long? = opt(name)?.toLong()
    private fun JsonElement.bool(name: String): Boolean = jsonObject.getValue(name).jsonPrimitive.boolean

    fun transaction(e: JsonElement) = Transaction(
        id = e.field("id").str, occurredAt = e.field("occurredAt").str, datePrecision = e.field("datePrecision").str,
        sourceOrder = e.field("sourceOrder").jsonPrimitive.int,
        economicKind = EconomicKind.fromWire(e.field("economicKind").str), economicKindConfirmed = e.bool("economicKindConfirmed"),
        observedDirection = Direction.fromWire(e.field("observedDirection").str), amountMinor = e.field("amountMinor").num,
        currency = Currency.valueOf(e.field("currency").str), categoryConfirmed = e.bool("categoryConfirmed"),
        excludedFromBudget = e.bool("excludedFromBudget"), reviewState = ReviewState.fromWire(e.field("reviewState").str),
        isCashTagged = e.bool("isCashTagged"), createdAt = e.field("createdAt").str, updatedAt = e.field("updatedAt").str,
        sourceTime = e.opt("sourceTime"), originalAmountMinor = e.optLong("originalAmountMinor"), merchantId = e.opt("merchantId"),
        categoryId = e.opt("categoryId"), note = e.opt("note"), walletId = e.opt("walletId"), transferToWalletId = e.opt("transferToWalletId"),
        statedBalanceMinor = e.optLong("statedBalanceMinor"), rawDescription = e.opt("rawDescription"), rawMerchantName = e.opt("rawMerchantName"),
        sourceCategory = e.opt("sourceCategory"), sourceOperationType = e.opt("sourceOperationType"),
    )

    fun transactions(e: JsonElement) = e.jsonArray.map(::transaction)

    /** العملية بأسماء التخزين، والحقل الاختياري الفاضي مش موجود (زي جافاسكربت). */
    fun transactionJson(t: Transaction) = obj(
        "id" to t.id, "occurredAt" to t.occurredAt, "datePrecision" to t.datePrecision, "sourceOrder" to t.sourceOrder,
        "economicKind" to t.economicKind.wire, "economicKindConfirmed" to t.economicKindConfirmed, "observedDirection" to t.observedDirection.wire,
        "amountMinor" to t.amountMinor, "currency" to t.currency.name, "categoryConfirmed" to t.categoryConfirmed,
        "excludedFromBudget" to t.excludedFromBudget, "reviewState" to t.reviewState.wire, "isCashTagged" to t.isCashTagged,
        "createdAt" to t.createdAt, "updatedAt" to t.updatedAt, "sourceTime" to t.sourceTime, "originalAmountMinor" to t.originalAmountMinor,
        "merchantId" to t.merchantId, "categoryId" to t.categoryId, "note" to t.note, "walletId" to t.walletId,
        "transferToWalletId" to t.transferToWalletId, "statedBalanceMinor" to t.statedBalanceMinor, "rawDescription" to t.rawDescription,
        "rawMerchantName" to t.rawMerchantName, "sourceCategory" to t.sourceCategory, "sourceOperationType" to t.sourceOperationType,
    )

    fun allocations(e: JsonElement) = e.jsonArray.map {
        PersonAllocation(
            it.field("id").str, it.field("transactionId").str, it.field("personId").str,
            AllocationKind.fromWire(it.field("allocationKind").str), it.field("amountMinor").num, Currency.valueOf(it.field("currency").str),
        )
    }

    fun obligation(e: JsonElement) = Obligation(
        e.field("id").str, e.field("personId").str, e.opt("originTransactionId"),
        ObligationKind.fromWire(e.field("kind").str), e.field("originalMinor").num, Currency.valueOf(e.field("currency").str),
    )

    fun obligations(e: JsonElement) = e.jsonArray.map(::obligation)

    fun settlements(e: JsonElement) = e.jsonArray.map {
        Settlement(it.field("id").str, it.field("transactionId").str, it.field("obligationId").str, it.field("amountMinor").num)
    }

    /** كائن JSON من أزواج، والقيمة null بتتشال (زي الحقل الاختياري الغايب في جافاسكربت). */
    fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(pairs.filter { it.second != null }.associate { it.first to json(it.second) })

    fun kinds(list: List<EconomicKind>) = list.map { it.wire }
    fun primitive(value: String) = JsonPrimitive(value)
}
