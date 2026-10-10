package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.core.countryPack
import app.masroufy.port.DeviceNotifier
import app.masroufy.port.SmsInboxPort
import app.masroufy.usecase.AutoRecordSms
import app.masroufy.usecase.AutoRecordSmsDeps
import app.masroufy.usecase.BackgroundCycleDeps
import app.masroufy.usecase.ImportStatementDeps
import app.masroufy.usecase.ManageSmsInbox
import app.masroufy.usecase.MerchantMemory
import app.masroufy.usecase.RememberChosenCategoryEffect
import app.masroufy.usecase.RunBackgroundCycle
import app.masroufy.usecase.SmsLane
import app.masroufy.usecase.smsRecordEffects

/** نفس اعتمادات استيراد الكشف في البلد (ومعاها قرارات «زون التحويلات» ومصادر الدخل — §60 · §64) — رسايل البنك والكشف بخط واحد. */
fun importDeps(r: SpaceRepositories, env: DeviceEnv) = ImportStatementDeps(
    r.transactions, r.sourceRecords, r.importBatches, r.merchants, r.categories, r.rules, r.uow, env.ids, env.clock,
    transferParties = r.transferParties, incomeSources = r.incomeSources,
)

/**
 * نفس [importDeps] + آثار وقت التسجيل لرسايل البنك بالترتيب اللي عقد الدامج حدده (`smsRecordEffects` — decisions-77، §75/§77): `SmsLane.of`
 * بيرفض من غير آثار S1. التصنيف اللي المالك اختاره بيتحفظ للمحل (`MerchantMemory` من غير رفع للقايمة المشتركة)، وخصم الاشتراك بيحرّك
 * ميعاده. ⚠️ «اللي رجع» من غير روابط الدين (`ReturnsWiring` محتاج روابط الأشخاص والمستحقات) ⇒ `SmsLane.of` بيضيفها بتسأل بس — HANDOVER.
 */
fun smsImportDeps(spaceId: String, r: SpaceRepositories, env: DeviceEnv, inbox: SmsInboxPort) = importDeps(r, env).copy(
    effects = smsRecordEffects(
        spaceId, r.wallets, inbox, r.categories,
        remember = RememberChosenCategoryEffect(MerchantMemory(r.merchants, env.ids)), subscriptions = r.recurring,
    ),
)

/**
 * دورة الخلفية للحساب الداخل (`MasroufyBackground.install` — OVERRIDES §72 · ARCHITECTURE §31.29): رسايل البنك الجديدة بتتسجل لوحدها في
 * بلد كل رسالة (قارئ البلد من حزمتها — بلد مالهاش قارئ ما بتاخدش رسايل).
 * ⚠️ **محرك التنبيهات مش متوصل هنا لسه** (`candidates = null`): المحرك بيشيل من صفحة الإشعارات أي موضوع مش في المرشحين، فلازم ياخد
 * **كل** المرشحين (المستحقات · الميزانية · التحويلات · الزكاة · المناسبات · الدخل · المساعد …) بنفس اللي الشاشة بتجمعه — شغل منطقة
 * الإشعارات (`GatherAllSpaceAlerts`). لحد ما يتوصل: التسجيل لوحده شغال، ومفيش إشعار جوال.
 */
fun backgroundCycle(spaces: List<Pair<Space, SpaceRepositories>>, inbox: SmsInboxPort, notifier: DeviceNotifier, env: DeviceEnv): RunBackgroundCycle {
    val lanes = spaces.mapNotNull { (space, r) ->
        val reader = countryPack(space.countryCode).smsReader ?: return@mapNotNull null
        SmsLane.of(space.id, smsImportDeps(space.id, r, env, inbox), ManageSmsInbox(inbox, reader::parse), r.wallets)
    }
    return RunBackgroundCycle(BackgroundCycleDeps(sms = AutoRecordSms(AutoRecordSmsDeps(inbox, lanes)), candidates = null, engine = null, notifier = notifier))
}
