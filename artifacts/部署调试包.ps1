$tool = 'C:\Android\Sdk\platform-tools\adb.exe'
$arguments = @('-s', 'd0512adb', 'install', '-r', 'C:\WorkSpace\UbikiTouch\app\build\outputs\apk\debug\app-debug.apk')
$process = Start-Process -FilePath $tool -ArgumentList $arguments -Wait -NoNewWindow -PassThru
exit $process.ExitCode
