package app.masroufy.ui.components

import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertEquals

/** L5 (OVERRIDES §79): الأرقام العربية والفارسية اللي بتتكتب بتبقى 0-9 قبل أي قراءة — وكلمة السر ما بتتلمسش. */
class TypedDigitsTest {
    @Test
    fun amountFieldsConvertDigitsAndSeparators() {
        assertEquals("1,250.75", typedDigits("١٬٢٥٠٫٧٥", KeyboardType.Decimal))
        assertEquals("42", typedDigits("۴۲", KeyboardType.Number))
    }

    @Test
    fun textFieldsConvertDigitsOnly() {
        assertEquals("قهوة 25", typedDigits("قهوة ٢٥", KeyboardType.Text))
    }

    @Test
    fun passwordsAreLeftAlone() {
        assertEquals("سر١٢٣", typedDigits("سر١٢٣", KeyboardType.Password))
        assertEquals("١٢٣٤", typedDigits("١٢٣٤", KeyboardType.Text, password = true))
        assertEquals("١٢٣٤", typedDigits("١٢٣٤", KeyboardType.NumberPassword))
    }
}
