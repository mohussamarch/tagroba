package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.TextKey
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/** الدواير اللي بتتعرض للشخص الجديد (النموذج: عائلة · أصدقاء · عمل). */
internal val NEW_PERSON_CIRCLES = listOf(PersonCircle.FAMILY, PersonCircle.FRIEND, PersonCircle.WORK)

/** صندوق «شخص جديد»: الاسم (جاهز من المصدر) ودايرته — مختار ⇒ أخضر بحد. */
@Composable
internal fun NewPersonBox(name: String, selected: Boolean, circle: PersonCircle?, onName: (String) -> Unit, onCircle: (PersonCircle) -> Unit) {
    val shape = RoundedCornerShape(Radius.control)
    val surface = if (selected) Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary) else Modifier.background(Color(0x0A193D33))
    Column(Modifier.fillMaxWidth().clip(shape).then(surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.TRANSFER_PARTY_NEW_TITLE), style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary))
        TextInput(name, onName, placeholder = t(UiKey.TRANSFER_PARTY_NAME_LABEL))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in NEW_PERSON_CIRCLES) SelectChip(c.label, circle == c, { onCircle(c) })
        }
    }
}

/** أشخاصك: صف 52 فيه أول حرف في دايرة 36 والاسم — المختار أخضر بحد. */
@Composable
internal fun PeopleList(people: List<Person>, picked: Person?, onPick: (Person) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in people) {
            val on = picked?.id == p.id
            val shape = RoundedCornerShape(16.dp)
            val press = rememberPress()
            val surface = if (on) Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary) else Modifier.background(Color(0x0A193D33))
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).pressScale(press).clip(shape).then(surface)
                    .tap(press, role = Role.RadioButton, onClick = { onPick(p) }).semantics { selected = on }.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Initial(p.name)
                BasicText(p.name, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
            }
        }
    }
}

/** أول حرف من الاسم في دايرة (أخضر فاتح). */
@Composable
internal fun Initial(name: String, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Ink.selected), contentAlignment = Alignment.Center) {
        BasicText(name.trim().take(1), style = Type.of(if (size >= 40) 16 else 15, FontWeight.Bold).copy(color = Ink.primary))
    }
}
