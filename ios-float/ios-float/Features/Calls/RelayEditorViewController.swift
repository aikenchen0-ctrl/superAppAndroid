import UIKit

struct RelayEntry: Hashable {
    let id: UUID
    var name: String
    var text: String
    var submittedAt: Date
    var tieBreaker: Double
}

final class RelayEditorViewController: UIViewController {
    var onConfirm: ((String, [RelayEntry]) -> Void)?

    private let titleText: String
    private var noteText: String
    private let currentUser: ChatParticipant
    private let canEditNote: Bool
    private var entries: [RelayEntry]
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let inputContainer = UIView()
    private let noteField = UITextField()
    private let inputField = UITextField()
    private let deleteButton = UIButton(type: .system)
    private let confirmButton = UIButton(type: .system)

    init(titleText: String, noteText: String, entries: [RelayEntry], currentUser: ChatParticipant, canEditNote: Bool) {
        self.titleText = titleText
        self.noteText = noteText
        self.entries = entries
        self.currentUser = currentUser
        self.canEditNote = canEditNote
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "群接龙"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "返回",
            style: .plain,
            target: self,
            action: #selector(close)
        )

        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "RelayEntryCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        inputContainer.backgroundColor = UIColor.secondarySystemGroupedBackground
        inputContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(inputContainer)

        noteField.borderStyle = .roundedRect
        noteField.placeholder = "设置群接龙备注信息"
        noteField.text = noteText
        noteField.returnKeyType = .next
        noteField.isEnabled = canEditNote
        noteField.textColor = canEditNote ? .label : .secondaryLabel
        noteField.backgroundColor = canEditNote ? .systemBackground : UIColor.systemGray6
        noteField.addTarget(self, action: #selector(noteChanged), for: .editingChanged)
        noteField.translatesAutoresizingMaskIntoConstraints = false
        inputContainer.addSubview(noteField)

        inputField.borderStyle = .roundedRect
        inputField.placeholder = "输入自己的接龙信息"
        inputField.text = currentEntry()?.text
        inputField.returnKeyType = .done
        inputField.delegate = self
        inputField.translatesAutoresizingMaskIntoConstraints = false
        inputContainer.addSubview(inputField)

        var deleteConfig = UIButton.Configuration.plain()
        deleteConfig.title = "删除我的接龙"
        deleteConfig.image = UIImage(systemName: "trash")
        deleteConfig.imagePadding = 6
        deleteButton.configuration = deleteConfig
        deleteButton.tintColor = UIColor.systemRed
        deleteButton.addTarget(self, action: #selector(deleteCurrentEntry), for: .touchUpInside)
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        inputContainer.addSubview(deleteButton)

        var confirmConfig = UIButton.Configuration.filled()
        confirmConfig.title = "确认发送"
        confirmConfig.image = UIImage(systemName: "paperplane.fill")
        confirmConfig.imagePadding = 6
        confirmConfig.baseBackgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        confirmConfig.baseForegroundColor = .black
        confirmButton.configuration = confirmConfig
        confirmButton.addTarget(self, action: #selector(confirmRelay), for: .touchUpInside)
        confirmButton.translatesAutoresizingMaskIntoConstraints = false
        inputContainer.addSubview(confirmButton)

        NSLayoutConstraint.activate([
            inputContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            inputContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            inputContainer.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor),

            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: inputContainer.topAnchor),

            noteField.leadingAnchor.constraint(equalTo: inputContainer.leadingAnchor, constant: 16),
            noteField.topAnchor.constraint(equalTo: inputContainer.topAnchor, constant: 14),
            noteField.trailingAnchor.constraint(equalTo: inputContainer.trailingAnchor, constant: -16),
            noteField.heightAnchor.constraint(equalToConstant: 42),

            inputField.leadingAnchor.constraint(equalTo: inputContainer.leadingAnchor, constant: 16),
            inputField.topAnchor.constraint(equalTo: noteField.bottomAnchor, constant: 10),
            inputField.trailingAnchor.constraint(equalTo: inputContainer.trailingAnchor, constant: -16),
            inputField.heightAnchor.constraint(equalToConstant: 42),

            deleteButton.leadingAnchor.constraint(equalTo: inputField.leadingAnchor),
            deleteButton.topAnchor.constraint(equalTo: inputField.bottomAnchor, constant: 10),
            deleteButton.bottomAnchor.constraint(equalTo: inputContainer.safeAreaLayoutGuide.bottomAnchor, constant: -12),

            confirmButton.trailingAnchor.constraint(equalTo: inputField.trailingAnchor),
            confirmButton.centerYAnchor.constraint(equalTo: deleteButton.centerYAnchor),
            confirmButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 118),
            confirmButton.heightAnchor.constraint(equalToConstant: 36)
        ])

        updateDeleteButton()
    }

    private func currentEntry() -> RelayEntry? {
        entries.first { $0.id == currentUser.id }
    }

    private func updateCurrentEntryText() {
        let text = inputField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if let index = entries.firstIndex(where: { $0.id == currentUser.id }) {
            entries[index].text = text
            entries[index].submittedAt = Date()
            entries[index].tieBreaker = Double.random(in: 0..<1)
        } else if !text.isEmpty {
            entries.append(RelayEntry(
                id: currentUser.id,
                name: currentUser.displayName,
                text: text,
                submittedAt: Date(),
                tieBreaker: Double.random(in: 0..<1)
            ))
        }
    }

    private func sortedEntries() -> [RelayEntry] {
        entries.sorted {
            let delta = $0.submittedAt.timeIntervalSince($1.submittedAt)
            if abs(delta) < 0.001 {
                return $0.tieBreaker < $1.tieBreaker
            }
            return $0.submittedAt < $1.submittedAt
        }
    }

    private func updateDeleteButton() {
        let hasCurrentEntry = currentEntry() != nil
        deleteButton.isEnabled = hasCurrentEntry
        deleteButton.alpha = hasCurrentEntry ? 1 : 0.35
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func deleteCurrentEntry() {
        entries.removeAll { $0.id == currentUser.id }
        inputField.text = nil
        updateDeleteButton()
        tableView.reloadData()
    }

    @objc private func noteChanged() {
        tableView.reloadSections(IndexSet(integer: 0), with: .none)
    }

    @objc private func confirmRelay() {
        updateCurrentEntryText()
        let finalEntries = sortedEntries().filter {
            !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        guard !finalEntries.isEmpty else {
            let alert = UIAlertController(title: "请先输入接龙信息", message: nil, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .default))
            present(alert, animated: true)
            return
        }
        onConfirm?(noteField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "", finalEntries)
    }
}

extension RelayEditorViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        sortedEntries().isEmpty ? 1 : 2
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? titleText : "接龙列表"
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if section == 0 { return 1 }
        return sortedEntries().count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "RelayEntryCell", for: indexPath)
        var configuration = cell.defaultContentConfiguration()
        if indexPath.section == 0 {
            configuration.image = UIImage(systemName: "list.bullet.rectangle")
            configuration.imageProperties.tintColor = UIColor.systemGreen
            configuration.text = titleText
            let note = noteField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if note.isEmpty {
                configuration.secondaryText = canEditNote ? "可设置备注信息，发送后群成员继续接上。" : "发起人未设置备注。"
            } else {
                configuration.secondaryText = "备注：\(note)"
            }
        } else {
            let sorted = sortedEntries()
            let entry = sorted[indexPath.row]
            configuration.image = UIImage(systemName: entry.id == currentUser.id ? "person.crop.circle.fill.badge.checkmark" : "person.crop.circle")
            configuration.imageProperties.tintColor = entry.id == currentUser.id ? UIColor.systemGreen : UIColor.systemBlue
            configuration.text = "\(indexPath.row + 1). \(entry.name)"
            configuration.secondaryText = entry.text.isEmpty ? "未填写" : entry.text
        }
        configuration.textProperties.font = .systemFont(ofSize: 15, weight: .semibold)
        configuration.secondaryTextProperties.font = .systemFont(ofSize: 13)
        configuration.secondaryTextProperties.color = UIColor.secondaryLabel
        cell.contentConfiguration = configuration
        cell.selectionStyle = .none
        return cell
    }
}

extension RelayEditorViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        updateCurrentEntryText()
        updateDeleteButton()
        tableView.reloadData()
        return true
    }
}

