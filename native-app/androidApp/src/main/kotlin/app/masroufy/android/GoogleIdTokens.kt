package app.masroufy.android

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.GoogleIdToken
import app.masroufy.port.GoogleIdTokenSource
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.cancellation.CancellationException

/**
 * دخول جوجل بنافذة النظام (Credential Manager — ARCHITECTURE §31.31): «اختار حسابك» من الحسابات اللي على الجوال ⇒ رمز هوية ⇒ فايربيز
 * (`SignIn.withGoogle`). التطبيق ما بيشوفش كلمة سر. [webClientId] من إعدادات فايربيز (عميل الويب) — بصمة مفتاح التطوير متسجلة في المشروع.
 * القفل = «اتلغى» (من غير رسالة)؛ مفيش حساب جوجل على الجوال أو مفيش خدمات جوجل ⇒ السبب بيتقال.
 */
class AndroidGoogleIdTokens(private val activity: () -> Activity?, private val webClientId: String) : GoogleIdTokenSource {
    override suspend fun request(): GoogleIdToken {
        val host = activity() ?: return GoogleIdToken.Unavailable(uiText(TextKey.AUTH_GOOGLE_FAILED))
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build()).build()
        return try {
            val credential = CredentialManager.create(host).getCredential(host, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdToken.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else GoogleIdToken.Unavailable(uiText(TextKey.AUTH_GOOGLE_FAILED))
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            GoogleIdToken.Cancelled
        } catch (_: NoCredentialException) {
            GoogleIdToken.Unavailable(uiText(TextKey.AUTH_GOOGLE_NO_ACCOUNT))
        } catch (_: GetCredentialException) {
            GoogleIdToken.Unavailable(uiText(TextKey.AUTH_GOOGLE_FAILED))
        }
    }
}
