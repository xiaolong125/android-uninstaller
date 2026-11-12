import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only helper run as the ADB shell user; no installed companion app needed. */
public final class AppLabels {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        PackageManager pm = context.getPackageManager();
        JSONArray apps = new JSONArray();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            JSONObject item = new JSONObject();
            item.put("package", info.packageName);
            try { item.put("name", pm.getApplicationLabel(info).toString()); }
            catch (Exception ignored) { item.put("name", info.packageName); }
            try {
                Drawable icon = pm.getApplicationIcon(info);
                Bitmap bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
                try {
                    Canvas canvas = new Canvas(bitmap);
                    icon.setBounds(0, 0, 72, 72);
                    icon.draw(canvas);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
                    item.put("icon", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                } finally { bitmap.recycle(); }
            } catch (Exception ignored) { /* Keep the name even if an icon cannot be loaded. */ }
            apps.put(item);
        }
        System.out.println("APP_LABELS_JSON=" + apps.toString());
    }
}
