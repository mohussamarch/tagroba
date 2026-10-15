package app.masroufy.ui.screens.common

import app.masroufy.core.CalendarItemType
import app.masroufy.core.Halalas
import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.text.t

/**
 * أفعال القايمة السريعة (الضغطة الطويلة — قايمة المالك §6.5-13). **كل فعل هنا بينادي حاجة موجودة** (شاشة · لوحة · حالة استخدام)
 * — مفيش فعل جديد اتخترع. [DELETE] بيظهر بس لو الشاشة عندها مسح فعلًا (`canDelete`) — المسح نفسه شغل فرع r04-delete
 * (بتأكيد جوه الصفحة + «تراجع»)، والشاشات هنا سايبة له مكان (`delete = null`) يتوصل وقت الدمج.
 */
enum class QuickAct(val label: TextRef) {
    OPEN(UiKey.QM_OPEN),
    /** نفس الضغطة العادية لما بتودّي لقسم مش لصفحة العنصر (مواعيد التقويم). */
    OPEN_SECTION(UiKey.QM_OPEN_SECTION),
    EDIT(UiKey.QM_EDIT),
    SETTLE(UiKey.QM_SETTLE),
    PERSON(UiKey.QM_PERSON),
    ADD_OCCASION(UiKey.QM_ADD_OCCASION),
    PREP(UiKey.QM_PREP),
    STAR(UiKey.GOALS_STAR_ADD),
    UNSTAR(UiKey.GOALS_STAR_REMOVE),
    ARCHIVE(UiKey.QM_ARCHIVE),
    UNARCHIVE(UiKey.QM_UNARCHIVE),
    SHOW_DAY(UiKey.QM_SHOW_DAY),
    ADD_EVENT_ON_DAY(UiKey.QM_ADD_EVENT_DAY),
    MARK_READ(UiKey.QM_MARK_READ),
    DELETE(UiKey.QM_DELETE),
}

/** البنود للقايمة بالترتيب — [onAct] بيتنادي بعد ما القايمة تقفل. */
fun quickItems(acts: List<QuickAct>, onAct: (QuickAct) -> Unit): List<MenuItem> = acts.map { a -> MenuItem(t(a.label)) { onAct(a) } }

private fun MutableList<QuickAct>.deleteIf(canDelete: Boolean) {
    if (canDelete) add(QuickAct.DELETE)
}

/** دين (التزام): التسوية بس لو لسه فيه باقي — الدين المسدّد ما فيهوش «سدّد». */
fun debtQuickActs(remainingMinor: Halalas?, canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    if (remainingMinor != null && remainingMinor > 0) add(QuickAct.SETTLE)
    add(QuickAct.PERSON)
    deleteIf(canDelete)
}

/** خطة تقسيط: التعديل من شاشة التعديل الموجودة. */
fun installmentQuickActs(canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    add(QuickAct.EDIT)
    deleteIf(canDelete)
}

/** اشتراك/فاتورة · جمعية · مشروع: الفتح (وفيه كل أفعالها)، والمسح لو موجود. */
fun openOnlyQuickActs(canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    deleteIf(canDelete)
}

/** خطة ادخار: النجمة زي زرارها على الكارت. */
fun goalQuickActs(starred: Boolean, canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    add(if (starred) QuickAct.UNSTAR else QuickAct.STAR)
    deleteIf(canDelete)
}

/** أصل: الأرشفة زي زرار تفاصيل الأصل (`archiveAsset`). */
fun assetQuickActs(archived: Boolean, canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    add(if (archived) QuickAct.UNARCHIVE else QuickAct.ARCHIVE)
    deleteIf(canDelete)
}

/** حدث: التجهيزات للحدث اللي لسه شغال بس (المؤرشف بيتفتح بس). */
fun eventQuickActs(archived: Boolean, canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    if (!archived) add(QuickAct.PREP)
    deleteIf(canDelete)
}

/** شخص: ملفه · مناسبة جديدة له (`OccasionSheetRoute`). */
fun personQuickActs(canDelete: Boolean = false): List<QuickAct> = buildList {
    add(QuickAct.OPEN)
    add(QuickAct.ADD_OCCASION)
    deleteIf(canDelete)
}

/** يوم في التقويم: مواعيده (زي الضغطة) · حدث جديد فيه (لو لوحة «حدث جديد» شغالة). */
fun calendarDayQuickActs(canAddEvent: Boolean): List<QuickAct> = buildList {
    add(QuickAct.SHOW_DAY)
    if (canAddEvent) add(QuickAct.ADD_EVENT_ON_DAY)
}

/** الأنواع اللي ليها صفحة بالرقم بتاعها في التقويم (`CalendarItem.sourceId`). */
fun calendarItemHasPage(type: CalendarItemType): Boolean = when (type) {
    CalendarItemType.RECURRING, CalendarItemType.INSTALLMENT, CalendarItemType.ROSCA_CONTRIBUTION, CalendarItemType.ROSCA_PAYOUT,
    CalendarItemType.DEBT, CalendarItemType.EVENT, CalendarItemType.PROJECT -> true
    CalendarItemType.OCCASION, CalendarItemType.PAYDAY, CalendarItemType.INCOME_PAY, CalendarItemType.ZAKAT, CalendarItemType.PUBLIC_OCCASION -> false
}

/**
 * ميعاد في التقويم: صفحته (لو ليه) · «سجّل سداد» للدين · القسم (= الضغطة العادية). الدين اللي اتسدّد ما بيظهرش في التقويم أصلًا
 * (المواعيد = الأقساط اللي لسه)، فـ«سدّد» دايمًا للدين اللي ظاهر.
 */
fun calendarItemQuickActs(type: CalendarItemType): List<QuickAct> = buildList {
    if (calendarItemHasPage(type)) add(QuickAct.OPEN)
    if (type == CalendarItemType.DEBT) add(QuickAct.SETTLE)
    if (type == CalendarItemType.EVENT) add(QuickAct.PREP)
    add(QuickAct.OPEN_SECTION)
}

/** تنبيه: «اتقرا» للجديد بس. */
fun notificationQuickActs(unread: Boolean, canOpen: Boolean, canDelete: Boolean = false): List<QuickAct> = buildList {
    if (canOpen) add(QuickAct.OPEN)
    if (unread) add(QuickAct.MARK_READ)
    deleteIf(canDelete)
}
