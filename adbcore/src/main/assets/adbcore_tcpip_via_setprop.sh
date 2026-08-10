#!/system/bin/sh
# ====================================================================
#  adbcore: enable ADB TCP/IP mode via SHELL setprop (path B fallback)
#  ------------------------------------------------------------------
#  Most ROMs reject SHELL writes to service.adb.tcp.port via SELinux.
#  On AOSP-near / lightly-customized ROMs this still works.
#
#  Sequence:
#    1. setprop service.adb.tcp.port {{TCP_PORT}}
#    2. stop adbd                    — kill the running daemon
#    3. start adbd                   — init re-launches it, picking up
#                                      the new property
#
#  Each line is independent; we deliberately do NOT chain with `&&`
#  because a partial success (setprop ok, stop fails) still benefits
#  from the start step. exit 0 at the end keeps the loop scheduler
#  happy regardless of any individual command's status.
#
#  Template placeholder:
#    {{TCP_PORT}}  the desired ADB TCP/IP listen port
# ====================================================================

setprop service.adb.tcp.port {{TCP_PORT}} 2>/dev/null
stop adbd 2>/dev/null
start adbd 2>/dev/null

exit 0
