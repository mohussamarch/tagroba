package app.masroufy.wiring

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Space
import app.masroufy.core.countryPack
import app.masroufy.core.parseTimed
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
fun smsImportDeps(spaceId: String, r: SpaceRepositories, env: DeviceEnv, inbox: SmsInboxPort, share: app.masroufy.usecase.ShareMerchantCategory? = null) = importDeps(r, env).copy(
    effects = smsRecordEffects(
        spaceId, r.wallets, inbox, r.categories,
        // التصنيف اللي المالك اختاره (شراء صادر) بيترفع كمان لقاعدة التجار المشتركة لو [share] موجود (رد المالك 2026-10-22)
        remember = RememberChosenCategoryEffect(MerchantMemory(r.merchants, env.ids, share?.hook)), subscriptions = r.recurring,
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
 * الإشعارات (`GatherAllSpaceAlerts`). لحد ما يتوصل: التسجيل لوحده شغال، وإشعارات الجوال الوحيدة هي حالات القراية التلاتة
 * (PLAN-0.6 §13 ج — `SmsStateNotices`، لو [noticeMemory] موجود): شكل جديد مستني · الإذن اتسحب · رسايل فاتته.
 */
fun backgroundCycle(
    spaces: List<Pair<Space, SpaceRepositories>>, inbox: SmsInboxPort, notifier: DeviceNotifier, env: DeviceEnv,
    noticeMemory: app.masroufy.port.NoticeMemoryPort? = null,
    support: app.masroufy.usecase.SendSupportMessage? = null,
): RunBackgroundCycle {
    val lanes = spaces.mapNotNull { (space, r) -> smsLaneOf(space, r, env, inbox) }
    val auto = AutoRecordSms(AutoRecordSmsDeps(inbox, lanes, work = env.work))
    // PLAN-0.6 §13 (ج): التلات حالات بس (شكل جديد مستني · الإذن اتسحب · رسايل فاتته) — من غير المحرك، والمسجّل لوحده من غير إشعار
    val notices = noticeMemory?.let { memory ->
        app.masroufy.usecase.SmsStateNotices(
            app.masroufy.usecase.SmsStateNoticesDeps({ auto.inboxState() }, { env.backgroundRead?.lastAnyRead() }, memory, notifier),
        )
    }
    // §83-2: من آخر نقطة لكل بلد (إعدادات الحساب — نفس المخزن في كل البلاد)
    val catchUp = spaces.firstOrNull()?.let { (_, r) -> SmsCatchUp(inbox, r.assistant.settings, lanes.map { it.spaceId }, env.clock) }
    return RunBackgroundCycle(
        BackgroundCycleDeps(sms = auto, candidates = null, engine = null, notifier = notifier, smsCatchUp = catchUp, smsNotices = notices,
            // r06-shared: قاعدة التجار المشتركة — التغييرات من آخر مؤشر كل دورة (رد المالك 2026-10-22)
            sharedMerchants = syncSharedMerchantsOf(spaces, env),
            // «ابعتلنا» (OVERRIDES §114): الرسايل اللي ما اتبعتتش لفايربيز (النت) بتتعاد كل دورة
            support = support,
            openingDebtRepairs = spaces.map { (space, r) -> app.masroufy.usecase.RepairOpeningDebtCurrency(r.people, r.obligations, space.currency) },
            duplicateScans = spaces.map { (space, r) -> app.masroufy.usecase.DuplicateChargeAsks(space.id, r.transactions, env.clock) },
            categoryRepairs = spaces.map { (space, r) -> app.masroufy.usecase.RepairMissingDefaultSubs(r.categories, app.masroufy.seed.BundledSeeds, countryPack(space.countryCode)) },
            // r06-1e (§110-2): قواعد محلات دراهم للحسابات الموجودة — مرة واحدة لكل بلد (علامة «خلص» في إعدادات الحساب)
            ruleRepairs = spaces.flatMap { (space, r) ->
                listOf(
                    app.masroufy.usecase.RepairDarahimRules(
                        space.id, r.categories, r.rules, app.masroufy.seed.BundledSeeds, countryPack(space.countryCode), r.assistant.settings, env.clock,
                    ),
                    // الدفعة التانية (§109 تعديل — مراجعة المالك 2026-10-23): علامة «خلص» لوحدها
                    app.masroufy.usecase.RepairDarahimRules(
                        space.id, r.categories, r.rules, app.masroufy.seed.BundledSeeds, countryPack(space.countryCode), r.assistant.settings, env.clock,
                        app.masroufy.usecase.DARAHIM_RULES2_REPAIR, app.masroufy.usecase.DARAHIM_RULES2_FROM_PRIORITY, Int.MAX_VALUE,
                    ),
                )
            },
        ),
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
    val manage = ManageSmsInbox(inbox, { m, n -> reader.parseTimed(m, n, pack.timeZone) }, otherCountryReaders(space.countryCode))
    return SmsLane.of(space.id, smsImportDeps(space.id, r, env, inbox, shareMerchantOf(space, r, env)), manage, r.wallets, pack.timeZone)
}

/**
 * OVERRIDES §112-5 (قرار المالك 2026-10-23): قرّاية **البلاد التانية** (كل حزمة ليها قارئ غير بلد [countryCode]) — `ManageSmsInbox` بيخبّي
 * بيهم رسايل البلد التانية من «ما اتفهمتش» هنا. الرسالة بتتسجل في بلدها من التسجيل لوحده (كل البلاد)، ولو مالهاش بلد عند المالك بتتساب في هدوء.
 */
internal fun otherCountryReaders(countryCode: String?): List<app.masroufy.port.BankSmsParser> {
    val here = countryPack(countryCode).code
    return app.masroufy.core.COUNTRY_PACKS.values.filter { it.code != here }.mapNotNull { pack ->
        pack.smsReader?.let { reader -> { m: app.masroufy.core.BankSmsMessage, n: Int -> reader.parseTimed(m, n, pack.timeZone) } }
    }
}
