package com.blinkvoice.visual.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class BlinkVoiceContinuousDetectorTest {
    @Test
    public void frameExposesElaAverageAndClassifierState() {
        BlinkVoiceFrame frame = new BlinkVoiceFrame(
                true,
                false,
                70f,
                72f,
                3L,
                640,
                480,
                90,
                "OPEN",
                "OPEN_NO_CLOSE",
                "-",
                0L,
                0L
        );

        assertEquals(true, frame.hasFace());
        assertEquals(false, frame.isClosed());
        assertEquals(70f, frame.getLeftEla(), 0.0001f);
        assertEquals(72f, frame.getRightEla(), 0.0001f);
        assertEquals(71f, frame.getAverageEla(), 0.0001f);
        assertEquals(3L, frame.getInferenceMs());
        assertEquals(640, frame.getImageWidth());
        assertEquals(480, frame.getImageHeight());
        assertEquals(90, frame.getRotationDegrees());
        assertEquals("OPEN", frame.getPhase());
        assertEquals("OPEN_NO_CLOSE", frame.getLastReason());
        assertEquals("-", frame.getLastEvent());
        assertEquals(0L, frame.getClosedDurationMs());
        assertEquals(0L, frame.getPendingElapsedMs());
    }

    @Test
    public void builderCreatesJavaCallableContinuousDetector() {
        Context context = RuntimeEnvironment.getApplication();
        TestLifecycleOwner lifecycleOwner = new TestLifecycleOwner();
        PreviewView previewView = new PreviewView(context);
        BlinkVoiceContinuousListener listener = new BlinkVoiceContinuousListener() {
        };

        BlinkVoiceContinuousDetector detector = new BlinkVoiceContinuousDetector.Builder(
                context,
                lifecycleOwner,
                previewView,
                listener
        )
                .setOptions(null)
                .setUsePreloadedDetector(false)
                .setTargetResolution(640, 480)
                .build();

        assertNotNull(detector);
    }

    private static final class TestLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

        TestLifecycleOwner() {
            lifecycle.setCurrentState(Lifecycle.State.RESUMED);
        }

        @Override
        public Lifecycle getLifecycle() {
            return lifecycle;
        }
    }
}
