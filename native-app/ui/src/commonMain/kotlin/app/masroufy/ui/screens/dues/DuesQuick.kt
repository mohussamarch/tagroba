package app.masroufy.ui.screens.dues

import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.screens.common.QuickAct
import app.masroufy.ui.screens.common.debtQuickActs
import app.masroufy.ui.screens.common.installmentQuickActs
import app.masroufy.ui.screens.common.openOnlyQuickActs
import app.masroufy.ui.screens.common.quickItems
import app.masroufy.ui.screens.people.PersonProfileRoute

/**
 * القايمة السريعة في «المستحقات» (قايمة المالك §6.5-13): بنود كل نوع من أفعاله الموجودة — الدين: تفاصيله · لوحة التسوية (`SettleSheetRoute`)
 * · ملف الشخص · الخطة: تفاصيلها · تعديلها (`InstallmentEditRoute`) · الاشتراك والجمعية: التفاصيل. الشاشات بتحمّل تاني لوحدها بعد أي
 * تغيير (`DuesChanges`).
 *
 * **المسح** (فرع r04-delete): كل دالة هنا واخدة [delete] = null لحد ما يتوصل — لما يبقى موجود «امسح» بيظهر في القايمة لوحده
 * (`QuickAct.DELETE`)، والمسح نفسه (تأكيد جوه الصفحة + «تراجع») شغل الدالة اللي هتتبعت.
 */
internal fun debtQuickItems(row: DebtRowUi, nav: Navigator, delete: ((DebtRowUi) -> Unit)? = null): List<MenuItem> =
    quickItems(debtQuickActs(row.amountMinor, canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> nav.push(DebtDetailRoute(row.obligationId))
            QuickAct.SETTLE -> nav.open(SettleSheetRoute(row.obligationId))
            QuickAct.PERSON -> nav.push(PersonProfileRoute(row.personId))
            QuickAct.DELETE -> delete?.invoke(row)
            else -> Unit
        }
    }

internal fun planQuickItems(planId: String, nav: Navigator, delete: ((String) -> Unit)? = null): List<MenuItem> =
    quickItems(installmentQuickActs(canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> nav.push(InstallmentDetailRoute(planId))
            QuickAct.EDIT -> nav.push(InstallmentEditRoute(planId))
            QuickAct.DELETE -> delete?.invoke(planId)
            else -> Unit
        }
    }

internal fun subscriptionQuickItems(itemId: String, nav: Navigator, delete: ((String) -> Unit)? = null): List<MenuItem> =
    quickItems(openOnlyQuickActs(canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> nav.push(SubscriptionDetailRoute(itemId))
            QuickAct.DELETE -> delete?.invoke(itemId)
            else -> Unit
        }
    }

internal fun roscaQuickItems(roscaId: String, nav: Navigator, delete: ((String) -> Unit)? = null): List<MenuItem> =
    quickItems(openOnlyQuickActs(canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> nav.push(RoscaDetailRoute(roscaId))
            QuickAct.DELETE -> delete?.invoke(roscaId)
            else -> Unit
        }
    }
