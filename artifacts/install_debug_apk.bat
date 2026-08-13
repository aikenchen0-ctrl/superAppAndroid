@echo off
"C:\Android\Sdk\platform-tools\adb.exe" -s d0512adb install -r "C:\WorkSpace\UbikiTouch\app\build\outputs\apk\debug\app-debug.apk"
exit /b %errorlevel%
