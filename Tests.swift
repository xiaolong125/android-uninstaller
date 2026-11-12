import Foundation

@main struct EngineTests {
    static func main() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("uninstaller-tests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        var passed = 0
        for scenario in ["success", "uninstall_fail", "verify_fail", "cleanup_fail", "query_error", "absent", "push_fail", "similar", "both_fail"] {
            let folder = root.appendingPathComponent(scenario)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let mock = folder.appendingPathComponent("adb")
            let script = """
            #!/bin/sh
            cd "$(dirname "$0")"
            echo "$*" >> calls
            case "$*" in
              *"pm list packages"*)
                if [ "\(scenario)" = query_error ]; then echo 'Error: permission denied'; exit 0; fi
                if [ "\(scenario)" = absent ]; then exit 0; fi
                if [ ! -f queried ]; then touch queried; echo package:com.example.app; echo package:android
                elif [ "\(scenario)" = verify_fail ]; then echo package:com.example.app
                elif [ "\(scenario)" = similar ]; then echo package:com.example.app.extra
                fi ;;
              *" push "*) if [ "\(scenario)" = push_fail ]; then echo 'push failed'; exit 1; fi ;;
              *app_process*) if [ "\(scenario)" = uninstall_fail ] || [ "\(scenario)" = both_fail ]; then echo 'uninstall failed'; exit 1; fi ;;
              *"rm -f"*) if [ "\(scenario)" = cleanup_fail ] || [ "\(scenario)" = both_fail ]; then echo 'cleanup failed'; exit 1; fi ;;
            esac
            """
            try script.write(to: mock, atomically: true, encoding: .utf8)
            try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: mock.path)
            let engine = ADBEngine(adb: mock, jar: folder.appendingPathComponent("tool.jar"))
            let successExpected = ["success", "absent", "similar"].contains(scenario)
            do {
                _ = try engine.uninstall(serial: "test-device", package: "com.example.app")
                precondition(successExpected, "Expected failure: \(scenario)")
            } catch {
                precondition(!successExpected, "Unexpected error: \(scenario): \(error)")
                if scenario == "both_fail" { precondition(error.localizedDescription.contains("uninstall failed") && error.localizedDescription.contains("cleanup failed")) }
            }
            let calls = try String(contentsOf: folder.appendingPathComponent("calls"), encoding: .utf8)
            if scenario != "absent" { precondition(calls.contains("rm -f"), "Cleanup missing: \(scenario)") }
            if scenario == "absent" || scenario == "query_error" { precondition(!calls.contains("app_process")) }
            passed += 1
        }
        let invalid = ADBEngine(adb: root.appendingPathComponent("missing"), jar: root)
        do { _ = try invalid.uninstall(serial: "device", package: "com.app;rm -rf /"); fatalError("Invalid package accepted") }
        catch { precondition(error.localizedDescription.contains("有效")) }
        passed += 1
        // A hanging child must be terminated within the configured timeout.
        let slow = root.appendingPathComponent("slow")
        try "#!/bin/sh\nexec /bin/sleep 10\n".write(to: slow, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: slow.path)
        let start = Date()
        do { _ = try ADBEngine(adb: slow, jar: root).run([], timeout: 0.1); fatalError("Timeout missing") }
        catch { precondition(error.localizedDescription.contains("超时")); precondition(Date().timeIntervalSince(start) < 4) }
        passed += 1
        print("PASS: \(passed) workflow tests; no Android device used.")
    }
}
