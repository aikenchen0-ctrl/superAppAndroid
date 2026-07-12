-keep class com.paifa.ubikitouch.accessibility.UbikiAccessibilityService { *; }

# MediaPipe discovers fields from generated Proto messages by their original names.
-keepclassmembers class com.google.mediapipe.** { <fields>; }

# Flogger locates the enclosing logger through its own stack frame. R8 must not inline it.
-keep class com.google.common.flogger.** { *; }
