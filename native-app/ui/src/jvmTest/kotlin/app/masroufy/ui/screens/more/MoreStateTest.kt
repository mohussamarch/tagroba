package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import app.masroufy.core.AlertGroup
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Language
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «المزيد» · صيغ العدد والتاريخ · إعدادات الإشعارات · البلدان — من بيانات حالات الاستخدام للعرض (دوال نقية). */
class MoreStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val saudi = Space(DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "2025-01-01T00:00:00Z")
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-05-12T09:00:00Z")

    @Test
    fun countWordsFollowArabicNumberRules() {
        assertEquals(t(UiKey.WLIST_ONE), countText(1, WALLET_WORDS))
        assertEquals(t(UiKey.WLIST_TWO), countText(2, WALLET_WORDS))
        assertEquals(t(UiKey.WLIST_FEW, sentenceNumber(3)), countText(3, WALLET_WORDS))
        assertEquals(t(UiKey.WLIST_FEW, sentenceNumber(10)), countText(10, WALLET_WORDS))
        assertEquals(t(UiKey.WLIST_MANY, sentenceNumber(11)), countText(11, WALLET_WORDS))
        assertEquals(t(UiKey.WLIST_MANY, sentenceNumber(0)), countText(0, WALLET_WORDS))
    }

    @Test
    fun fullDateTakesTheDayFromAFullTimestampAndRejectsGarbage() {
        val day = fullDate("2026-05-12")
        assertEquals(day, fullDate("2026-05-12T09:00:00Z"))
        assertTrue(day!!.contains(sentenceNumber(2026)))
        assertNull(fullDate(null))
        assertNull(fullDate("not a date"))
        assertNull(fullDate("2026-02-30"))
    }

    @Test
    fun moreViewShowsOnlyWhatIsKnown() {
        val v = moreView(MeInfo("  محمد ", null), 28, listOf(SpaceCard(egypt, false, 1), SpaceCard(saudi, true, 3)), saudi)
        assertEquals("محمد", v.name)
        assertEquals(t(UiKey.MORE_INCOME_HINT, sentenceNumber(28)), v.incomeHint)
        // البلد الشغالة الأول
        assertTrue(v.spacesHint!!.startsWith(t(TextKey.COUNTRY_SA)))
        assertTrue(v.spaceLine.contains(t(TextKey.COUNTRY_SA)))

        val unknown = moreView(MeInfo(" ", null), null, null, saudi)
        assertNull(unknown.name)
        assertNull(unknown.incomeHint, "يوم الراتب مش معروف ⇒ مفيش سطر (مش «يوم ٠»)")
        assertNull(unknown.spacesHint)
        assertNull(moreView(null, null, emptyList(), saudi).spacesHint)
    }

    @Test
    fun moreGroupsLeadToTheAreaScreens() {
        val groups = moreGroups(moreView(null, 28, null, saudi))
        assertEquals(listOf(UiKey.MORE_GROUP_ACCOUNT, UiKey.MORE_GROUP_DATA, UiKey.MORE_GROUP_SETTINGS), groups.map { it.title })
        val routes = groups.flatMap { g -> g.items.map { it.to } }
        listOf(AccountRoute, IncomeSourcesRoute, SpacesRoute, WalletsRoute, BackupRoute, NotificationSettingsRoute, AppSettingsRoute)
            .forEach { assertTrue(it in routes, "$it مش في القايمة") }
        val income = groups[0].items.first { it.to == IncomeSourcesRoute }
        assertEquals(t(UiKey.MORE_INCOME_HINT, sentenceNumber(28)), income.hint)
        assertNull(groups[0].items.first { it.to == AccountRoute }.hint)
    }

    @Test
    fun notificationRowsAreAllOnExceptWhatTheUserTurnedOff() {
        val all = notificationRows(emptySet())
        assertEquals(AlertGroup.BANK_SMS, all.first().group)
        assertTrue(all.first().isNew)
        assertTrue(all.all { it.on })
        assertEquals(all.size, all.map { it.group }.toSet().size)
        assertEquals(UiKey.NSET_BANK_DESC, all.first().desc)

        val off = notificationRows(setOf(AlertGroup.BANK_SMS, AlertGroup.ZAKAT))
        assertFalse(off.first { it.group == AlertGroup.BANK_SMS }.on)
        assertEquals(UiKey.NSET_BANK_OFF_DESC, off.first { it.group == AlertGroup.BANK_SMS }.desc)
        assertFalse(off.first { it.group == AlertGroup.ZAKAT }.on)
        assertEquals(UiKey.NSET_ZAKAT_DESC, off.first { it.group == AlertGroup.ZAKAT }.desc)
        assertEquals(off.size - 2, off.count { it.on })
    }

    @Test
    fun spaceSectionsKeepSaudiFirstAndNeverArchiveIt() {
        val sections = spaceSections(listOf(SpaceCard(egypt, true, 2), SpaceCard(saudi, false, null)), emptyList())
        assertEquals(1, sections.size, "مفيش مؤرشف ⇒ مفيش قسم مؤرشف")
        val (sa, eg) = sections[0].cards
        assertEquals(DEFAULT_SPACE_ID, sa.space.id)
        assertTrue(sa.isDefault)
        assertFalse(sa.canArchive)
        assertTrue(sa.canSwitch)
        assertEquals(t(UiKey.SPC_META_AUTO), sa.meta, "عدد المحافظ مش معروف ⇒ بيتشال (مش صفر)")
        assertFalse(eg.canSwitch, "الشغالة ما بتتبدلش لنفسها")
        assertTrue(eg.canArchive)
        assertTrue(eg.meta.startsWith(t(UiKey.WLIST_TWO)))
        assertTrue(eg.meta.contains(fullDate(egypt.createdAt)!!))
    }

    @Test
    fun archivedCountriesGetTheirOwnSection() {
        val old = egypt.copy(archived = true)
        val sections = spaceSections(listOf(SpaceCard(saudi, true, 1)), listOf(old))
        assertEquals(listOf(UiKey.SPC_ACTIVE_HEAD, UiKey.SPC_ARCHIVED_HEAD), sections.map { it.title })
        val card = sections[1].cards.single()
        assertTrue(card.archived)
        assertFalse(card.canSwitch)
        assertFalse(card.canArchive)
        assertEquals(t(UiKey.SPC_META_ARCHIVED), card.meta)
    }

    @Test
    fun countryOptionsMarkTakenAndArchivedCountries() {
        val fresh = countryOptions(listOf(saudi), emptyList()).associate { it.countryCode to it.state }
        assertEquals(CountryState.TAKEN, fresh["SA"])
        assertEquals(CountryState.AVAILABLE, fresh["EG"])
        val archived = countryOptions(listOf(saudi), listOf(egypt.copy(archived = true))).associate { it.countryCode to it.state }
        assertEquals(CountryState.ARCHIVED, archived["EG"])
    }

    @Test
    fun saudiAndEgyptianWordingDiffer() {
        Texts.arabicVariant = ArabicVariant.MSA
        val msa = moreView(null, 28, null, saudi).incomeHint
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val egyptian = moreView(null, 28, null, egypt).incomeHint
        assertTrue(msa!!.contains("الراتب"))
        assertTrue(egyptian!!.contains("المرتب"))
        assertEquals(t(UiKey.MORE_TODAY), dateChipLabel("2026-10-09", "2026-10-09"))
        assertEquals("النهارده", t(UiKey.MORE_TODAY))
    }
}
