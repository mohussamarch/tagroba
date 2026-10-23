package app.masroufy.ui.screens.imports

import androidx.compose.runtime.Composable
import app.masroufy.core.Category
import app.masroufy.core.SmsKind
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.screens.operations.EmployerPickSheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** اللي بيتصنّف من اللوحة: رسالة لسه مستنية (التصنيف بيتحفظ مع «سجّل الكل») أو عملية اتسجلت. */
internal sealed interface PickFor {
    data class Line(val line: WaitLineUi) : PickFor

    data class Row(val row: RecordedUi) : PickFor
}

/**
 * لوحات «رسائل البنك» (`BankSmsScreen` — اتفصلت في r06-polish عشان حد الـ300 سطر، من غير تغيير في السلوك): التصنيف · «أيوه، ده حسابي»
 * (OVERRIDES §91-1) · جهة الشغل بعد «أيوه ده راتبي» · محفظة البنك. الحالة نفسها عايشة في الشاشة، وهنا بس الرسم والأفعال.
 */
@Composable
internal fun BankSmsSheets(
    deps: ImportsDeps,
    r05: SmsR05,
    scope: CoroutineScope,
    categories: List<Category>,
    wallets: List<Wallet>,
    chosen: Map<String, String>,
    pick: PickFor?,
    onPickClosed: () -> Unit,
    accountLine: WaitLineUi?,
    onAccountClosed: () -> Unit,
    bankSheet: BankWaitingUi?,
    onBankClosed: () -> Unit,
    act: (suspend () -> Unit) -> Unit,
    reload: () -> Unit,
) {
    val toaster = LocalToaster.current
    // r05 (3a): العنوان اسم التاجر أو الطرف — عمره ما يبقى اسم المرسل («AlRajhiBank»)، ومن غير اسم ⇒ «تصنيف العملية»
    val title = when (pick) {
        is PickFor.Line -> pick.line.merchant
        is PickFor.Row -> pick.row.title
        null -> ""
    }
    CategoryPickerSheet(
        visible = pick != null,
        title = if (title.isBlank()) t(UiKey.SMS_R05_CATPICK_PLAIN) else t(UiKey.SMS_CATPICK_TITLE, title),
        categories = categories,
        selectedId = when (pick) { is PickFor.Line -> chosen[pick.line.messageId] ?: pick.line.categoryId; is PickFor.Row -> pick.row.categoryId; null -> null },
        onPick = { cat ->
            onPickClosed()
            val sms = deps.sms
            when (pick) {
                is PickFor.Line -> {
                    // r05 (4a): الاختيار بيشيلها على طول، و«تراجع» 8 ثواني، وبعدها بتتسجل (وشكلها بيتعلّم)
                    r05.picks.add(scope, pick.line, cat) { id, catId -> r05.commitPick(id, catId); reload() }
                    val party = pick.line.kind == SmsKind.TRANSFER_IN || pick.line.kind == SmsKind.TRANSFER_OUT
                    if (!party && pick.line.merchant.isNotBlank()) scope.launch { sms?.remember(pick.line.merchant, cat.id, pick.line.direction) }
                }
                is PickFor.Row -> act {
                    deps.transactions.setCategory(pick.row.id, cat.id)
                    // r041b P9: اسم شخص عمره ما يدخل ذاكرة التجار (CLAUDE.md الأمان 7)
                    if (!pick.row.party) sms?.remember(pick.row.merchant, cat.id, pick.row.direction)
                }
                null -> Unit
            }
            if (title.isNotBlank() && (pick as? PickFor.Row)?.row?.party != true) toaster.show(t(UiKey.BANK_SMS_REMEMBERED, title, cat.name), dark = true)
        },
        onDismiss = onPickClosed,
    )
    val asked = accountLine
    // r041b P10: «محفظة جديدة لهذا الحساب» + كل محافظ البنك (نقل الأرقام بملاحظة صريحة) — مفيش لوحة فاضية من غير مخرج
    // فحص المحاكي (بند 8): محافظ بنك الرسالة بس — رسالة الراجحي ما تعرضش محفظة بنك تاني
    AccountWalletSheet(asked?.askLast4, accountSheetWallets(wallets, asked?.walletId), onDismiss = onAccountClosed) { choice ->
        onAccountClosed()
        val last4 = asked?.askLast4
        val messageId = asked?.messageId
        val from = asked?.walletId
        if (last4 != null) act {
            when (choice) {
                is AccountPick.Existing -> {
                    val r = deps.sms?.answerAccount(choice.wallet.id, last4, mine = true, messageId = messageId, replace = choice.replaces != null)
                    toaster.show(t(UiKey.SMS_ACCOUNT_MINE_SAVED, last4, choice.wallet.name, opsCount(r?.recorded ?: 0)), dark = true)
                }
                AccountPick.NewWallet -> if (from != null) deps.sms?.accountNewWallet(from, last4, messageId)?.let { (w, r) ->
                    toaster.show(t(UiKey.SMS_ACCOUNT_NEW_SAVED, w.name, last4, opsCount(r.recorded)), dark = true)
                }
            }
        }
    }

    val employer = r05.employerTxn
    if (employer != null && LocalSpace.current.operations.incomeAnswers != null) {
        EmployerPickSheet(true, employer, onDismiss = { r05.employerTxn = null }) { done -> r05.employerTxn = null; toaster.show(done.title, dark = true); reload() }
    }
    BankWalletSheet(
        bank = bankSheet,
        wallets = wallets,
        onDismiss = onBankClosed,
        onSave = { bank, wallet ->
            onBankClosed()
            act {
                val r = deps.sms?.chooseWallet(bank.sender, wallet.id)
                toaster.show(t(UiKey.BANK_SMS_WALLET_SAVED, opsCount(r?.recorded ?: 0), wallet.name), dark = true)
            }
        },
    )
}
