package dev.composescene3d.sample.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import dev.composescene3d.compose.rememberSceneCameraState
import dev.composescene3d.core.CameraDescription
import dev.composescene3d.core.Vec3
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

fun MainViewController() = ComposeUIViewController { LifecycleAwareIosSample() }

/**
 * Removes the Metal viewport from composition while iOS has suspended the application.
 *
 * Filament's iOS surface destroys its swap chain from DisposableEffect. Mounting it again after
 * UIApplicationDidBecomeActive creates a fresh CAMetalLayer-backed swap chain, so rendering never
 * continues against a drawable that iOS has invalidated in the background.
 */
@Composable
private fun LifecycleAwareIosSample() {
    var isActive by remember { mutableStateOf(true) }
    // This state intentionally lives above the lifecycle gate. The native Metal viewport and its
    // renderer are recreated after backgrounding, while the user's orbit/pan/zoom remains intact.
    val cameraState = rememberSceneCameraState(
        CameraDescription(eye = Vec3(0f, 2f, 5f), target = Vec3(0f, 0.5f, 0f))
    )

    DisposableEffect(Unit) {
        val notifications = NSNotificationCenter.defaultCenter
        val resignObserver = notifications.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { isActive = false }
        val activeObserver = notifications.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { isActive = true }

        onDispose {
            notifications.removeObserver(resignObserver)
            notifications.removeObserver(activeObserver)
        }
    }

    if (isActive) {
        IosSample(cameraState = cameraState)
    } else {
        Box(Modifier.fillMaxSize())
    }
}
