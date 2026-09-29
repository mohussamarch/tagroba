package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.ReferenceSeedPort
import app.masroufy.port.RuleRepository
import app.masroufy.port.SeedSource
import app.masroufy.port.SeedState
import app.masroufy.port.WalletRepository

/** تجهيز حساب جديد: المحافظ الأولى (`seedWallets.ts`) والمراجع الأولى (`seedUserReferences.ts`). */

/** معرّفات ثابتة: مطلوبة لربط العمليات بالمحفظة عبر الجلسات. */
const val BANK_WALLET_ID = "wallet-bank"
const val CASH_WALLET_ID = "wallet-cash"

data class SeedWalletsOutcome(val seeded: Boolean, val wallets: List<Wallet>, val reason: String)

/**
 * المحفظتين الأوليين **برصيد صفر وتاريخ النهارده** — والمستخدم بيعدّلهم بنفسه. الزرع مرة واحدة:
 * لو فيه محافظ فالمستخدم عدّلها، وممنوع الكتابة فوقها.
 * مسار «أرصدة المالك» اللي في التطبيق الحالي **مش منقول عن قصد**: حساب المالك ومحافظه موجودين في فايربيز
 * أصلًا (KOTLIN_PLAN §1)، والمستودع عام فأرصدته ما تتكتبش في الكود.
 */
class SeedWallets(private val wallets: WalletRepository, private val clock: Clock) {
    suspend fun seed(): SeedWalletsOutcome {
        val existing = wallets.listAll()
        if (existing.isNotEmpty()) return SeedWalletsOutcome(false, existing, "المحافظ موجودة قبل كده. مش هنكتب فوق تعديلاتك.")
        val today = clock.nowIso().take(10)
        val bank = Wallet(BANK_WALLET_ID, "الراجحي", Currency.SAR, "bank", 0, today)
        val cash = Wallet(CASH_WALLET_ID, "كاش", Currency.SAR, "cash", 0, today)
        wallets.save(bank)
        wallets.save(cash)
        return SeedWalletsOutcome(true, listOf(bank, cash), "اتزرعت محفظتان برصيد صفر — عدّل الرصيد الافتتاحي من الإعدادات.")
    }
}

data class SeedOutcome(
    /** false = كانت موجودة فما اتلمسش حاجة. */
    val seeded: Boolean,
    val categories: Int,
    val rules: Int,
    val merchants: Int,
    val reason: String,
)

/**
 * زرع المراجع الأولية في تخزين المستخدم أول دخول. spec/05: «مرجع أولي **قابل للتحرير**» ⇒ لازم يتخزن.
 * العلامة: pending قبل أي كتابة و complete بعدها، والاستكمال بيضيف الناقص بس.
 */
class SeedUserReferences(
    private val categories: CategoryRepository,
    private val rules: RuleRepository,
    private val progress: ReferenceSeedPort,
) {
    suspend fun seed(source: SeedSource): SeedOutcome {
        val existing = categories.listAll()
        if (progress.begin(existing.isNotEmpty()) == SeedState.COMPLETE) {
            // التجار ما بيتعدّوش: القايمة كبيرة والعدّ قراية من غير داعي
            return SeedOutcome(false, existing.size, rules.listAll().size, 0, "المراجع متزروعة قبل كده. مش هنكتب فوق تعديلاتك.")
        }
        progress.insertMissing(source)
        progress.complete()
        return SeedOutcome(
            true, source.categories.size, source.rules.size, source.merchants.size,
            "اكتمل تجهيز التصنيفات والقواعد والتجار كمرجع أولي تقدر تعدّله.",
        )
    }
}
