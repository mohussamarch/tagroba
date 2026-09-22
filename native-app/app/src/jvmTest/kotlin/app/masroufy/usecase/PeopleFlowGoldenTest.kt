package app.masroufy.usecase

import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Settlement
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test

/** الأشخاص والديون والأمانات على `peopleFlow.json`. */
class PeopleFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun person(e: JsonElement) = Person(e.field("id").str, e.field("name").str, e.field("archived").jsonPrimitive.boolean)

    private fun personJson(p: Person) = JsonObject(
        mapOf("id" to json(p.id), "name" to json(p.name), "archived" to json(p.archived)),
    )

    private fun obligationJson(o: Obligation) = JsonObject(
        mapOf(
            "id" to json(o.id), "personId" to json(o.personId), "originTransactionId" to nullable(o.originTransactionId),
            "kind" to json(o.kind.wire), "originalMinor" to json(o.originalMinor), "currency" to json(o.currency.name),
        ),
    )

    private fun settlementJson(s: Settlement) = JsonObject(
        mapOf(
            "id" to json(s.id), "transactionId" to json(s.transactionId),
            "obligationId" to json(s.obligationId), "amountMinor" to json(s.amountMinor),
        ),
    )

    private fun allocationJson(a: PersonAllocation) = JsonObject(
        mapOf(
            "id" to json(a.id), "transactionId" to json(a.transactionId), "personId" to json(a.personId),
            "allocationKind" to json(a.allocationKind.wire), "amountMinor" to json(a.amountMinor), "currency" to json(a.currency.name),
        ),
    )

    @Test
    fun managePeople() {
        Golden.check("peopleFlow", "managePeople") { input ->
            val seed = input.field("seed")
            val people = MemoryPersonRepository(seed.field("people").jsonArray.map(::person))
            val obligations = MemoryObligationRepository(EntityJson.obligations(seed.field("obligations")))
            val settlements = MemorySettlementRepository(EntityJson.settlements(seed.field("settlements")))
            val allocations = MemoryAllocationRepository(EntityJson.allocations(seed.field("allocations")))
            val seedTxns = EntityJson.transactions(seed.field("transactions"))
            val txns = MemoryTransactionRepository(seedTxns)
            val manage = ManagePeople(
                ManagePeopleDeps(
                    people = people, obligations = obligations, settlements = settlements,
                    settlementWriter = MemorySettlementWriter(obligations, settlements),
                    allocations = allocations, txns = txns,
                    uow = MemoryUnitOfWork(listOf(txns)),
                    ids = SequentialIdGenerator(),
                    clock = FixedClock("2026-09-22T10:00:00.000Z"),
                ),
            )
            val action = input.field("action")

            runBlocking {
                suspend fun stored() = mapOf(
                    "storedPeople" to JsonArray(people.listAll().map(::personJson)),
                    "storedObligations" to JsonArray(obligations.all().map(::obligationJson)),
                    "storedSettlements" to JsonArray(settlements.all().map(::settlementJson)),
                    "storedAllocations" to JsonArray(
                        allocations.listByTransactionIds(seedTxns.map { it.id }).map(::allocationJson),
                    ),
                )
                when (action.field("kind").str) {
                    "list" -> JsonObject(
                        mapOf(
                            "rows" to JsonArray(
                                manage.listWithBalances().map { row ->
                                    JsonObject(
                                        mapOf(
                                            "personId" to json(row.person.id),
                                            "archived" to json(row.person.archived),
                                            "balance" to JsonObject(
                                                mapOf(
                                                    "personId" to json(row.balance.personId),
                                                    "receivableMinor" to json(row.balance.receivableMinor),
                                                    "payableLoanMinor" to json(row.balance.payableLoanMinor),
                                                    "payableCustodyMinor" to json(row.balance.payableCustodyMinor),
                                                ),
                                            ),
                                            "obligations" to JsonArray(
                                                row.obligations.map {
                                                    JsonObject(mapOf("obligationId" to json(it.obligation.id), "remainingMinor" to json(it.remainingMinor)))
                                                },
                                            ),
                                        ),
                                    )
                                },
                            ),
                        ),
                    )
                    "addPerson" -> JsonObject(mapOf("created" to personJson(manage.addPerson(action.field("name").str))) + stored())
                    "archive" -> {
                        manage.archivePerson(action.field("personId").str, action.field("archived").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                    "link" -> {
                        val result = manage.linkToPerson(
                            transactionId = action.field("transactionId").str,
                            personId = action.field("personId").str,
                            kind = ObligationKind.fromWire(action.field("obligationKind").str),
                            amountMinor = action.field("amountMinor").jsonPrimitive.long,
                            asGift = action.jsonObject["asGift"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean ?: false,
                        )
                        JsonObject(
                            mapOf(
                                "obligation" to (result.obligation?.let(::obligationJson) ?: JsonNull),
                                "allocation" to allocationJson(result.allocation),
                            ) + stored(),
                        )
                    }
                    "settle" -> {
                        val first = manage.settle(
                            obligationId = action.field("obligationId").str,
                            personId = action.field("personId").str,
                            amountMinor = action.field("amountMinor").jsonPrimitive.long,
                            transactionId = action.optStr("transactionId"),
                            requestId = action.optStr("requestId"),
                        )
                        val second = if (action.jsonObject["repeat"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean == true) {
                            manage.settle(
                                obligationId = action.field("obligationId").str,
                                personId = action.field("personId").str,
                                amountMinor = action.jsonObject["secondAmountMinor"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.long
                                    ?: action.field("amountMinor").jsonPrimitive.long,
                                transactionId = action.optStr("transactionId"),
                                requestId = action.optStr("requestId"),
                            )
                        } else {
                            null
                        }
                        JsonObject(
                            mapOf(
                                "first" to settlementJson(first),
                                "second" to (second?.let(::settlementJson) ?: JsonNull),
                            ) + stored(),
                        )
                    }
                    else -> {
                        val obligation = manage.addOpeningDebt(
                            personId = action.field("personId").str,
                            kind = ObligationKind.fromWire(action.field("obligationKind").str),
                            amountMinor = action.field("amountMinor").jsonPrimitive.long,
                        )
                        JsonObject(mapOf("obligation" to obligationJson(obligation)) + stored())
                    }
                }
            }
        }
    }
}
