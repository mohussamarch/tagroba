package app.masroufy.core

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCompatibilityMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun nfkc(text: String): String =
    NSString.create(string = text).precomposedStringWithCompatibilityMapping
