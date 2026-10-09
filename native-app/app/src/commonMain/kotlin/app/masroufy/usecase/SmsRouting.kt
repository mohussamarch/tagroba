package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Wallet
import app.masroufy.port.smsSenderKey

/**
 * **§75-11 «بنك واحد بحسابين ⇒ التفرقة بآخر ٤ أرقام»** (قرار المالك 2026-10-08 — بيقفل سؤال (ش) في §72.3): كل رسالة بتروح المحفظة اللي
 * آخر 4 أرقام حسابها = آخر 4 أرقام **حسابك** في الرسالة (`SmsRow.ownLast4`) — محفظة بنك واحدة بس في البلد بالأرقام دي. غير كده ⇒ ربط
 * المرسل زي الأول (رد المالك ١ في §72: محفظة لكل بنك، أو حساب البنك الوحيد) — ورقم كارت مش لأي محفظة بيروح محفظة المرسل.
 * المحفظة اللي أرقامها في الرسالة **مش صالحة** (مش بنك · عملة غير عملة محفظة المرسل · أكتر من محفظة بنفس الأرقام) ⇒ الرسالة بتروح محفظة
 * المرسل وبتستنى «حساب تاني من حساباتك» زي الأول (`SmsWaitReasons.kt`).
 */
internal class SmsRoute(val target: SmsReviewTarget, val messageIds: Set<String>)

/** رسايل مرسل واحد: كل محفظة ورسايلها · وعدد الرسايل اللي مالهاش محفظة (البلد فيها أكتر من حساب والمالك لسه ما اختارش). */
internal class SmsSenderRoutes(val routes: List<SmsRoute>, val unrouted: Int)

/** آخر 4 أرقام من خانة رقم الحساب (ممكن تبقى مكتوبة بمسافات أو كاملة في بيانات قديمة) — أقل من 4 أرقام ⇒ null. */
internal fun walletLast4(value: String?): String? = value?.filter { it in '0'..'9' }?.takeLast(4)?.takeIf { it.length == 4 }

/** المحفظة في البلد كهدف تسجيل: أرقامها · أرقام حسابات المالك التانية · محفظة الكاش الوحيدة (عقد C0). */
internal fun targetOf(wallet: Wallet, all: List<Wallet>): SmsReviewTarget {
    val others = all.filter { it.id != wallet.id }.mapNotNull { walletLast4(it.accountLast4) }.toSet()
    val cash = all.filter { it.kind == "cash" }.singleOrNull()?.id // عقد C0 (§75-4)
    return SmsReviewTarget(wallet.id, wallet.name, wallet.currency, walletLast4(wallet.accountLast4), others, cash)
}

/**
 * محفظة رسايل [senderKey] في البلد (رد المالك ١ — 2026-10-08): اللي المالك ربطها بالبنك ده، وإلا **لو في البلد حساب بنك واحد بس** هو.
 * أكتر من حساب بنك (أو مفيش) ومالوش ربط ⇒ null. ربط لمحفظة اتمسحت ⇒ null برضه (ما بنخمّنش).
 */
internal fun senderWallet(all: List<Wallet>, senderKey: String, mapping: Map<String, String>): Wallet? {
    val mapped = mapping[senderKey]
    return if (mapped != null) all.firstOrNull { it.id == mapped } else all.filter { it.kind == "bank" }.singleOrNull()
}

/** كل رسايل [senderKey] المفهومة في [items] ⇒ محفظتها (§75-11). */
internal fun routeSender(all: List<Wallet>, senderKey: String, mapping: Map<String, String>, items: List<InboxItem>): SmsSenderRoutes {
    val base = senderWallet(all, senderKey, mapping)
    // محفظة بنك **واحدة** بالأرقام دي في البلد — رقمين لمحفظتين ⇒ مش بنفرّق بيهم
    val byLast4: Map<String, Wallet> = all.filter { it.kind == "bank" }
        .groupBy { walletLast4(it.accountLast4) }
        .mapNotNull { (last4, wallets) -> if (last4 != null && wallets.size == 1) last4 to wallets.single() else null }
        .toMap()
    // من غير محفظة للمرسل: عملة المحفظة اللي بالأرقام لازم ما تبقاش محل شك (كل حسابات البنك في البلد بعملة واحدة)
    val oneBankCurrency = all.filter { it.kind == "bank" }.map { it.currency }.toSet().size == 1
    val grouped = LinkedHashMap<Id, MutableSet<String>>()
    var unrouted = 0
    for (item in items) {
        val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
        if (smsSenderKey(item.sender) != senderKey) continue
        val matched = row.ownLast4?.let { byLast4[it] }?.takeIf { if (base == null) oneBankCurrency else it.currency == base.currency }
        val wallet = matched ?: base
        if (wallet == null) unrouted++ else grouped.getOrPut(wallet.id) { linkedSetOf() } += item.id
    }
    val routes = grouped.map { (walletId, ids) -> SmsRoute(targetOf(all.first { it.id == walletId }, all), ids) }
    return SmsSenderRoutes(routes, unrouted)
}
