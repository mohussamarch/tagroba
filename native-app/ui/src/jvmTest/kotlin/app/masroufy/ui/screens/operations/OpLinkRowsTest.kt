package app.masroufy.ui.screens.operations

import app.masroufy.ui.screens.dues.DebtDetailRoute
import app.masroufy.ui.screens.dues.InstallmentDetailRoute
import app.masroufy.ui.screens.dues.SubscriptionDetailRoute
import app.masroufy.ui.screens.investment.AssetDetailRoute
import app.masroufy.ui.screens.more.IncomeSourceDetailRoute
import app.masroufy.ui.screens.more.WalletDetailRoute
import app.masroufy.ui.screens.people.PersonProfileRoute
import app.masroufy.usecase.OpLink
import app.masroufy.usecase.OpLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-links2 فجوة ٣: كل رابط في تفاصيل العملية بيفتح صفحته بالمسار الموجود. أسامي مخترعة. */
class OpLinkRowsTest {
    @Test fun everyLinkKindOpensItsExistingPage() {
        val rows = opLinkRows(
            listOf(
                OpLink(OpLinkKind.WALLET, "w-1", "بنك وهمي"), OpLink(OpLinkKind.PERSON, "p-1", "صديق وهمي"), OpLink(OpLinkKind.DEBT, "obl-1", ""),
                OpLink(OpLinkKind.PLAN, "ip-1", "جهاز"), OpLink(OpLinkKind.SUBSCRIPTION, "rec-1", "خدمة"), OpLink(OpLinkKind.ASSET_SALE, "as-1", "ذهب"),
                OpLink(OpLinkKind.INCOME_SOURCE, "inc-1", "جهة"),
            ),
        )
        assertEquals(
            listOf(
                WalletDetailRoute("w-1"), PersonProfileRoute("p-1"), DebtDetailRoute("obl-1"), InstallmentDetailRoute("ip-1"),
                SubscriptionDetailRoute("rec-1"), AssetDetailRoute("as-1"), IncomeSourceDetailRoute("inc-1"),
            ),
            rows.map { it.route },
        )
        assertEquals("بنك وهمي", rows.first().name)
        assertTrue(rows.all { it.label.isNotBlank() }, "كل سطر ليه وسم")
        assertEquals(7, rows.map { it.label }.toSet().size, "وسم مختلف لكل نوع")
    }
}
