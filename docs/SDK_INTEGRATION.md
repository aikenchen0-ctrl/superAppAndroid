# SDK Integration

## Local Publish

Build and publish SDK artifacts to local Maven:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat publishToMavenLocal --no-daemon
```

Artifacts:

```text
com.paifa.ubikitouch:ubiki-core:0.1.0
com.paifa.ubikitouch:ubiki-overlay:0.1.0
com.paifa.ubikitouch:ubiki-accessibility:0.1.0
```

## Host App Dependency

```kotlin
repositories {
    mavenLocal()
    google()
    mavenCentral()
}

dependencies {
    implementation("com.paifa.ubikitouch:ubiki-accessibility:0.1.0")
}
```

## Host Manifest

The host app must declare the accessibility service. The user must manually enable it in Android settings.

```xml
<service
    android:name="com.paifa.ubikitouch.accessibility.UbikiAccessibilityService"
    android:exported="false"
    android:label="@string/ubiki_accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:stopWithTask="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/ubiki_accessibility_service" />
</service>
```

The bundled service XML enables `flagRetrieveInteractiveWindows` and `canRetrieveWindowContent` so the SDK can detect input method windows and hide edge bars while the keyboard is visible. Keep this permission surface visible to users in production onboarding.

## SDK Facade

```kotlin
val ubikiTouch = UbikiTouch.create(context)
ubikiTouch.globalEnabled = true
ubikiTouch.setOverlayAppearance(
    showIndicators = true,
    opacityPercent = 42,
    swipeThresholdDp = 24,
    hapticFeedback = true,
    disableInLandscape = true,
    disableWhenKeyboardShown = true
)
ubikiTouch.setAction(EdgeSide.LEFT, GestureType.PULL_INWARD, GestureAction.Back)
ubikiTouch.setAction(EdgeSide.LEFT, GestureType.PULL_INWARD_HOLD, GestureAction.Home)
ubikiTouch.setEdgeConfig(
    EdgeZoneConfig(
        side = EdgeSide.LEFT,
        enabled = true,
        thicknessDp = 24,
        topInsetPercent = 15,
        bottomInsetPercent = 15
    )
)
ubikiTouch.setEdgeConfigs(
    EdgeSide.RIGHT,
    listOf(
        EdgeZoneConfig(
            side = EdgeSide.RIGHT,
            zoneId = 0,
            enabled = true,
            thicknessDp = 24,
            topInsetPercent = 0,
            bottomInsetPercent = 55
        ),
        EdgeZoneConfig(
            side = EdgeSide.RIGHT,
            zoneId = 1,
            enabled = true,
            thicknessDp = 24,
            topInsetPercent = 55,
            bottomInsetPercent = 0
        )
    )
)
ubikiTouch.addBlockedPackage("com.example.game")
ubikiTouch.pauseFor(5L * 60L * 1000L)
ubikiTouch.resumeNow()
```
