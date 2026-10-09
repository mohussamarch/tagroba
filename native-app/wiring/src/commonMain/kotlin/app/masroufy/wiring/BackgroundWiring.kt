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
import app.masroufy.usecase.RunBackgroundCycle
import app.masroufy.usecase.SmsLane

/** نفس اعتمادات استيراد الكشف في البلد (ومعاها قرارات «زون التحويلات» ومصادر الدخل — §60 · §64) — رسايل البنك والكشف بخط واحد. */
fun importDeps(r: SpaceRepositories, env: DeviceEnv) = ImportStatementDeps(
    r.transactions, r.sourceRecords, r.importBatches, r.merchants, r.categories, r.rules, r.uow, env.ids, env.clock,
    transferParties = r.transferParties, incomeSources = r.incomeSources,
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
        SmsLane.of(space.id, importDeps(r, env), ManageSmsInbox(inbox, reader::parse), r.wallets)
    }
    return RunBackgroundCycle(BackgroundCycleDeps(sms = AutoRecordSms(AutoRecordSmsDeps(inbox, lanes)), candidates = null, engine = null, notifier = notifier))
}
