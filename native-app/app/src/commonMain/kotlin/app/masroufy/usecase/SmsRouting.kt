package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.Wallet
import app.masroufy.port.WalletRepository
import app.masroufy.port.smsSenderKey

/**
 * **§75-11 «بنك واحد بحسابين ⇒ التفرقة بآخر ٤ أرقام»** (قرار المالك 2026-10-08 — بيقفل سؤال (ش) في §72.3): كل رسالة بتروح المحفظة اللي
 * آخر 4 أرقام حسابها = آخر 4 أرقام **حسابك** في الرسالة (`SmsRow.accountLast4` — سطر الحساب بس، **مش الكارت**) — محفظة بنك واحدة بس في
 * البلد بالأرقام دي، و**بتاعة المرسل ده**: مربوطة بيه أو مش مربوطة بمرسل تاني ([accountWallet] — مراجعة S1: آخر 4 أرقام كارت من بنك تاني
 * زي أرقام حساب في بنك تاني كانت بتودّي كل مشترياته المحفظة الغلط لوحدها، وكانت بتغلب اختيار المالك لمحفظة البنك ده). غير كده ⇒ ربط
 * المرسل زي الأول (رد المالك ١ في §72: محفظة لكل بنك، أو حساب البنك الوحيد).
 * المحفظة اللي أرقامها في الرسالة **مش صالحة** (مش بنك · عملة غير عملة محفظة المرسل · أكتر من محفظة بنفس الأرقام · بتاعة بنك تاني) ⇒ الرسالة
 * بتروح محفظة المرسل وبتستنى «حساب تاني من حساباتك» زي الأول (`SmsWaitReasons.kt`).
 * **التسجيل في الخلفية والشاشة نفس القاعدة** (مراجعة S1): الشاشة كانت بتسجّل كل الرسايل في المحفظة اللي اتفتحت عليها.
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

/**
 * المحفظة اللي أرقام **حساب** [row] بتقولها (§75-11)، أو null: محفظة بنك **واحدة** بس في البلد بالأرقام دي (رقمين لمحفظتين ⇒ مش بنفرّق
 * بيهم)، **بتاعة [senderKey]** (مربوطة بيه، أو مش مربوطة بأي مرسل تاني)، وعملتها عملة [base] — ولو [base] null (المرسل مالوش محفظة) كل
 * حسابات البنك في البلد بعملة واحدة (العملة ما تبقاش محل شك).
 */
internal fun accountWallet(row: SmsRow, senderKey: String, all: List<Wallet>, mapping: Map<String, String>, base: Wallet?): Wallet? {
    val digits = row.accountLast4 ?: return null
    val banks = all.filter { it.kind == "bank" }
    val wallet = banks.filter { walletLast4(it.accountLast4) == digits }.singleOrNull() ?: return null
    val owners = mapping.filterValues { it == wallet.id }.keys
    if (owners.isNotEmpty() && senderKey !in owners) return null
    val currencyOk = if (base == null) banks.map { it.currency }.toSet().size == 1 else wallet.currency == base.currency
    return wallet.takeIf { currencyOk }
}

/** كل رسايل [senderKey] المفهومة في [items] ⇒ محفظتها (§75-11). */
internal fun routeSender(all: List<Wallet>, senderKey: String, mapping: Map<String, String>, items: List<InboxItem>): SmsSenderRoutes {
    val base = senderWallet(all, senderKey, mapping)
    val grouped = LinkedHashMap<Id, MutableSet<String>>()
    var unrouted = 0
    for (item in items) {
        val row = (item.parsed as? SmsParseResult.Ok)?.row ?: continue
        if (smsSenderKey(item.sender) != senderKey) continue
        val wallet = accountWallet(row, senderKey, all, mapping, base) ?: base
        if (wallet == null) unrouted++ else grouped.getOrPut(wallet.id) { linkedSetOf() } += item.id
    }
    val routes = grouped.map { (walletId, ids) -> SmsRoute(targetOf(all.first { it.id == walletId }, all), ids) }
    return SmsSenderRoutes(routes, unrouted)
}

/**
 * مراجعة S1 — الشاشة (تسجيل المالك) بنفس قاعدة الخلفية: الرسالة اللي أرقام حسابها لمحفظة تانية بتاعة نفس البنك ([accountWallet]) بتتسجل
 * **هناك**، والباقي في المحفظة اللي الشاشة اتفتحت عليها ([screen] — اختيار المالك). محافظ البلد وربط البنوك بيتقروا مرة لكل معاينة.
 */
internal class ScreenRouter private constructor(
    private val all: List<Wallet>,
    private val mapping: Map<String, String>,
    private val screen: SmsReviewTarget,
) {
    private val base = all.firstOrNull { it.id == screen.walletId }

    /** هدف تسجيل [row] من [senderKey]: محفظة أرقام حسابها لو غير محفظة الشاشة، وإلا الشاشة نفسها. */
    fun targetFor(row: SmsRow, senderKey: String): SmsReviewTarget {
        val routed = accountWallet(row, senderKey, all, mapping, base)?.takeIf { it.id != screen.walletId } ?: return screen
        return targetOf(routed, all)
    }

    companion object {
        suspend fun load(wallets: WalletRepository, learning: SmsLearning, screen: SmsReviewTarget): ScreenRouter =
            ScreenRouter(wallets.listAll(), learning.inbox.senderWallets(learning.spaceId), screen)
    }
}
