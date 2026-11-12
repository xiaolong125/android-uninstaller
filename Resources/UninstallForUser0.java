import android.content.pm.VersionedPackage;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.*;
import java.util.concurrent.*;

public final class UninstallForUser0 {
  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]+([.][A-Za-z0-9_]+)+")) {
      throw new IllegalArgumentException("Usage: UninstallForUser0 <package.name>; removes package for user 0, including its user data");
    }
    final String pkg = args[0];
    final String descriptor = "android.content.pm.IPackageDeleteObserver2";
    final CountDownLatch done = new CountDownLatch(1);
    final int[] result = {-999};
    Binder callback = new Binder() {
      @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == IBinder.INTERFACE_TRANSACTION) {
          if (reply != null) reply.writeString(descriptor);
          return true;
        }
        data.enforceInterface(descriptor);
        if (code == 2) {
          String name = data.readString();
          result[0] = data.readInt();
          String message = data.readString();
          System.out.println("onPackageDeleted package=" + name + " result=" + result[0] + " message=" + message);
          if (reply != null) reply.writeNoException();
          done.countDown();
          return true;
        }
        if (code == 1) {
          System.out.println("User action required; no automatic confirmation performed.");
          if (reply != null) reply.writeNoException();
          done.countDown();
          return true;
        }
        return false;
      }
    };
    Class<?> observerClass = Class.forName(descriptor);
    Object observer = Proxy.newProxyInstance(observerClass.getClassLoader(), new Class<?>[]{observerClass}, (proxy, method, values) -> {
      if (method.getName().equals("asBinder")) return callback;
      throw new UnsupportedOperationException(method.getName());
    });
    Object packageBinder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package");
    Object pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, packageBinder);
    Method delete = Class.forName("android.content.pm.IPackageManager").getMethod("deletePackageVersioned", VersionedPackage.class, observerClass, int.class, int.class);
    System.out.println("Requesting removal for package=" + pkg + " user=0 DELETE_SYSTEM_APP=4 uid=" + android.os.Process.myUid());
    delete.invoke(pm, new VersionedPackage(pkg, -1L), observer, 0, 4);
    if (!done.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for uninstall result");
    if (result[0] != 1) throw new IllegalStateException("Uninstall did not succeed: " + result[0]);
    System.out.println("Success");
  }
}
