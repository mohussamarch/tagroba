package app.masroufy.ui.screens.more

import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Language
import app.masroufy.core.Space
import app.masroufy.usecase.UpdateBalance

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

    /** «حدّث الرصيد» / «حدّث الكاش» (قايمة المالك البنود 4 و5 — UpdateBalance). null ⇒ الزرار مش ظاهر. */
    val updateBalance: UpdateBalance? get() = null

    /**
     * إضافة بلد · أرشفة · رجوع من الأرشيف — **متوصّل** في `MoreGraph` (`ManageSpaces` بالشجرة المضمّنة `BundledSeeds`، والجلسة بتفتح البلد
     * الجديدة وتقفل المؤرشفة من غير إعادة تشغيل — `SessionSpacesAdmin`). null بس لو الجلسة مالهاش إدارة بلاد (المعاينة).
     */
    val spacesAdmin: SpacesAdmin? get() = null

    /** لغة التطبيق المحفوظة على الجهاز (`Texts.language` بتتظبط مرة عند البداية — مفيش مكان بيتحفظ فيه الاختيار). */
    val appLanguage: LanguageSetting? get() = null

    /** «ابنِ شخصيتك» / «عدّل شخصيتك» (r041) ⇒ `ManageProfile.character` و`saveCharacter` — متوصّل في `MoreGraph`. */
    val character: app.masroufy.ui.screens.character.CharacterSetting? get() = null

    /**
     * «المرتب الشهري» — **مبلغ بالظبط بيتكتب** (قرار المالك 2026-10-16، بدل النطاقات) — **متوصّل** في `MoreGraph`.
     * null (المعاينة) ⇒ «غير متاح بعد».
     */
    val salary: app.masroufy.usecase.ProfileSalary? get() = null

    /** «سؤال الكاش» وتكراره في إعدادات الإشعارات (§74 — مالوش إعداد ولا تنبيه في كوتلن). */
    val cashQuestion: CashQuestionSetting? get() = null

    /** «جهات الشغل» (قايمة المالك 2026-10-15، البند 7) — **متوصّلة** في `MoreGraph`. null (المعاينة) ⇒ «غير متاح بعد». */
    val employers: app.masroufy.usecase.ManageEmployers? get() = null

    /** «طبّق على اللي فات» بعد تغيير جهة أو حساباتها (معاينة بالعدد ⇒ نسخة ⇒ تطبيق ⇒ تراجع). */
    val employerRetro: app.masroufy.usecase.EmployerRetroApply? get() = null
}

interface MainWalletAccess {
    /** معرّف المحفظة الأساسية في البلد الشغالة، أو null لو لسه ما اتحددتش. */
    suspend fun current(): Id?

    suspend fun set(walletId: Id)
}

/** المحفظة اللي المستخدم بيضيفها (`WalletAddSheet`): العملة = عملة البلد، و[last4] آخر 4 أرقام بس (CLAUDE.md #11). [bankId] = `Bank.key` من «اختار بنكك» (§81). */
data class NewWallet(val kind: String, val name: String, val last4: String?, val openingMinor: Halalas?, val openingAt: IsoDate?, val bankId: String? = null)

interface WalletEditor {
    /** بيرجّع رسالة الغلط (اسم مكرر …) أو null لو اتحفظت. */
    suspend fun add(wallet: NewWallet): String?

    /** رصيد البداية في تاريخ — الرصيد الجديد بيتحسب في المنطق مش في الشاشة. */
    suspend fun setOpening(walletId: Id, openingMinor: Halalas, openingAt: IsoDate): String?

    /** sms-fix: آخر 4 أرقام حساب المحفظة (اختياري — فاضي يشيلها). بيرجّع رسالة الغلط أو null. الافتراضي للبدائل في الاختبار. */
    suspend fun setLast4(walletId: Id, last4: String?): String? = null

    /** «النهارده» اللي التأكد بيقارن بيه (يوم البلد — `ManageWallets.today`) — اللوحة بتبدأ بيه. null ⇒ يوم الجوال. */
    fun today(): IsoDate? = null
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

/** كل كام أسبوع بيسأل «كم معك كاش؟». */
enum class CashQuestionEvery { WEEK, TWO_WEEKS, MONTH }

interface CashQuestionSetting {
    suspend fun current(): CashQuestionEvery?

    /** null = مقفول. */
    suspend fun choose(every: CashQuestionEvery?)
}
