package app.masroufy.usecase

import app.masroufy.core.BIRTH_DATE_KEY
import app.masroufy.port.Clock
import app.masroufy.core.FUTURE_MAX_YEARS
import app.masroufy.core.FutureChoice
import app.masroufy.core.FutureMix
import app.masroufy.core.FuturePath
import app.masroufy.core.IsoDate
import app.masroufy.core.UserSetting
import app.masroufy.core.investViewKey
import app.masroufy.port.UserSettingsStore

/**
 * اختيارات «هتبقى معاك كام» المحفوظة لكل بلد (مواصفة الاستثمار §1.5 · §3.2): علامات السنين بتفضل على التشارت لحد ما تتشال بـ«×»
 * (كلام المالك «بتفضل على التشارت لحد ما تتشال»)، والطريق والمبلغ والخلطة بيتحفظوا (سؤال Q8 — النموذج كان بيرجّعهم كل مرة).
 * المدة و«بفلوس النهارده» **مش** بيتحفظوا: الشاشة بتفتح دايمًا على 10 سنين بفلوس النهارده (§87-5).
 */
class ManageInvestView(private val settings: UserSettingsStore, private val clock: Clock) {
    suspend fun load(spaceId: String): FutureChoice {
        val s = stored(spaceId) ?: return FutureChoice()
        return FutureChoice(path = FuturePath.fromWire(s.path), amountMinor = s.amountMinor, mix = FutureMix.fromWire(s.mix) ?: FutureMix.MINE, marks = s.marks.sorted())
    }

    /** بيحفظ الطريق والمبلغ والخلطة والعلامات من [c]. */
    suspend fun save(spaceId: String, c: FutureChoice) {
        settings.save(UserSetting.InvestView(spaceId, c.marks.distinct().sorted(), c.path?.wire, c.amountMinor, c.mix.wire, clock.nowIso()))
    }

    /**
     * «سجّلها علامة»: السنة لازم تكون بعد السنة دي ولحد 40 سنة ([thisYear], thisYear + 40] — غير كده [IllegalArgumentException]
     * (الشاشة بتفحص قبلها وبتقول «اكتب سنة من … إلى …»). بترجع العلامات بعد الإضافة (من غير تكرار، بالترتيب).
     */
    suspend fun addMark(spaceId: String, year: Int, thisYear: Int): List<Int> {
        require(year in markRange(thisYear))
        val c = load(spaceId)
        val marks = (c.marks + year).distinct().sorted()
        save(spaceId, c.copy(marks = marks))
        return marks
    }

    suspend fun removeMark(spaceId: String, year: Int): List<Int> {
        val c = load(spaceId)
        val marks = c.marks.filter { it != year }
        save(spaceId, c.copy(marks = marks))
        return marks
    }

    private suspend fun stored(spaceId: String): UserSetting.InvestView? =
        settings.listAll().filterIsInstance<UserSetting.InvestView>().firstOrNull { it.key == investViewKey(spaceId) }

    companion object {
        /** السنين المسموحة للعلامة: من السنة الجاية لحد 40 سنة. */
        fun markRange(thisYear: Int): IntRange = (thisYear + 1)..(thisYear + FUTURE_MAX_YEARS)
    }
}

/** تاريخ الميلاد على مستوى الحساب (§87-8 «يتحفظ هناك» — حاسبة التقاعد بتكتبه، و«هتبقى معاك كام» بتقراه). */
class ManageBirthDate(private val settings: UserSettingsStore, private val clock: Clock) {
    suspend fun load(): IsoDate? = settings.listAll().filterIsInstance<UserSetting.BirthDate>().firstOrNull { it.key == BIRTH_DATE_KEY }?.date

    suspend fun save(date: IsoDate) {
        settings.save(UserSetting.BirthDate(date, clock.nowIso()))
    }
}
