package io.github.xiaolong125.uninstaller;
interface IUninstallService {
    int getUid() = 0;
    String uninstall(String packageName) = 1;
    void destroy() = 16777114;
}
