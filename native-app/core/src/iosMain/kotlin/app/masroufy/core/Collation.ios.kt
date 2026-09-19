package app.masroufy.core

import platform.Foundation.NSLocale
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.compare
import platform.Foundation.create

// ⚠️ ما اتبناش لسه (محتاج ماك) — بيتأكد في GitHub Actions على ماك (KOTLIN_PLAN §4-أ٥)
private val arabic = NSLocale(localeIdentifier = "ar")

internal actual fun compareArabic(a: String, b: String): Int =
    NSString.create(string = a).compare(b, options = 0u, range = NSMakeRange(0u, a.length.toULong()), locale = arabic).toInt()
