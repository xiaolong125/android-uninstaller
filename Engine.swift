import Foundation

struct ToolError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}
struct AppMetadata {
    let name: String
    let icon: Data?
}
struct CommandResult {
    let code: Int32
    let output: String
}
struct Device: Identifiable {
    let id: String
    let state: String
    let model: String
    var label: String { "\(model) · \(id)" + (state == "device" ? "" : " · \(state)") }
}
final class ADBEngine {
    let adb: URL
    let jar: URL
    let remote = "/data/local/tmp/uninstall-user0.jar"
    let log: (String) -> Void
    init(adb: URL, jar: URL, log: @escaping (String) -> Void = { _ in }) {
        self.adb = adb; self.jar = jar; self.log = log
    }
    static func validPackage(_ name: String) -> Bool {
        name.range(of: "^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$", options: .regularExpression) != nil
    }
    func run(_ arguments: [String], timeout: TimeInterval = 40, logOutput: Bool = true) throws -> CommandResult {
        log("$ adb " + arguments.joined(separator: " "))
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        FileManager.default.createFile(atPath: outputURL.path, contents: nil)
        let output = try FileHandle(forWritingTo: outputURL)
        defer { try? output.close(); try? FileManager.default.removeItem(at: outputURL) }
        let process = Process()
        process.executableURL = adb
        process.arguments = arguments
        process.standardOutput = output
        process.standardError = output
        process.standardInput = FileHandle.nullDevice
        try process.run()
        let deadline = Date().addingTimeInterval(timeout)
        while process.isRunning && Date() < deadline { Thread.sleep(forTimeInterval: 0.05) }
        let timedOut = process.isRunning
        if timedOut {
            process.terminate()
            let stopDeadline = Date().addingTimeInterval(2)
            while process.isRunning && Date() < stopDeadline { Thread.sleep(forTimeInterval: 0.05) }
            if process.isRunning { kill(process.processIdentifier, SIGKILL) }
        }
        process.waitUntilExit()
        let text = String(decoding: (try? Data(contentsOf: outputURL)) ?? Data(), as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        if logOutput && !text.isEmpty { log(text) }
        if timedOut { throw ToolError(message: "ADB 命令超时，请检查设备连接。") }
        return CommandResult(code: process.terminationStatus, output: text)
    }
    func checked(_ arguments: [String], logOutput: Bool = true) throws -> String {
        let result = try run(arguments, logOutput: logOutput)
        guard result.code == 0 else { throw ToolError(message: result.output.isEmpty ? "ADB 执行失败（\(result.code)）" : result.output) }
        return result.output
    }
    func devices() throws -> [Device] {
        let result = try checked(["devices", "-l"])
        return result.components(separatedBy: .newlines).compactMap { line in
            let parts = line.split(whereSeparator: { $0.isWhitespace }).map(String.init)
            guard parts.count >= 2, !line.hasPrefix("List "), !line.hasPrefix("*"), ["device", "unauthorized", "offline", "recovery", "sideload", "bootloader", "no"].contains(parts[1]) else { return nil }
            let model = parts.first(where: { $0.hasPrefix("model:") }).map { String($0.dropFirst(6)).replacingOccurrences(of: "_", with: " ") } ?? "Android 设备"
            return Device(id: parts[0], state: parts[1], model: model)
        }
    }
    func packages(serial: String, filter: String? = nil) throws -> [String] {
        var args = ["-s", serial, "shell", "pm", "list", "packages", "--user", "0"]
        if let filter = filter { args.append(filter) }
        let result = try checked(args)
        let lines = result.components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        // A permission/service error may have exit status 0; never interpret it as an empty package list.
        guard lines.allSatisfy({ $0.hasPrefix("package:") && String($0.dropFirst(8)).trimmingCharacters(in: .whitespacesAndNewlines).range(of: "^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$", options: .regularExpression) != nil }) else {
            throw ToolError(message: "无法读取应用列表：\(result)")
        }
        return lines.map { String($0.dropFirst(8)).trimmingCharacters(in: .whitespacesAndNewlines) }.sorted()
    }
    func appLabels(serial: String) throws -> [String: AppMetadata] {
        let prefix = ["-s", serial]
        let helper = jar.deletingLastPathComponent().appendingPathComponent("app-labels.jar")
        let destination = "/data/local/tmp/mac-uninstaller-app-labels.jar"
        defer {
            do { _ = try checked(prefix + ["shell", "rm", "-f", destination]) }
            catch { log("名称读取工具清理失败，请重新连接设备后点击“清理工具”：" + error.localizedDescription) }
        }
        _ = try checked(prefix + ["push", helper.path, destination])
        let output = try checked(prefix + ["shell", "CLASSPATH=\(destination)", "app_process", "/system/bin", "AppLabels"], logOutput: false)
        guard let line = output.components(separatedBy: .newlines).first(where: { $0.hasPrefix("APP_LABELS_JSON=") }),
              let data = String(line.dropFirst("APP_LABELS_JSON=".count)).data(using: .utf8),
              let entries = try JSONSerialization.jsonObject(with: data) as? [[String: String]] else {
            throw ToolError(message: "手机未返回有效的应用名称数据。")
        }
        var labels: [String: AppMetadata] = [:]
        for entry in entries {
            if let package = entry["package"], let name = entry["name"], !name.isEmpty { labels[package] = AppMetadata(name: name, icon: entry["icon"].flatMap { Data(base64Encoded: $0) }) }
        }
        log("已读取 \(labels.count) 个应用的名称和图标。")
        return labels
    }
    func uninstall(serial: String, package: String) throws -> String {
        guard Self.validPackage(package) else { throw ToolError(message: "请输入完整且有效的包名，例如 com.example.app。") }
        let prefix = ["-s", serial]
        var outcome: Result<String, Error>
        do {
            guard try packages(serial: serial, filter: package).contains(package) else {
                return "用户 0 未安装 \(package)，无需卸载。"
            }
            log("步骤 1/4 · 上传工具")
            _ = try checked(prefix + ["push", jar.path, remote])
            log("步骤 2/4 · 卸载用户 0 的应用")
            _ = try checked(prefix + ["shell", "CLASSPATH=\(remote)", "app_process", "/system/bin", "UninstallForUser0", package])
            log("步骤 3/4 · 核对完整包名")
            let remaining = try packages(serial: serial, filter: package)
            guard !remaining.contains(package) else { throw ToolError(message: "核对未通过：用户 0 仍安装此应用。") }
            outcome = .success("卸载成功，已确认用户 0 不再安装 \(package)。")
        } catch { outcome = .failure(error) }
        log("步骤 4/4 · 清理设备上的工具")
        do {
            _ = try checked(prefix + ["shell", "rm", "-f", remote])
        } catch {
            switch outcome {
            case .success(let message): throw ToolError(message: message + "\n但清理失败：\(error.localizedDescription)\n请重新连接后点击“清理工具”。")
            case .failure(let original): throw ToolError(message: original.localizedDescription + "\n清理也失败：\(error.localizedDescription)\n请重新连接后点击“清理工具”。")
            }
        }
        return try outcome.get()
    }
    func cleanup(serial: String) throws {
        _ = try checked(["-s", serial, "shell", "rm", "-f", remote, "/data/local/tmp/mac-uninstaller-app-labels.jar"])
    }
}
