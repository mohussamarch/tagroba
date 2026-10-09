package app.masroufy.device

import app.masroufy.port.HttpFailure
import app.masroufy.port.HttpTextPort
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL

/**
 * الآيفون: `NSData(contentsOf:)` من Foundation (من غير مكتبة) على خيط تاني. ⚠️ **ما اتجربش** (محتاج ماك) — وما بيفرّقش بين «مفيش نت»
 * و404 (الاتنين `status = null`). بيتبني على ماك GitHub بس.
 */
actual class PlatformHttpText actual constructor() : HttpTextPort {
    @OptIn(BetaInteropApi::class)
    actual override suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val nsUrl = NSURL.URLWithString(url) ?: throw HttpFailure(null, "bad url")
        val data: NSData = NSData.dataWithContentsOfURL(nsUrl) ?: throw HttpFailure(null, "no response")
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: throw HttpFailure(null, "not text")
    }
}
