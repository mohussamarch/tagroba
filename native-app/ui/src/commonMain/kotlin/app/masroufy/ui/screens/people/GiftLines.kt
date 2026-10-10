package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Person
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.usecase.GiftEntry

/**
 * سطور النقوط الكاش (`NuqootSheet`) من غير رسم: الفحص قبل أي كتابة (كل سطر اسم ومبلغ أكبر من صفر، ومن غير اسم مكرر — نفس ترتيب النموذج)،
 * والاسم ⇒ شخص موجود بنفس الاسم أو شخص جديد وقت الحفظ.
 */
internal sealed interface GiftPlan {
    data class Ready(val entries: List<Pair<String, Halalas>>, val known: Map<String, Id>) : GiftPlan {
        val amounts: List<Halalas> get() = entries.map { it.second }
    }

    data class Bad(val message: String) : GiftPlan
}

internal fun checkGiftLines(rows: List<GiftLine>, people: List<Person>, currency: Currency): GiftPlan {
    val filled = rows.map { jsTrim(it.name) to it.amount }.filter { (n, a) -> n.isNotEmpty() || a.isNotBlank() }
    if (filled.isEmpty()) return GiftPlan.Bad(t(TextKey.NUQOOT_NEED_ONE))
    val parsed = filled.map { (n, a) -> n to tryParseMoney(a, currency) }
    if (parsed.any { (n, m) -> n.isEmpty() || m == null || m <= 0 }) return GiftPlan.Bad(t(TextKey.NUQOOT_FILL_ROWS))
    val names = parsed.map { it.first }
    if (names.toSet().size != names.size) return GiftPlan.Bad(t(TextKey.NUQOOT_DUP))
    val known = people.filter { !it.archived }.associateBy { jsTrim(it.name) }
    return GiftPlan.Ready(parsed.map { (n, m) -> n to m!! }, names.mapNotNull { n -> known[n]?.let { n to it.id } }.toMap())
}

/** الأسامي الجديدة بتتضاف أشخاص، وبعدين عملية لكل اسم في كتابة واحدة (`recordGifts`). */
internal suspend fun saveGifts(deps: PeopleDeps, eventId: Id, direction: Direction, walletId: Id, date: IsoDate, plan: GiftPlan.Ready) {
    val entries = plan.entries.map { (name, minor) -> GiftEntry(plan.known[name] ?: deps.people.addPerson(name).id, minor) }
    deps.gifts.recordGifts(eventId, direction, walletId, date, entries)
}

/** صف بيتختار (عملية موجودة): رمادي خفيف، والمختار `#DCEBD6` بحد أخضر 1.5. */
@Composable
internal fun PickRow(on: Boolean, label: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val press = rememberPress()
    Column(
        Modifier.fillMaxWidth().pressScale(press).clip(shape)
            .then(if (on) Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary) else Modifier.background(PeopleInk.rowBg))
            .tap(press, label = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}
