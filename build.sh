#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
APP="build/Android卸载助手.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources" build/swift-cache
if [ ! -x Resources/adb ]; then
    if command -v adb >/dev/null 2>&1; then
        cp "$(command -v adb)" Resources/adb
    elif [ -x "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb" ]; then
        cp "${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb" Resources/adb
    else
        curl --fail --location https://dl.google.com/android/repository/platform-tools-latest-darwin.zip -o build/platform-tools.zip
        unzip -q -o build/platform-tools.zip -d build
        cp build/platform-tools/adb Resources/adb
        cp build/platform-tools/NOTICE.txt Resources/ADB-NOTICE.txt
    fi
fi
if [ ! -f Resources/AppIcon.icns ]; then
    swift -module-cache-path build/swift-cache tools/MakeIcon.swift build/AppIcon.iconset Resources/AppIcon.icns
fi
swiftc -swift-version 5 -O -module-cache-path build/swift-cache -target arm64-apple-macosx13.0 -framework SwiftUI -framework AppKit Engine.swift App.swift -o "$APP/Contents/MacOS/AndroidUninstaller"
cp Resources/AppIcon.icns Resources/adb Resources/app-labels.jar Resources/uninstall-user0.jar Resources/ADB-NOTICE.txt "$APP/Contents/Resources/"
chmod +x "$APP/Contents/Resources/adb"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleExecutable</key><string>AndroidUninstaller</string>
<key>CFBundleIdentifier</key><string>local.android.user0uninstaller</string>
<key>CFBundleIconFile</key><string>AppIcon</string>
<key>CFBundleName</key><string>Android 卸载助手</string>
<key>CFBundleDisplayName</key><string>Android 卸载助手</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleShortVersionString</key><string>1.2.0</string>
<key>CFBundleVersion</key><string>3</string>
<key>LSMinimumSystemVersion</key><string>13.0</string>
<key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
codesign --force --sign - "$APP/Contents/Resources/adb"
codesign --force --sign - "$APP"
echo "已构建：$APP"
