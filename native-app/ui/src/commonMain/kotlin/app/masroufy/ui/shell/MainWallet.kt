package app.masroufy.ui.shell

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet

/**
 * **المحفظة الأساسية لكل بلد** (قرار المالك 2026-10-09 — OVERRIDES §78 ٢): أول مصروف بيسأل «بتصرف عادةً منين؟» (لوحة «+» مفتوحة **من غير محفظة
 * مختارة** والحفظ مقفول لحد ما يختار)، والاختيار بيبقى الأساسي ⇒ بعدها بتتختار لوحدها في كل إضافة، وتتغير في أي عملية عادي.
 * متخزنة **على الحساب لكل بلد** من المحرك (`MainSpendingWallets` عن طريق `ShellDeps.addWalletDefault` / `setMainWallet`) — تفاصيل المحفظة
 * «اجعلها الأساسية» وعلامة «الأساسية» في قايمة المحافظ من نفس المكان (`MoreGraph.mainWallet`).
 */

/** المحفظة أول ما اللوحة تفتح: الأساسية لو لسه موجودة — وإلا **فاضية** (مش أول بنك زي الأول). */
fun initialFromWallet(mainWalletId: String?, wallets: List<Wallet>): String? = mainWalletId?.takeIf { id -> wallets.any { it.id == id } }

/** عنوان سطر المحفظة: «من أين؟» لو فيه أساسية · «من أين تصرف عادةً؟» لو لسه. */
fun fromLabelKey(hasMain: Boolean): TextRef = if (hasMain) UiKey.ADD_FROM else UiKey.ADD_FROM_USUAL
