package app.masroufy.personal;

import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@CapacitorPlugin(name = "LocalFiles")
public class LocalFilesPlugin extends Plugin {
    /** اسم ملف بسيط بس — لا مسارات ولا «..». */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]{1,120}$");

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

    /**
     * كتابة من غير نافذة في مجلد التطبيق — النافذة بترمي الـWebView للخلفية وتقطع
     * أي عملية شغالة (HANDOVER «إصلاح البيانات القديمة اتقطع»).
     */
    @PluginMethod
    public void writeAppFile(PluginCall call) {
        String name = call.getString("name");
        String content = call.getString("content");
        if (name == null || content == null || !SAFE_NAME.matcher(name).matches()) {
            call.reject("اسم الملف أو محتواه غير صالح");
            return;
        }
        File file = new File(backupDir(), name);
        try (FileOutputStream out = new FileOutputStream(file, Boolean.TRUE.equals(call.getBoolean("append", false)))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            JSObject ret = new JSObject();
            ret.put("location", file.getAbsolutePath());
            call.resolve(ret);
        } catch (Exception error) {
            call.reject("تعذر حفظ النسخة على الجهاز", error);
        }
    }

    @PluginMethod
    public void appFileSize(PluginCall call) {
        String name = call.getString("name");
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            call.reject("اسم الملف غير صالح");
            return;
        }
        File file = new File(backupDir(), name);
        JSObject ret = new JSObject();
        ret.put("bytes", file.isFile() ? file.length() : 0);
        ret.put("location", file.getAbsolutePath());
        call.resolve(ret);
    }

    /** مجلد التطبيق على التخزين (بيتشاف من الكمبيوتر بكابل)، وإلا الداخلي. بيتمسح مع إلغاء التثبيت. */
    private File backupDir() {
        File base = getContext().getExternalFilesDir(null);
        if (base == null) base = getContext().getFilesDir();
        File dir = new File(base, "backups");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }
}
