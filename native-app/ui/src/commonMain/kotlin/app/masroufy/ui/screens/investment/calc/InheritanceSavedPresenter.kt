package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.Currency
import app.masroufy.core.EstateOwner
import app.masroufy.core.Halalas
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.InheritanceResult
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.TextKey
import app.masroufy.core.countryPack
import app.masroufy.core.dayMonth
import app.masroufy.ui.text.t

/**
 * «الحسبات المحفوظة» (لوحة `InheritanceSaved`): كل حسبة بقانون بلدها (§69.4)، والنتيجة بتتحسب من جديد وقت الفتح. «مجموع التركة» على الكارت
 * من نتيجة المحرك (`ManageInheritanceScenarios.calculate`) — لو الحسبة مسودة أو مش محسوبة ⇒ «غير متاح» (الشاشة ما بتجمعش مبالغ).
 */
data class SavedRowUi(
    val id: String,
    val name: String,
    val whose: String,
    val law: String,
    val lawEg: Boolean,
    val itemsLine: String,
    val totalMinor: Halalas?,
    val currency: Currency,
    val heirs: String,
    val whenText: String,
)

fun savedRowUi(s: InheritanceScenario, people: List<PersonChoice>, result: InheritanceResult?): SavedRowUi {
    val eg = InheritanceLaw.of(s.input.countryCode) == InheritanceLaw.EG
    val whose = when (s.estateOf) {
        EstateOwner.MINE -> t(TextKey.INHCALC_MINE)
        EstateOwner.OTHER -> s.personId?.let { id -> people.firstOrNull { it.id == id }?.name }?.let { t(TextKey.INHCALC_OF, it) } ?: t(TextKey.INHCALC_OTHER)
    }
    return SavedRowUi(
        id = s.id,
        name = s.name,
        whose = whose,
        law = t(if (eg) TextKey.INHSAVED_LAW_EG else TextKey.INHSAVED_LAW_SA),
        lawEg = eg,
        itemsLine = thingsPhrase(s.input.items.size),
        totalMinor = (result as? InheritanceResult.Computed)?.grossMinor,
        currency = countryPack(s.input.countryCode).currency,
        heirs = heirsSummary(s.input.heirs) ?: t(TextKey.INHSAVED_DRAFT),
        whenText = t(TextKey.INHSAVED_WHEN, dayMonth(s.updatedAt.take(10)), dayMonth(s.createdAt.take(10))),
    )
}
