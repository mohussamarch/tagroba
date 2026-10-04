package app.masroufy.core

/**
 * تحويل لنفسك بين بلدين (رد المالك §64: «زوج مربوط»): العملية **الطالعة** من بلد والعملية **الداخلة** في التانية متربطين،
 * **لا مصروف ولا دخل** (الاتنين «تحويل داخلي» مؤكد)، والسعر اللي اتاخد فعلًا **معلومة بس** — مش بيتخزن كرقم ولا بيتستعمل لتحويل أي رقم تاني
 * (القاعدة 1 و10: مفيش رقم بيتغير بكرة بسعر صرف). كل رجل بعملتها هي بالظبط زي ما في الكشف.
 * الزوج على مستوى الحساب ([SPACE_TRANSFERS_GROUP]) لأنه بين بلدين.
 */
data class SpaceTransfer(
    val id: Id,
    val fromSpaceId: String,
    val fromTransactionId: Id,
    val fromAmountMinor: Halalas,
    val fromCurrency: Currency,
    val toSpaceId: String,
    val toTransactionId: Id,
    val toAmountMinor: Halalas,
    val toCurrency: Currency,
    val createdAt: String,
    val note: String? = null,
) {
    fun involves(spaceId: String, transactionId: Id): Boolean =
        (fromSpaceId == spaceId && fromTransactionId == transactionId) || (toSpaceId == spaceId && toTransactionId == transactionId)
}

/**
 * معرّف الزوج من الرجل الطالعة (بلدها + معرّفها): نفس العملية الطالعة ما تبقاش في زوجين. البلد في المعرّف لأن معرّفات العمليات
 * ممكن تتكرر بين البلدين (كل بلد ليها عدّادها) — اختيار Claude §64.
 */
fun spaceTransferId(fromSpaceId: String, fromTransactionId: Id): Id = "stx-$fromSpaceId-$fromTransactionId"

class SpaceTransferError(message: String) : IllegalArgumentException(message)

/** رجل من الزوج: البلد والعملية ومحفظتها (لو موجودة في البلد دي). */
data class SpaceLeg(val space: Space, val transaction: Transaction, val wallet: Wallet?)

/**
 * فحص الزوج قبل أي كتابة: بلدين مختلفين · الطالعة صادرة والداخلة واردة · المبلغ موجب · كل رجل بعملة محفظتها والمحفظة موجودة في بلدها ·
 * ولا رجل في زوج تاني ([existing]) · ولا رجل متربطة بحاجة تانية ([linkedElsewhere] — مستحقات · زكاة · نقطة · مشروع · شخص).
 */
fun checkSpaceTransfer(from: SpaceLeg, to: SpaceLeg, existing: List<SpaceTransfer>, linkedElsewhere: Set<Pair<String, Id>>) {
    fun fail(key: TextKey): Nothing = throw SpaceTransferError(uiText(key))
    if (from.space.id == to.space.id) fail(TextKey.SPACE_TRANSFER_SAME_SPACE)
    if (from.transaction.observedDirection != Direction.OUT) fail(TextKey.SPACE_TRANSFER_NEEDS_OUT)
    if (to.transaction.observedDirection != Direction.IN) fail(TextKey.SPACE_TRANSFER_NEEDS_IN)
    if (from.transaction.amountMinor <= 0 || to.transaction.amountMinor <= 0) fail(TextKey.SPACE_TRANSFER_AMOUNT)
    for (leg in listOf(from, to)) {
        val wallet = leg.wallet ?: fail(TextKey.SPACE_TRANSFER_WALLET)
        if (wallet.currency != leg.transaction.currency) fail(TextKey.SPACE_TRANSFER_CURRENCY)
    }
    for (leg in listOf(from, to)) {
        if (existing.any { it.involves(leg.space.id, leg.transaction.id) }) fail(TextKey.SPACE_TRANSFER_ALREADY)
        if ((leg.space.id to leg.transaction.id) in linkedElsewhere) fail(TextKey.SPACE_TRANSFER_LINKED)
    }
}

/** الرجل بعد الربط: «تحويل داخلي» مؤكد ومراجَع — **من غير محفظة مستقبِلة** (الفلوس راحت لبلد تانية، مش لمحفظة هنا). */
fun asSpaceTransferLeg(t: Transaction, nowIso: String): Transaction = t.copy(
    economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED,
    transferToWalletId = null, updatedAt = nowIso,
)

/** بعد الفك: النوع بيرجع «لسه ما اتحددش» ويتسأل تاني (زي فك القسط والنقطة) — **العملية نفسها ما بتتمسحش**. */
fun asUnlinkedLeg(t: Transaction, nowIso: String): Transaction = t.copy(
    economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW, updatedAt = nowIso,
)

/**
 * السعر اللي اتاخد فعلًا **للعرض بس** («1 ر.س = 12.3456 ج.م»): أعداد صحيحة، 4 أرقام بعد العلامة، تقريب نص لفوق.
 * مش بيتخزن ومش بيدخل أي حساب.
 */
fun spaceTransferRateText(t: SpaceTransfer): String {
    // قسمة طويلة رقم رقم عشان ما يحصلش فيض حتى مع أكبر مبلغ آمن
    val from = t.fromAmountMinor
    var whole = t.toAmountMinor / from
    var rem = t.toAmountMinor % from
    var frac = 0L
    repeat(4) {
        rem *= 10
        frac = frac * 10 + rem / from
        rem %= from
    }
    if (rem * 2 >= from) frac++
    if (frac == 10_000L) {
        whole++
        frac = 0
    }
    return "1 ${t.fromCurrency.name} = $whole.${frac.toString().padStart(4, '0')} ${t.toCurrency.name}"
}
