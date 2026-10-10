package app.masroufy.usecase

import app.masroufy.core.CountryPack
import app.masroufy.core.Halalas
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.port.WalletRepository

/**
 * كارتين الفلوس (OVERRIDES §81 — «معاك كام كاش؟» ثم «عندك حساب بنكي؟») لما الحساب الجديد يختار **بلد غير الأساسية** في أول تشغيل
 * (مصر — جولة 0.3): البلد بتتعمل من نفس طريق «البلدان» (`ManageSpaces` — تصنيفات وقواعد من غير محافظ)، فالكاش والبنك بيتكتبوا هنا
 * **في محافظ البلد دي** بعملتها ويومها ([manage] متجمّع على مستودعات البلد).
 * - كل حاجة بتتأكد قبل أي كتابة (المبلغ · البنك بـ`ManageWallets.prepare`).
 * - محفظة الكاش بتتعمل دايمًا (صفر لو اتخطى — زي البلد الأساسية)، باسم الحزمة وعملتها، ورصيدها من النهارده في البلد.
 * - الإعادة بعد انقطاع ما بتكررش: محفظة كاش واحدة ([CASH_WALLET_ID]) والبنك ما بيتضافش تاني لو نفس البنك موجود.
 */
class NewCountryMoney(
    private val wallets: WalletRepository,
    private val manage: ManageWallets,
    private val pack: CountryPack,
) {
    suspend fun apply(cashMinor: Halalas?, bank: AddWalletInput?): OnboardingResult {
        if (cashMinor != null && (cashMinor < 0 || cashMinor > MAX_SAFE_HALALAS)) {
            return OnboardingResult.Failed("cash", uiText(TextKey.ONBOARD_CASH_INVALID))
        }
        val existing = wallets.listAll()
        val newBank = bank?.takeUnless { b -> b.bankId != null && existing.any { it.bankId == b.bankId } }
        val bankWallet = if (newBank == null) null else {
            try {
                manage.prepare(newBank)
            } catch (e: WalletError) {
                return OnboardingResult.Failed("bank", e.message ?: uiText(TextKey.NOT_AVAILABLE))
            }
        }
        val today = manage.today()
        val cash = existing.find { it.kind == "cash" }
        if (cash == null) {
            wallets.save(Wallet(CASH_WALLET_ID, pack.cashWalletName, pack.currency, "cash", cashMinor ?: 0, today))
        } else if (cashMinor != null && cashMinor != cash.openingBalanceMinor) {
            wallets.save(cash.copy(openingBalanceMinor = cashMinor, openingAt = today))
        }
        if (bankWallet != null) wallets.save(bankWallet)
        return OnboardingResult.Ok
    }
}
