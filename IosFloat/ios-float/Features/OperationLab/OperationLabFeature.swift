import UIKit
import UniformTypeIdentifiers

final class UIOperationLabViewController: UIViewController, UIPickerViewDataSource, UIPickerViewDelegate, UIDragInteractionDelegate, UITextFieldDelegate {
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let loadingOverlay = UIView()
    private let loadingProgressView = CircularProgressView()
    private let loadingLabel = UILabel()
    private let nameField = UITextField()
    private let budgetField = UITextField()
    private let modeSegment = UISegmentedControl(items: ["销售", "服务", "运营"])
    private let pickerView = UIPickerView()
    private let chartView = OperationBarChartView()
    private let lineChartView = OperationLineChartView()
    private let rangeSlider = DoubleRangeSliderView()
    private let rulerControl = RulerControl()
    private let circularProgressView = CircularProgressView()
    private let progressValueLabel = UILabel()
    private let rangeValueLabel = UILabel()
    private let rulerValueLabel = UILabel()
    private let dropZoneView = OperationDropZoneView()
    private let resultLabel = UILabel()
    private var loadingTimer: Timer?
    private var loadingProgress: CGFloat = 0
    private let pickerRows = [
        "客户增长分析",
        "朋友圈素材投放",
        "视频号转化",
        "群运营复盘"
    ]

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "UI组件"
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        view.backgroundColor = UIColor(red: 0.94, green: 0.96, blue: 0.97, alpha: 1)
        configureScrollView()
        configureContent()
        configureLoadingOverlay()
        updateDashboard(animated: false)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        startLoadingProgress()
    }

    deinit {
        loadingTimer?.invalidate()
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }

    private func configureScrollView() {
        scrollView.alwaysBounceVertical = true
        scrollView.showsVerticalScrollIndicator = false
        scrollView.keyboardDismissMode = .interactive
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 14),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 14),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -14),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -22),
            contentStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -28)
        ])
    }

    private func configureContent() {
        contentStack.addArrangedSubview(makeHeaderCard())
        contentStack.addArrangedSubview(makeFormCard())
        contentStack.addArrangedSubview(makePickerCard())
        contentStack.addArrangedSubview(makeChartCard())
        contentStack.addArrangedSubview(makeControlsCard())
        contentStack.addArrangedSubview(makeDropCard())
        contentStack.addArrangedSubview(makeExecutionCard())
    }

    private func makeHeaderCard() -> UIView {
        let title = UILabel()
        title.text = "操作组件工作台"
        title.font = .systemFont(ofSize: 22, weight: .bold)
        title.textColor = UIColor(red: 0.09, green: 0.13, blue: 0.16, alpha: 1)

        let subtitle = UILabel()
        subtitle.text = "表单、选择器、双向范围滑块、刻度尺、拖放区、圆形进度条和图表统一在这里测试。"
        subtitle.font = .systemFont(ofSize: 13)
        subtitle.textColor = UIColor(red: 0.39, green: 0.45, blue: 0.50, alpha: 1)
        subtitle.numberOfLines = 0

        let icon = UIImageView(image: UIImage(systemName: "rectangle.3.group.bubble.left.fill"))
        icon.tintColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            icon.widthAnchor.constraint(equalToConstant: 34),
            icon.heightAnchor.constraint(equalToConstant: 34)
        ])

        let textStack = UIStackView(arrangedSubviews: [title, subtitle])
        textStack.axis = .vertical
        textStack.spacing = 5

        let row = UIStackView(arrangedSubviews: [textStack, icon])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        return makeCard(row, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
    }

    private func makeFormCard() -> UIView {
        nameField.placeholder = "任务名称，例如 客户转化日报"
        budgetField.placeholder = "预算金额，例如 12800"
        budgetField.keyboardType = .decimalPad
        [nameField, budgetField].forEach(configureTextField)
        nameField.delegate = self
        budgetField.delegate = self
        nameField.text = "客户转化日报"
        budgetField.text = "12800"

        modeSegment.selectedSegmentIndex = 0
        modeSegment.addTarget(self, action: #selector(formChanged), for: .valueChanged)

        let submitButton = UIButton(type: .system)
        var config = UIButton.Configuration.filled()
        config.title = "执行表单操作"
        config.image = UIImage(systemName: "play.fill")
        config.imagePadding = 6
        config.baseBackgroundColor = UIColor(red: 0.13, green: 0.48, blue: 0.36, alpha: 1)
        config.baseForegroundColor = .white
        config.cornerStyle = .medium
        submitButton.configuration = config
        submitButton.addTarget(self, action: #selector(submitForm), for: .touchUpInside)
        submitButton.heightAnchor.constraint(equalToConstant: 44).isActive = true

        let stack = makeVerticalStack([
            sectionTitle("表单", subtitle: "输入任务和预算，执行后同步更新下方结果"),
            nameField,
            budgetField,
            modeSegment,
            submitButton
        ], spacing: 10)
        return makeCard(stack)
    }

    private func makePickerCard() -> UIView {
        pickerView.dataSource = self
        pickerView.delegate = self
        pickerView.heightAnchor.constraint(equalToConstant: 112).isActive = true

        let stack = makeVerticalStack([
            sectionTitle("选择器", subtitle: "切换业务场景，图表会按场景刷新"),
            pickerView
        ], spacing: 8)
        return makeCard(stack)
    }

    private func makeChartCard() -> UIView {
        chartView.heightAnchor.constraint(equalToConstant: 150).isActive = true
        lineChartView.heightAnchor.constraint(equalToConstant: 116).isActive = true

        let legend = UIStackView(arrangedSubviews: [
            makeLegendDot(text: "成交", color: UIColor(red: 0.18, green: 0.56, blue: 0.46, alpha: 1)),
            makeLegendDot(text: "触达趋势", color: UIColor(red: 0.89, green: 0.42, blue: 0.26, alpha: 1))
        ])
        legend.axis = .horizontal
        legend.spacing = 12

        let stack = makeVerticalStack([
            sectionTitle("图表", subtitle: "柱状图与折线图根据当前控件状态实时变化"),
            chartView,
            lineChartView,
            legend
        ], spacing: 10)
        return makeCard(stack)
    }

    private func makeControlsCard() -> UIView {
        rangeSlider.minimumValue = 0
        rangeSlider.maximumValue = 100
        rangeSlider.lowerValue = 22
        rangeSlider.upperValue = 74
        rangeSlider.addTarget(self, action: #selector(rangeChanged), for: .valueChanged)
        rangeSlider.heightAnchor.constraint(equalToConstant: 42).isActive = true

        rulerControl.minimumValue = 0
        rulerControl.maximumValue = 100
        rulerControl.value = 64
        rulerControl.addTarget(self, action: #selector(rulerChanged), for: .valueChanged)
        rulerControl.heightAnchor.constraint(equalToConstant: 72).isActive = true

        circularProgressView.progress = 0.64
        circularProgressView.lineWidth = 9
        circularProgressView.trackColor = UIColor(red: 0.82, green: 0.87, blue: 0.89, alpha: 1)
        circularProgressView.progressColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        circularProgressView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            circularProgressView.widthAnchor.constraint(equalToConstant: 82),
            circularProgressView.heightAnchor.constraint(equalToConstant: 82)
        ])

        progressValueLabel.font = .monospacedDigitSystemFont(ofSize: 18, weight: .bold)
        progressValueLabel.textColor = UIColor(red: 0.10, green: 0.16, blue: 0.18, alpha: 1)
        progressValueLabel.textAlignment = .center

        let progressStack = UIStackView(arrangedSubviews: [circularProgressView, progressValueLabel])
        progressStack.axis = .horizontal
        progressStack.alignment = .center
        progressStack.spacing = 14

        rangeValueLabel.font = .systemFont(ofSize: 13, weight: .medium)
        rangeValueLabel.textColor = UIColor(red: 0.34, green: 0.39, blue: 0.43, alpha: 1)
        rulerValueLabel.font = .systemFont(ofSize: 13, weight: .medium)
        rulerValueLabel.textColor = rangeValueLabel.textColor

        let stack = makeVerticalStack([
            sectionTitle("范围滑块 / 刻度尺 / 圆形进度条", subtitle: "拖动双向范围和刻度尺，圆形进度与图表会同步变化"),
            rangeValueLabel,
            rangeSlider,
            rulerValueLabel,
            rulerControl,
            progressStack
        ], spacing: 9)
        return makeCard(stack)
    }

    private func makeDropCard() -> UIView {
        dropZoneView.heightAnchor.constraint(equalToConstant: 104).isActive = true
        dropZoneView.onReceiveText = { [weak self] text in
            self?.resultLabel.text = "拖放已接收：\(text)"
            self?.dropZoneView.setStatus("已放入 \(text)", symbolName: "checkmark.circle.fill")
            self?.bumpProgress()
        }

        let chip = UIButton(type: .system)
        chip.setTitle("拖动：高价值客户", for: .normal)
        chip.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        chip.tintColor = UIColor(red: 0.12, green: 0.34, blue: 0.44, alpha: 1)
        chip.backgroundColor = UIColor(red: 0.81, green: 0.91, blue: 0.92, alpha: 1)
        chip.layer.cornerRadius = 16
        chip.layer.cornerCurve = .continuous
        chip.applyContentInsets(top: 7, leading: 12, bottom: 7, trailing: 12)
        chip.addInteraction(UIDragInteraction(delegate: self))

        let stack = makeVerticalStack([
            sectionTitle("拖放区", subtitle: "长按下方标签拖入虚线区域，模拟文件/指标拖放"),
            chip,
            dropZoneView
        ], spacing: 10)
        return makeCard(stack)
    }

    private func makeExecutionCard() -> UIView {
        resultLabel.text = "等待执行操作"
        resultLabel.font = .systemFont(ofSize: 14, weight: .medium)
        resultLabel.textColor = UIColor(red: 0.18, green: 0.22, blue: 0.25, alpha: 1)
        resultLabel.numberOfLines = 0

        let resetButton = UIButton(type: .system)
        var config = UIButton.Configuration.bordered()
        config.title = "重置组件状态"
        config.image = UIImage(systemName: "arrow.clockwise")
        config.imagePadding = 6
        config.baseForegroundColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        resetButton.configuration = config
        resetButton.addTarget(self, action: #selector(resetControls), for: .touchUpInside)
        resetButton.heightAnchor.constraint(equalToConstant: 40).isActive = true

        let stack = makeVerticalStack([
            sectionTitle("执行结果", subtitle: "所有控件变化会汇总到这里"),
            resultLabel,
            resetButton
        ], spacing: 10)
        return makeCard(stack)
    }

    private func configureLoadingOverlay() {
        loadingOverlay.backgroundColor = UIColor(red: 0.94, green: 0.96, blue: 0.97, alpha: 0.98)
        loadingOverlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingOverlay)

        loadingProgressView.lineWidth = 10
        loadingProgressView.trackColor = UIColor(red: 0.79, green: 0.86, blue: 0.88, alpha: 1)
        loadingProgressView.progressColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        loadingProgressView.translatesAutoresizingMaskIntoConstraints = false

        loadingLabel.text = "组件加载中"
        loadingLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        loadingLabel.textColor = UIColor(red: 0.18, green: 0.25, blue: 0.29, alpha: 1)
        loadingLabel.textAlignment = .center
        loadingLabel.translatesAutoresizingMaskIntoConstraints = false

        loadingOverlay.addSubview(loadingProgressView)
        loadingOverlay.addSubview(loadingLabel)
        NSLayoutConstraint.activate([
            loadingOverlay.topAnchor.constraint(equalTo: view.topAnchor),
            loadingOverlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            loadingOverlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            loadingOverlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            loadingProgressView.centerXAnchor.constraint(equalTo: loadingOverlay.centerXAnchor),
            loadingProgressView.centerYAnchor.constraint(equalTo: loadingOverlay.centerYAnchor, constant: -14),
            loadingProgressView.widthAnchor.constraint(equalToConstant: 92),
            loadingProgressView.heightAnchor.constraint(equalToConstant: 92),
            loadingLabel.topAnchor.constraint(equalTo: loadingProgressView.bottomAnchor, constant: 14),
            loadingLabel.centerXAnchor.constraint(equalTo: loadingOverlay.centerXAnchor)
        ])
    }

    private func startLoadingProgress() {
        guard loadingTimer == nil, loadingOverlay.alpha > 0 else { return }
        loadingProgress = 0
        loadingProgressView.progress = 0
        loadingTimer = Timer.scheduledTimer(withTimeInterval: 0.024, repeats: true) { [weak self] timer in
            guard let self else {
                timer.invalidate()
                return
            }
            self.loadingProgress = min(self.loadingProgress + 0.035, 1)
            self.loadingProgressView.setProgress(self.loadingProgress, animated: true)
            if self.loadingProgress >= 1 {
                timer.invalidate()
                self.loadingTimer = nil
                UIView.animate(withDuration: 0.22, delay: 0.08, options: [.curveEaseOut]) {
                    self.loadingOverlay.alpha = 0
                } completion: { _ in
                    self.loadingOverlay.removeFromSuperview()
                }
            }
        }
    }

    private func configureTextField(_ field: UITextField) {
        field.backgroundColor = UIColor(red: 0.97, green: 0.98, blue: 0.98, alpha: 1)
        field.layer.cornerRadius = 10
        field.layer.cornerCurve = .continuous
        field.layer.borderWidth = 1
        field.layer.borderColor = UIColor.black.withAlphaComponent(0.08).cgColor
        field.font = .systemFont(ofSize: 15)
        field.textColor = UIColor(red: 0.11, green: 0.14, blue: 0.16, alpha: 1)
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        field.leftViewMode = .always
        field.clearButtonMode = .whileEditing
        field.heightAnchor.constraint(equalToConstant: 42).isActive = true
    }

    private func makeVerticalStack(_ views: [UIView], spacing: CGFloat) -> UIStackView {
        let stack = UIStackView(arrangedSubviews: views)
        stack.axis = .vertical
        stack.spacing = spacing
        return stack
    }

    private func makeCard(_ content: UIView, insets: UIEdgeInsets = UIEdgeInsets(top: 14, left: 14, bottom: 14, right: 14)) -> UIView {
        let container = UIView()
        container.backgroundColor = .white
        container.layer.cornerRadius = 8
        container.layer.borderWidth = 1
        container.layer.borderColor = UIColor.black.withAlphaComponent(0.06).cgColor
        container.translatesAutoresizingMaskIntoConstraints = false
        content.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(content)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: container.topAnchor, constant: insets.top),
            content.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: insets.left),
            content.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -insets.right),
            content.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -insets.bottom)
        ])
        return container
    }

    private func sectionTitle(_ title: String, subtitle: String) -> UIView {
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.14, blue: 0.16, alpha: 1)

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12)
        subtitleLabel.textColor = UIColor(red: 0.43, green: 0.48, blue: 0.52, alpha: 1)
        subtitleLabel.numberOfLines = 0

        return makeVerticalStack([titleLabel, subtitleLabel], spacing: 3)
    }

    private func makeLegendDot(text: String, color: UIColor) -> UIView {
        let dot = UIView()
        dot.backgroundColor = color
        dot.layer.cornerRadius = 4
        dot.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            dot.widthAnchor.constraint(equalToConstant: 8),
            dot.heightAnchor.constraint(equalToConstant: 8)
        ])

        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 12, weight: .medium)
        label.textColor = UIColor(red: 0.37, green: 0.42, blue: 0.46, alpha: 1)

        let row = UIStackView(arrangedSubviews: [dot, label])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 5
        return row
    }

    @objc private func submitForm() {
        let taskName = nameField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "未命名任务"
        let budget = budgetField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "未填写"
        let mode = modeSegment.titleForSegment(at: modeSegment.selectedSegmentIndex) ?? "销售"
        resultLabel.text = "已执行：\(taskName) · \(mode)模式 · 预算 \(budget)"
        bumpProgress()
    }

    @objc private func formChanged() {
        updateDashboard(animated: true)
    }

    @objc private func rangeChanged() {
        updateDashboard(animated: true)
    }

    @objc private func rulerChanged() {
        updateDashboard(animated: true)
    }

    @objc private func resetControls() {
        nameField.text = "客户转化日报"
        budgetField.text = "12800"
        modeSegment.selectedSegmentIndex = 0
        pickerView.selectRow(0, inComponent: 0, animated: true)
        rangeSlider.lowerValue = 22
        rangeSlider.upperValue = 74
        rulerControl.value = 64
        dropZoneView.setStatus("拖入指标、文件或文本", symbolName: "tray.and.arrow.down")
        resultLabel.text = "已重置组件状态"
        updateDashboard(animated: true)
    }

    private func bumpProgress() {
        rulerControl.value = min(rulerControl.value + 8, rulerControl.maximumValue)
        updateDashboard(animated: true)
    }

    private func updateDashboard(animated: Bool) {
        let selectedScene = pickerRows[pickerView.selectedRow(inComponent: 0)]
        let modeOffset = CGFloat(max(modeSegment.selectedSegmentIndex, 0)) * 8
        let spread = rangeSlider.upperValue - rangeSlider.lowerValue
        let progress = max(0.05, min(1, rulerControl.value / max(rulerControl.maximumValue, 1)))
        let seed = CGFloat(pickerView.selectedRow(inComponent: 0) + 1) * 7
        let values = (0..<6).map { index -> CGFloat in
            let wave = CGFloat((index + 2) * 9) + seed + modeOffset
            return min(100, max(8, wave + spread * CGFloat(index % 3 + 1) * 0.12))
        }
        let lineValues = values.enumerated().map { index, value in
            min(100, max(5, value * (0.52 + progress * 0.45) + CGFloat(index) * 2))
        }
        chartView.setValues(values, animated: animated)
        lineChartView.setValues(lineValues, animated: animated)
        circularProgressView.setProgress(progress, animated: animated)
        progressValueLabel.text = "完成度 \(Int(progress * 100))%\n\(selectedScene)"
        progressValueLabel.numberOfLines = 2
        rangeValueLabel.text = "范围：\(Int(rangeSlider.lowerValue)) - \(Int(rangeSlider.upperValue))"
        rulerValueLabel.text = "刻度尺：\(Int(rulerControl.value))"
    }

    func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }

    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        pickerRows.count
    }

    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        pickerRows[row]
    }

    func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
        resultLabel.text = "已选择场景：\(pickerRows[row])"
        updateDashboard(animated: true)
    }

    func dragInteraction(_ interaction: UIDragInteraction, itemsForBeginning session: UIDragSession) -> [UIDragItem] {
        let provider = NSItemProvider(object: "高价值客户" as NSString)
        let item = UIDragItem(itemProvider: provider)
        item.localObject = "高价值客户"
        return [item]
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        submitForm()
        return true
    }
}

private final class CircularProgressView: UIView {
    var progress: CGFloat = 0 {
        didSet { setNeedsDisplay() }
    }
    var lineWidth: CGFloat = 8 {
        didSet { setNeedsDisplay() }
    }
    var trackColor: UIColor = UIColor(white: 0.88, alpha: 1) {
        didSet { setNeedsDisplay() }
    }
    var progressColor: UIColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1) {
        didSet { setNeedsDisplay() }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        configureTransparentDrawing()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configureTransparentDrawing()
    }

    private func configureTransparentDrawing() {
        isOpaque = false
        backgroundColor = .clear
    }

    func setProgress(_ value: CGFloat, animated: Bool) {
        let clamped = min(max(value, 0), 1)
        guard animated else {
            progress = clamped
            return
        }
        UIView.animate(withDuration: 0.18) {
            self.progress = clamped
            self.setNeedsDisplay()
        }
    }

    override func draw(_ rect: CGRect) {
        let inset = lineWidth / 2 + 1
        let center = CGPoint(x: bounds.midX, y: bounds.midY)
        let radius = max(0, min(bounds.width, bounds.height) / 2 - inset)
        let start = -CGFloat.pi / 2
        let end = start + CGFloat.pi * 2

        let track = UIBezierPath(arcCenter: center, radius: radius, startAngle: start, endAngle: end, clockwise: true)
        track.lineWidth = lineWidth
        track.lineCapStyle = .round
        trackColor.setStroke()
        track.stroke()

        let progressPath = UIBezierPath(arcCenter: center, radius: radius, startAngle: start, endAngle: start + CGFloat.pi * 2 * progress, clockwise: true)
        progressPath.lineWidth = lineWidth
        progressPath.lineCapStyle = .round
        progressColor.setStroke()
        progressPath.stroke()
    }
}

extension UIViewController {
    func showCircularPageLoading(title: String = "加载中", duration: TimeInterval = 0.58) {
        let overlayTag = 740_621
        guard view.viewWithTag(overlayTag) == nil else { return }

        let overlay = UIView()
        overlay.tag = overlayTag
        overlay.backgroundColor = (view.backgroundColor ?? .systemBackground).withAlphaComponent(0.96)
        overlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlay)

        let progressView = CircularProgressView()
        progressView.progress = 0
        progressView.lineWidth = 9
        progressView.trackColor = UIColor(red: 0.80, green: 0.86, blue: 0.88, alpha: 1)
        progressView.progressColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        progressView.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.18, green: 0.24, blue: 0.28, alpha: 1)
        titleLabel.textAlignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        overlay.addSubview(progressView)
        overlay.addSubview(titleLabel)
        NSLayoutConstraint.activate([
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            progressView.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            progressView.centerYAnchor.constraint(equalTo: overlay.centerYAnchor, constant: -16),
            progressView.widthAnchor.constraint(equalToConstant: 78),
            progressView.heightAnchor.constraint(equalToConstant: 78),
            titleLabel.topAnchor.constraint(equalTo: progressView.bottomAnchor, constant: 12),
            titleLabel.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            titleLabel.leadingAnchor.constraint(greaterThanOrEqualTo: overlay.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: overlay.safeAreaLayoutGuide.trailingAnchor, constant: -24)
        ])
        view.bringSubviewToFront(overlay)

        var progress: CGFloat = 0
        let step = max(0.025, duration / 22)
        Timer.scheduledTimer(withTimeInterval: step, repeats: true) { [weak overlay, weak progressView] timer in
            guard let overlay, let progressView else {
                timer.invalidate()
                return
            }
            progress = min(progress + 0.055, 1)
            progressView.setProgress(progress, animated: true)
            if progress >= 1 {
                timer.invalidate()
                UIView.animate(withDuration: 0.20, delay: 0.04, options: [.curveEaseOut]) {
                    overlay.alpha = 0
                } completion: { _ in
                    overlay.removeFromSuperview()
                }
            }
        }
    }
}

private final class OperationBarChartView: UIView {
    private var values: [CGFloat] = [32, 54, 44, 68, 58, 76]

    func setValues(_ values: [CGFloat], animated: Bool) {
        self.values = values
        if animated {
            UIView.transition(with: self, duration: 0.18, options: [.transitionCrossDissolve]) {
                self.setNeedsDisplay()
            }
        } else {
            setNeedsDisplay()
        }
    }

    override func draw(_ rect: CGRect) {
        guard !values.isEmpty else { return }
        let plot = bounds.insetBy(dx: 10, dy: 12)
        UIColor(red: 0.94, green: 0.96, blue: 0.97, alpha: 1).setFill()
        UIBezierPath(roundedRect: plot, cornerRadius: 8).fill()

        let maxValue = max(values.max() ?? 1, 1)
        let barWidth = max(10, (plot.width - CGFloat(values.count + 1) * 8) / CGFloat(values.count))
        for (index, value) in values.enumerated() {
            let ratio = value / maxValue
            let height = max(8, (plot.height - 24) * ratio)
            let x = plot.minX + 8 + CGFloat(index) * (barWidth + 8)
            let y = plot.maxY - 12 - height
            let rect = CGRect(x: x, y: y, width: barWidth, height: height)
            let color = index % 2 == 0
                ? UIColor(red: 0.18, green: 0.56, blue: 0.46, alpha: 1)
                : UIColor(red: 0.25, green: 0.48, blue: 0.66, alpha: 1)
            color.setFill()
            UIBezierPath(roundedRect: rect, cornerRadius: 5).fill()
        }
    }
}

private final class OperationLineChartView: UIView {
    private var values: [CGFloat] = [22, 38, 34, 52, 48, 66]

    func setValues(_ values: [CGFloat], animated: Bool) {
        self.values = values
        if animated {
            UIView.transition(with: self, duration: 0.18, options: [.transitionCrossDissolve]) {
                self.setNeedsDisplay()
            }
        } else {
            setNeedsDisplay()
        }
    }

    override func draw(_ rect: CGRect) {
        guard values.count > 1 else { return }
        let plot = bounds.insetBy(dx: 10, dy: 12)
        UIColor(red: 0.98, green: 0.95, blue: 0.91, alpha: 1).setFill()
        UIBezierPath(roundedRect: plot, cornerRadius: 8).fill()

        UIColor.black.withAlphaComponent(0.08).setStroke()
        for index in 0..<3 {
            let y = plot.minY + CGFloat(index + 1) * plot.height / 4
            let path = UIBezierPath()
            path.move(to: CGPoint(x: plot.minX + 8, y: y))
            path.addLine(to: CGPoint(x: plot.maxX - 8, y: y))
            path.lineWidth = 1
            path.stroke()
        }

        let maxValue = max(values.max() ?? 1, 1)
        let points = values.enumerated().map { index, value -> CGPoint in
            let x = plot.minX + 12 + CGFloat(index) * (plot.width - 24) / CGFloat(values.count - 1)
            let y = plot.maxY - 12 - (plot.height - 24) * (value / maxValue)
            return CGPoint(x: x, y: y)
        }
        let line = UIBezierPath()
        line.move(to: points[0])
        points.dropFirst().forEach { line.addLine(to: $0) }
        UIColor(red: 0.89, green: 0.42, blue: 0.26, alpha: 1).setStroke()
        line.lineWidth = 3
        line.lineJoinStyle = .round
        line.lineCapStyle = .round
        line.stroke()

        UIColor.white.setFill()
        UIColor(red: 0.89, green: 0.42, blue: 0.26, alpha: 1).setStroke()
        points.forEach { point in
            let dot = UIBezierPath(ovalIn: CGRect(x: point.x - 4, y: point.y - 4, width: 8, height: 8))
            dot.fill()
            dot.lineWidth = 2
            dot.stroke()
        }
    }
}

private final class DoubleRangeSliderView: UIControl {
    var minimumValue: CGFloat = 0 { didSet { setNeedsDisplay() } }
    var maximumValue: CGFloat = 100 { didSet { setNeedsDisplay() } }
    var lowerValue: CGFloat = 20 { didSet { lowerValue = clamped(lowerValue); setNeedsDisplay() } }
    var upperValue: CGFloat = 80 { didSet { upperValue = clamped(upperValue); setNeedsDisplay() } }
    private let handleRadius: CGFloat = 13
    private var activeHandle: Handle?

    private enum Handle {
        case lower
        case upper
    }

    override func draw(_ rect: CGRect) {
        let trackRect = CGRect(x: handleRadius, y: bounds.midY - 3, width: bounds.width - handleRadius * 2, height: 6)
        UIColor(red: 0.88, green: 0.91, blue: 0.92, alpha: 1).setFill()
        UIBezierPath(roundedRect: trackRect, cornerRadius: 3).fill()

        let lowerX = xPosition(for: lowerValue)
        let upperX = xPosition(for: upperValue)
        let selectedRect = CGRect(x: lowerX, y: trackRect.minY, width: max(0, upperX - lowerX), height: trackRect.height)
        UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1).setFill()
        UIBezierPath(roundedRect: selectedRect, cornerRadius: 3).fill()

        drawHandle(center: CGPoint(x: lowerX, y: bounds.midY))
        drawHandle(center: CGPoint(x: upperX, y: bounds.midY))
    }

    private func drawHandle(center: CGPoint) {
        UIColor.white.setFill()
        UIColor.black.withAlphaComponent(0.14).setStroke()
        let rect = CGRect(x: center.x - handleRadius, y: center.y - handleRadius, width: handleRadius * 2, height: handleRadius * 2)
        let path = UIBezierPath(ovalIn: rect)
        path.fill()
        path.lineWidth = 1
        path.stroke()
    }

    override func beginTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        let point = touch.location(in: self)
        activeHandle = abs(point.x - xPosition(for: lowerValue)) <= abs(point.x - xPosition(for: upperValue)) ? .lower : .upper
        updateValue(for: point)
        return true
    }

    override func continueTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        updateValue(for: touch.location(in: self))
        return true
    }

    override func endTracking(_ touch: UITouch?, with event: UIEvent?) {
        activeHandle = nil
    }

    private func updateValue(for point: CGPoint) {
        let value = value(for: point.x)
        switch activeHandle {
        case .lower:
            lowerValue = min(value, upperValue - 1)
        case .upper:
            upperValue = max(value, lowerValue + 1)
        case .none:
            break
        }
        sendActions(for: .valueChanged)
    }

    private func clamped(_ value: CGFloat) -> CGFloat {
        min(max(value, minimumValue), maximumValue)
    }

    private func xPosition(for value: CGFloat) -> CGFloat {
        let available = max(1, bounds.width - handleRadius * 2)
        let ratio = (value - minimumValue) / max(maximumValue - minimumValue, 1)
        return handleRadius + available * min(max(ratio, 0), 1)
    }

    private func value(for x: CGFloat) -> CGFloat {
        let available = max(1, bounds.width - handleRadius * 2)
        let ratio = min(max((x - handleRadius) / available, 0), 1)
        return minimumValue + (maximumValue - minimumValue) * ratio
    }
}

private final class RulerControl: UIControl {
    var minimumValue: CGFloat = 0 { didSet { setNeedsDisplay() } }
    var maximumValue: CGFloat = 100 { didSet { setNeedsDisplay() } }
    var value: CGFloat = 50 {
        didSet {
            value = min(max(value, minimumValue), maximumValue)
            setNeedsDisplay()
        }
    }
    private var startValue: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        addGestureRecognizer(pan)
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        addGestureRecognizer(pan)
    }

    override func draw(_ rect: CGRect) {
        let centerY = bounds.midY
        let baseline = UIBezierPath()
        baseline.move(to: CGPoint(x: 10, y: centerY))
        baseline.addLine(to: CGPoint(x: bounds.maxX - 10, y: centerY))
        UIColor(red: 0.84, green: 0.88, blue: 0.89, alpha: 1).setStroke()
        baseline.lineWidth = 2
        baseline.stroke()

        let tickCount = 20
        for index in 0...tickCount {
            let ratio = CGFloat(index) / CGFloat(tickCount)
            let x = 12 + ratio * (bounds.width - 24)
            let major = index % 5 == 0
            let height: CGFloat = major ? 22 : 12
            let tick = UIBezierPath()
            tick.move(to: CGPoint(x: x, y: centerY - height / 2))
            tick.addLine(to: CGPoint(x: x, y: centerY + height / 2))
            UIColor(red: 0.48, green: 0.56, blue: 0.60, alpha: major ? 0.9 : 0.45).setStroke()
            tick.lineWidth = major ? 1.5 : 1
            tick.stroke()
        }

        let ratio = (value - minimumValue) / max(maximumValue - minimumValue, 1)
        let markerX = 12 + min(max(ratio, 0), 1) * (bounds.width - 24)
        let marker = UIBezierPath(roundedRect: CGRect(x: markerX - 2, y: 8, width: 4, height: bounds.height - 16), cornerRadius: 2)
        UIColor(red: 0.89, green: 0.42, blue: 0.26, alpha: 1).setFill()
        marker.fill()
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            startValue = value
        case .changed:
            let translation = gesture.translation(in: self).x
            let delta = translation / max(bounds.width - 24, 1) * (maximumValue - minimumValue)
            value = startValue + delta
            sendActions(for: .valueChanged)
        default:
            UISelectionFeedbackGenerator().selectionChanged()
        }
    }
}

private final class OperationDropZoneView: UIView, UIDropInteractionDelegate {
    var onReceiveText: ((String) -> Void)?
    private let iconView = UIImageView(image: UIImage(systemName: "tray.and.arrow.down"))
    private let titleLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        backgroundColor = UIColor(red: 0.96, green: 0.98, blue: 0.98, alpha: 1)
        layer.cornerRadius = 8
        layer.cornerCurve = .continuous
        layer.borderWidth = 1.5
        layer.borderColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 0.36).cgColor
        addInteraction(UIDropInteraction(delegate: self))

        iconView.tintColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 1)
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.text = "拖入指标、文件或文本"
        titleLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.26, green: 0.34, blue: 0.38, alpha: 1)
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        addSubview(iconView)
        addSubview(titleLabel)
        NSLayoutConstraint.activate([
            iconView.centerXAnchor.constraint(equalTo: centerXAnchor),
            iconView.topAnchor.constraint(equalTo: topAnchor, constant: 18),
            iconView.widthAnchor.constraint(equalToConstant: 28),
            iconView.heightAnchor.constraint(equalToConstant: 28),
            titleLabel.topAnchor.constraint(equalTo: iconView.bottomAnchor, constant: 8),
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12)
        ])
    }

    func setStatus(_ text: String, symbolName: String) {
        titleLabel.text = text
        iconView.image = UIImage(systemName: symbolName)
    }

    func dropInteraction(_ interaction: UIDropInteraction, canHandle session: UIDropSession) -> Bool {
        session.hasItemsConforming(toTypeIdentifiers: [UTType.text.identifier])
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidEnter session: UIDropSession) {
        backgroundColor = UIColor(red: 0.88, green: 0.95, blue: 0.96, alpha: 1)
        layer.borderColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 0.8).cgColor
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidExit session: UIDropSession) {
        backgroundColor = UIColor(red: 0.96, green: 0.98, blue: 0.98, alpha: 1)
        layer.borderColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 0.36).cgColor
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidUpdate session: UIDropSession) -> UIDropProposal {
        UIDropProposal(operation: .copy)
    }

    func dropInteraction(_ interaction: UIDropInteraction, performDrop session: UIDropSession) {
        backgroundColor = UIColor(red: 0.96, green: 0.98, blue: 0.98, alpha: 1)
        layer.borderColor = UIColor(red: 0.16, green: 0.48, blue: 0.62, alpha: 0.36).cgColor
        session.loadObjects(ofClass: NSString.self) { [weak self] items in
            let text = (items.first as? String) ?? (items.first as? NSString).map(String.init) ?? "已接收内容"
            DispatchQueue.main.async {
                self?.onReceiveText?(text)
            }
        }
    }
}

final class SelectionAssistJoystickView: UIView {
    var onPan: ((CGPoint) -> Void)?

    private let trackView = UIView()
    private let knobView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        backgroundColor = UIColor(red: 0.90, green: 0.95, blue: 0.98, alpha: 0.86)
        layer.cornerRadius = 37
        layer.cornerCurve = .continuous
        layer.borderWidth = 1 / UIScreen.main.scale
        layer.borderColor = UIColor.black.withAlphaComponent(0.08).cgColor
        clipsToBounds = false

        trackView.backgroundColor = UIColor.white.withAlphaComponent(0.72)
        trackView.layer.cornerRadius = 29
        trackView.layer.cornerCurve = .continuous
        trackView.translatesAutoresizingMaskIntoConstraints = false

        knobView.backgroundColor = UIColor(red: 0.15, green: 0.43, blue: 0.62, alpha: 1)
        knobView.layer.cornerRadius = 13
        knobView.layer.cornerCurve = .continuous
        knobView.translatesAutoresizingMaskIntoConstraints = false

        addSubview(trackView)
        addSubview(knobView)
        NSLayoutConstraint.activate([
            trackView.centerXAnchor.constraint(equalTo: centerXAnchor),
            trackView.centerYAnchor.constraint(equalTo: centerYAnchor),
            trackView.widthAnchor.constraint(equalToConstant: 58),
            trackView.heightAnchor.constraint(equalToConstant: 58),

            knobView.centerXAnchor.constraint(equalTo: centerXAnchor),
            knobView.centerYAnchor.constraint(equalTo: centerYAnchor),
            knobView.widthAnchor.constraint(equalToConstant: 26),
            knobView.heightAnchor.constraint(equalToConstant: 26)
        ])

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        addGestureRecognizer(pan)
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        let translation = gesture.translation(in: self)
        switch gesture.state {
        case .began, .changed:
            let clampedX = min(max(translation.x, -20), 20)
            let clampedY = min(max(translation.y, -20), 20)
            knobView.transform = CGAffineTransform(translationX: clampedX, y: clampedY)
            onPan?(translation)
            gesture.setTranslation(.zero, in: self)
        default:
            UIView.animate(withDuration: 0.16, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
                self.knobView.transform = .identity
            }
        }
    }
}

final class SelectionLassoOverlayView: UIView {
    var onFinish: ((CGRect) -> Void)?
    var onCancel: (() -> Void)?

    private let selectionLayer = CAShapeLayer()
    private let hintLabel = UILabel()
    private var startPoint: CGPoint?
    private var currentRect: CGRect = .zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        backgroundColor = UIColor.black.withAlphaComponent(0.04)
        isUserInteractionEnabled = true

        selectionLayer.fillColor = UIColor(red: 0.18, green: 0.48, blue: 0.72, alpha: 0.14).cgColor
        selectionLayer.strokeColor = UIColor(red: 0.12, green: 0.40, blue: 0.62, alpha: 0.95).cgColor
        selectionLayer.lineWidth = 1.5
        selectionLayer.lineDashPattern = [6, 4]
        layer.addSublayer(selectionLayer)

        hintLabel.text = "拖动框选消息"
        hintLabel.textAlignment = .center
        hintLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        hintLabel.textColor = UIColor(red: 0.10, green: 0.30, blue: 0.44, alpha: 1)
        hintLabel.backgroundColor = UIColor.white.withAlphaComponent(0.86)
        hintLabel.layer.cornerRadius = 13
        hintLabel.layer.cornerCurve = .continuous
        hintLabel.clipsToBounds = true
        hintLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(hintLabel)
        NSLayoutConstraint.activate([
            hintLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            hintLabel.topAnchor.constraint(equalTo: topAnchor, constant: 14),
            hintLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 122),
            hintLabel.heightAnchor.constraint(equalToConstant: 28)
        ])

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        addGestureRecognizer(pan)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
        tap.require(toFail: pan)
        addGestureRecognizer(tap)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if !currentRect.isEmpty {
            selectionLayer.path = UIBezierPath(roundedRect: currentRect, cornerRadius: 12).cgPath
        }
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        let point = gesture.location(in: self)
        switch gesture.state {
        case .began:
            startPoint = point
            currentRect = .zero
            updateSelectionPath()
            hintLabel.alpha = 0
        case .changed:
            guard let startPoint else { return }
            currentRect = CGRect(
                x: min(startPoint.x, point.x),
                y: min(startPoint.y, point.y),
                width: abs(point.x - startPoint.x),
                height: abs(point.y - startPoint.y)
            )
            updateSelectionPath()
        case .ended:
            guard currentRect.width >= 18, currentRect.height >= 18 else {
                onCancel?()
                return
            }
            onFinish?(currentRect.insetBy(dx: -4, dy: -4))
        default:
            onCancel?()
        }
    }

    @objc private func handleTap() {
        onCancel?()
    }

    private func updateSelectionPath() {
        selectionLayer.path = UIBezierPath(roundedRect: currentRect, cornerRadius: 12).cgPath
    }
}

