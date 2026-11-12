package io.github.xiaolong125.uninstaller;
import org.junit.Test;
import static org.junit.Assert.*;
public class PackageRulesTest {
 @Test public void searchesChineseName(){assertTrue(PackageRules.matches("微信输入法","com.tencent.wetype","微信"));}
 @Test public void searchesEnglishIgnoringCase(){assertTrue(PackageRules.matches("Google Play Store","com.android.vending"," PLAY STORE "));}
 @Test public void searchesPackage(){assertTrue(PackageRules.matches("相册","com.google.android.apps.photos","APPS.PHOTOS"));}
 @Test public void emptyQueryShowsApps(){assertTrue(PackageRules.matches("应用","com.example.app","  "));}
 @Test public void rejectsShellInjection(){assertFalse(PackageRules.valid("com.example.app; rm -rf /"));assertFalse(PackageRules.valid("com.app\nother"));assertFalse(PackageRules.valid("android"));assertFalse(PackageRules.valid(null));}
 @Test public void checksExactPackage(){assertFalse(PackageRules.containsExact("package:com.example.app.extra\n","com.example.app"));assertTrue(PackageRules.containsExact("package:com.example.app\r\npackage:com.other.app","com.example.app"));}
}
