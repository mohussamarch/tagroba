package app.masroufy.personal;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * قفل التطبيق بالبصمة أو رمز الجوال — OVERRIDES §21، ARCHITECTURE §27.
 * التطبيق ما بيستلمش البصمة ولا الرمز: BiometricPrompt بيرجّع نجح/فشل بس.
 */
@CapacitorPlugin(name = "DeviceLock")
public class DeviceLockPlugin extends Plugin {

    /**
     * بصمة قوية أو رمز الجوال. على أندرويد 9 و10 الجمع ده مش مدعوم
     * (توثيق androidx.biometric)، فبنستعمل الضعيفة + الرمز.
     */
    private static int authenticators() {
        int credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ? BiometricManager.Authenticators.BIOMETRIC_STRONG | credential
            : BiometricManager.Authenticators.BIOMETRIC_WEAK | credential;
    }

    @PluginMethod
    public void availability(PluginCall call) {
        int code = BiometricManager.from(getContext()).canAuthenticate(authenticators());
        JSObject ret = new JSObject();
        ret.put("available", code == BiometricManager.BIOMETRIC_SUCCESS);
        ret.put("code", codeName(code));
        call.resolve(ret);
    }

    @PluginMethod
    public void authenticate(PluginCall call) {
        AppCompatActivity activity = getActivity();
        if (activity == null) {
            resolve(call, "unavailable");
            return;
        }
        String title = call.getString("title", "مصروفي");
        String subtitle = call.getString("subtitle", "");
        AtomicBoolean done = new AtomicBoolean(false);
        activity.runOnUiThread(() -> {
            BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        if (done.compareAndSet(false, true)) resolve(call, "ok");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        if (!done.compareAndSet(false, true)) return;
                        boolean cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED
                            || errorCode == BiometricPrompt.ERROR_CANCELED
                            || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON;
                        boolean unavailable = errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                            || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                            || errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                            || errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS;
                        resolve(call, cancelled ? "cancelled" : unavailable ? "unavailable" : "failed");
                    }

                    // onAuthenticationFailed = محاولة بصمة غلط واحدة؛ الحوار بيفضل مفتوح لمحاولة تانية، فمش نهاية
                });
            BiometricPrompt.PromptInfo.Builder info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(authenticators());
            if (!subtitle.isEmpty()) info.setSubtitle(subtitle);
            prompt.authenticate(info.build());
        });
    }

    private static void resolve(PluginCall call, String result) {
        JSObject ret = new JSObject();
        ret.put("result", result);
        call.resolve(ret);
    }

    private static String codeName(int code) {
        switch (code) {
            case BiometricManager.BIOMETRIC_SUCCESS: return "SUCCESS";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED: return "NONE_ENROLLED";
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE: return "NO_HARDWARE";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE: return "HW_UNAVAILABLE";
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED: return "SECURITY_UPDATE_REQUIRED";
            default: return "UNSUPPORTED";
        }
    }
}
