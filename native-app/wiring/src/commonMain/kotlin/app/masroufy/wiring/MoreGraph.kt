package app.masroufy.wiring

import app.masroufy.core.AlertGroup
import app.masroufy.ui.screens.more.MoreDeps
import app.masroufy.ui.screens.more.SpaceCard
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

/**
 * «المزيد» — ملفك (`c.shell.profile` نفس كائن الهيكل) · مصادر الدخل وإشاراتها · إعدادات الإشعارات (`c.shell.engine`) · المحافظ
 * (`LoadWithYouNow` + حركات الشهر + `ReconcileBalance`) · البلدان · التصدير والنسخة الشاملة. (`AppLock` و`SignIn` على مستوى التطبيق: `LocalApp`.)
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`.
 * نقاط الربط في `MoreHooks` (المحفظة الأساسية · إضافة محفظة · إنشاء بلد وأرشفته · اللغة · الشكل · نطاق الراتب · سؤال الكاش) **null** هنا لحد
 * ما منطقها يتبني (المحفظة الأساسية على فرع `assistant-engine`) ⇒ الشاشات بتقول «غير متاح بعد» بدل ما تزيّف.
 */
class MoreGraph(private val c: AreaContext) : MoreDeps {
    private val r = c.repos

    override val profile = c.shell.profile

    override val incomeSources = ManageIncomeSources(ManageIncomeSourcesDeps(r.incomeSources, c.shell.profile, r.uow, c.env.ids, c.env.clock))

    override val incomeSignals = IncomeSourceSignals(
        IncomeSignalsDeps(r.incomeSources, r.transactions, r.allocations, r.profile, r.uow, c.env.clock, parties = r.transferParties),
    )

    override val alerts = c.shell.engine

    // `RunAlertEngine` مالوش دالة قراية للإعداد — نفس المخزن اللي `setGroupEnabled` بيكتب فيه
    override suspend fun disabledAlertGroups(): Set<AlertGroup> = r.alertSettings.disabledGroups()

    override val wallets = LoadWithYouNow(r.wallets, r.transactions, c.space.currency)

    override val transactions = LoadTransactionsScreen(
        LoadTransactionsScreenDeps(r.transactions, r.categories, r.allocations, r.merchants, r.tags, r.transactionTags),
    )

    override val reconcile = ReconcileBalance(ReconcileDeps(r.transactions, r.wallets))

    /** البلاد المفتوحة في الجلسة بعدد محافظ كل واحدة (من «معك الآن» بتاعها) — العدد `null` لو القراية فشلت (مش صفر). */
    override suspend fun spaces(): List<SpaceCard> = c.session.spaces().map { (s, repos) ->
        val count = runCatching { LoadWithYouNow(repos.wallets, repos.transactions, s.currency).load(c.env.today()).wallets.size }.getOrNull()
        SpaceCard(s, s.id == c.space.id, count)
    }

    override val exportCsv = ExportCsv(r.transactions)

    override val fullBackup: FullBackup? = r.fullBackup?.let { FullBackup(it, r.spacesBackup) }

    override fun nowIso(): String = c.env.clock.nowIso()
}
