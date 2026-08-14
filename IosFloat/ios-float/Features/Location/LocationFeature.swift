import UIKit
import CoreLocation
import MapKit

final class LiveLocationViewController: UIViewController {
    private let titleText: String
    private let detailText: String
    private let participants: [ChatParticipant]
    private let mapView = MKMapView()
    private let statusLabel = UILabel()
    private var timer: Timer?
    private var tickCount = 0

    init(titleText: String, detailText: String, participants: [ChatParticipant]) {
        self.titleText = titleText
        self.detailText = detailText
        self.participants = Array(participants.prefix(6))
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "实时位置"
        view.backgroundColor = UIColor.systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configure()
        updateAnnotations()
        timer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: true) { [weak self] _ in
            self?.advanceLocations()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        timer?.invalidate()
    }

    private func configure() {
        mapView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(mapView)

        let panel = UIView()
        panel.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.96)
        panel.layer.cornerRadius = 18
        panel.layer.cornerCurve = .continuous
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 17, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.text = statusText
        statusLabel.font = .systemFont(ofSize: 13)
        statusLabel.textColor = .secondaryLabel
        statusLabel.numberOfLines = 2
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        panel.addSubview(titleLabel)
        panel.addSubview(statusLabel)

        NSLayoutConstraint.activate([
            mapView.topAnchor.constraint(equalTo: view.topAnchor),
            mapView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            mapView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            mapView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            panel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -14),
            panel.heightAnchor.constraint(greaterThanOrEqualToConstant: 88),
            titleLabel.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -16),
            titleLabel.topAnchor.constraint(equalTo: panel.topAnchor, constant: 14),
            statusLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            statusLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            statusLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 7),
            statusLabel.bottomAnchor.constraint(lessThanOrEqualTo: panel.bottomAnchor, constant: -14)
        ])
    }

    private var statusText: String {
        "\(detailText)\n\(participants.count) 人正在共享，位置每秒自动更新。"
    }

    private func updateAnnotations() {
        mapView.removeAnnotations(mapView.annotations)
        let base = CLLocationCoordinate2D(latitude: 31.2304, longitude: 121.4737)
        for (index, participant) in participants.enumerated() {
            let annotation = MKPointAnnotation()
            annotation.coordinate = CLLocationCoordinate2D(
                latitude: base.latitude + Double(index) * 0.002 + Double(tickCount) * 0.00012,
                longitude: base.longitude + Double(index % 3) * 0.002 - Double(tickCount) * 0.00008
            )
            annotation.title = participant.displayName
            annotation.subtitle = "实时位置已更新"
            mapView.addAnnotation(annotation)
        }
        mapView.setRegion(MKCoordinateRegion(center: base, latitudinalMeters: 2200, longitudinalMeters: 2200), animated: tickCount > 0)
    }

    private func advanceLocations() {
        tickCount += 1
        updateAnnotations()
        statusLabel.text = statusText
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

final class LocationPickerViewController: UIViewController {
    var onPickLocation: ((PickedLocation, ChatMessageType) -> Void)?

    private let messageType: ChatMessageType
    private let initialQuery: String?
    private let locationManager = CLLocationManager()
    private let mapView = MKMapView()
    private let searchContainer = UIView()
    private let searchField = UITextField()
    private let currentLocationButton = UIButton(type: .system)
    private let resultsTableView = UITableView(frame: .zero, style: .plain)
    private let selectedLocationView = UIView()
    private let selectedTitleLabel = UILabel()
    private let selectedAddressLabel = UILabel()
    private let sendButton = UIButton(type: .system)

    private var searchCompleter = MKLocalSearchCompleter()
    private var searchResults: [MKLocalSearchCompletion] = []
    private var selectedLocation: PickedLocation?
    private var activeSearch: MKLocalSearch?
    private var hasCenteredOnUser = false

    init(messageType: ChatMessageType, initialQuery: String?) {
        self.messageType = messageType
        self.initialQuery = initialQuery
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = messageType == .liveLocation ? "选择实时位置" : "选择位置"
        view.backgroundColor = UIColor.systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .cancel,
            target: self,
            action: #selector(cancel)
        )
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "发送",
            style: .done,
            target: self,
            action: #selector(sendLocation)
        )
        navigationItem.rightBarButtonItem?.isEnabled = false

        configureMap()
        configureSearch()
        configureResults()
        configureSelectionPanel()
        requestLocationAccessIfNeeded()

        if let initialQuery, !initialQuery.isEmpty {
            searchField.text = initialQuery
            performSearch(query: initialQuery)
        }
    }

    private func configureMap() {
        mapView.translatesAutoresizingMaskIntoConstraints = false
        mapView.delegate = self
        mapView.showsUserLocation = true
        mapView.pointOfInterestFilter = .includingAll
        view.addSubview(mapView)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleMapTap(_:)))
        mapView.addGestureRecognizer(tap)

        NSLayoutConstraint.activate([
            mapView.topAnchor.constraint(equalTo: view.topAnchor),
            mapView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            mapView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            mapView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func configureSearch() {
        searchContainer.backgroundColor = UIColor.secondarySystemBackground.withAlphaComponent(0.96)
        searchContainer.layer.cornerRadius = 16
        searchContainer.layer.cornerCurve = .continuous
        searchContainer.layer.shadowColor = UIColor.black.cgColor
        searchContainer.layer.shadowOpacity = 0.14
        searchContainer.layer.shadowRadius = 14
        searchContainer.layer.shadowOffset = CGSize(width: 0, height: 6)
        searchContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(searchContainer)

        let searchIcon = UIImageView(image: UIImage(systemName: "magnifyingglass"))
        searchIcon.tintColor = UIColor.secondaryLabel
        searchIcon.contentMode = .scaleAspectFit
        searchIcon.translatesAutoresizingMaskIntoConstraints = false

        searchField.placeholder = "搜索地点"
        searchField.font = .systemFont(ofSize: 15)
        searchField.clearButtonMode = .whileEditing
        searchField.returnKeyType = .search
        searchField.delegate = self
        searchField.addTarget(self, action: #selector(searchTextChanged), for: .editingChanged)
        searchField.translatesAutoresizingMaskIntoConstraints = false

        currentLocationButton.setImage(UIImage(systemName: "location.fill"), for: .normal)
        currentLocationButton.tintColor = UIColor.systemBlue
        currentLocationButton.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.12)
        currentLocationButton.layer.cornerRadius = 15
        currentLocationButton.addTarget(self, action: #selector(centerOnCurrentLocation), for: .touchUpInside)
        currentLocationButton.translatesAutoresizingMaskIntoConstraints = false

        searchContainer.addSubview(searchIcon)
        searchContainer.addSubview(searchField)
        searchContainer.addSubview(currentLocationButton)

        NSLayoutConstraint.activate([
            searchContainer.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            searchContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            searchContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            searchContainer.heightAnchor.constraint(equalToConstant: 48),

            searchIcon.leadingAnchor.constraint(equalTo: searchContainer.leadingAnchor, constant: 14),
            searchIcon.centerYAnchor.constraint(equalTo: searchContainer.centerYAnchor),
            searchIcon.widthAnchor.constraint(equalToConstant: 18),
            searchIcon.heightAnchor.constraint(equalToConstant: 18),

            currentLocationButton.trailingAnchor.constraint(equalTo: searchContainer.trailingAnchor, constant: -10),
            currentLocationButton.centerYAnchor.constraint(equalTo: searchContainer.centerYAnchor),
            currentLocationButton.widthAnchor.constraint(equalToConstant: 30),
            currentLocationButton.heightAnchor.constraint(equalToConstant: 30),

            searchField.leadingAnchor.constraint(equalTo: searchIcon.trailingAnchor, constant: 10),
            searchField.trailingAnchor.constraint(equalTo: currentLocationButton.leadingAnchor, constant: -10),
            searchField.topAnchor.constraint(equalTo: searchContainer.topAnchor),
            searchField.bottomAnchor.constraint(equalTo: searchContainer.bottomAnchor)
        ])

        searchCompleter.delegate = self
        searchCompleter.resultTypes = [.address, .pointOfInterest]
    }

    private func configureResults() {
        resultsTableView.backgroundColor = UIColor.secondarySystemBackground.withAlphaComponent(0.98)
        resultsTableView.layer.cornerRadius = 14
        resultsTableView.layer.cornerCurve = .continuous
        resultsTableView.clipsToBounds = true
        resultsTableView.separatorInset = UIEdgeInsets(top: 0, left: 16, bottom: 0, right: 16)
        resultsTableView.register(UITableViewCell.self, forCellReuseIdentifier: "LocationResultCell")
        resultsTableView.dataSource = self
        resultsTableView.delegate = self
        resultsTableView.isHidden = true
        resultsTableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(resultsTableView)

        NSLayoutConstraint.activate([
            resultsTableView.topAnchor.constraint(equalTo: searchContainer.bottomAnchor, constant: 8),
            resultsTableView.leadingAnchor.constraint(equalTo: searchContainer.leadingAnchor),
            resultsTableView.trailingAnchor.constraint(equalTo: searchContainer.trailingAnchor),
            resultsTableView.heightAnchor.constraint(lessThanOrEqualToConstant: 260)
        ])
    }

    private func configureSelectionPanel() {
        selectedLocationView.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.96)
        selectedLocationView.layer.cornerRadius = 18
        selectedLocationView.layer.cornerCurve = .continuous
        selectedLocationView.layer.shadowColor = UIColor.black.cgColor
        selectedLocationView.layer.shadowOpacity = 0.16
        selectedLocationView.layer.shadowRadius = 18
        selectedLocationView.layer.shadowOffset = CGSize(width: 0, height: -4)
        selectedLocationView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(selectedLocationView)

        let pinIcon = UIImageView(image: UIImage(systemName: "mappin.circle.fill"))
        pinIcon.tintColor = UIColor.systemGreen
        pinIcon.contentMode = .scaleAspectFit
        pinIcon.translatesAutoresizingMaskIntoConstraints = false

        selectedTitleLabel.text = "点击地图或搜索选择位置"
        selectedTitleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        selectedTitleLabel.textColor = UIColor.label
        selectedTitleLabel.numberOfLines = 1
        selectedTitleLabel.translatesAutoresizingMaskIntoConstraints = false

        selectedAddressLabel.text = "选中后可以发送到聊天"
        selectedAddressLabel.font = .systemFont(ofSize: 12)
        selectedAddressLabel.textColor = UIColor.secondaryLabel
        selectedAddressLabel.numberOfLines = 2
        selectedAddressLabel.translatesAutoresizingMaskIntoConstraints = false

        sendButton.setTitle("发送位置", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        sendButton.tintColor = .white
        sendButton.backgroundColor = UIColor.systemGreen
        sendButton.layer.cornerRadius = 18
        sendButton.layer.cornerCurve = .continuous
        sendButton.isEnabled = false
        sendButton.alpha = 0.45
        sendButton.addTarget(self, action: #selector(sendLocation), for: .touchUpInside)
        sendButton.translatesAutoresizingMaskIntoConstraints = false

        selectedLocationView.addSubview(pinIcon)
        selectedLocationView.addSubview(selectedTitleLabel)
        selectedLocationView.addSubview(selectedAddressLabel)
        selectedLocationView.addSubview(sendButton)

        NSLayoutConstraint.activate([
            selectedLocationView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            selectedLocationView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            selectedLocationView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            selectedLocationView.heightAnchor.constraint(greaterThanOrEqualToConstant: 92),

            pinIcon.leadingAnchor.constraint(equalTo: selectedLocationView.leadingAnchor, constant: 14),
            pinIcon.centerYAnchor.constraint(equalTo: selectedLocationView.centerYAnchor),
            pinIcon.widthAnchor.constraint(equalToConstant: 32),
            pinIcon.heightAnchor.constraint(equalToConstant: 32),

            sendButton.trailingAnchor.constraint(equalTo: selectedLocationView.trailingAnchor, constant: -14),
            sendButton.centerYAnchor.constraint(equalTo: selectedLocationView.centerYAnchor),
            sendButton.widthAnchor.constraint(equalToConstant: 88),
            sendButton.heightAnchor.constraint(equalToConstant: 36),

            selectedTitleLabel.topAnchor.constraint(equalTo: selectedLocationView.topAnchor, constant: 15),
            selectedTitleLabel.leadingAnchor.constraint(equalTo: pinIcon.trailingAnchor, constant: 12),
            selectedTitleLabel.trailingAnchor.constraint(equalTo: sendButton.leadingAnchor, constant: -12),

            selectedAddressLabel.topAnchor.constraint(equalTo: selectedTitleLabel.bottomAnchor, constant: 5),
            selectedAddressLabel.leadingAnchor.constraint(equalTo: selectedTitleLabel.leadingAnchor),
            selectedAddressLabel.trailingAnchor.constraint(equalTo: selectedTitleLabel.trailingAnchor),
            selectedAddressLabel.bottomAnchor.constraint(lessThanOrEqualTo: selectedLocationView.bottomAnchor, constant: -14)
        ])
    }

    private func requestLocationAccessIfNeeded() {
        locationManager.delegate = self
        switch locationManager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            locationManager.startUpdatingLocation()
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        default:
            centerOnDefaultRegion()
        }
    }

    private func centerOnDefaultRegion() {
        let coordinate = CLLocationCoordinate2D(latitude: 31.2304, longitude: 121.4737)
        let region = MKCoordinateRegion(
            center: coordinate,
            latitudinalMeters: 6000,
            longitudinalMeters: 6000
        )
        mapView.setRegion(region, animated: false)
    }

    @objc private func searchTextChanged() {
        let query = searchField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        activeSearch?.cancel()
        if query.isEmpty {
            searchResults = []
            resultsTableView.reloadData()
            resultsTableView.isHidden = true
        } else {
            searchCompleter.queryFragment = query
            resultsTableView.isHidden = false
        }
    }

    @objc private func centerOnCurrentLocation() {
        guard let coordinate = mapView.userLocation.location?.coordinate else {
            requestLocationAccessIfNeeded()
            return
        }
        let region = MKCoordinateRegion(center: coordinate, latitudinalMeters: 1200, longitudinalMeters: 1200)
        mapView.setRegion(region, animated: true)
        reverseGeocode(coordinate: coordinate)
    }

    @objc private func handleMapTap(_ gesture: UITapGestureRecognizer) {
        let point = gesture.location(in: mapView)
        let coordinate = mapView.convert(point, toCoordinateFrom: mapView)
        reverseGeocode(coordinate: coordinate)
    }

    private func performSearch(query: String) {
        activeSearch?.cancel()
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.region = mapView.region
        let search = MKLocalSearch(request: request)
        activeSearch = search
        search.start { [weak self] response, _ in
            guard let self, let mapItem = response?.mapItems.first else { return }
            self.applyMapItem(mapItem)
            self.resultsTableView.isHidden = true
            self.searchField.resignFirstResponder()
        }
    }

    private func performSearch(completion: MKLocalSearchCompletion) {
        activeSearch?.cancel()
        let request = MKLocalSearch.Request(completion: completion)
        request.region = mapView.region
        let search = MKLocalSearch(request: request)
        activeSearch = search
        search.start { [weak self] response, _ in
            guard let self, let mapItem = response?.mapItems.first else { return }
            self.applyMapItem(mapItem)
            self.searchField.text = completion.title
            self.resultsTableView.isHidden = true
            self.searchField.resignFirstResponder()
        }
    }

    private func applyMapItem(_ mapItem: MKMapItem) {
        let coordinate = mapItem.placemark.coordinate
        let title = mapItem.name?.isEmpty == false ? mapItem.name! : "已选位置"
        let address = formattedAddress(from: mapItem.placemark)
        setSelectedLocation(PickedLocation(title: title, address: address, coordinate: coordinate))
        mapView.setRegion(
            MKCoordinateRegion(center: coordinate, latitudinalMeters: 900, longitudinalMeters: 900),
            animated: true
        )
    }

    private func reverseGeocode(coordinate: CLLocationCoordinate2D) {
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        CLGeocoder().reverseGeocodeLocation(location) { [weak self] placemarks, _ in
            guard let self else { return }
            if let placemark = placemarks?.first {
                let title = placemark.name?.isEmpty == false ? placemark.name! : "已选位置"
                self.setSelectedLocation(
                    PickedLocation(
                        title: title,
                        address: self.formattedAddress(from: placemark),
                        coordinate: coordinate
                    )
                )
            } else {
                self.setSelectedLocation(
                    PickedLocation(
                        title: "已选位置",
                        address: String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude),
                        coordinate: coordinate
                    )
                )
            }
        }
    }

    private func setSelectedLocation(_ location: PickedLocation) {
        selectedLocation = location
        selectedTitleLabel.text = location.title
        selectedAddressLabel.text = location.address
        navigationItem.rightBarButtonItem?.isEnabled = true
        sendButton.isEnabled = true
        sendButton.alpha = 1

        mapView.removeAnnotations(mapView.annotations.filter { !($0 is MKUserLocation) })
        let annotation = MKPointAnnotation()
        annotation.coordinate = location.coordinate
        annotation.title = location.title
        annotation.subtitle = location.address
        mapView.addAnnotation(annotation)
        mapView.selectAnnotation(annotation, animated: true)
    }

    private func formattedAddress(from placemark: MKPlacemark) -> String {
        var parts: [String] = []
        if let administrativeArea = placemark.administrativeArea {
            parts.append(administrativeArea)
        }
        if let locality = placemark.locality, locality != placemark.administrativeArea {
            parts.append(locality)
        }
        if let subLocality = placemark.subLocality {
            parts.append(subLocality)
        }
        if let thoroughfare = placemark.thoroughfare {
            parts.append(thoroughfare)
        }
        if let subThoroughfare = placemark.subThoroughfare {
            parts.append(subThoroughfare)
        }
        return parts.isEmpty ? "位置已选择" : parts.joined(separator: " ")
    }

    private func formattedAddress(from placemark: CLPlacemark) -> String {
        var parts: [String] = []
        if let administrativeArea = placemark.administrativeArea {
            parts.append(administrativeArea)
        }
        if let locality = placemark.locality, locality != placemark.administrativeArea {
            parts.append(locality)
        }
        if let subLocality = placemark.subLocality {
            parts.append(subLocality)
        }
        if let thoroughfare = placemark.thoroughfare {
            parts.append(thoroughfare)
        }
        if let subThoroughfare = placemark.subThoroughfare {
            parts.append(subThoroughfare)
        }
        return parts.isEmpty ? "位置已选择" : parts.joined(separator: " ")
    }

    @objc private func sendLocation() {
        guard let selectedLocation else { return }
        onPickLocation?(selectedLocation, messageType)
        dismiss(animated: true)
    }

    @objc private func cancel() {
        dismiss(animated: true)
    }
}

extension LocationPickerViewController: CLLocationManagerDelegate {
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.startUpdatingLocation()
        case .denied, .restricted:
            centerOnDefaultRegion()
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard !hasCenteredOnUser, let coordinate = locations.last?.coordinate else { return }
        hasCenteredOnUser = true
        mapView.setRegion(
            MKCoordinateRegion(center: coordinate, latitudinalMeters: 1600, longitudinalMeters: 1600),
            animated: true
        )
        manager.stopUpdatingLocation()
    }
}

extension LocationPickerViewController: MKMapViewDelegate {
    func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
        if annotation is MKUserLocation {
            return nil
        }
        let identifier = "PickedLocationAnnotation"
        let annotationView = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
            ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
        annotationView.annotation = annotation
        annotationView.markerTintColor = UIColor.systemGreen
        annotationView.glyphImage = UIImage(systemName: "mappin")
        annotationView.canShowCallout = true
        return annotationView
    }
}

extension LocationPickerViewController: MKLocalSearchCompleterDelegate {
    func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        searchResults = completer.results
        resultsTableView.reloadData()
        resultsTableView.isHidden = searchResults.isEmpty
    }

    func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        searchResults = []
        resultsTableView.reloadData()
        resultsTableView.isHidden = true
    }
}

extension LocationPickerViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        searchResults.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "LocationResultCell", for: indexPath)
        let result = searchResults[indexPath.row]
        var configuration = cell.defaultContentConfiguration()
        configuration.image = UIImage(systemName: "mappin.and.ellipse")
        configuration.text = result.title
        configuration.secondaryText = result.subtitle
        configuration.textProperties.font = .systemFont(ofSize: 14, weight: .semibold)
        configuration.secondaryTextProperties.font = .systemFont(ofSize: 12)
        configuration.secondaryTextProperties.color = UIColor.secondaryLabel
        cell.contentConfiguration = configuration
        cell.backgroundColor = .clear
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        performSearch(completion: searchResults[indexPath.row])
    }
}

extension LocationPickerViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        let query = textField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !query.isEmpty {
            performSearch(query: query)
        }
        return true
    }
}

