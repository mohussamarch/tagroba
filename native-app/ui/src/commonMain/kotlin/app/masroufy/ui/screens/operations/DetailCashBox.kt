package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/** «هذه ليست عملية مسجّلة» — فرق عدّ الكاش بنقطة حمرا لحد ما تحدد فيمَ صُرف (§73). اتنقل من `OperationDetailScreen` (حد الـ300 سطر). */
@Composable
internal fun CashDifferenceBox(onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Color(0x14BE3D48)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(t(UiKey.OPERATION_DETAIL_CASH_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.expense))
        BasicText(t(UiKey.OPERATION_DETAIL_CASH_BODY), style = Type.of(13))
        PrimaryButton(t(UiKey.CATEGORY_PICKER_CASH_TITLE), onClick = onPick, modifier = Modifier.fillMaxWidth())
    }
}
