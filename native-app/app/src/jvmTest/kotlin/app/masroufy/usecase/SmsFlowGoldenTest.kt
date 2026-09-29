package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Direction
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.parseBankSms
import app.masroufy.core.smsRowsJson
import app.masroufy.core.str
import app.masroufy.core.toParsedRow
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.BankSmsPort
import app.masroufy.port.BankSmsRead
import app.masroufy.port.QueuedSms
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

/** قراية رسايل البنك + صندوق الرسايل + شاشة المراجعة على `smsFlow.json`. */
class SmsFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun JsonElement.bool(name: String): Boolean = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean ?: false

    private fun strings(e: JsonElement) = e.jsonArray.map { it.str }

    private fun queued(e: JsonElement) = QueuedSms(e.field("id").str, e.field("sender").str, e.field("receivedAt").str, e.field("body").str)

    private fun queuedJson(q: QueuedSms) = EntityJson.obj("id" to q.id, "sender" to q.sender, "receivedAt" to q.receivedAt, "body" to q.body)

    private fun rowJson(r: SmsRow) = JsonObject(
        mapOf(
            "lineNumber" to json(r.lineNumber), "date" to json(r.date), "amountMinor" to json(r.amountMinor),
            "direction" to json(r.direction.wire), "merchantName" to json(r.merchantName), "reference" to nullable(r.reference),
            "sourceName" to json(r.sourceName), "description" to json(r.description), "raw" to json(r.raw),
        ),
    )

    private fun parsedJson(p: SmsParseResult) = when (p) {
        is SmsParseResult.Ok -> JsonObject(mapOf("ok" to json(true), "row" to rowJson(p.row)))
        is SmsParseResult.Rejected -> JsonObject(mapOf("ok" to json(false), "reason" to json(p.reason)))
    }

    private fun batchJson(b: SmsBatch) = JsonObject(
        mapOf(
            "rows" to JsonArray(b.rows.map(::rowJson)),
            "skipped" to JsonArray(b.skipped.map { EntityJson.obj("line" to it.line, "reason" to it.reason) }),
            "content" to json(b.content),
            "truncated" to json(b.truncated),
        ),
    )

    private fun inboxJson(v: InboxView) = JsonObject(
        mapOf(
            "enabled" to json(v.enabled), "permission" to json(v.permission), "more" to json(v.more), "count" to json(v.count),
            "senders" to json(v.senders), "messages" to JsonArray(v.messages.map(::queuedJson)),
            "items" to JsonArray(
                v.items.map {
                    JsonObject(mapOf("id" to json(it.id), "sender" to json(it.sender), "receivedAt" to json(it.receivedAt), "parsed" to parsedJson(it.parsed)))
                },
            ),
        ),
    )

    private fun lineJson(l: SmsReviewLine) = EntityJson.obj(
        "messageId" to l.messageId, "lineNumber" to l.lineNumber, "date" to l.date, "merchant" to l.merchant,
        "amountMinor" to l.amountMinor, "direction" to l.direction.wire, "categoryId" to l.categoryId,
        "remembered" to l.remembered, "state" to l.state.wire, "reason" to l.reason,
    )

    private fun reviewJson(r: SmsReview) = JsonObject(
        mapOf(
            "enabled" to json(r.enabled), "permission" to json(r.permission), "senders" to json(r.senders), "more" to json(r.more),
            "failed" to JsonArray(r.failed.map { EntityJson.obj("messageId" to it.messageId, "sender" to it.sender, "date" to it.date, "reason" to it.reason) }),
            "categories" to json(r.categories.map { it.id }),
            "ready" to JsonArray(r.ready.map(::lineJson)),
            "similar" to JsonArray(r.similar.map(::lineJson)),
            "duplicates" to JsonArray(r.duplicates.map(::lineJson)),
        ),
    )

    @Test
    fun readBankSms() {
        Golden.check("smsFlow", "readBankSms") { input ->
            runBlocking {
                if (input.field("kind").str == "paste") {
                    val port = object : BankSmsPort {
                        override val available = false
                        override suspend fun read(from: String, to: String, senders: List<String>) = BankSmsRead(emptyList(), false)
                    }
                    batchJson(ReadBankSms(port, ::parseBankSms).paste(input.field("body").str))
                } else {
                    val r = input.field("request")
                    val messages = input.field("messages").jsonArray.map { BankSmsMessage(it.field("sender").str, it.field("receivedAt").str, it.field("body").str) }
                    val port = object : BankSmsPort {
                        override val available = true
                        override suspend fun read(from: String, to: String, senders: List<String>) = BankSmsRead(messages, r.bool("truncated"))
                    }
                    batchJson(ReadBankSms(port, ::parseBankSms).read(r.field("from").str, r.field("to").str, strings(r.field("senders"))))
                }
            }
        }
    }

    @Test
    fun manageSmsInbox() {
        Golden.check("smsFlow", "manageSmsInbox") { input ->
            val manage = ManageSmsInbox(MemorySmsInbox(input.field("inbox").jsonArray.map(::queued), available = true), ::parseBankSms)
            runBlocking {
                val out = input.field("steps").jsonArray.map { step ->
                    when (step.field("kind").str) {
                        "refresh" -> inboxJson(manage.refresh())
                        "enable" -> inboxJson(manage.enable(strings(step.field("senders"))))
                        "disable" -> inboxJson(manage.disable())
                        "dismiss" -> inboxJson(manage.dismiss(strings(step.field("ids"))))
                        else -> {
                            manage.imported(
                                step.field("items").jsonArray.map { InboxLine(it.field("id").str, it.field("lineNumber").jsonPrimitive.int) },
                                step.field("confirmed").jsonArray.map { it.jsonPrimitive.int },
                            )
                            JsonNull
                        }
                    }
                }
                JsonObject(mapOf("available" to json(manage.available), "steps" to JsonArray(out)))
            }
        }
    }

    @Test
    fun reviewSmsInbox() {
        Golden.check("smsFlow", "reviewSmsInbox") { input ->
            val inbox = input.field("inbox").jsonArray.map(::queued)
            val target = input.field("target").let { SmsReviewTarget(it.field("walletId").str, it.field("accountIdentity").str) }
            val txns = MemoryTransactionRepository(EntityJson.transactions(JsonArray(listOf(input.field("statementTxn")))))
            val sources = MemorySourceRecordRepository(listOf(ReferenceJson.sourceRecord(input.field("statementRecord"))))
            val batches = MemoryImportBatchRepository(listOf(ReferenceJson.batch(input.field("statementBatch"))))
            val merchants = MemoryMerchantRepository(input.field("merchants").jsonArray.map(ReferenceJson::merchant))
            val categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(ReferenceJson::category))
            val ids = SequentialIdGenerator()
            val importer = ImportStatement(
                ImportStatementDeps(
                    txns = txns, sources = sources, batches = batches, merchants = merchants, categories = categories,
                    rules = MemoryRuleRepository(input.field("rules").jsonArray.map(ReferenceJson::rule)),
                    uow = MemoryUnitOfWork(listOf(txns, sources, batches)), ids = ids, clock = FixedClock("2026-09-22T10:00:00.000Z"),
                ),
            )
            val contributed = mutableListOf<JsonElement>()
            var failContribute = false
            val review = ReviewSmsInbox(
                ReviewSmsInboxDeps(
                    inbox = ManageSmsInbox(MemorySmsInbox(inbox, available = true), ::parseBankSms),
                    importer = importer, merchants = merchants, categories = categories, ids = ids,
                    contribute = if (input.bool("withContribute")) {
                        { c, categoryId ->
                            if (failContribute) throw IllegalStateException("الشبكة مش متاحة")
                            contributed += JsonObject(
                                mapOf(
                                    "transaction" to EntityJson.obj(
                                        "economicKind" to c.economicKind.wire, "observedDirection" to c.observedDirection.wire, "rawMerchantName" to c.rawMerchantName,
                                    ),
                                    "categoryId" to json(categoryId),
                                ),
                            )
                        }
                    } else {
                        null
                    },
                ),
            )

            runBlocking {
                val pre = input.jsonObject["preRecorded"]?.takeIf { it !is JsonNull }?.let(::strings).orEmpty()
                if (pre.isNotEmpty()) {
                    val rows = inbox.filter { it.id in pre }.mapIndexedNotNull { i, m -> (parseBankSms(m.message(), i + 1) as? SmsParseResult.Ok)?.row }
                    val request = ImportRequest(
                        fileName = "earlier-sms.json", content = smsRowsJson(rows), accountIdentity = target.accountIdentity,
                        sourceType = ImportSourceType.SMS, walletId = target.walletId, schema = SchemaId.SMS, parsedRows = rows.map { it.toParsedRow() },
                    )
                    importer.commit(request, importer.preview(request))
                }
                val out = input.field("steps").jsonArray.map { step ->
                    when (step.field("kind").str) {
                        "load" -> reviewJson(review.load(target))
                        "enable" -> reviewJson(review.enable(strings(step.field("senders")), target))
                        "disable" -> reviewJson(review.disable(target))
                        "dismiss" -> reviewJson(review.dismiss(strings(step.field("ids")), target))
                        "recordAll" -> {
                            val chosen = step.field("categories").jsonArray.associate { it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].str }
                            JsonObject(mapOf("recorded" to json(review.recordAll(chosen, step.field("includeSimilar").jsonArray.map { it.jsonPrimitive.int }))))
                        }
                        else -> {
                            failContribute = step.bool("contributeFails")
                            val remembered = review.remember(step.field("merchantName").str, step.field("categoryId").str, Direction.fromWire(step.field("direction").str))
                            JsonObject(mapOf("remembered" to json(remembered)))
                        }
                    }
                }
                JsonObject(
                    mapOf(
                        "available" to json(review.available),
                        "steps" to JsonArray(out),
                        "contributed" to JsonArray(contributed),
                        "storedTransactions" to JsonArray(
                            txns.all().map { t ->
                                JsonObject(
                                    mapOf(
                                        "id" to json(t.id), "occurredAt" to json(t.occurredAt), "amountMinor" to json(t.amountMinor),
                                        "observedDirection" to json(t.observedDirection.wire), "categoryId" to nullable(t.categoryId),
                                        "categoryConfirmed" to json(t.categoryConfirmed), "rawMerchantName" to nullable(t.rawMerchantName),
                                        "walletId" to nullable(t.walletId),
                                    ),
                                )
                            },
                        ),
                        "storedMerchants" to JsonArray(merchants.listAll().map(ReferenceJson::merchantJson)),
                    ),
                )
            }
        }
    }
}
