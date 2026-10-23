package app.masroufy.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * fix053 (فحص 0.5.3 — «أضف دينًا» بحساب فيه 25 شخص): جسم لوحة ممكن يطوّل أكتر من الشاشة ⇒ بيسكرول جوه اللوحة، فزرار الحفظ اللي في آخره
 * دايمًا بيتوصله. اللوحة نفسها بتفضل بطولها لحد الشاشة (`weight(fill = false)` — القصير بياخد طوله بس).
 * ⚠️ ما يتحطش جوه حاجة بتسكرول لفوق وتحت (ولا فيه `LazyColumn`).
 */
@Composable
fun ColumnScope.SheetScroll(spacing: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
