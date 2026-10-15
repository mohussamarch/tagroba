package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.Bank
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.core.UiKey
import app.masroufy.core.bankLabel
import app.masroufy.core.banksIn
import app.masroufy.ui.components.BankLogo
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «اختار بنكك» (OVERRIDES §81): بحث بالاسم (عربي أو إنجليزي، من غير همزات) + قايمة من دليل البنوك بلوجوهاتها — الشغالين للأفراد بس،
 * و«اعرض الكل» بيضيف الباقي بسببه («للشركات …») — + «بنك تاني» ([allowOther] — اسم بيتكتب، من غير بنك من الدليل ⇒ [onPick] null).
 * [walletKind] «bank» = البنوك (والرقمية) · «digital_wallet» = المحافظ والتطبيقات. القايمة نفسها بتتمرر جوه [maxHeight].
 */
@Composable
fun BankPickerList(country: String, walletKind: String, onPick: (Bank?) -> Unit, allowOther: Boolean = true, maxHeight: Dp = 340.dp) {
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    val banks = remember(country, walletKind, query, all) { banksIn(country, walletKind, query, all) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(query, { query = it }, placeholder = t(UiKey.BANK_SEARCH), imeAction = ImeAction.Search,
            trailing = { LucideIcon(Lucide.SEARCH, size = 18.dp, tint = Ink.muted) }, draft = false)
        if (banks.isEmpty()) BasicText(t(UiKey.BANK_NONE), style = Type.caption().copy(color = Ink.muted))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            items(banks, key = { it.key }) { b -> BankRow(b) { onPick(b) } }
        }
        if (!all) TonalButton(t(UiKey.BANK_SHOW_ALL), { all = true }, Modifier.fillMaxWidth(), height = 44.dp, muted = true)
        if (allowOther) {
            ListRow(
                t(if (walletKind == "digital_wallet") UiKey.BANK_OTHER_WALLET else UiKey.BANK_OTHER),
                leading = { IconTile(Ink.primary) { LucideIcon(Lucide.PLUS, size = 18.dp, tint = Ink.primary) } },
                onClick = { onPick(null) },
                padding = PaddingValues(vertical = 6.dp),
            )
        }
    }
}

/** سطر بنك: اللوجو (أو الحرف) · الاسم · تحته الاسم باللغة التانية (أو سبب إنه مش للأفراد في «اعرض الكل»). */
@Composable
private fun BankRow(b: Bank, onClick: () -> Unit) {
    val other = if (Texts.language == Language.EN) b.nameAr else b.nameEn
    ListRow(
        bankLabel(b),
        subtitle = if (b.retail) other else t(UiKey.BANK_NOT_RETAIL),
        leading = { BankLogo(b, b.nameAr, size = 36.dp, wide = true) },
        onClick = onClick,
        chevron = false,
        padding = PaddingValues(vertical = 6.dp),
    )
}

/** البنك اللي اتختار (فوق الخانات): لوجو · اسم · «تغيير» بيرجّع القايمة. */
@Composable
fun PickedBankRow(bank: Bank, onChange: () -> Unit) {
    ListRow(
        bankLabel(bank),
        leading = { BankLogo(bank, bank.nameAr, size = 40.dp, wide = true) },
        trailing = { BasicText(t(UiKey.BANK_CHANGE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary)) },
        onClick = onChange,
        padding = PaddingValues(vertical = 4.dp),
    )
}

/** «بنك تاني» اتختار: الاسم بيتكتب في الخانة اللي تحت · «تغيير» بيرجّع القايمة. */
@Composable
fun PickedOtherRow(label: String, onChange: () -> Unit) {
    ListRow(
        label,
        leading = { IconTile(Ink.primary) { LucideIcon(Lucide.BUILDING_2, size = 18.dp, tint = Ink.primary) } },
        trailing = { BasicText(t(UiKey.BANK_CHANGE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary)) },
        onClick = onChange,
        padding = PaddingValues(vertical = 4.dp),
    )
}
