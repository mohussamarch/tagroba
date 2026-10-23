package app.masroufy.core

/**
 * دليل البنوك جوه البرنامج (OVERRIDES §81 · §74 · §76): السعودية ومصر من بحث `research/banks` (ملفات JSON) — الاسم بالعربي والإنجليزي والنوع
 * والحالة وأسماء المرسلين **المؤكدة بس** (`verified: true` في البحث) ومفتاح اللوجو. البيانات نفسها متولّدة في `BankCatalogSaudi.kt` و
 * `BankCatalogEgypt.kt` (`node scripts/bank-catalog.mjs` — ما تتعدلش باليد). اللوجو صورة في أندرويد (`ui` — `BankLogo`)، وهنا مفتاحه بس.
 * **المرسل اللي مش في الدليل بيفضل باسمه الخام** (§74: «البرنامج يعرف البنوك المشهورة») — مفيش تخمين.
 */
enum class BankKind { BANK, DIGITAL_BANK, FOREIGN_BANK, WALLET, PAYMENT_APP, REMITTANCE, FINTECH }

data class Bank(
    /** «<البلد>-<المعرّف في البحث>» بحروف صغيرة («sa-al-rajhi-bank») — ده اللي بيتخزن في المحفظة (`Wallet.bankId`)، فما يتغيرش. */
    val key: String,
    /** «SA» / «EG». */
    val country: String,
    /** الاسم اللي بيظهر بالعربي (وهو الاسم المقترح للمحفظة). */
    val nameAr: String,
    val nameEn: String,
    val kind: BankKind,
    /** شغال وبيخدم الأفراد — القايمة بتعرض دول بس، و«اعرض الكل» بيضيف الباقي (شركات بس · لسه ما بدأش · خرج من السوق). */
    val retail: Boolean,
    /** أسماء المرسلين المؤكدة من رسايل حقيقية (البحث) — كتابتها زي ما بتيجي؛ المقارنة من غير حالة الحروف والرموز. */
    val senders: List<String> = emptyList(),
    /** كلمات تانية للبحث: الاسم الرسمي والمختصر والأسماء القديمة. */
    val words: List<String> = emptyList(),
    /** مفتاح صورة اللوجو (`bank_<logo>` في أندرويد) — null = مفيش لوجو (زي التجاري الدولي) ⇒ الحرف في دايرة. */
    val logo: String? = null,
) {
    /** نوع المحفظة اللي بيتعمل للبنك ده: محفظة رقمية للمحافظ والتطبيقات، وإلا حساب بنكي. */
    val walletKind: String
        get() = if (kind == BankKind.WALLET || kind == BankKind.PAYMENT_APP || kind == BankKind.REMITTANCE || kind == BankKind.FINTECH) "digital_wallet" else "bank"
}

/** كل الدليل (السعودية ثم مصر) بترتيب البحث: البنوك المحلية ثم الرقمية ثم الأجنبية ثم المحافظ. */
val BANKS: List<Bank> by lazy { SAUDI_BANKS + EGYPT_BANKS }

private val byKey: Map<String, Bank> by lazy { BANKS.associateBy { it.key } }

fun bankByKey(key: String?): Bank? = key?.let(byKey::get)

/** الاسم المعروض باللغة الحالية. */
fun bankLabel(bank: Bank): String = if (Texts.language == Language.EN) bank.nameEn else bank.nameAr

/**
 * بنوك بلد لنوع محفظة ([walletKind] «bank» / «digital_wallet» — null = الكل). [all] = false ⇒ الشغالين للأفراد بس.
 * [query] بيدوّر في الاسم بالعربي والإنجليزي والكلمات التانية (من غير همزات ولا تشكيل ولا حالة حروف).
 */
fun banksIn(country: String, walletKind: String? = null, query: String = "", all: Boolean = false): List<Bank> {
    val q = normalizeText(query)
    return BANKS.filter { b ->
        b.country.equals(country, ignoreCase = true) && (all || b.retail) && (walletKind == null || b.walletKind == walletKind) &&
            (q.isEmpty() || (listOf(b.nameAr, b.nameEn) + b.words).any { normalizeText(it).contains(q) })
    }
}

/** المرسل كمفتاح مقارنة: من غير حالة حروف ولا مسافات ولا رموز («BanK-AlAhly» = «bank alahly»). */
private fun senderKey(sender: String): String = normalizeText(sender).replace(" ", "")

/**
 * البنك المقترح لاسم مرسل رسالة (§74). [country] = بلد المساحة الشغالة (نفس الاسم ممكن يبقى لبنك تاني برا — «CIB» في المجر مثلًا)؛
 * null ⇒ يدوّر في البلدين ويرجّع بس لو بنك واحد. **مش في الدليل ⇒ null** والشاشة بتعرض الاسم الخام زي ما جه.
 */
fun bankForSender(sender: String, country: String? = null): Bank? {
    val key = senderKey(sender)
    if (key.isEmpty()) return null
    val hits = BANKS.filter { b -> (country == null || b.country.equals(country, true)) && b.senders.any { senderKey(it) == key } }
    return hits.singleOrNull()
}

/**
 * المرسل معروف في الدليل **لبلد تاني بس** (فحص 0.5.3: `AlRajhiBank` كان ظاهر في «إعداد القراءة» في مصر ومربوط ببنك مصري).
 * مرسل مش في الدليل ⇒ false (ما نعرفش بلده، فبيفضل ظاهر). [country] null ⇒ false.
 */
fun senderBelongsElsewhere(sender: String, country: String?): Boolean {
    if (country.isNullOrBlank()) return false
    val key = senderKey(sender)
    if (key.isEmpty()) return false
    val hits = BANKS.filter { b -> b.senders.any { senderKey(it) == key } }
    return hits.isNotEmpty() && hits.none { it.country.equals(country, true) }
}

/**
 * بنك المحفظة: المحفوظ فيها ([Wallet.bankId]) أولًا، وإلا من اسمها (محافظ قديمة قبل الدليل — «الراجحي» = مصرف الراجحي) لو الاسم
 * هو اسم البنك بالظبط أو فيه اسمه الكامل. الكاش ما لوش بنك. [country] = بلد المحفظة (الاسم المختصر «الأهلي» غير في البلدين).
 */
fun bankForWallet(wallet: Wallet, country: String?): Bank? {
    if (wallet.kind == "cash") return null
    bankByKey(wallet.bankId)?.let { return it }
    val name = normalizeText(wallet.name)
    if (name.isEmpty() || country == null) return null
    val local = BANKS.filter { it.country.equals(country, true) }
    local.firstOrNull { b -> (listOf(b.nameAr, b.nameEn) + b.words).any { normalizeText(it) == name } }?.let { return it }
    return local.filter { b -> normalizeText(b.nameAr).let { it.contains(' ') && name.contains(it) } }.singleOrNull()
}
