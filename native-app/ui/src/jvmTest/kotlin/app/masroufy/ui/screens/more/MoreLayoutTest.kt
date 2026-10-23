package app.masroufy.ui.screens.more

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Language
import app.masroufy.core.R06Key
import app.masroufy.core.Space
import app.masroufy.core.Texts
import app.masroufy.core.UiKey
import app.masroufy.core.emptyProfile
import app.masroufy.ui.screens.budgets.CategoriesRoute
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.screens.imports.ImportBatchesRoute
import app.masroufy.ui.screens.imports.StatementImportRoute
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** r06-more (OVERRIDES §116 · `More.dc.html` و`Account.dc.html` فرع التصميم c37ce76a) — دوال نقية. بيانات مخترعة. */
class MoreLayoutTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val saudi = Space(DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "2025-01-01T00:00:00Z")
    private val egypt = Space("sp-eg", "مصر", "EG", Currency.EGP, "2026-09-01T00:00:00Z")

    @Test
    fun moreHasExactlyTheDesignedRowsInOrder() {
        val groups = moreGroups()
        assertEquals(listOf(R06Key.MR_GROUP_MONEY, UiKey.MORE_GROUP_DATA, null), groups.map { it.title })
        assertEquals(
            listOf(listOf(WalletsRoute, CategoriesRoute), listOf(ImportHubRoute, BackupRoute), listOf(SettingsHubRoute)),
            groups.map { g -> g.items.map { it.to } },
        )
        assertEquals(
            listOf(UiKey.MORE_ITEM_WALLETS, R06Key.MR_CATS, R06Key.MR_IMPORT, R06Key.MR_BACKUP, R06Key.MR_SETTINGS),
            groups.flatMap { g -> g.items.map { it.label } },
        )
        assertEquals(t(R06Key.MR_SETTINGS_HINT), groups[2].items[0].hint)
        assertNull(groups[1].items[1].hint, "«آخر نسخة اليوم» مالهاش مصدر هنا ⇒ مفيش سطر (القاعدة 10)")
    }

    @Test
    fun egyptianWordingForTheMoneyGroup() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("فلوسك", t(moreGroups()[0].title!!))
        assertEquals("أموالك", t(R06Key.MR_GROUP_MONEY).let { Texts.arabicVariant = ArabicVariant.MSA; t(R06Key.MR_GROUP_MONEY) })
    }

    @Test
    fun importHubListsItsThreeScreens() {
        assertEquals(listOf(BankSmsRoute, StatementImportRoute, ImportBatchesRoute), importHubItems().map { it.to })
    }

    @Test
    fun settingsHubListsItsFiveEntries() {
        val items = settingsHubItems()
        assertEquals(
            listOf(UiKey.MORE_ITEM_NOTIFICATIONS, UiKey.MORE_ITEM_LOCK, R06Key.NP_THEME_TITLE, R06Key.MR_SET_LANG, UiKey.PLOG_TITLE),
            items.map { it.label },
        )
        assertEquals(listOf(NotificationSettingsRoute, AppSettingsRoute, AppSettingsRoute, AppSettingsRoute, ProblemLogRoute), items.map { it.to })
    }

    @Test
    fun profileIncomeGroupStartsWithIncomeSources() {
        val income = accountGroups(emptyProfile(), Currency.SAR).first { it.title == UiKey.ACC_GROUP_INCOME }
        assertEquals(AccountField.INCOME_SOURCES, income.rows.first().field)
        assertEquals(UiKey.MORE_ITEM_INCOME, income.rows.first().label)
        assertEquals(t(R06Key.MR_INCOME_VALUE), income.rows.first().value)
        assertNull(applyAnswer(emptyProfile(), AccountField.INCOME_SOURCES, "x"), "سطر رابط — مالوش إجابة")
    }

    @Test
    fun flagShowsOnlyWhenBothCountriesAreActive() {
        assertNull(swapTarget(saudi, listOf(saudi)), "بلد واحدة ⇒ مفيش علم")
        assertNull(swapTarget(saudi, emptyList()))
        assertNull(swapTarget(saudi, listOf(saudi, egypt.copy(archived = true))), "مصر مؤرشفة ⇒ بلد واحدة")
        assertEquals(egypt, swapTarget(saudi, listOf(saudi, egypt)))
        assertEquals(saudi, swapTarget(egypt, listOf(saudi, egypt)))
    }

    @Test
    fun quickMenuAddsArchivesAndManages() {
        assertEquals(listOf(CountryAct.ADD, CountryAct.MANAGE), countryMenu(listOf(saudi)).map { it.act }, "السعودية ما بتتأرشفش")
        val both = countryMenu(listOf(saudi, egypt))
        assertEquals(listOf(CountryAct.ADD, CountryAct.ARCHIVE, CountryAct.MANAGE), both.map { it.act })
        assertEquals(egypt, both[1].space)
    }
}
