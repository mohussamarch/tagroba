package app.masroufy.personal;

import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@CapacitorPlugin(name = "LocalFiles")
public class LocalFilesPlugin extends Plugin {
    @PluginMethod
    public void save(PluginCall call) {
        if (call.getString("content") == null || call.getString("filename") == null) {
            call.reject("بيانات الملف غير مكتملة");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(call.getString("mimeType", "text/plain"));
        intent.putExtra(Intent.EXTRA_TITLE, call.getString("filename"));
        startActivityForResult(call, intent, "saved");
    }

    @ActivityCallback
    private void saved(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("تم إلغاء حفظ الملف");
            return;
        }
        try (OutputStream stream = getContext().getContentResolver().openOutputStream(result.getData().getData())) {
            if (stream == null) throw new IllegalStateException("No output stream");
            stream.write(call.getString("content", "").getBytes(StandardCharsets.UTF_8));
            call.resolve();
        } catch (Exception error) {
            call.reject("تعذر حفظ الملف في المكان المختار", error);
        }
    }
}
