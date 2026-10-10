package app.masroufy.core

/**
 * قاعدة التجار المشتركة — نقل `src/domain/sharedMerchantCatalog.ts` (OVERRIDES §25 و§25.1).
 * بيتشارك الاسم وأشكاله وتصنيف من الشجرة وحالة التأكيد بس — مفيش مبالغ ولا تواريخ ولا مين ضاف.
 */
data class SharedMerchantEntry(
    val normalizedName: String,
    val displayName: String,
    val aliases: List<String>,
    /** تصنيف من شجرة التطبيق بس. */
    val categoryId: Id?,
    /** بيتكتب من لوحة فايربيز بس — التطبيق عمره ما يكتبه true. */
    val confirmed: Boolean,
    val updatedAt: String? = null,
)

const val MAX_SHARED_NAME = 80
const val MAX_SHARED_ALIASES = 10

/** مفتاح المستند: الاسم الموحد بشرطة مكان المسافة. */
fun sharedMerchantKey(normalizedName: String): String = normalizeText(normalizedName).replace(' ', '-')

/** 4 أرقام ورا بعض (أي نوع أرقام) = شكل حساب أو تليفون ⇒ ما يتبعتش (قاعدة 11). */
private val LONG_DIGITS = Regex("\\p{N}{4,}")

/** ينفع يتشارك؟ فاضي أو طويل أو فيه رقم طويل ⇒ null. */
fun shareableName(name: String?): String? {
    val normalized = normalizeText(name ?: "")
    if (normalized.isEmpty() || normalized.length > MAX_SHARED_NAME || LONG_DIGITS.containsMatchIn(normalized)) return null
    return normalized
}

/** كلمات الاسم بعد التوحيد اللي فيها حرف (من غير `****1234` ولا أرقام لوحدها). */
private fun nameWords(name: String): List<String> = normalizeText(name).split(' ').filter { w -> w.any { it.isLetter() } }

private fun List<String>.hasRun(run: List<String>): Boolean =
    run.isNotEmpty() && run.size <= size && (0..size - run.size).any { i -> subList(i, i + run.size) == run }

/**
 * OVERRIDES §80 · CLAUDE.md «الأمان» البند 6: القايمة المشتركة **أسامي تجار بس** — اسم شخص (من «الأشخاص» أو طرف تحويل بأي قرار)
 * عمره ما يترفع. الاسم بيتمنع لو كلمات اسم الشخص كلها جوه اسم المحل ورا بعض، أو اسم المحل كله جوه اسم الشخص، أو نفس الاسم
 * من غير مسافات («عبدالله» = «عبد الله»). الحذر أولى: محل اسمه زي اسم شخص عندك ما بيتشاركش — وبيفضل تصنيفه عندك عادي.
 */
fun namesAPerson(merchantName: String, personNames: Collection<String>): Boolean {
    val merchant = nameWords(merchantName)
    if (merchant.isEmpty()) return false
    val merchantCompact = merchant.joinToString("")
    return personNames.any { p ->
        val person = nameWords(p)
        val compact = person.joinToString("")
        compact.length >= 2 && (
            merchant.hasRun(person) || person.hasRun(merchant) || compact == merchantCompact ||
                (person.size >= 2 && merchantCompact.contains(compact))
            )
    }
}

/** المرجع اللي جوه التطبيق كأساس: التاجر المؤكد فيه بيبدأ مؤكد. */
fun baselineCatalog(merchants: List<Merchant>): Map<String, SharedMerchantEntry> {
    val out = LinkedHashMap<String, SharedMerchantEntry>()
    for (m in merchants) {
        out[sharedMerchantKey(m.normalizedName)] = SharedMerchantEntry(
            m.normalizedName, m.displayName, m.aliases.orEmpty(), m.verifiedCategoryId, !m.verifiedCategoryId.isNullOrEmpty(),
        )
    }
    return out
}

/** تعديل مش مؤكد ما يغلبش تاجر مؤكد في المرجع. */
fun effectiveEntry(baseline: SharedMerchantEntry?, remote: SharedMerchantEntry?): SharedMerchantEntry? {
    if (remote == null) return baseline
    if (baseline?.confirmed == true && !remote.confirmed) return baseline
    return remote
}

/**
 * مساهمة المستخدم لما يأكد تصنيف **شراء**؛ null = مفيش حاجة تتبعت (والمؤكد ما يتغيرش إلا من اللوحة).
 * [personNames] **إجباري** (§80): أسامي الأشخاص وأطراف التحويل — الاسم اللي يطابق واحد منهم ما بيتبعتش ([namesAPerson]).
 */
fun contributionFor(
    economicKind: EconomicKind,
    observedDirection: Direction,
    rawMerchantName: String?,
    categoryId: Id,
    treeCategoryIds: Set<Id>,
    current: SharedMerchantEntry?,
    personNames: Collection<String>,
): SharedMerchantEntry? {
    if (economicKind != EconomicKind.PURCHASE || observedDirection != Direction.OUT) return null
    if (categoryId !in treeCategoryIds) return null
    val normalized = shareableName(rawMerchantName) ?: return null
    if (namesAPerson(rawMerchantName ?: "", personNames)) return null
    if (current?.confirmed == true) return null
    if (current != null && current.categoryId == categoryId) return null
    val displayName = JsText.collapseWhitespace(JsText.trim(rawMerchantName ?: "")).take(MAX_SHARED_NAME)
    return SharedMerchantEntry(normalized, current?.displayName ?: displayName, (current?.aliases ?: emptyList()).take(MAX_SHARED_ALIASES), categoryId, false)
}

data class AccountMerchantSyncPlan(val add: List<Merchant>, val fill: List<Merchant>)

/** **المؤكد بس** بيوصل الحساب، وتصنيف المستخدم نفسه عمره ما يتكتب فوقه؛ المعرّف ثابت من الاسم. */
fun planAccountMerchantSync(entries: List<SharedMerchantEntry>, account: List<Merchant>, treeCategoryIds: Set<Id>): AccountMerchantSyncPlan {
    val index = merchantIndex(account)
    val add = mutableListOf<Merchant>()
    val fill = mutableListOf<Merchant>()
    val seen = mutableSetOf<String>()
    for (entry in entries) {
        val normalized = normalizeText(entry.normalizedName)
        if (!entry.confirmed || entry.categoryId.isNullOrEmpty() || entry.categoryId !in treeCategoryIds) continue
        if (normalized.isEmpty() || !seen.add(normalized)) continue
        val existing = index[normalized]
        if (existing != null) {
            if (existing.verifiedCategoryId.isNullOrEmpty()) fill += existing.copy(verifiedCategoryId = entry.categoryId)
            continue
        }
        add += Merchant(
            id = "merch-shared-${sharedMerchantKey(normalized)}",
            displayName = entry.displayName.ifEmpty { normalized },
            normalizedName = normalized,
            aliases = entry.aliases.takeIf { it.isNotEmpty() }?.take(MAX_SHARED_ALIASES),
            verifiedCategoryId = entry.categoryId,
        )
    }
    return AccountMerchantSyncPlan(add, fill)
}

/** أحدث وقت تعديل — منه المزامنة الجاية بتبدأ. */
fun latestUpdate(entries: List<SharedMerchantEntry>, previous: String?): String? {
    var latest = previous
    for (e in entries) if (!e.updatedAt.isNullOrEmpty() && (latest.isNullOrEmpty() || e.updatedAt > latest)) latest = e.updatedAt
    return latest
}
