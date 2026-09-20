package app.masroufy.core

import platform.Foundation.NSLocale
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.create

// ترتيب الحروف العربي من ICU بتاع النظام — زي ICU4J على الكمبيوتر وأندرويد
private val arabic = NSLocale(localeIdentifier = "ar")

internal actual fun compareArabic(a: String, b: String): Int =
    NSString.create(string = a).compare(b, options = 0u, range = NSMakeRange(0u, a.length.toULong()), locale = arabic).toInt()
