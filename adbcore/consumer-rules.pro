# JNI: PairingContext is bound by class path in adb_pairing.cpp (RegisterNatives)
-keep class com.adbcore.adb.PairingContext { *; }
-keepclassmembers class com.adbcore.adb.PairingContext { *; }

# Public manifest components
-keep class com.adbcore.AdbBootReceiver { *; }
-keep class com.adbcore.AdbCoreProvider { *; }
-keep class com.adbcore.AdbKeepAliveService { *; }

# Server is loaded by class name from libadbcore.so via app_process,
# and its binder is invoked by AIDL-generated stubs — keep everything.
-keep class com.adbcore.server.** { *; }
-keepclassmembers class com.adbcore.server.** { *; }

# AIDL-generated stub
-keep class com.adbcore.server.IAdbCoreService { *; }
-keep class com.adbcore.server.IAdbCoreService$Stub { *; }
-keep class com.adbcore.server.IAdbCoreService$Stub$* { *; }
