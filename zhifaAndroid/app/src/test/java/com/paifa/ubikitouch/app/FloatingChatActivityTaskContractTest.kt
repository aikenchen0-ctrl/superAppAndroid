package com.paifa.ubikitouch.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatActivityTaskContractTest {
    @Test
    fun floatingChatBridgeActivitiesDoNotShareSettingsTask() {
        val manifest = androidManifest()
        val mainAffinity = manifest.activity(".MainActivity").taskAffinity.orEmpty()
        val floatingAffinity = "com.paifa.ubikitouch.floatingchat"

        listOf(
            ".FloatingChatMediaPickerActivity",
            ".FloatingChatCameraActivity",
            ".FloatingChatVoicePermissionActivity",
            ".FloatingChatLocationPermissionActivity",
            ".FloatingChatMediaPreviewActivity"
        ).forEach { activityName ->
            val activity = manifest.activity(activityName)
            assertEquals(floatingAffinity, activity.taskAffinity)
            assertNotEquals(mainAffinity, activity.taskAffinity)
            assertEquals("true", activity.excludeFromRecents)
        }
    }

    @Test
    fun gestureServiceDeclaresPersistenceHooks() {
        val manifest = androidManifest()
        assertTrue(manifest.usesPermission("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.usesPermission("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.usesPermission("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(manifest.usesPermission("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))

        val service = manifest.service("com.paifa.ubikitouch.accessibility.UbikiAccessibilityService")
        assertEquals("false", service.stopWithTask)
        assertEquals("specialUse", service.foregroundServiceType)
        assertEquals(
            "persistent_edge_gesture_accessibility_overlay",
            service.property("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
        )

        val receiver = manifest.receiver(".GesturePersistenceReceiver")
        assertEquals("true", receiver.exported)
        assertEquals("true", receiver.directBootAware)
        assertTrue(receiver.actions.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(receiver.actions.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertTrue(receiver.actions.contains("android.intent.action.USER_UNLOCKED"))
        assertTrue(receiver.actions.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(receiver.actions.contains("com.paifa.ubikitouch.action.RECOVER_GESTURE"))

        val keepAlive = manifest.service("com.paifa.ubikitouch.accessibility.UbikiGestureKeepAliveService")
        assertEquals("false", keepAlive.exported)
        assertEquals("false", keepAlive.stopWithTask)
        assertEquals("specialUse", keepAlive.foregroundServiceType)
        assertEquals(
            "persistent_edge_gesture_keep_alive",
            keepAlive.property("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
        )
    }

    @Test
    fun gesturePersistenceUsesWatchdogAfterTaskRemoval() {
        val persistenceSource = sourceFile(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiGesturePersistence.kt"
        ).readText()
        val keepAliveSource = sourceFile(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiGestureKeepAliveService.kt"
        ).readText()

        assertTrue(persistenceSource.contains("AlarmManager"))
        assertTrue(persistenceSource.contains("ACTION_RECOVER_GESTURE"))
        assertTrue(keepAliveSource.contains("onTaskRemoved"))
        assertTrue(keepAliveSource.contains("scheduleRecoveryWatchdog"))
        assertTrue(keepAliveSource.contains("requestOverlayRecoveryCheck"))
    }

    private fun androidManifest(): ManifestDoc {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidates.firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        return ManifestDoc(document)
    }

    private fun sourceFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("../$path")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}

private class ManifestDoc(
    private val document: org.w3c.dom.Document
) {
    fun usesPermission(name: String): Boolean {
        val nodes = document.getElementsByTagName("uses-permission")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as org.w3c.dom.Element
            if (node.getAttribute("android:name") == name) return true
        }
        return false
    }

    fun activity(name: String): ManifestActivity {
        val nodes = document.getElementsByTagName("activity")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as org.w3c.dom.Element
            if (node.getAttribute("android:name") == name) {
                return ManifestActivity(
                    name = name,
                    taskAffinity = node.optionalAttribute("android:taskAffinity"),
                    excludeFromRecents = node.optionalAttribute("android:excludeFromRecents")
                )
            }
        }
        assertTrue("Activity $name exists in manifest", false)
        error("unreachable")
    }

    fun service(name: String): ManifestService {
        val nodes = document.getElementsByTagName("service")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as org.w3c.dom.Element
            if (node.getAttribute("android:name") == name) {
                val properties = linkedMapOf<String, String>()
                val children = node.getElementsByTagName("property")
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex) as org.w3c.dom.Element
                    properties[child.getAttribute("android:name")] = child.getAttribute("android:value")
                }
                return ManifestService(
                    name = name,
                    exported = node.optionalAttribute("android:exported"),
                    stopWithTask = node.optionalAttribute("android:stopWithTask"),
                    foregroundServiceType = node.optionalAttribute("android:foregroundServiceType"),
                    properties = properties
                )
            }
        }
        assertTrue("Service $name exists in manifest", false)
        error("unreachable")
    }

    fun receiver(name: String): ManifestReceiver {
        val nodes = document.getElementsByTagName("receiver")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as org.w3c.dom.Element
            if (node.getAttribute("android:name") == name) {
                return ManifestReceiver(
                    name = name,
                    exported = node.optionalAttribute("android:exported"),
                    directBootAware = node.optionalAttribute("android:directBootAware"),
                    actions = node.intentFilterActions()
                )
            }
        }
        assertTrue("Receiver $name exists in manifest", false)
        error("unreachable")
    }
}

private data class ManifestActivity(
    val name: String,
    val taskAffinity: String?,
    val excludeFromRecents: String?
)

private data class ManifestService(
    val name: String,
    val exported: String?,
    val stopWithTask: String?,
    val foregroundServiceType: String?,
    val properties: Map<String, String>
) {
    fun property(name: String): String? = properties[name]
}

private data class ManifestReceiver(
    val name: String,
    val exported: String?,
    val directBootAware: String?,
    val actions: Set<String>
)

private fun org.w3c.dom.Element.optionalAttribute(name: String): String? {
    return if (hasAttribute(name)) getAttribute(name) else null
}

private fun org.w3c.dom.Element.intentFilterActions(): Set<String> {
    val actions = linkedSetOf<String>()
    val filters = getElementsByTagName("intent-filter")
    for (filterIndex in 0 until filters.length) {
        val filter = filters.item(filterIndex) as org.w3c.dom.Element
        val nodes = filter.getElementsByTagName("action")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as org.w3c.dom.Element
            actions += node.getAttribute("android:name")
        }
    }
    return actions
}
