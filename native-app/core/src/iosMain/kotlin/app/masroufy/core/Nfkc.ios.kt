package app.masroufy.core

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCompatibilityMapping

// خصائص NSString الجاية من «categories» محتاجة استيراد بالاسم في كوتلن/نيتف
internal actual fun nfkc(text: String): String =
    NSString.create(string = text).precomposedStringWithCompatibilityMapping
