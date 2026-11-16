package io.github.xiaolong125.uninstaller;

import android.content.Context;
import android.content.pm.VersionedPackage;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Runs only inside the Shizuku shell process, never as an exported Android service. */
public final class UninstallService extends IUninstallService.Stub {
    private final int ownerUid;
    public UninstallService(Context context) throws Exception {
        ownerUid = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid;
    }
    private void enforceCaller() {
        int uid = Binder.getCallingUid();
        if (uid != ownerUid && uid != 2000 && uid != 0) throw new SecurityException("调用方未获授权");
    }
    @Override public int getUid() { enforceCaller(); return android.os.Process.myUid(); }
    @Override public void destroy() { enforceCaller(); System.exit(0); }
    private Object packageManager() throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package");
        return Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
    @Override public synchronized String uninstall(String pkg) {
        enforceCaller();
        try {
            if (!PackageRules.valid(pkg)) throw new IllegalArgumentException("包名无效");
            if (pkg.equals(BuildConfig.APPLICATION_ID) || pkg.equals("moe.shizuku.privileged.api")) throw new IllegalArgumentException("不能卸载本工具或 Shizuku");
            int uid = android.os.Process.myUid();
            if (uid != 2000 && uid != 0) throw new SecurityException("服务未取得 shell 权限");
            Object pm = packageManager();
            if (!InstalledPackageState.isInstalled(pm, pkg)) return "ALREADY_ABSENT\n用户 0 未安装此应用，无需卸载。";
            final String descriptor = "android.content.pm.IPackageDeleteObserver2";
            final CountDownLatch done = new CountDownLatch(1);
            final int[] result = {-999};
            final String[] detail = {""};
            Binder callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                    if (code == IBinder.INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(descriptor); return true; }
                    data.enforceInterface(descriptor);
                    if (code == 2) {
                        data.readString(); result[0] = data.readInt(); detail[0] = data.readString();
                        if (reply != null) reply.writeNoException(); done.countDown(); return true;
                    }
                    if (code == 1) { detail[0] = "系统要求用户操作，未执行自动确认。"; if (reply != null) reply.writeNoException(); done.countDown(); return true; }
                    return false;
                }
            };
            Class<?> observerClass = Class.forName(descriptor);
            Object observer = Proxy.newProxyInstance(observerClass.getClassLoader(), new Class<?>[]{observerClass}, (proxy, method, values) -> {
                if (method.getName().equals("asBinder")) return callback;
                throw new UnsupportedOperationException(method.getName());
            });
            Class.forName("android.content.pm.IPackageManager").getMethod("deletePackageVersioned", VersionedPackage.class, observerClass, int.class, int.class)
                .invoke(pm, new VersionedPackage(pkg, -1L), observer, 0, 4);
            if (!done.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("等待卸载结果超时，请刷新列表核对应用状态。");
            if (result[0] != 1) throw new IllegalStateException("卸载未成功，系统返回 " + result[0] + "；" + detail[0]);
            if (InstalledPackageState.isInstalled(pm, pkg)) throw new IllegalStateException("核对失败，用户 0 仍安装此包。");
            return "SUCCESS\n卸载成功，已确认用户 0 不再安装 " + pkg + "。";
        } catch (Exception error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException)error).getTargetException() : error;
            return "ERROR\n" + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }
}
