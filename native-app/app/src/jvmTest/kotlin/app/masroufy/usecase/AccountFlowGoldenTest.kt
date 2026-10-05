package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ProfileCheck
import app.masroufy.core.Texts
import app.masroufy.core.UserProfile
import app.masroufy.core.Wallet
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryReferenceSeed
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.SeedSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** الملف الشخصي + أسئلة البداية + المحافظ والمراجع الأولى على `accountFlow.json`. */
class AccountFlowGoldenTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val now = "2026-09-22T10:00:00.000Z"

    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.orNull(name: String): JsonElement? = jsonObject[name]?.takeIf { it !is JsonNull }

    private fun profile(e: JsonElement) = UserProfile(
        e.orNull("displayName")?.str, e.orNull("salaryMinor")?.jsonPrimitive?.long, e.field("payday").jsonPrimitive.int, e.orNull("gender")?.str,
        e.orNull("supportsDependents")?.jsonPrimitive?.boolean, e.orNull("dependentKinds")?.jsonArray?.map { it.str },
        e.orNull("hasCar")?.jsonPrimitive?.boolean, e.orNull("renter")?.jsonPrimitive?.boolean, e.orNull("domesticWorker")?.jsonPrimitive?.boolean,
        e.orNull("business")?.jsonPrimitive?.boolean, e.orNull("onboardedAt")?.str,
    )

    private fun profileJson(p: UserProfile?): JsonElement = if (p == null) JsonNull else JsonObject(
        mapOf(
            "displayName" to nullable(p.displayName), "salaryMinor" to nullable(p.salaryMinor), "payday" to json(p.payday), "gender" to nullable(p.gender),
            "supportsDependents" to nullable(p.supportsDependents), "dependentKinds" to nullable(p.dependentKinds), "hasCar" to nullable(p.hasCar),
            "renter" to nullable(p.renter), "domesticWorker" to nullable(p.domesticWorker), "business" to nullable(p.business), "onboardedAt" to nullable(p.onboardedAt),
        ),
    )

    private fun checkJson(c: ProfileCheck) = when (c) {
        is ProfileCheck.Ok -> JsonObject(mapOf("ok" to json(true), "profile" to profileJson(c.profile)))
        is ProfileCheck.Invalid -> JsonObject(mapOf("ok" to json(false), "field" to json(c.field), "message" to json(c.message)))
    }

    private fun wallet(e: JsonElement) = Wallet(
        id = e.field("id").str, name = e.field("name").str, currency = Currency.valueOf(e.field("currency").str),
        kind = e.field("kind").str, openingBalanceMinor = e.field("openingBalanceMinor").jsonPrimitive.long,
        openingAt = e.field("openingAt").str, accountLast4 = e.orNull("accountLast4")?.str,
    )

    private fun walletJson(w: Wallet) = EntityJson.obj(
        "id" to w.id, "name" to w.name, "currency" to w.currency.name, "kind" to w.kind,
        "openingBalanceMinor" to w.openingBalanceMinor, "openingAt" to w.openingAt, "accountLast4" to w.accountLast4,
    )

    private fun personJson(p: Person) = JsonObject(mapOf("id" to json(p.id), "name" to json(p.name), "archived" to json(p.archived)))

    private fun obligationJson(o: Obligation) = JsonObject(
        mapOf(
            "id" to json(o.id), "personId" to json(o.personId), "originTransactionId" to nullable(o.originTransactionId),
            "kind" to json(o.kind.wire), "originalMinor" to json(o.originalMinor), "currency" to json(o.currency.name),
        ),
    )

    @Test
    fun manageProfile() {
        Golden.check("accountFlow", "manageProfile") { input ->
            val account = MemoryAccount(input.orNull("email")?.str)
            val manage = ManageProfile(ManageProfileDeps(MemoryProfileRepository(input.orNull("stored")?.let(::profile)), account, FixedClock(now)))
            runBlocking {
                JsonArray(
                    input.field("steps").jsonArray.map { step ->
                        when (step.field("kind").str) {
                            "load" -> profileJson(manage.load())
                            "email" -> nullable(manage.email())
                            "sendPasswordReset" -> {
                                manage.sendPasswordReset()
                                JsonObject(mapOf("resetsSent" to json(account.resetsSent)))
                            }
                            "save" -> checkJson(manage.save(profile(step.field("profile"))))
                            "completeOnboarding" -> checkJson(manage.completeOnboarding(profile(step.field("profile"))))
                            else -> json(manage.needsOnboarding(profile(step.field("profile"))))
                        }
                    },
                )
            }
        }
    }

    @Test
    fun seedWallets() {
        Golden.check("accountFlow", "seedWallets") { input ->
            val repo = MemoryWalletRepository(input.field("wallets").jsonArray.map(::wallet))
            runBlocking {
                val outcome = SeedWallets(repo, FixedClock(now)).seed()
                JsonObject(
                    mapOf(
                        "outcome" to JsonObject(
                            mapOf("seeded" to json(outcome.seeded), "wallets" to JsonArray(outcome.wallets.map(::walletJson)), "reason" to json(outcome.reason)),
                        ),
                        "storedWallets" to JsonArray(repo.listAll().map(::walletJson)),
                    ),
                )
            }
        }
    }

    @Test
    fun seedUserReferences() {
        Golden.check("accountFlow", "seedUserReferences") { input ->
            val categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(ReferenceJson::category))
            val rules = MemoryRuleRepository(input.field("rules").jsonArray.map(ReferenceJson::rule))
            val merchants = MemoryMerchantRepository(input.field("merchants").jsonArray.map(ReferenceJson::merchant))
            val s = input.field("source")
            val source = SeedSource(
                s.field("categories").jsonArray.map(ReferenceJson::category),
                s.field("rules").jsonArray.map(ReferenceJson::rule),
                s.field("merchants").jsonArray.map(ReferenceJson::merchant),
            )
            val seed = SeedUserReferences(categories, rules, MemoryReferenceSeed(categories, rules, merchants))
            runBlocking {
                val outcomes = (0 until input.field("times").jsonPrimitive.int).map {
                    val o = seed.seed(source)
                    EntityJson.obj("seeded" to o.seeded, "categories" to o.categories, "rules" to o.rules, "merchants" to o.merchants, "reason" to o.reason)
                }
                JsonObject(
                    mapOf(
                        "outcomes" to JsonArray(outcomes),
                        "storedCategories" to JsonArray(categories.listAll().map(::categoryJson)),
                        "storedRules" to JsonArray(rules.listAll().map(::ruleJson)),
                        "storedMerchants" to JsonArray(merchants.listAll().map(ReferenceJson::merchantJson)),
                    ),
                )
            }
        }
    }

    private fun categoryJson(c: app.masroufy.core.Category) = EntityJson.obj(
        "id" to c.id, "name" to c.name, "iconKey" to c.iconKey, "lightColor" to c.lightColor, "darkColor" to c.darkColor,
        "active" to c.active, "order" to c.order, "groupKey" to c.groupKey, "requires" to c.requires,
    ).let { JsonObject(it + ("parentId" to nullable(c.parentId))) }

    private fun ruleJson(r: app.masroufy.core.ClassificationRule) = EntityJson.obj(
        "id" to r.id, "priority" to r.priority, "matchText" to r.matchText, "matchMode" to r.matchMode.wire, "categoryId" to r.categoryId, "enabled" to r.enabled,
    )

    @Test
    fun onboardAccount() {
        Golden.check("accountFlow", "onboardAccount") { input ->
            val profiles = MemoryProfileRepository(input.orNull("profile")?.let(::profile))
            val wallets = MemoryWalletRepository(input.field("wallets").jsonArray.map(::wallet))
            val people = MemoryPersonRepository(input.field("people").jsonArray.map { Person(it.field("id").str, it.field("name").str, it.field("archived").jsonPrimitive.boolean) })
            val obligations = MemoryObligationRepository(EntityJson.obligations(input.field("obligations")))
            val settlements = MemorySettlementRepository()
            val txns = MemoryTransactionRepository()
            val ids = SequentialIdGenerator()
            val clock = FixedClock(now)
            val onboard = OnboardAccount(
                OnboardAccountDeps(
                    profile = ManageProfile(ManageProfileDeps(profiles, MemoryAccount(), clock)),
                    people = ManagePeople(
                        ManagePeopleDeps(
                            people = people, obligations = obligations, settlements = settlements,
                            settlementWriter = MemorySettlementWriter(obligations, settlements),
                            allocations = MemoryAllocationRepository(), txns = txns,
                            uow = MemoryUnitOfWork(listOf(txns)), ids = ids, clock = clock,
                        ),
                    ),
                    wallets = wallets, clock = clock,
                ),
            )
            val action = input.field("action")
            runBlocking {
                val result: JsonElement = if (action is JsonPrimitive && action.content == "start") {
                    val start = onboard.start()
                    JsonObject(
                        mapOf(
                            "profile" to profileJson(start.profile),
                            "cashOpening" to (start.cashOpening?.let { EntityJson.obj("amountMinor" to it.amountMinor, "openingAt" to it.openingAt) } ?: JsonNull),
                        ),
                    )
                } else {
                    val finished = onboard.finish(
                        OnboardingInput(
                            profile = profile(action.field("profile")),
                            cashMinor = action.orNull("cashMinor")?.jsonPrimitive?.long,
                            debts = action.field("debts").jsonArray.map {
                                OpeningDebtInput(it.field("name").str, ObligationKind.fromWire(it.field("kind").str), it.field("amountMinor").jsonPrimitive.long)
                            },
                        ),
                    )
                    when (finished) {
                        OnboardingResult.Ok -> JsonObject(mapOf("ok" to json(true)))
                        is OnboardingResult.Failed -> JsonObject(mapOf("ok" to json(false), "step" to json(finished.step), "message" to json(finished.message)))
                    }
                }
                JsonObject(
                    mapOf(
                        "result" to result,
                        "storedProfile" to profileJson(profiles.load()),
                        "storedWallets" to JsonArray(wallets.listAll().map(::walletJson)),
                        "storedPeople" to JsonArray(people.listAll().map(::personJson)),
                        "storedObligations" to JsonArray(obligations.all().map(::obligationJson)),
                    ),
                )
            }
        }
    }
}
