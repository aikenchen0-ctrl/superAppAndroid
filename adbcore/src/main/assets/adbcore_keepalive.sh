#!/system/bin/sh
# ====================================================================
#  adbcore unified keep-alive script
#  ------------------------------------------------------------------
#  Executed by adbcore_server (uid=2000, ADB shell SELinux domain) on a
#  fixed interval — default every 5s, configured by the caller of
#  AdbCore.armKeepAlive(intervalMs).
#
#  All commands are wrapped with 2>/dev/null and the script always exits
#  zero, so a single ROM-rejected setprop / settings write never
#  poisons the loop scheduler.
#
#  Edit this file to tune the keep-alive policy — no Kotlin recompile
#  needed; the host App reads it from :adbcore/assets at task arm time
#  and pushes the (template-substituted) string into the server.
#
#  Template placeholders (substituted by AdbCore at load time):
#    {{HOST_PKG}}      host App's packageName
#    {{HOST_SERVICE}}  fully-qualified AdbKeepAliveService class name
#    {{TCP_PORT}}      ADB TCP/IP port (DEFAULT_TCPIP_PORT, e.g. 6088)
# ====================================================================

# (1) Wireless debugging — keep the master switch on.
#     Defends against:
#       - user toggling "wireless debugging" off in Developer Options
#       - ROM power policy resetting it after WiFi loss
#       - OEM custom resets
#     Runs as SHELL uid, so it does NOT need WRITE_SECURE_SETTINGS.
settings put global adb_wifi_enabled 1 2>/dev/null
settings put global adb_allowed_connection_time 0 2>/dev/null

# (2) ADB TCP/IP mode — keep adbd listening on 0.0.0.0:{{TCP_PORT}}.
#     Once this is in effect, 127.0.0.1:{{TCP_PORT}} stays reachable
#     from inside the device, which means the host App can re-launch
#     the server without WiFi / mDNS / external network.
#     Most custom ROMs (e.g. ColorOS) reject SHELL writes to
#     service.adb.tcp.port via SELinux — that's expected, the line
#     simply no-ops in that case.
setprop service.adb.tcp.port {{TCP_PORT}} 2>/dev/null

# (3) Host App process keep-alive — bring it up via Service so the
#     process runs in the background WITHOUT pulling any UI to the
#     foreground. Idempotent: if the App is already running this just
#     fires onStartCommand once more, no harm done.
#     Android 12+ background-service-start restrictions DO NOT apply
#     here because the caller is uid SHELL, AMS exempts shell.
am startservice --user 0 -n "{{HOST_PKG}}/{{HOST_SERVICE}}" >/dev/null 2>&1

exit 0
