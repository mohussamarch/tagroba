package app.masroufy.core

/**
 * صفحة الشخص (جولة r05 — المالك 2026-10-16: «ليه لما أدخل على أي شخص مش بيظهر السجل بينا؟»): «السجل بينا» من **كل** اللي بيربط عملية
 * بالشخص — روابط [PersonLink] · ديونه ([Obligation]) وسداداتها ([Settlement]) · تحويلات حساباته المربوطة ([TransferParty]). حساب بس من غير
 * تخزين. الصافي = ليك − عليك **بنفس العملة** (من غير جمع عملتين ولا مقاصة في الأرصدة نفسها — spec/02). كل المبالغ بالهللة.
 */
enum class PersonEntryType {
    /** عملية اتربطت بالشخص بمعنى (سلفة · سداد · هدية · «مع الشخص ده»). */
    LINK,

    /** دين أصله عملية (من «اربطها بـ» القديمة أو أسئلة التحويلات). */
    DEBT,

    /** سداد على دين، بعملية. */
    SETTLEMENT,

    /** دين قديم من غير عملية (OVERRIDES §27). */
    OPENING_DEBT,

    /** سداد اتسجل بإيدك من غير عملية. */
    MANUAL_SETTLEMENT,

    /** تحويل من أو لحساب مربوط بالشخص — من غير معنى لسه. */
    ACCOUNT_TRANSFER,
}

data class PersonEntry(
    val type: PersonEntryType,
    val transactionId: Id?,
    val obligationId: Id?,
    /** يوم العملية — "" = من غير تاريخ (دين قديم أو سداد بإيدك). */
    val date: String,
    val amountMinor: Halalas,
    val currency: Currency,
    /** فلوس طلعت منك ولا دخلتلك — null = مفيش عملية. */
    val direction: Direction?,
    val meaning: PersonMeaning?,
    val obligationKind: ObligationKind?,
    /** الاسم زي ما جه في العملية (التاجر · الطرف · ملاحظتك) — null = مفيش. */
    val title: String?,
    val auto: Boolean,
    /** أثر السطر على الصافي: موجب = زوّد اللي ليك أو قلّل اللي عليك. */
    val effectMinor: Halalas,
    /** الصافي بعد السطر ده بنفس العملة (بالترتيب الزمني). */
    val netAfterMinor: Halalas,
)

/** دين مع الشخص بالمتبقي — [date] يوم العملية اللي عملته ("" = دين قديم). */
data class PersonDebtLine(val obligation: Obligation, val remainingMinor: Halalas, val date: String)

/** ليك · عليك · الصافي لعملة واحدة (من الديون المفتوحة). */
data class PersonTotals(val currency: Currency, val owedToYouMinor: Halalas, val youOweMinor: Halalas, val netMinor: Halalas)

data class PersonLedger(
    /** الأحدث الأول. */
    val entries: List<PersonEntry>,
    val open: List<PersonDebtLine>,
    val settled: List<PersonDebtLine>,
    val totals: List<PersonTotals>,
) {
    val isEmpty: Boolean get() = entries.isEmpty() && open.isEmpty() && settled.isEmpty()
}

private fun signOf(kind: ObligationKind): Long = if (kind == ObligationKind.RECEIVABLE) 1L else -1L

private fun titleOf(t: Transaction): String? =
    (t.rawMerchantName ?: t.note ?: t.rawDescription)?.let { logicalArabic(it) }?.trim()?.take(60)?.takeIf { it.isNotEmpty() }

/**
 * «السجل بينا» + الديون المفتوحة واللي اتقفلت + الأرصدة. [txns] = العمليات اللي الروابط والديون بتشاور عليها (اللي مش موجودة بتتعرض من غير
 * تاريخ) · [accountTxns] = تحويلات الحسابات المربوطة (اللي ليها سطر تاني ما بتتكررش).
 */
fun personLedger(
    links: List<PersonLink>,
    obligations: List<Obligation>,
    settlements: List<Settlement>,
    txns: Map<Id, Transaction>,
    accountTxns: List<Transaction> = emptyList(),
): PersonLedger {
    val byObligation = obligations.associateBy { it.id }
    // مراجعة r051: الدين اللي الرابط نفسه عمله بس بيتعد جوه سطر الرابط — دين تاني من نفس العملية (نصيب «حد تاني» · سؤال) ليه سطره
    val linkObligations = links.mapNotNull { it.obligationId }.toSet()
    val linkSettlements = links.flatMap { it.settlementIds }.toSet()
    fun settleEffect(s: Settlement): Halalas = byObligation[s.obligationId]?.let { -signOf(it.kind) * s.amountMinor } ?: 0L
    val raw = mutableListOf<PersonEntry>()
    for (l in links) {
        val t = txns[l.transactionId] ?: continue
        val ob = l.obligationId?.let(byObligation::get)
        val effect = sumMoney(listOf(ob?.let { signOf(it.kind) * it.originalMinor } ?: 0L) + settlements.filter { it.id in l.settlementIds }.map(::settleEffect))
        raw += PersonEntry(
            PersonEntryType.LINK, t.id, l.obligationId, t.occurredAt.take(10), l.amountMinor, t.currency, t.observedDirection, l.meaning, ob?.kind,
            titleOf(t), l.auto, effect, 0L,
        )
    }
    for (o in obligations) {
        if (o.id in linkObligations) continue
        val origin = o.originTransactionId
        val t = origin?.let(txns::get)
        raw += PersonEntry(
            if (origin == null) PersonEntryType.OPENING_DEBT else PersonEntryType.DEBT, origin, o.id, t?.occurredAt?.take(10).orEmpty(),
            o.originalMinor, o.currency, t?.observedDirection, null, o.kind, t?.let(::titleOf), false, signOf(o.kind) * o.originalMinor, 0L,
        )
    }
    for (s in settlements) {
        if (s.id in linkSettlements) continue
        val ob = byObligation[s.obligationId] ?: continue
        val t = s.transactionId.takeIf { it.isNotEmpty() }?.let(txns::get)
        raw += PersonEntry(
            if (t == null) PersonEntryType.MANUAL_SETTLEMENT else PersonEntryType.SETTLEMENT, t?.id, ob.id, t?.occurredAt?.take(10).orEmpty(),
            s.amountMinor, ob.currency, t?.observedDirection, null, ob.kind, t?.let(::titleOf), false, settleEffect(s), 0L,
        )
    }
    val seen = raw.mapNotNull { it.transactionId }.toSet()
    for (t in accountTxns.distinctBy { it.id }) {
        if (t.id in seen) continue
        raw += PersonEntry(
            PersonEntryType.ACCOUNT_TRANSFER, t.id, null, t.occurredAt.take(10), t.amountMinor, t.currency, t.observedDirection, null, null,
            titleOf(t), false, 0L, 0L,
        )
    }
    val chrono = withRunningNet(raw)
    val lines = obligations.map { o ->
        PersonDebtLine(o, remainingOfObligation(o, settlements), o.originTransactionId?.let(txns::get)?.occurredAt?.take(10).orEmpty())
    }.sortedWith(compareByDescending<PersonDebtLine> { it.date }.thenBy { it.obligation.id })
    val totals = lines.groupBy { it.obligation.currency }.map { (currency, list) ->
        val owed = sumMoney(list.filter { it.obligation.kind == ObligationKind.RECEIVABLE }.map { it.remainingMinor })
        val owe = sumMoney(list.filter { it.obligation.kind != ObligationKind.RECEIVABLE }.map { it.remainingMinor })
        PersonTotals(currency, owed, owe, subtractMoney(owed, owe))
    }
    return PersonLedger(chrono.reversed(), lines.filter { it.remainingMinor > 0 }, lines.filter { it.remainingMinor <= 0 }, totals)
}

/** بالترتيب الزمني (اللي من غير تاريخ الأول — أقدم حاجة) عشان الصافي يتراكم صح **لكل عملة لوحدها**؛ الشاشة بتعرض الأحدث الأول. */
private fun withRunningNet(raw: List<PersonEntry>): List<PersonEntry> {
    val running = HashMap<Currency, Halalas>()
    return raw.sortedWith(compareBy<PersonEntry>({ it.date }, { it.transactionId.orEmpty() }, { it.obligationId.orEmpty() })).map { e ->
        val net = addMoney(running[e.currency] ?: 0L, e.effectMinor)
        running[e.currency] = net
        e.copy(netAfterMinor = net)
    }
}

/**
 * صفحة الشخص في **كل البلاد** (رد المالك 2026-10-16 — A3): السجل كله مع بعض، والصافي بيتراكم لكل عملة لوحدها، والأرصدة لكل عملة لوحدها —
 * عمر مبلغين بعملتين ما بيتجمعوا.
 */
fun mergePersonLedgers(ledgers: List<PersonLedger>): PersonLedger {
    if (ledgers.size == 1) return ledgers.single()
    val byDate = compareByDescending<PersonDebtLine> { it.date }.thenBy { it.obligation.id }
    val totals = ledgers.flatMap { it.totals }.groupBy { it.currency }.map { (currency, list) ->
        val owed = sumMoney(list.map { it.owedToYouMinor })
        val owe = sumMoney(list.map { it.youOweMinor })
        PersonTotals(currency, owed, owe, subtractMoney(owed, owe))
    }
    return PersonLedger(
        withRunningNet(ledgers.flatMap { it.entries }).reversed(), ledgers.flatMap { it.open }.sortedWith(byDate),
        ledgers.flatMap { it.settled }.sortedWith(byDate), totals,
    )
}

/** ليه العملية دي متقترحة للشخص: نفس الحساب · نفس الاسم · نفس مبلغ دين مفتوح (بالترتيب ده في القوة). */
enum class SuggestWhy { ACCOUNT, NAME, AMOUNT }

data class PersonSuggestion(val transaction: Transaction, val why: SuggestWhy)

/**
 * «اربط عمليات موجودة»: من [candidates] (العمليات اللي **مش مربوطة** بحد) — طرفها حساب مربوط بالشخص · أو اسم الطرف أو التاجر فيه كلمة من اسمه
 * (3 حروف أو أكتر، مطبّعة) · أو تحويل مبلغه = متبقي دين مفتوح معاه. الأقوى الأول وبعدين الأحدث.
 */
fun personSuggestions(
    personName: String,
    accounts: List<TransferParty>,
    openAmounts: Set<Halalas>,
    candidates: List<Transaction>,
    limit: Int = 20,
): List<PersonSuggestion> {
    val tokens = normalizeText(personName).split(' ').filter { it.length >= 3 }
    fun named(text: String?) = text != null && tokens.isNotEmpty() && normalizeText(logicalArabic(text)).let { n -> tokens.any { n.contains(it) } }
    return candidates.mapNotNull { t ->
        val party = transferPartyOf(t)
        val why = when {
            party != null && accounts.any { partyMatches(it, party) } -> SuggestWhy.ACCOUNT
            named(party?.label) || named(t.rawMerchantName) -> SuggestWhy.NAME
            t.amountMinor in openAmounts && isTransferLike(t) -> SuggestWhy.AMOUNT
            else -> null
        } ?: return@mapNotNull null
        PersonSuggestion(t, why)
    }.sortedWith(compareBy<PersonSuggestion> { it.why.ordinal }.thenByDescending { it.transaction.occurredAt }).take(limit)
}

/** البحث في «اربط عملية»: بالاسم (التاجر · الوصف · الملاحظة، مطبّع) أو بالمبلغ لو كتبت أرقام بس (الريالات أو الهللات). الأحدث الأول. */
fun searchOperations(query: String, txns: List<Transaction>, limit: Int = 30): List<Transaction> {
    val q = normalizeText(logicalArabic(latinizeDigits(query)))
    if (q.isEmpty()) return emptyList()
    val digits = q.replace(" ", "").takeIf { s -> s.all { it in '0'..'9' } }
    return txns.filter { t ->
        listOfNotNull(t.rawMerchantName, t.rawDescription, t.note).any { normalizeText(logicalArabic(it)).contains(q) } ||
            (digits != null && (t.amountMinor.toString() == digits || (t.amountMinor / 100).toString() == digits))
    }.sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenBy { it.id }).take(limit)
}

/**
 * «سجّل تحويلاته لوحده»: العمليات اللي طرفها حساب مربوط بشخص والتسجيل التلقائي شغال عليه ([TransferParty.autoMeaning])، يومها من يوم التشغيل
 * ولقدام ([TransferParty.autoSince])، ومش مربوطة ([linked]). العملية الواحدة بأول حساب يطابقها.
 */
fun autoLinkTargets(parties: List<TransferParty>, txns: List<Transaction>, linked: Set<Id>): List<Pair<Transaction, TransferParty>> {
    val autos = parties.filter { it.verdict == TransferVerdict.PERSON && it.personId != null && it.autoMeaning != null }
    if (autos.isEmpty()) return emptyList()
    return txns.mapNotNull { t ->
        if (t.id in linked) return@mapNotNull null
        // A2/F3: المالك فك ربطها أو مسح دينها أو أكد نوعها بإيده (المراجعة · التغيير المجمّع · لوحة النوع) ⇒ القاعدة ما تكتبش فوق اختياره
        if (t.personRulesOff == true || t.economicKindConfirmed) return@mapNotNull null
        val ref = transferPartyOf(t) ?: return@mapNotNull null
        val party = autos.firstOrNull { partyMatches(it, ref) && t.occurredAt.take(10) >= it.autoSince.orEmpty().take(10) } ?: return@mapNotNull null
        // A2/F3: المالك فك ربطها أو غيّر معناها ⇒ القاعدة عمرها ما ترجع عليها
        if (autos.any { partyMatches(it, ref) && it.skips(t.id) }) return@mapNotNull null
        t to party
    }
}
