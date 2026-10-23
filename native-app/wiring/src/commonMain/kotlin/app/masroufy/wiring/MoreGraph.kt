package app.masroufy.wiring

import app.masroufy.core.AlertGroup
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MainWalletSource
import app.masroufy.core.countryPack
import app.masroufy.core.CharacterLook
import app.masroufy.ui.screens.character.CharacterSetting
import app.masroufy.ui.screens.more.MainWalletAccess
import app.masroufy.ui.screens.more.MoreDeps
import app.masroufy.ui.screens.more.NewWallet
import app.masroufy.usecase.badgeWalletId
import app.masroufy.ui.screens.more.SpaceCard
import app.masroufy.ui.screens.more.WalletEditor
import app.masroufy.usecase.AddWalletInput
import app.masroufy.usecase.ManageWallets
import app.masroufy.usecase.WalletError
import app.masroufy.usecase.ExportCsv
import app.masroufy.usecase.FullBackup
import app.masroufy.usecase.IncomeSignalsDeps
import app.masroufy.usecase.IncomeSourceSignals
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageIncomeSources
import app.masroufy.usecase.ManageIncomeSourcesDeps
import app.masroufy.usecase.ReconcileBalance
import app.masroufy.usecase.ReconcileDeps
import app.masroufy.usecase.UpdateBalance

/**
 * «المزيد» — ملفك (`c.shell.profile` نفس كائن الهيكل) · مصادر الدخل وإشاراتها · إعدادات الإشعارات (`c.shell.engine`) · المحافظ
 * (`LoadWithYouNow` + حركات الشهر + `ReconcileBalance`) · البلدان · التصدير والنسخة الشاملة. (`AppLock` و`SignIn` على مستوى التطبيق: `LocalApp`.)
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`.
 * إضافة محفظة متوصلة (`ManageWallets`). المحفظة الأساسية **متوصّلة** (محرك المساعد — `MainSpendingWallets`). إنشاء بلد وأرشفته **متوصّلين**
 * (`ManageSpaces` من الجلسة — [SessionSpacesAdmin]). باقي نقاط الربط في `MoreHooks` (اللغة · الشكل · نطاق الراتب · سؤال الكاش) **null** هنا
 * لحد ما منطقها يتبني ⇒ الشاشات بتقول «غير متاح بعد» بدل ما تزيّف.
 */
class MoreGraph(private val c: AreaContext) : MoreDeps {
    private val r = c.repos

    override val profile = c.shell.profile

    override val incomeSources = ManageIncomeSources(ManageIncomeSourcesDeps(r.incomeSources, c.shell.profile, r.uow, c.env.ids, c.env.clock))

    /** «المرتب الشهري» — مبلغ بالظبط (قرار المالك 2026-10-16) على جهة الشغل الرئيسية أو الملف. */
    override val salary = salaryCase(c, incomeSources)

    override val incomeSignals = IncomeSourceSignals(
        IncomeSignalsDeps(r.incomeSources, r.transactions, r.allocations, r.profile, r.uow, c.env.clock, parties = r.transferParties),
    )

    // «جهات الشغل» (البند 7) — نفس حالات استخدام «العمليات»
    private val employerCases = EmployerUseCases(c)
    override val employers = employerCases.employers
    override val employerRetro = employerCases.retro

    override val alerts = c.shell.engine

    // `RunAlertEngine` مالوش دالة قراية للإعداد — نفس المخزن اللي `setGroupEnabled` بيكتب فيه
    override suspend fun disabledAlertGroups(): Set<AlertGroup> = r.alertSettings.disabledGroups()

    override val wallets = LoadWithYouNow(r.wallets, r.transactions, c.space.currency)

    override val transactions = LoadTransactionsScreen(
        LoadTransactionsScreenDeps(r.transactions, r.categories, r.allocations, r.merchants, r.tags, r.transactionTags),
    )

    override val reconcile = ReconcileBalance(ReconcileDeps(r.transactions, r.wallets))

    /** «اجعلها الأساسية» في تفاصيل المحفظة + علامة «الأساسية» في القايمة (§78 ٢) — نفس `MainSpendingWallets` بتاع لوحة «+» والمساعد. */
    override val mainWallet: MainWalletAccess = object : MainWalletAccess {
        override suspend fun current(): Id? = c.shell.mainWallet.badgeWalletId()

        override suspend fun set(walletId: Id) {
            c.shell.mainWallet.set(walletId, MainWalletSource.WALLET_DETAIL)
        }
    }

    /** البلاد المفتوحة في الجلسة بعدد محافظ كل واحدة (من «معك الآن» بتاعها) — العدد `null` لو القراية فشلت (مش صفر). */
    override suspend fun spaces(): List<SpaceCard> = c.session.spaces().map { (s, repos) ->
        val count = runCatching { LoadWithYouNow(repos.wallets, repos.transactions, s.currency).load(c.env.today()).wallets.size }.getOrNull()
        SpaceCard(s, s.id == c.space.id, count)
    }

    /** «أضف بلدًا» · «أرشفة» · «استعادة» (`ManageSpaces` بالشجرة المضمّنة — [SessionSpacesAdmin]): البلد الجديدة بتتفتح في نفس الجلسة. */
    override val spacesAdmin = c.shell.spacesAdmin

    /** «ابنِ شخصيتك» / «عدّل شخصيتك» ⇒ ملفك (`ManageProfile.saveCharacter`) — الحفظ الغلط بيرمي عشان الشاشة ما تقفلش كأنه اتحفظ. */
    override val character: CharacterSetting = object : CharacterSetting {
        override suspend fun current(): CharacterLook? = c.shell.profile.character()

        override suspend fun profileGender(): String? = c.shell.profile.load().gender

        override val draft = c.env.characterDraft

        override suspend fun save(look: CharacterLook) {
            val r = c.shell.profile.saveCharacter(look)
            if (r is app.masroufy.core.ProfileCheck.Invalid) throw IllegalArgumentException(r.message)
        }
    }

    override val exportCsv = ExportCsv(r.transactions)

    override val fullBackup: FullBackup? = r.fullBackup?.let { FullBackup(it, r.spacesBackup, c.env.work) }

    /** «افهم رصيدك» (بند المالك 3) — علامة «تعديل رصيد» بتتوصل مع فرع «حدّث الرصيد» (الافتراضي ولا عملية). */
    /**
     * «صلّح رسايل البنك القديمة» (r04-sms-fix): بقارئ رسايل بنوك البلد ونسخة «قبل الإصلاح» على الجهاز — من غير الاتنين مش متوصل. نفس
     * `duplicates()` بيغذي «راجع المكرر» بأزواج عكس الاتجاه (التحويل بين حساباتك اللي اتسجل بعكس الكشف).
     */
    override val smsRepair: app.masroufy.usecase.RepairOldSms? = run {
        val reader = countryPack(c.space.countryCode).smsReader
        val backup = c.env.repairBackup
        // r041-B: «الكشف غطّاها» بنفس اعتمادات وآثار خط رسايل البلد (`smsImportDeps`) — من غير صندوق رسايل السطور دي ما بتظهرش
        val covered = c.env.smsInbox?.let { inbox ->
            app.masroufy.usecase.CoveredSmsRepair(smsImportDeps(c.space.id, r, c.env, inbox), r.wallets, listOf(reader ?: return@let null))
        }
        if (reader == null || backup == null) null
        else app.masroufy.usecase.RepairOldSms(r.transactions, r.sourceRecords, r.importBatches, r.wallets, listOf(reader), backup, c.env.clock, r.uow, covered)
    }
    private val rematch: (suspend () -> List<app.masroufy.core.SmsStatementPair>)? = smsRepair?.let { s -> { s.duplicates() } }

    override val balanceWhy = app.masroufy.usecase.ExplainBalance(
        app.masroufy.usecase.ExplainBalanceDeps(r.transactions, r.sourceRecords, r.importBatches, r.wallets, rematch = rematch),
    )

    /** «راجع المكرر» — نفس دمج الكشف والرسالة؛ من غير ملفات الجهاز (`repairBackup`) بيرفض بـ NO_BACKUP. */
    override val mergeDuplicates = app.masroufy.usecase.MergeDuplicates(
        app.masroufy.usecase.MergeDuplicatesDeps(
            r.transactions, r.sourceRecords, r.importBatches, r.uow, c.env.clock, c.env.repairBackup, r.allocations, r.settlements, r.obligations,
            app.masroufy.usecase.RevertLinkDeps(
                r.projectLinks, r.eventLinks, r.transactionTags, r.roscaEntries, r.installmentPayments, r.installmentPlans,
                r.zakatPayments, r.assetLots, r.assetSales, personLinks = r.personLinks,
            ),
            r.spaceTransfers,
            rematch = rematch,
        ),
    )

    override fun nowIso(): String = c.env.clock.nowIso()

    /** «حدّث الرصيد» لأي محفظة — بيوم البلد (مش يوم الجوال) عشان «مش في المستقبل». */
    override val updateBalance = UpdateBalance(r.wallets, r.transactions, r.categories, c.env.ids, c.env.clock, countryPack(c.space.countryCode).timeZone)

    // «صلّح رصيدي» (2026-10-16): نفس الأدوات اللي فوق بالترتيب
    override val fixBalance = app.masroufy.usecase.FixMyBalance(app.masroufy.usecase.FixMyBalanceDeps(smsRepair, mergeDuplicates, balanceWhy, updateBalance, r.wallets))

    /** «أضف محفظة» و«رصيد البداية» (`ManageWallets` — الغلط رسالة بترجع للوحة بدل ما توقع). */
    override val walletEditor: WalletEditor = object : WalletEditor {
        private val manage = ManageWallets(r.wallets, c.env.ids, c.env.clock, c.space.currency, countryPack(c.space.countryCode).timeZone)

        /** يوم البلد (مش يوم الجوال ولا جرينتش) — نفس اللي `ManageWallets` بيتأكد بيه. */
        override fun today(): IsoDate = manage.today()

        override suspend fun add(wallet: NewWallet): String? = attempt {
            manage.add(AddWalletInput(wallet.kind, wallet.name, wallet.last4, wallet.openingMinor, wallet.openingAt, wallet.bankId))
        }

        override suspend fun setOpening(walletId: Id, openingMinor: Halalas, openingAt: IsoDate): String? = attempt {
            manage.setOpening(walletId, openingMinor, openingAt)
        }

        override suspend fun setLast4(walletId: Id, last4: String?): String? = attempt { manage.setLast4(walletId, last4) }

        override suspend fun setHidden(walletId: Id, hidden: Boolean): String? = attempt { manage.setHidden(walletId, hidden) }

        private suspend fun attempt(block: suspend () -> Unit): String? = try {
            block()
            null
        } catch (e: WalletError) {
            e.message
        }
    }
}
