package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.Space

/**
 * هيكل شاشات المستحقات الداخلية: نفس `InnerScaffold` (الحواف 20 · المسافة 14 · من غير الشريطين) بس الرأس فيه سطر تحت العنوان
 * ([DuesHeader]) زي لوحات `DuesDebts` و`InstallmentDetail` و`SubscriptionDetail`.
 */
@Composable
internal fun DuesScaffold(
    title: String,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.gutter + top, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        item(key = "header") { DuesHeader(title, subtitle, actions) }
        content()
    }
}
