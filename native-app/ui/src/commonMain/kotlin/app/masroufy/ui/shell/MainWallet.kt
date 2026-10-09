package app.masroufy.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet

/**
 * **المحفظة الأساسية لكل بلد** (قرار المالك 2026-10-09 — آخر §76): أول مصروف بيسأل «من أين تصرف عادةً؟» (لوحة «+» مفتوحة **من غير محفظة
 * مختارة** والحفظ مقفول لحد ما يختار)، والاختيار بيبقى الأساسي ⇒ بعدها بتتختار لوحدها في كل إضافة، وتتغير في أي عملية عادي.
 * ⚠️ **ناقص في المنطق هنا** (بيتبني على فرع `assistant-engine`، ويتوصل وقت الدمج): حفظها مع الحساب
 * (`AssistantSettings.set(walletId, MainWalletSource.ADD_SHEET)`) · «اجعلها الأساسية» في تفاصيل المحفظة وعلامة «الأساسية» في قايمة المحافظ
 * (منطقة المحافظ) · نفس السؤال في المساعد. لحد ده: **للجلسة دي بس** — زي النموذج بالظبط (`masroufy-main-wallet` في `sessionStorage`).
 */
@Stable
object MainWalletChoice {
    private val bySpace = mutableStateMapOf<String, String>()

    fun of(spaceId: String): String? = bySpace[spaceId]

    fun set(spaceId: String, walletId: String) {
        bySpace[spaceId] = walletId
    }

    /** للاختبارات بس. */
    internal fun clear() = bySpace.clear()
}

/** المحفظة أول ما اللوحة تفتح: الأساسية لو لسه موجودة — وإلا **فاضية** (مش أول بنك زي الأول). */
fun initialFromWallet(mainWalletId: String?, wallets: List<Wallet>): String? = mainWalletId?.takeIf { id -> wallets.any { it.id == id } }

/** عنوان سطر المحفظة: «من أين؟» لو فيه أساسية · «من أين تصرف عادةً؟» لو لسه. */
fun fromLabelKey(hasMain: Boolean): TextKey = if (hasMain) TextKey.ADD_FROM else TextKey.ADD_FROM_USUAL
