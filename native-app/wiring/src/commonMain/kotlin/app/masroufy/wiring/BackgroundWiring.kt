package app.masroufy.wiring

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
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
import app.masroufy.usecase.SmsCatchUp
import app.masroufy.usecase.SmsHistory
import app.masroufy.usecase.SmsHistoryDeps
import app.masroufy.usecase.SmsLane
import app.masroufy.usecase.smsRecordEffects

/**
 * نفس اعتمادات استيراد الكشف في البلد (ومعاها قرارات «زون التحويلات» ومصادر الدخل — §60 · §64) — رسايل البنك والكشف بخط واحد.
 * **§75-10 (الدمج) متوصّل** (الجولة 0.3 — §83): الكشف والرسالة لنفس الحركة في حدود يومين = عملية واحدة بمصدرين. كان مبني في حالة الاستخدام
 * ومش متوصّل هنا، فقراية التاريخ كله (§83) على حساب متستورد كشفه كانت هتكرر كل عملية.
 */
fun importDeps(r: SpaceRepositories, env: DeviceEnv) = ImportStatementDeps(
    r.transactions, r.sourceRecords, r.importBatches, r.merchants, r.categories, r.rules, r.uow, env.ids, env.clock,
    transferParties = r.transferParties, incomeSources = r.incomeSources, crossSourceWindowDays = CROSS_SOURCE_WINDOW_DAYS,
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
        txns = r.transactions, sources = r.sourceRecords,
        // A2: «مستحقات دايمًا» و«سجّل تحويلاته لوحده» أول ما الرسالة تتسجل (الخلفية · التسجيل لوحده · تأكيد المالك)
        personRules = app.masroufy.usecase.PersonRulesEffect(personLinksOf(r, env.ids, env.clock, SpaceLegsOf(spaceId, r.spaceTransfers)).rules),
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
    val lanes = spaces.mapNotNull { (space, r) -> smsLaneOf(space, r, env, inbox) }
    // §83-2: من آخر نقطة لكل بلد (إعدادات الحساب — نفس المخزن في كل البلاد)
    val catchUp = spaces.firstOrNull()?.let { (_, r) -> SmsCatchUp(inbox, r.assistant.settings, lanes.map { it.spaceId }, env.clock) }
    return RunBackgroundCycle(
        BackgroundCycleDeps(sms = AutoRecordSms(AutoRecordSmsDeps(inbox, lanes, work = env.work)), candidates = null, engine = null, notifier = notifier, smsCatchUp = catchUp),
    )
}

/** §83-1: «اقرأ اللي فات؟» بنفس صندوق وحارات التسجيل لوحده ([backgroundCycle]) — للشاشة (`SmsGraph`). */
fun smsHistory(spaces: List<Pair<Space, SpaceRepositories>>, inbox: SmsInboxPort, env: DeviceEnv): SmsHistory? {
    val (_, first) = spaces.firstOrNull() ?: return null
    val lanes = spaces.mapNotNull { (space, r) -> smsLaneOf(space, r, env, inbox) }
    return SmsHistory(SmsHistoryDeps(AutoRecordSmsDeps(inbox, lanes, work = env.work), first.assistant.settings, env.clock, work = env.work))
}

/** حارة رسايل البلد [space]: قارئها **ومنطقة وقتها** من حزمتها (§88 — حدود اليوم بتوقيت كل بلد)، أو null لو البلد مالهاش قارئ. */
internal fun smsLaneOf(space: Space, r: SpaceRepositories, env: DeviceEnv, inbox: SmsInboxPort): SmsLane? {
    val pack = countryPack(space.countryCode)
    val reader = pack.smsReader ?: return null
    return SmsLane.of(space.id, smsImportDeps(space.id, r, env, inbox), ManageSmsInbox(inbox, reader::parse), r.wallets, pack.timeZone)
}
