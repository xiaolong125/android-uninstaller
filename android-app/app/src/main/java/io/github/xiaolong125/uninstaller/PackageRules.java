package io.github.xiaolong125.uninstaller;
import java.util.Locale;
public final class PackageRules {
    public static boolean valid(String name) { return name != null && name.matches("[A-Za-z0-9_]+([.][A-Za-z0-9_]+)+"); }
    public static boolean matches(String name, String pkg, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(q) || pkg.toLowerCase(Locale.ROOT).contains(q);
    }
    public static boolean containsExact(String output, String pkg) {
        for (String line : output.split("\\r?\\n")) if (line.trim().equals("package:" + pkg)) return true;
        return false;
    }
}
