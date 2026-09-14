package com.blinkvoice.visual.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BlinkApiContractTest {
    private static final List<Class<?>> PUBLIC_TYPES = Arrays.asList(
            BlinkOptions.class,
            BlinkFrame.class,
            BlinkEvent.class,
            BlinkEventType.class,
            BlinkState.class,
            BlinkError.class,
            BlinkDiagnostics.class,
            BlinkListener.class,
            BlinkSession.class,
            BlinkSessionFactory.class,
            DefaultBlinkSessionFactory.class,
            BlinkEvaluationMetrics.class,
            BlinkEventEvaluator.class,
            BlinkReplayEvent.class,
            BlinkReplayCsv.class,
            BlinkReplayReport.class,
            BlinkReplayCli.class,
            BlinkReplayFrame.class,
            BlinkReplayFrameCsv.class,
            BlinkReplayRunner.class
    );

    @Test
    public void publicContractDoesNotReferencePlatformVisionTypes() {
        for (Class<?> type : PUBLIC_TYPES) {
            assertFalse(type.getName(), type.getName().contains("android"));
            for (Field field : type.getDeclaredFields()) {
                assertAllowedType(type, field.getType());
            }
            for (Method method : type.getDeclaredMethods()) {
                assertAllowedType(type, method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertAllowedType(type, parameter);
                }
            }
        }
    }

    @Test
    public void valueTypesAreImmutable() {
        for (Class<?> type : PUBLIC_TYPES) {
            if (type.isEnum() || type.isInterface()) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    assertTrue(type.getName() + "." + field.getName(),
                            Modifier.isPrivate(field.getModifiers())
                                    || Modifier.isStatic(field.getModifiers()));
                    if (!Modifier.isStatic(field.getModifiers())) {
                        assertTrue(type.getName() + "." + field.getName(),
                                Modifier.isFinal(field.getModifiers()));
                    }
                }
            }
        }
    }

    @Test
    public void optionsValidateAndDefensivelyCopyEventTypes() {
        BlinkOptions options = new BlinkOptions.Builder().build();
        assertEquals(3, options.getEventTypes().size());
        try {
            options.getEventTypes().clear();
            throw new AssertionError("eventTypes must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected contract behavior.
        }
    }

    @Test
    public void lifecycleContractExposesStartStopAndClose() throws Exception {
        assertTrue(BlinkSession.class.getMethod("start") != null);
        assertTrue(BlinkSession.class.getMethod("stop") != null);
        assertTrue(BlinkSession.class.getMethod("close") != null);
        assertTrue(BlinkSession.class.getMethod("submitFrame", BlinkFrame.class) != null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void frameRejectsOutOfRangeMetrics() {
        new BlinkFrame(1L, true, 1.2d, 0.5d, 0.9d);
    }

    private static void assertAllowedType(Class<?> owner, Class<?> type) {
        String name = type.getName();
        assertFalse(owner.getName() + " leaks " + name, name.startsWith("android."));
        assertFalse(owner.getName() + " leaks " + name, name.startsWith("androidx."));
        assertFalse(owner.getName() + " leaks " + name, name.startsWith("com.google.mediapipe."));
        assertFalse(owner.getName() + " leaks " + name, name.contains("ImageProxy"));
        assertFalse(owner.getName() + " leaks " + name, name.contains("MPImage"));
        assertFalse(owner.getName() + " leaks " + name, name.contains("FaceLandmarkerResult"));
        assertFalse(owner.getName() + " leaks " + name, name.contains("BlinkDetector"));
        assertFalse(owner.getName() + " leaks " + name, name.contains("BlinkEventClassifier"));
    }
}
