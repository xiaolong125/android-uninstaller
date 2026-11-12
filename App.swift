import SwiftUI
import AppKit

final class AppModel: ObservableObject {
    static let shared = AppModel()
    @Published var devices: [Device] = []
    @Published var serial = ""
    @Published var packages: [String] = []
    @Published var labels: [String: String] = [:]
    @Published var icons: [String: NSImage] = [:]
    @Published var package = ""
    @Published var search = ""
    @Published var busy = false
    @Published var status = "连接 Android 设备后，点击刷新设备。"
    @Published var logs = ""
    @Published var failed = false
    var available: Bool { devices.contains { $0.id == serial && $0.state == "device" } }
    var filtered: [String] {
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        return packages.filter { query.isEmpty || $0.localizedCaseInsensitiveContains(query) || (labels[$0]?.localizedCaseInsensitiveContains(query) ?? false) }
    }
    func append(_ line: String) {
        let timestamp = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        logs += "[\(timestamp)] \(line)\n"
    }
    func execute(_ work: @escaping (ADBEngine) throws -> String) {
        guard !busy else { return }
        busy = true; failed = false
        let resources = Bundle.main.resourceURL!
        let engine = ADBEngine(adb: resources.appendingPathComponent("adb"), jar: resources.appendingPathComponent("uninstall-user0.jar")) { line in
            DispatchQueue.main.async { self.append(line) }
        }
        DispatchQueue.global(qos: .userInitiated).async {
            let result = Result { try work(engine) }
            DispatchQueue.main.async {
                self.busy = false
                switch result {
                case .success(let message): self.status = message; self.append(message)
                case .failure(let error): self.failed = true; self.status = error.localizedDescription; self.append("错误：" + error.localizedDescription)
                }
            }
        }
    }
    func refresh() {
        status = "正在查找设备…"
        execute { engine in
            let devices = try engine.devices()
            DispatchQueue.main.async {
                let previous = self.serial
                self.devices = devices
                if !devices.contains(where: { $0.id == self.serial }) { self.serial = devices.first(where: { $0.state == "device" })?.id ?? devices.first?.id ?? "" }
                if previous != self.serial { self.packages = []; self.labels = [:]; self.icons = [:]; self.package = "" }
            }
            return devices.isEmpty ? "未找到设备。请连接 USB 并开启 USB 调试。" : "找到 \(devices.count) 台设备。若显示 unauthorized，请在手机上允许 USB 调试。"
        }
    }
    func loadPackages() {
        let target = serial
        status = "正在读取用户 0 的应用…"
        execute { engine in
            let packages = try engine.packages(serial: target)
            DispatchQueue.main.async { self.packages = packages; self.labels = [:]; self.icons = [:]; self.status = "正在读取应用名称和图标…" }
            do {
                let labels = try engine.appLabels(serial: target)
                let icons = labels.compactMapValues { $0.icon.flatMap { NSImage(data: $0) } }
                DispatchQueue.main.async { self.labels = labels.mapValues { $0.name }; self.icons = icons }
                let count = packages.filter { labels[$0] != nil }.count
                return "已读取 \(packages.count) 个应用及 \(count) 个名称、\(icons.count) 个图标，可按应用名称或包名搜索。"
            } catch {
                return "已读取 \(packages.count) 个应用，但名称读取失败，暂时仅支持包名搜索：\(error.localizedDescription)"
            }
        }
    }
    func uninstall() {
        let target = serial, name = package.trimmingCharacters(in: .whitespacesAndNewlines)
        status = "正在卸载 \(name)…"
        execute { engine in
            let result = try engine.uninstall(serial: target, package: name)
            DispatchQueue.main.async { self.packages.removeAll { $0 == name } }
            return result
        }
    }
    func cleanup() {
        let target = serial
        execute { engine in try engine.cleanup(serial: target); return "设备上的工具已清理。" }
    }
}

struct ContentView: View {
    @StateObject private var model = AppModel.shared
    @State private var confirm = false
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 14) {
                Image(systemName: "app.badge.checkmark").font(.system(size: 34)).foregroundStyle(.blue)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Android 卸载助手").font(.title2.bold())
                    Text("上传 · 卸载 · 核对 · 清理").foregroundStyle(.secondary)
                }
                Spacer()
                Text("用户 0").font(.caption.bold()).padding(8).background(.blue.opacity(0.1), in: Capsule())
            }
            GroupBox {
                HStack {
                    Picker("设备", selection: $model.serial) {
                        Text("请选择设备").tag("")
                        ForEach(model.devices) { device in Text(device.label).tag(device.id) }
                    }.disabled(model.busy)
                    Button("刷新设备", systemImage: "arrow.clockwise", action: model.refresh).disabled(model.busy)
                }.padding(6)
            }
            HSplitView {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("已装应用").font(.headline)
                        Spacer()
                        Button("读取列表", action: model.loadPackages).disabled(model.busy || !model.available)
                    }
                    TextField("搜索应用名称或包名", text: $model.search).textFieldStyle(.roundedBorder)
                    List(model.filtered, id: \.self, selection: $model.package) { name in
                        HStack(spacing: 10) {
                            if let icon = model.icons[name] {
                                Image(nsImage: icon).resizable().interpolation(.high).scaledToFit().frame(width: 36, height: 36)
                            } else {
                                Image(systemName: "app.fill").font(.system(size: 28)).foregroundStyle(.secondary).frame(width: 36, height: 36)
                            }
                            VStack(alignment: .leading, spacing: 3) {
                            Text(model.labels[name] ?? name).lineLimit(1)
                            if let label = model.labels[name], label != name {
                                Text(name).font(.system(size: 10, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                            }
                            }
                        }.padding(.vertical, 3).tag(name)
                    }.disabled(model.busy).overlay {
                        if model.packages.isEmpty { Text("点击“读取列表”查看用户 0 的应用").font(.caption).foregroundStyle(.secondary).allowsHitTesting(false) }
                    }
                }.frame(minWidth: 290, idealWidth: 340).padding(.trailing, 12)
                VStack(alignment: .leading, spacing: 12) {
                    Text("卸载应用").font(.headline)
                    TextField("完整包名，如 com.example.app", text: $model.package).textFieldStyle(.roundedBorder).disabled(model.busy)
                    Label("仅处理用户 0，并删除该用户的应用数据。系统应用可能仍保留在系统分区。", systemImage: "info.circle")
                        .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    HStack {
                        Button(role: .destructive) { confirm = true } label: { Label("卸载并清理", systemImage: "trash") }
                            .buttonStyle(.borderedProminent).tint(.red)
                            .disabled(model.busy || !model.available || !ADBEngine.validPackage(model.package.trimmingCharacters(in: .whitespacesAndNewlines)))
                        Button("清理工具", action: model.cleanup).disabled(model.busy || !model.available)
                    }
                    Divider()
                    HStack(alignment: .top) {
                        if model.busy { ProgressView().controlSize(.small) }
                        Text(model.status).font(.callout).foregroundStyle(model.failed ? Color.red : Color.secondary).textSelection(.enabled).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Text("已内置 ADB 和卸载工具，无需手动上传。").font(.caption).foregroundStyle(.secondary)
                }.frame(minWidth: 320, maxWidth: .infinity, alignment: .leading).padding(.leading, 12)
            }.frame(minHeight: 240)
            HStack {
                Text("执行日志").font(.headline)
                Spacer()
                Button("复制日志") { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(model.logs, forType: .string) }
                Button("清空") { model.logs = "" }.disabled(model.busy)
            }
            ScrollViewReader { proxy in
                ScrollView {
                    Text(model.logs.isEmpty ? "等待操作…" : model.logs).font(.system(size: 11, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading).padding(10)
                    Color.clear.frame(height: 1).id("end")
                }.background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                    .onChange(of: model.logs) { _ in proxy.scrollTo("end", anchor: .bottom) }
            }.frame(height: 160)
        }.padding(24).frame(minWidth: 800, minHeight: 650)
            .onAppear { model.refresh() }
            .onChange(of: model.serial) { _ in model.packages = []; model.labels = [:]; model.icons = [:]; model.package = "" }
            .alert("确认卸载？", isPresented: $confirm) {
                Button("取消", role: .cancel) {}
                Button("确认卸载", role: .destructive, action: model.uninstall)
            } message: {
                Text("设备：\(model.serial)\n包名：\(model.package.trimmingCharacters(in: .whitespacesAndNewlines))\n\n将卸载用户 0 的此应用并删除其应用数据。此操作无法由本工具撤销。")
            }
    }
}

@main struct UninstallerApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene {
        WindowGroup { ContentView() }
            .windowStyle(.titleBar)
            .commands { CommandGroup(replacing: .newItem) {} }
    }
}
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) { NSApp.setActivationPolicy(.regular); NSApp.activate(ignoringOtherApps: true) }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard AppModel.shared.busy else { return .terminateNow }
        let alert = NSAlert()
        alert.messageText = "退出卸载助手？"
        alert.informativeText = "请等待当前操作结束后再退出，以便完成核对和清理。"
        alert.addButton(withTitle: "退出"); alert.addButton(withTitle: "返回")
        return alert.runModal() == .alertFirstButtonReturn ? .terminateNow : .terminateCancel
    }
}
