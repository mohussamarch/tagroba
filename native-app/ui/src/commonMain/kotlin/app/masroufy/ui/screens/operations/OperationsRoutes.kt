package app.masroufy.ui.screens.operations

import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Tab
import app.masroufy.usecase.EditTransaction
import app.masroufy.usecase.EventGifts
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageEvents
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePersonCircles
import app.masroufy.usecase.ManageProjects
import app.masroufy.usecase.ManageRules
import app.masroufy.usecase.ManageTransfers
import app.masroufy.usecase.SetEconomicKind
import app.masroufy.usecase.TransferBetweenSpaces

/**
 * منطقة «العمليات» (`SCREENS.md` §٢.٣). الشرايح المبنية هنا: Operations (+ OperationMenu) · OperationDetail (+ CategoryPicker) ·
 * OperationFilters · LinkPersonSheet · LinkProjectEventSheet · TagsSheet · MerchantProfile · Transfers · TransferParty · SpaceTransfer.
 * ReviewQueue · Budgets · CategoryBudget · BudgetLimitSheet شغل شريحة تانية: «الميزانيات» قطعة [app.masroufy.ui.nav.Slots.BUDGETS] و
 * «المراجعة» مسار [ReviewQueueRoute] (متعرّف هنا عشان الشريط بيفتحه — صاحب الشاشة بيسجّلها بس). `AddSheet` في الهيكل.
 */
interface OperationsDeps {
    /** سجل الفترة والمجاميع — `TransactionsScreenData` (المجاميع `null` ⇒ «غير متاح»). */
    val transactions: LoadTransactionsScreen

    /** تفاصيل العملية: التصنيف · الملاحظة · الوسوم (`load` · `setCategory` · `addTag` · `removeTag` · `listTags`). */
    val edit: EditTransaction

    /** النوع الاقتصادي بقرار المستخدم (`setOne`) — نوع الحوالة الواردة من شخص (§42). */
    val kinds: SetEconomicKind

    /** شجرة التصنيفات (`CategoryPicker`). */
    val categories: ManageCategories

    /** التاجر: تصنيفه المثبّت · اسمه · أسماؤه البديلة (`listMerchants` · `setMerchantCategory` · `renameMerchant` · `addAlias`). */
    val merchants: ManageRules

    /** الأشخاص بأرصدتهم · إضافة شخص · تقسيم شراء على شخص (`linkToPerson`). */
    val people: ManagePeople

    /** دايرة الشخص الجديد (عائلة · أصدقاء · عمل). */
    val circles: ManagePersonCircles

    /** مشاريع العملية (`membership` · `setMember`). */
    val projects: ManageProjects

    /** الأحداث (`list` · `detail` لمعرفة الحدث المربوط). */
    val events: ManageEvents

    /** ربط العملية بحدث بنسبة وفكّه (`link` · `unlink`). */
    val eventLinks: EventGifts

    /** زون التحويلات: الأطراف والقرارات (`zone` · `markOwnAccount` · `markPerson` · `dismiss` · `forget`). */
    val transfers: ManageTransfers

    /** التحويل لنفسك بين بلدين (`list` · `recordNew` · `linkExisting` · `unlink`). */
    val spaceTransfers: TransferBetweenSpaces

    /** يوم الراتب (بداية الشهر المالي) من ملفك. */
    suspend fun payday(): Int

    /** محافظ البلد الشغالة (أسماؤها في الصفوف والتفاصيل والتصفية). */
    suspend fun wallets(): List<Wallet>

    /** البلاد المفتوحة بمحافظها (للتحويل لنفسك). */
    suspend fun spaceBooks(): List<SpaceWallets>

    /**
     * عملية رجل من رجلين «التحويل لنفسك» في بلدها (المحفظة والتاريخ على كارت الزوج) — `EditTransaction.load` على مستودعات البلد دي.
     * البلد مش مفتوحة في الجلسة ⇒ `null`؛ العملية مش موجودة ⇒ بيرمي (الشاشة بتعرض البلد بس).
     */
    suspend fun legOf(spaceId: String, transactionId: Id): Transaction?
}

/** بلد بمحافظها — [active] = البلد الشغالة. */
data class SpaceWallets(val space: Space, val wallets: List<Wallet>, val active: Boolean)

/** تفاصيل عملية (`OperationDetail`) — [openPerson] = اتفتحت من «تخصيص مبلغ لشخص» في القايمة ⇒ لوحة الشخص مفتوحة. */
data class OperationDetailRoute(val transactionId: Id, val openPerson: Boolean = false) : Route {
    override val name = "OperationDetail"
}

/** تصفية العمليات (زرار الفلتر جنب الشهر). */
object OperationFiltersRoute : Route {
    override val name = "OperationFilters"
}

/** صفحة التاجر — [merchantName] الاسم زي ما جه في العملية (المطابقة بالاسم المطبّع والأسماء البديلة). */
data class MerchantProfileRoute(val merchantName: String) : Route {
    override val name = "MerchantProfile"
}

/** زون التحويلات (شريط «طرفان بانتظار ردك»). */
object TransfersRoute : Route {
    override val name = "Transfers"
}

/** تحويلاتك بين البلدين (من «البلدان» — الزرار شغل منطقة «المزيد»). */
object SpaceTransferRoute : Route {
    override val name = "SpaceTransfer"
}

/** مراجعة الغامض — الشاشة شغل شريحة «المراجعة» (من غير تسجيل هنا ⇒ «قيد البناء»). */
object ReviewQueueRoute : Route {
    override val name = "ReviewQueue"
}

fun RouteRegistry.registerOperations() {
    tabRoot(Tab.OPERATIONS) { OperationsScreen() }
    screen<OperationDetailRoute> { r -> OperationDetailScreen(r.transactionId, r.openPerson) }
    screen<OperationFiltersRoute> { OperationFiltersScreen() }
    screen<MerchantProfileRoute> { r -> MerchantProfileScreen(r.merchantName) }
    screen<TransfersRoute> { TransfersScreen() }
    screen<SpaceTransferRoute> { SpaceTransferScreen() }
}
