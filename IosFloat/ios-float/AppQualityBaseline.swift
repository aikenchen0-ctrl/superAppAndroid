import UIKit
import QuartzCore
import Darwin.Mach
import ObjectiveC
import os

final class AppQualityBaseline: NSObject {
    static let shared = AppQualityBaseline()

    private static let performanceLog = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "local.ios-float",
        category: "PerformanceBaseline"
    )
    private static let maximumFrameSamples = 7_200
    private static let frameSampleTrimCount = 1_200
    private let processStartTime = CACurrentMediaTime()
    private var didFinishLaunchTime: CFTimeInterval?
    private var firstSceneVisibleTime: CFTimeInterval?
    private var displayLink: CADisplayLink?
    private var lastFrameTimestamp: CFTimeInterval = 0
    private var lastDisplayTickTimestamp: CFTimeInterval = 0
    private var lastDebugReportTimestamp: CFTimeInterval = 0
    private var frameCount = 0
    private var fpsSamples: [Double] = []
    private var frameDurations: [CFTimeInterval] = []
    private var scrollEventCount = 0
    private var lastScrollEventTime: Date?
    private weak var installedWindow: UIWindow?

    private override init() {
        super.init()
    }

    func markDidFinishLaunching() {
        didFinishLaunchTime = CACurrentMediaTime()
    }

    func install(on window: UIWindow) {
        installedWindow = window
        markFirstSceneVisibleIfNeeded()
        startFPSMonitor()
        applyAccessibilityBaseline(in: window)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(accessibilitySettingsChanged),
            name: UIContentSizeCategory.didChangeNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(accessibilitySettingsChanged),
            name: UIAccessibility.voiceOverStatusDidChangeNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(accessibilitySettingsChanged),
            name: UIAccessibility.reduceMotionStatusDidChangeNotification,
            object: nil
        )
    }

    func recordScrollEvent() {
        scrollEventCount += 1
        lastScrollEventTime = Date()
    }

    func makeReport() -> AppQualityReport {
        let now = CACurrentMediaTime()
        let coldStart = firstSceneVisibleTime.map { $0 - processStartTime }
            ?? didFinishLaunchTime.map { $0 - processStartTime }
            ?? now - processStartTime
        let averageFPS = fpsSamples.isEmpty ? 0 : fpsSamples.reduce(0, +) / Double(fpsSamples.count)
        let sortedFrameDurations = frameDurations.sorted()
        let p95FrameDuration = percentile(0.95, sortedValues: sortedFrameDurations)
        let over33msCount = frameDurations.reduce(into: 0) { count, duration in
            if duration > 1.0 / 30.0 { count += 1 }
        }
        let over100msCount = frameDurations.reduce(into: 0) { count, duration in
            if duration > 0.1 { count += 1 }
        }
        return AppQualityReport(
            coldStartSeconds: coldStart,
            averageFPS: averageFPS,
            latestFPS: fpsSamples.last ?? 0,
            frameSampleCount: frameDurations.count,
            p95FrameDurationMilliseconds: p95FrameDuration * 1_000,
            maximumFrameDurationMilliseconds: (frameDurations.max() ?? 0) * 1_000,
            over33msFramePercentage: frameDurations.isEmpty
                ? 0
                : Double(over33msCount) / Double(frameDurations.count) * 100,
            over100msFrameCount: over100msCount,
            memoryFootprintMB: currentMemoryFootprintMB(),
            availableMemoryMB: availableMemoryMB(),
            physicalMemoryGB: Double(ProcessInfo.processInfo.physicalMemory) / 1024 / 1024 / 1024,
            lowEndModeEnabled: isLowEndDevice,
            reduceMotionEnabled: UIAccessibility.isReduceMotionEnabled,
            voiceOverEnabled: UIAccessibility.isVoiceOverRunning,
            preferredContentSizeCategory: UIApplication.shared.preferredContentSizeCategory.rawValue,
            scrollEventCount: scrollEventCount,
            lastScrollEventTime: lastScrollEventTime
        )
    }

    private var isLowEndDevice: Bool {
        ProcessInfo.processInfo.physicalMemory <= 3_500_000_000
            || ProcessInfo.processInfo.thermalState == .serious
            || ProcessInfo.processInfo.thermalState == .critical
    }

    private func markFirstSceneVisibleIfNeeded() {
        guard firstSceneVisibleTime == nil else { return }
        firstSceneVisibleTime = CACurrentMediaTime()
    }

    private func startFPSMonitor() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(displayLinkTick(_:)))
        let maximumFPS = installedWindow?.screen.maximumFramesPerSecond
            ?? UIScreen.main.maximumFramesPerSecond
        link.preferredFrameRateRange = CAFrameRateRange(
            minimum: Float(min(60, maximumFPS)),
            maximum: Float(maximumFPS),
            preferred: Float(maximumFPS)
        )
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    @objc private func displayLinkTick(_ link: CADisplayLink) {
        guard lastDisplayTickTimestamp > 0 else {
            lastDisplayTickTimestamp = link.timestamp
            lastFrameTimestamp = link.timestamp
            lastDebugReportTimestamp = link.timestamp
            return
        }

        let frameDuration = link.timestamp - lastDisplayTickTimestamp
        lastDisplayTickTimestamp = link.timestamp
        if UIApplication.shared.applicationState == .active, frameDuration > 0, frameDuration < 1 {
            frameDurations.append(frameDuration)
            if frameDurations.count > Self.maximumFrameSamples {
                frameDurations.removeFirst(Self.frameSampleTrimCount)
            }
            if frameDuration > 0.1 {
                PerformanceSignpost.event("FrameOver100ms")
            } else if frameDuration > 1.0 / 30.0 {
                PerformanceSignpost.event("FrameOver33ms")
            }
        }

        frameCount += 1
        let elapsed = link.timestamp - lastFrameTimestamp
        if elapsed >= 1 {
            let fps = Double(frameCount) / elapsed
            fpsSamples.append(fps)
            if fpsSamples.count > 30 {
                fpsSamples.removeFirst(fpsSamples.count - 30)
            }
            frameCount = 0
            lastFrameTimestamp = link.timestamp
        }

#if DEBUG
        if ChatDataFactory.isPerformanceScenarioRequested,
           link.timestamp - lastDebugReportTimestamp >= 5 {
            emitDebugFrameSummary()
            lastDebugReportTimestamp = link.timestamp
        }
#endif
    }

    private func percentile(_ percentile: Double, sortedValues: [CFTimeInterval]) -> CFTimeInterval {
        guard !sortedValues.isEmpty else { return 0 }
        let rank = Int(ceil(Double(sortedValues.count) * percentile)) - 1
        return sortedValues[min(max(rank, 0), sortedValues.count - 1)]
    }

#if DEBUG
    private func emitDebugFrameSummary() {
        guard !frameDurations.isEmpty else { return }
        let totalDuration = frameDurations.reduce(0, +)
        let averageFPS = totalDuration > 0 ? Double(frameDurations.count) / totalDuration : 0
        let over33msCount = frameDurations.reduce(into: 0) { count, duration in
            if duration > 1.0 / 30.0 { count += 1 }
        }
        let over100msCount = frameDurations.reduce(into: 0) { count, duration in
            if duration > 0.1 { count += 1 }
        }
        let over33msPercentage = Double(over33msCount) / Double(frameDurations.count) * 100
        Self.performanceLog.notice(
            "frames=\(self.frameDurations.count) averageFPS=\(averageFPS, format: .fixed(precision: 2)) over33msPercent=\(over33msPercentage, format: .fixed(precision: 2)) over100ms=\(over100msCount) maxFrameMS=\(((self.frameDurations.max() ?? 0) * 1_000), format: .fixed(precision: 2))"
        )
    }
#endif

    @objc private func accessibilitySettingsChanged() {
        guard let installedWindow else { return }
        applyAccessibilityBaseline(in: installedWindow)
    }

    private func applyAccessibilityBaseline(in view: UIView) {
        view.subviews.forEach { applyAccessibilityBaseline(in: $0) }

        if let label = view as? UILabel {
            label.adjustsFontForContentSizeCategory = true
            if label.numberOfLines == 1 {
                label.adjustsFontSizeToFitWidth = true
                label.minimumScaleFactor = min(label.minimumScaleFactor == 0 ? 0.82 : label.minimumScaleFactor, 0.82)
            }
            if label.accessibilityLabel?.isEmpty ?? true, let text = label.text, !text.isEmpty {
                label.accessibilityLabel = text
            }
        } else if let textField = view as? UITextField {
            textField.adjustsFontForContentSizeCategory = true
            if textField.accessibilityLabel?.isEmpty ?? true {
                textField.accessibilityLabel = textField.placeholder ?? "输入框"
            }
        } else if let textView = view as? UITextView {
            textView.adjustsFontForContentSizeCategory = true
            if textView.accessibilityLabel?.isEmpty ?? true {
                textView.accessibilityLabel = "文本输入"
            }
        } else if let button = view as? UIButton {
            button.titleLabel?.adjustsFontForContentSizeCategory = true
            if button.accessibilityLabel?.isEmpty ?? true {
                let title = button.title(for: .normal)
                    ?? button.configuration?.title
                    ?? button.titleLabel?.text
                if let title, !title.isEmpty {
                    button.accessibilityLabel = title
                }
            }
        } else if let imageView = view as? UIImageView {
            if imageView.accessibilityLabel?.isEmpty ?? true {
                imageView.accessibilityLabel = "图片"
            }
        }

        if UIAccessibility.isReduceMotionEnabled {
            view.layer.removeAllAnimations()
        }
    }

    private func currentMemoryFootprintMB() -> Double {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.stride / MemoryLayout<natural_t>.stride)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        guard result == KERN_SUCCESS else { return 0 }
        return Double(info.phys_footprint) / 1024 / 1024
    }

    private func availableMemoryMB() -> Double {
        if #available(iOS 13.0, *) {
            return Double(os_proc_available_memory()) / 1024 / 1024
        }
        return 0
    }
}

struct AppQualityReport {
    let coldStartSeconds: Double
    let averageFPS: Double
    let latestFPS: Double
    let frameSampleCount: Int
    let p95FrameDurationMilliseconds: Double
    let maximumFrameDurationMilliseconds: Double
    let over33msFramePercentage: Double
    let over100msFrameCount: Int
    let memoryFootprintMB: Double
    let availableMemoryMB: Double
    let physicalMemoryGB: Double
    let lowEndModeEnabled: Bool
    let reduceMotionEnabled: Bool
    let voiceOverEnabled: Bool
    let preferredContentSizeCategory: String
    let scrollEventCount: Int
    let lastScrollEventTime: Date?

    var rows: [(String, String, String)] {
        [
            ("冷启动", String(format: "%.2fs", coldStartSeconds), coldStartSeconds <= 2.2 ? "正常" : "需优化"),
            ("滚动 FPS", latestFPS > 0 ? String(format: "%.0f / %.0f", latestFPS, averageFPS) : "采集中", latestFPS >= 50 || latestFPS == 0 ? "正常" : "偏低"),
            ("帧耗时 p95", frameSampleCount > 0 ? String(format: "%.2fms", p95FrameDurationMilliseconds) : "采集中", p95FrameDurationMilliseconds <= 18 || frameSampleCount == 0 ? "正常" : "偏高"),
            (">33ms 帧", frameSampleCount > 0 ? String(format: "%.2f%%", over33msFramePercentage) : "采集中", over33msFramePercentage < 1 || frameSampleCount == 0 ? "正常" : "偏高"),
            (">100ms 帧", "\(over100msFrameCount)", over100msFrameCount == 0 ? "正常" : "需优化"),
            ("最大帧耗时", frameSampleCount > 0 ? String(format: "%.2fms", maximumFrameDurationMilliseconds) : "采集中", maximumFrameDurationMilliseconds <= 100 || frameSampleCount == 0 ? "正常" : "偏高"),
            ("内存占用", memoryFootprintMB > 0 ? String(format: "%.0f MB", memoryFootprintMB) : "暂不可用", memoryFootprintMB < 650 || memoryFootprintMB == 0 ? "正常" : "偏高"),
            ("可用内存", availableMemoryMB > 0 ? String(format: "%.0f MB", availableMemoryMB) : "暂不可用", availableMemoryMB > 250 || availableMemoryMB == 0 ? "正常" : "偏低"),
            ("低端机模式", lowEndModeEnabled ? "已启用" : "未启用", "自动"),
            ("VoiceOver", voiceOverEnabled ? "已开启" : "未开启", "支持"),
            ("动态字体", preferredContentSizeCategory, "支持"),
            ("减少动态效果", reduceMotionEnabled ? "已开启" : "未开启", "支持"),
            ("滚动监听", "\(scrollEventCount) 次", lastScrollEventTime.map { ChatMessage.displayTimestamp(for: $0) } ?? "暂无")
        ]
    }
}

final class AppQualityReportViewController: UIViewController {
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private var report = AppQualityBaseline.shared.makeReport()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.98, alpha: 1)
        configureHeader()
        configureTable()
    }

    private func configureHeader() {
        let header = UIView()
        header.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(header)

        let closeButton = UIButton(type: .system)
        closeButton.setTitle("返回", for: .normal)
        closeButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.accessibilityLabel = "返回"
        header.addSubview(closeButton)

        let titleLabel = UILabel()
        titleLabel.text = "可访问性与性能报告"
        titleLabel.font = .systemFont(ofSize: 18, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.14, blue: 0.17, alpha: 1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        header.addSubview(titleLabel)

        let refreshButton = UIButton(type: .system)
        refreshButton.setImage(UIImage(systemName: "arrow.clockwise"), for: .normal)
        refreshButton.tintColor = UIColor(red: 0.12, green: 0.36, blue: 0.48, alpha: 1)
        refreshButton.addTarget(self, action: #selector(refreshTapped), for: .touchUpInside)
        refreshButton.translatesAutoresizingMaskIntoConstraints = false
        refreshButton.accessibilityLabel = "刷新报告"
        header.addSubview(refreshButton)

        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            header.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            header.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: 54),
            closeButton.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            closeButton.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            titleLabel.centerXAnchor.constraint(equalTo: header.centerXAnchor),
            titleLabel.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            refreshButton.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            refreshButton.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            refreshButton.widthAnchor.constraint(equalToConstant: 36),
            refreshButton.heightAnchor.constraint(equalToConstant: 36)
        ])
    }

    private func configureTable() {
        tableView.dataSource = self
        tableView.delegate = self
        tableView.backgroundColor = .clear
        tableView.showsVerticalScrollIndicator = false
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 54),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    @objc private func refreshTapped() {
        report = AppQualityBaseline.shared.makeReport()
        tableView.reloadData()
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }
}

extension AppQualityReportViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        2
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? report.rows.count : 4
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? "实时指标" : "系统化基准"
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        cell.textLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        cell.textLabel?.textColor = UIColor(red: 0.12, green: 0.16, blue: 0.18, alpha: 1)
        cell.detailTextLabel?.font = .systemFont(ofSize: 12, weight: .regular)
        cell.detailTextLabel?.textColor = UIColor(red: 0.42, green: 0.47, blue: 0.50, alpha: 1)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.numberOfLines = 2

        if indexPath.section == 0 {
            let row = report.rows[indexPath.row]
            cell.textLabel?.text = "\(row.0)  \(row.1)"
            cell.detailTextLabel?.text = row.2
            cell.accessoryView = statusDot(title: row.2)
        } else {
            let rows = baselineRows
            let row = rows[indexPath.row]
            cell.textLabel?.text = row.0
            cell.detailTextLabel?.text = row.1
            cell.imageView?.image = UIImage(systemName: row.2)
            cell.imageView?.tintColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        }
        return cell
    }

    private var baselineRows: [(String, String, String)] {
        [
            ("VoiceOver", "按钮、输入框、图片补齐基础可读标签。", "speaker.wave.2"),
            ("动态字体", "页面文字跟随系统字体设置，单行文本自动压缩避免遮挡。", "textformat.size"),
            ("低端机模式", "低内存或高温状态下降低动画/刷新压力。", "iphone.gen3"),
            ("性能报告", "记录冷启动、滚动 FPS、内存与滚动监听数据。", "speedometer")
        ]
    }

    private func statusDot(title: String) -> UIView {
        let label = UILabel()
        label.text = title
        label.font = .systemFont(ofSize: 11, weight: .semibold)
        label.textColor = title.contains("偏") || title.contains("需") ? UIColor.systemOrange : UIColor.systemGreen
        label.backgroundColor = label.textColor.withAlphaComponent(0.12)
        label.textAlignment = .center
        label.layer.cornerRadius = 10
        label.clipsToBounds = true
        label.frame = CGRect(x: 0, y: 0, width: 54, height: 22)
        return label
    }
}
