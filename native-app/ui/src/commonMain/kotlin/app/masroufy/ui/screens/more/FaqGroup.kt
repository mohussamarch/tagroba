package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.FaqItem
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** كارت قسم: عنوانه + أسئلته. السؤال بيتفتح بالضغط ([openId])؛ [allOpen] (البحث شغال) ⇒ كلها مفتوحة. */
@Composable
internal fun FaqGroup(title: String, rows: List<FaqItem>, openId: Int, allOpen: Boolean, onToggle: (Int) -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        BasicText(title, Modifier.padding(top = 8.dp, bottom = 2.dp), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        rows.forEachIndexed { i, item ->
            if (i > 0) Divider()
            FaqRow(item, allOpen || openId == item.id) { onToggle(item.id) }
        }
    }
}

@Composable
private fun FaqRow(item: FaqItem, open: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).tap(press, role = Role.Button, label = item.question, onClick = onClick)
            .semantics { stateDescription = if (open) "expanded" else "collapsed" }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(item.question, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold).copy(color = Ink.text))
        Box(Modifier.rotate(if (open) 180f else 0f)) { LucideIcon(Lucide.CHEVRON_DOWN, size = 16.dp, tint = Ink.muted) }
    }
    if (open) Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in item.lines) BasicText(line, style = Type.of(14, lineHeight = 1.8).copy(color = Ink.soft))
    }
}
