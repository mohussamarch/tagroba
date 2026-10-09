package app.masroufy.ui.screens.more

import app.masroufy.core.COUNTRY_PACKS
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.ui.text.t

/**
 * «البلدان» (`Spaces`) — من البلاد للعرض، **دالة نقية** (بتتختبر على JVM). السعودية بتتعمل لوحدها وما بتتأرشفش (§41 · §64-٣)،
 * وبلد واحدة = حساب واحد (`SPACE_COUNTRY_TAKEN`) — الموجودة أو المؤرشفة ما بتتعملش تاني.
 */
data class SpaceCardView(
    val space: Space,
    val active: Boolean,
    val archived: Boolean,
    /** «٤ محافظ، أُنشئ تلقائيًا» — العدد بيتشال لو مش معروف (مش صفر). */
    val meta: String,
    val canSwitch: Boolean,
    val canArchive: Boolean,
    val isDefault: Boolean,
)

data class SpaceSection(val title: TextKey, val count: Int, val cards: List<SpaceCardView>)

fun spaceSections(open: List<SpaceCard>, archived: List<Space>): List<SpaceSection> {
    val live = open.sortedBy { if (it.space.id == DEFAULT_SPACE_ID) "" else it.space.createdAt }.map { c ->
        val isDefault = c.space.id == DEFAULT_SPACE_ID
        SpaceCardView(c.space, c.active, false, spaceMeta(c.space, c.walletCount, false), canSwitch = !c.active, canArchive = !isDefault, isDefault = isDefault)
    }
    val old = archived.map { s -> SpaceCardView(s, false, true, spaceMeta(s, null, true), canSwitch = false, canArchive = false, isDefault = false) }
    return listOfNotNull(
        SpaceSection(TextKey.SPC_ACTIVE_HEAD, live.size, live),
        old.takeIf { it.isNotEmpty() }?.let { SpaceSection(TextKey.SPC_ARCHIVED_HEAD, it.size, it) },
    )
}

private fun spaceMeta(space: Space, wallets: Int?, archived: Boolean): String {
    val created = when {
        archived -> t(TextKey.SPC_META_ARCHIVED)
        space.id == DEFAULT_SPACE_ID -> t(TextKey.SPC_META_AUTO)
        else -> fullDate(space.createdAt)?.let { t(TextKey.SPC_META_ADDED, it) } ?: ""
    }
    val count = wallets?.let { countText(it, WALLET_WORDS) }
    return listOfNotNull(count, created.takeIf { it.isNotEmpty() }).joinToString("، ")
}

/** حالة البلد في لوحة «أضف بلدًا». */
enum class CountryState { AVAILABLE, TAKEN, ARCHIVED }

data class CountryOption(val countryCode: String, val state: CountryState)

/** البلاد المدعومة (حزم البلاد — السعودية ومصر دلوقتي) وحالة كل واحدة قدام الموجود. */
fun countryOptions(open: List<Space>, archived: List<Space>): List<CountryOption> = COUNTRY_PACKS.values.map { pack ->
    val code = pack.code.uppercase()
    val state = when {
        open.any { it.countryCode.equals(code, true) } -> CountryState.TAKEN
        archived.any { it.countryCode.equals(code, true) } -> CountryState.ARCHIVED
        else -> CountryState.AVAILABLE
    }
    CountryOption(code, state)
}
