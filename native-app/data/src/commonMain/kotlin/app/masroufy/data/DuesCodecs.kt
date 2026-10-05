package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.CycleUnit
import app.masroufy.core.DebtTerms
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.RoscaMember

/**
 * «المستحقات» (OVERRIDES §50) — مجموعات **جديدة في كوتلن بس**، فمالهاش شكل قديم تتطابق معاه؛ الشكل هنا اتقرر
 * على نفس قواعد الباقي (أسماء حقول زي الكيان، الأعداد صحيحة، والاختياري ما بيتكتبش).
 * قواعد فايربيز (`firestore.rules`) بتسمح بأي مجموعة تحت `users/{uid}` لصاحبها ⇒ المجموعات دي مش محتاجة نشر قواعد جديد (اتأكد 2026-09-30).
 */
object DuesCodecs {
    val roscas: DocCodec<Rosca> = codec(
        "roscas", { it.id },
        { r ->
            doc {
                req("id", r.id); req("name", r.name); req("currency", r.currency.name); req("contributionMinor", r.contributionMinor)
                req("every", r.every); req("unit", r.unit.wire); req("firstDueAt", r.firstDueAt); req("cycleCount", r.cycleCount)
                req("myTurns", r.myTurns.map { it.toLong() }); req("payoutMinor", r.payoutMinor)
                req("members", r.members.map { m -> doc { req("turn", m.turn); req("name", m.name); opt("personId", m.personId) } })
                opt("organizerPersonId", r.organizerPersonId); req("createdAt", r.createdAt)
            }
        },
        { d ->
            Rosca(
                id = d.str("id"), name = d.str("name"), currency = d.wire("currency", Currency::valueOf), contributionMinor = d.long("contributionMinor"),
                every = d.int("every"), firstDueAt = d.str("firstDueAt"), cycleCount = d.int("cycleCount"), myTurns = d.ints("myTurns"),
                payoutMinor = d.long("payoutMinor"),
                members = d.maps("members").map { m -> RoscaMember(m.int("turn"), m.str("name"), m.strOrNull("personId")) },
                organizerPersonId = d.strOrNull("organizerPersonId"), createdAt = d.str("createdAt"), unit = d.wire("unit", CycleUnit::fromWire),
            )
        },
    )

    val roscaEntries: DocCodec<RoscaEntry> = codec(
        "roscaEntries", { it.id },
        { e -> doc { req("id", e.id); req("roscaId", e.roscaId); req("transactionId", e.transactionId); req("kind", e.kind.wire); req("amountMinor", e.amountMinor) } },
        { d -> RoscaEntry(d.str("id"), d.str("roscaId"), d.str("transactionId"), d.wire("kind", RoscaEntryKind::fromWire), d.long("amountMinor")) },
    )

    val installmentPlans: DocCodec<InstallmentPlan> = codec(
        "installmentPlans", { it.id },
        { p ->
            doc {
                req("id", p.id); req("name", p.name); req("provider", p.provider); req("kind", p.kind.wire); req("currency", p.currency.name)
                req("principalMinor", p.principalMinor); req("totalMinor", p.totalMinor); req("installmentMinor", p.installmentMinor)
                req("cycleMonths", p.cycleMonths); req("firstDueAt", p.firstDueAt); opt("hasInterest", p.hasInterest); req("createdAt", p.createdAt)
                opt("receivedTransactionId", p.receivedTransactionId)
            }
        },
        { d ->
            InstallmentPlan(
                d.str("id"), d.str("name"), d.str("provider"), d.wire("kind", InstallmentKind::fromWire), d.wire("currency", Currency::valueOf),
                d.long("principalMinor"), d.long("totalMinor"), d.long("installmentMinor"), d.int("cycleMonths"), d.str("firstDueAt"),
                d.boolOrNull("hasInterest"), d.str("createdAt"), d.strOrNull("receivedTransactionId"),
            )
        },
    )

    val installmentPayments: DocCodec<InstallmentPayment> = codec(
        "installmentPayments", { it.id },
        { p -> doc { req("id", p.id); req("planId", p.planId); req("transactionId", p.transactionId); req("amountMinor", p.amountMinor) } },
        { d -> InstallmentPayment(d.str("id"), d.str("planId"), d.str("transactionId"), d.long("amountMinor")) },
    )

    /** مواعيد دين = مستند واحد لكل دين ⇒ المعرّف = معرّف الدين. */
    val debtTerms: DocCodec<DebtTerms> = codec(
        "debtTerms", { it.obligationId },
        { t ->
            doc {
                req("obligationId", t.obligationId); req("personId", t.personId); req("firstDueAt", t.firstDueAt); req("cycleMonths", t.cycleMonths)
                opt("installmentMinor", t.installmentMinor); opt("hasInterest", t.hasInterest)
            }
        },
        { d ->
            DebtTerms(d.str("obligationId"), d.str("personId"), d.str("firstDueAt"), d.int("cycleMonths"), d.longOrNull("installmentMinor"), d.boolOrNull("hasInterest"))
        },
    )
}

/** كل المحوّلات بالمجموعة — الـ24 بتوع التطبيق الحالي (نفس ترتيب `BACKUP_GROUPS`) + «المستحقات» + «زون التحويلات» + الزكاة + الأحداث + مصادر الدخل. */
object DocumentCodecs {
    val current: List<DocCodec<*>> = listOf(
        ReferenceCodecs.wallets, ReferenceCodecs.categories, ReferenceCodecs.merchants, ReferenceCodecs.rules, ReferenceCodecs.people,
        AssetProjectCodecs.assets, ReferenceCodecs.tags, ReferenceCodecs.budgets, ReferenceCodecs.recurringItems, LedgerCodecs.importBatches,
        LedgerCodecs.transactions, LedgerCodecs.obligations, LedgerCodecs.allocations, LedgerCodecs.settlements, LedgerCodecs.sourceRecords,
        LedgerCodecs.transactionTags, ReferenceCodecs.categoryBudgets, AssetProjectCodecs.assetLots, AssetProjectCodecs.assetSales,
        AssetProjectCodecs.assetPrices, ReferenceCodecs.notificationReceipts, AssetProjectCodecs.projects, AssetProjectCodecs.projectLinks,
        AssetProjectCodecs.projectRules,
    )

    val dues: List<DocCodec<*>> = listOf(DuesCodecs.roscas, DuesCodecs.roscaEntries, DuesCodecs.installmentPlans, DuesCodecs.installmentPayments, DuesCodecs.debtTerms)

    /** «زون التحويلات» (§60). */
    val transfers: List<DocCodec<*>> = listOf(TransferCodecs.transferParties)

    /** الزكاة (§62). */
    val zakat: List<DocCodec<*>> = listOf(ZakatCodecs.zakatFacts, ZakatCodecs.zakatYears, ZakatCodecs.zakatPayments)

    /** الأحداث ومناسبات الشخص (§64). */
    val events: List<DocCodec<*>> = listOf(EventCodecs.lifeEvents, EventCodecs.eventLinks, EventCodecs.occasions)

    /** مصادر الدخل (§48 · §64). */
    val income: List<DocCodec<*>> = listOf(IncomeCodecs.incomeSources)

    /** حساب لكل بلد (§41 · §64): سجل المساحات (حساب) + تصنيف التاجر جوه البلد (مساحة). */
    val spaces: List<DocCodec<*>> = listOf(SpaceCodecs.spaces, SpaceCodecs.spaceTransfers, SpaceCodecs.merchantCategories)

    /** التقويم: المبالغ المحجوزة وتجهيزات الأحداث (§65). */
    val calendar: List<DocCodec<*>> = listOf(CalendarCodecs.reservations, CalendarCodecs.eventPrep)

    /** دواير الأشخاص والصلات بينهم (جلسة 16) — على مستوى الحساب. */
    val circles: List<DocCodec<*>> = listOf(PersonCircleCodecs.personProfiles, PersonCircleCodecs.personRelations)

    /** خطط الادخار وإيداعاتها (§68) — على مستوى الحساب. */
    val goals: List<DocCodec<*>> = listOf(SavingsGoalCodecs.savingsGoals, SavingsGoalCodecs.goalContributions)

    val byGroup: Map<String, DocCodec<*>> = (current + dues + transfers + zakat + events + income + spaces + calendar + circles + goals).associateBy { it.group }
}
