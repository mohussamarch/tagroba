package app.masroufy.ui.screens.more

import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Language
import app.masroufy.core.Space

/**
 * **نقاط ربط** لمنطق لسه ما اتبناش في كوتلن (CLAUDE.md #15 — «ميزة تصميمها ظاهر ليست ميزة مكتملة»). كل واحدة قيمتها `null` في
 * `MoreGraph` لحد ما المنطق يتبني ⇒ الشاشة بترسم حالة النموذج وتقول «غير متاح بعد» بدل ما تزيّف أو تحسب جوه الواجهة (CLAUDE.md #4).
 * مكتوبة في HANDOVER «missingLogic» للجلسة اللي هتدمج. **الشاشة عمرها ما بتحسب مبلغ هنا** — النتيجة بترجع جاهزة من المنطق.
 */
interface MoreHooks {
    /** المحفظة الأساسية لكل بلد (قرار المالك 2026-10-09 — بيتبني على فرع `assistant-engine`). */
    val mainWallet: MainWalletAccess? get() = null

    /** إضافة محفظة وتعديل رصيد البداية (`WalletAddSheet` — مفيش حالة استخدام لإدارة المحافظ: SCREENS.md «Wallets»). */
    val walletEditor: WalletEditor? get() = null

    /** إضافة بلد · أرشفة · رجوع من الأرشيف (`ManageSpaces` مش متجمّع على الجوال: مصدر شجرة التصنيفات وإعادة فتح البلاد في الجلسة). */
    val spacesAdmin: SpacesAdmin? get() = null

    /** لغة التطبيق المحفوظة على الجهاز (`Texts.language` بتتظبط مرة عند البداية — مفيش مكان بيتحفظ فيه الاختيار). */
    val appLanguage: LanguageSetting? get() = null

    /** «اختر شكلك» (الكاركتر المؤقت 1–6) — `UserProfile` مالوش حقل للشكل (SCREENS.md §3 سؤال 12). */
    val look: LookSetting? get() = null

    /** نطاق الراتب («أقل من 5 آلاف» …) — `UserProfile` فيه راتب بالهللة بس، والنموذج بيسأل نطاقات (§63). */
    val salaryRange: SalaryRangeSetting? get() = null

    /** «سؤال الكاش» وتكراره في إعدادات الإشعارات (§74 — مالوش إعداد ولا تنبيه في كوتلن). */
    val cashQuestion: CashQuestionSetting? get() = null
}

interface MainWalletAccess {
    /** معرّف المحفظة الأساسية في البلد الشغالة، أو null لو لسه ما اتحددتش. */
    suspend fun current(): Id?

    suspend fun set(walletId: Id)
}

/** المحفظة اللي المستخدم بيضيفها (`WalletAddSheet`): العملة = عملة البلد، و[last4] آخر 4 أرقام بس (CLAUDE.md #11). */
data class NewWallet(val kind: String, val name: String, val last4: String?, val openingMinor: Halalas?, val openingAt: IsoDate?)

interface WalletEditor {
    /** بيرجّع رسالة الغلط (اسم مكرر …) أو null لو اتحفظت. */
    suspend fun add(wallet: NewWallet): String?

    /** رصيد البداية في تاريخ — الرصيد الجديد بيتحسب في المنطق مش في الشاشة. */
    suspend fun setOpening(walletId: Id, openingMinor: Halalas, openingAt: IsoDate): String?
}

interface SpacesAdmin {
    suspend fun create(countryCode: String): Space

    suspend fun archive(spaceId: String): Space

    suspend fun unarchive(spaceId: String): Space

    /** المؤرشفة (للقسم التاني في الشاشة). */
    suspend fun archived(): List<Space>
}

interface LanguageSetting {
    fun current(): Language

    /** بيتطبق لما التطبيق يتفتح تاني. */
    fun choose(language: Language)
}

interface LookSetting {
    suspend fun current(): Int

    suspend fun choose(look: Int)
}

/** نطاقات الراتب الأربعة + «أفضّل ألا أقول» (§63). */
enum class SalaryRange { R1, R2, R3, R4, SKIP }

interface SalaryRangeSetting {
    suspend fun current(): SalaryRange?

    suspend fun choose(range: SalaryRange)
}

/** كل كام أسبوع بيسأل «كم معك كاش؟». */
enum class CashQuestionEvery { WEEK, TWO_WEEKS, MONTH }

interface CashQuestionSetting {
    suspend fun current(): CashQuestionEvery?

    /** null = مقفول. */
    suspend fun choose(every: CashQuestionEvery?)
}
