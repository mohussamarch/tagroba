package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.KNOWN_TIME_ZONES
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.WalletRepository

/** بلد واحدة: قارئ رسايلها (جوه `ReviewSmsInbox`) ومحافظها ومستودع سجلات المصدر (عشان معرّفات العمليات اللي اتسجلت). */
class SmsLane private constructor(
    val spaceId: String,
    internal val review: ReviewSmsInbox,
    internal val wallets: WalletRepository,
    internal val sources: SourceRecordRepository,
    /** عقد C0: نفس المستورد اللي جوه [review] (الآثار والسجلات). */
    internal val importer: ImportStatement,
    private val importDeps: ImportStatementDeps,
    private val screenDeps: ReviewSmsInboxDeps,
    /** منطقة وقت البلد (`CountryPack.timeZone` — §88): حدود اليوم في قراية التاريخ (§83). */
    val timeZone: String,
) {
    /**
     * S2 (§75-4): استيراد البلد فيه `CashWithdrawalEffect` — من غيره `SmsReviewTarget.cashWalletId` = null فالسحب من الصرّاف بيستنى (ما
     * يتسجلش صرف من البنك لوحده). حارس توصيل: لو حد بنى البلد من غير الأثر. الشاشة بتاخد نفس القيمة (`ReviewSmsInboxDeps`).
     */
    internal val movesCashWithdrawals: Boolean get() = screenDeps.movesCashWithdrawals

    /**
     * **شاشة رسايل البنك للبلد دي** (مراجعة S1): نفس التعلّم (§77-A) والآثار (§75-2 · §75-11) والمحافظ بتوع التسجيل التلقائي — الشاشة
     * اللي اتبنت لوحدها من غيرهم ما كانتش بتعلّم ولا بتسأل ولا بتوزّع. نسخة جديدة كل مرة (جلسة الشاشة ما تتخلطش بجلسة الخلفية).
     */
    fun screen(contribute: (suspend (MerchantContribution, Id) -> Unit)? = null): ReviewSmsInbox =
        ReviewSmsInbox(screenDeps.copy(importer = ImportStatement(importDeps), contribute = contribute))

    companion object {
        /**
         * [importDeps] = **نفس** اعتمادات استيراد الكشف في البلد دي — فيها قرارات «زون التحويلات» (`transferParties`) لازم، وآثار وقت
         * التسجيل لرسايل البنك لازم (`effects` — S1: [OwnAccountByLast4Effect] · [SmsSalaryEffect]؛ مراجعة S1: من غيرهم «ده راتبك؟ أيوه»
         * و«حسابي التاني» ما كانوش بيعملوا حاجة في صمت). [inbox] = الصندوق بقارئ رسايل البلد دي (حزمة البلد).
         */
        fun of(
            spaceId: String,
            importDeps: ImportStatementDeps,
            inbox: ManageSmsInbox,
            wallets: WalletRepository,
            timeZone: String = SAUDI_TIME_ZONE,
        ): SmsLane {
            require(timeZone in KNOWN_TIME_ZONES) { "unknown time zone $timeZone" }
            require(importDeps.transferParties != null) { "SMS lane needs the transfer-party decisions (OVERRIDES §60/§72)" }
            require(importDeps.effects.any { it is SmsSalaryEffect } && importDeps.effects.any { it is OwnAccountByLast4Effect }) {
                "SMS lane needs the S1 record effects: SmsSalaryEffect and OwnAccountByLast4Effect (OVERRIDES §75-2/§75-11)"
            }
            // الشريحة S3: آثار «اللي رجع» والأجنبي لازم (`ReturnsWiring.kt`) — للخلفية **وللشاشة** ([screen] بيبني مستورد من [full])
            val full = withReturnEffects(importDeps)
            val importer = ImportStatement(full)
            val learning = SmsLearning(inbox.port, spaceId)
            val movesCash = full.effects.any { it is CashWithdrawalEffect }
            val deps = ReviewSmsInboxDeps(
                inbox, importer, full.merchants, full.categories, full.ids, learning, wallets, movesCashWithdrawals = movesCash,
            )
            return SmsLane(spaceId, ReviewSmsInbox(deps), wallets, full.sources, importer, full, deps, timeZone)
        }
    }
}
