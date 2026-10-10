package app.masroufy.usecase

import app.masroufy.port.CategoryRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.WalletRepository

/**
 * آثار وقت التسجيل لخط رسايل البنك في بلد واحدة — **بالترتيب ده** (عقد الدامج، قرارات §75/§77 — `ImportStatementDeps.effects`):
 * 1. S1: «حسابي التاني» بآخر 4 أرقام ([OwnAccountByLast4Effect] §75-11) · «ده راتبك؟» ([SmsSalaryEffect] §75-2) — `SmsLane.of` بيطلبهم.
 * 2. S2: السحب من الصرّاف ⇒ نقل للكاش ([CashWithdrawalEffect] §75-4).
 * 3. S3: «اللي رجع» والمبلغ الأجنبي ([returns] = `ReturnsWiring.effects` بالروابط؛ فاضية ⇒ `SmsLane.of` بيضيفهم من غير روابط — بيسأل بس).
 * 4. S2: الرسوم عملية لوحدها ([SmsFeeEffect] §77-B) — **بعد** أي أثر بيغيّر المبلغ: لو اتغيّر ما بيقسمش الرسوم اللي جوه المبلغ.
 * 5. S2: الاسترداد اللي المالك سجّله ⇒ «استرداد» مؤكد ([RefundConfirmEffect] §75-6).
 * 6. S5: التصنيف اللي المالك اختاره بإيده بيتحفظ للمحل ([remember] §75-16) — بعد الحفظ.
 * 7. S4: خصم الاشتراك بيحرّك الميعاد الجاي ([subscriptions] ⇒ [SubscriptionChargeEffect] §75-7) — بعد الحفظ.
 */
fun smsRecordEffects(
    spaceId: String,
    wallets: WalletRepository,
    inbox: SmsInboxPort,
    categories: CategoryRepository,
    returns: List<RecordEffect> = emptyList(),
    remember: RememberChosenCategoryEffect? = null,
    subscriptions: RecurringRepository? = null,
): List<RecordEffect> = buildList {
    add(OwnAccountByLast4Effect(wallets))
    add(SmsSalaryEffect(inbox, spaceId))
    add(CashWithdrawalEffect(wallets))
    addAll(returns)
    add(SmsFeeEffect(categories))
    add(RefundConfirmEffect())
    remember?.let(::add)
    subscriptions?.let { add(SubscriptionChargeEffect(it)) }
}
