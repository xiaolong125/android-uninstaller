package io.github.xiaolong125.uninstaller;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.lang.reflect.Method;

/** Queries the exact package and user; OEM command-line lists may omit installed packages. */
public final class InstalledPackageState {
    private InstalledPackageState() {}

    public static boolean isInstalled(Object packageManager, String pkg) throws Exception {
        Class<?> api = Class.forName("android.content.pm.IPackageManager");
        Method query;
        Object flags;
        try {
            query = api.getMethod("getApplicationInfo", String.class, long.class, int.class);
            flags = Long.valueOf(PackageManager.MATCH_UNINSTALLED_PACKAGES);
        } catch (NoSuchMethodException olderApi) {
            query = api.getMethod("getApplicationInfo", String.class, int.class, int.class);
            flags = Integer.valueOf(PackageManager.MATCH_UNINSTALLED_PACKAGES);
        }
        // Include retained system APKs, but distinguish their per-user installed flag.
        // Query failures propagate: an unreadable state must never count as successful removal.
        ApplicationInfo info = (ApplicationInfo) query.invoke(packageManager, pkg, flags, 0);
        if (info == null) return false;
        if (!pkg.equals(info.packageName)) throw new IllegalStateException("系统返回的包名与查询目标不一致");
        return (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
    }
}
