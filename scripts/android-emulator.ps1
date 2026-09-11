# مصروفي — تشغيل التطبيق على محاكي أندرويد وتجربته (HANDOVER: بروتوكول المحاكي)
# Usage: powershell -ExecutionPolicy Bypass -File scripts/android-emulator.ps1 -Action <action> [-Apk path] [-Out path] [-X n -Y n]
# Actions: setup | start | install | launch | shot | ui | tap | back | log | logclear | status | stop
# No "type text" action on purpose: passwords are entered by the owner in the emulator window, never by an agent.
param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('setup', 'start', 'install', 'launch', 'shot', 'ui', 'tap', 'back', 'log', 'logclear', 'status', 'stop')]
  [string]$Action,
  [string]$Apk = '',
  [string]$Out = '',
  [int]$X = -1,
  [int]$Y = -1
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$tools = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Documents/Codex/android-build-tools'
if (!$env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem "$tools/java" -Directory | Select-Object -First 1).FullName }
$env:ANDROID_HOME = "$tools/sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
$emulator = "$env:ANDROID_HOME/emulator/emulator.exe"
$avdmanager = "$env:ANDROID_HOME/cmdline-tools/unpacked/cmdline-tools/bin/avdmanager.bat"
$avd = 'masroufy-pixel8'
$image = 'system-images;android-35;google_apis;x86_64'
$package = 'app.masroufy.personal'
$shots = Join-Path $env:TEMP 'masroufy-emulator'
New-Item -ItemType Directory -Path $shots -Force | Out-Null

# Windows PowerShell 5.1 turns native stderr into terminating errors under 'Stop'; judge by exit code.
function Invoke-Native([scriptblock]$Command) {
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & $Command 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $previous }
}
function Assert-Device {
  $devices = Invoke-Native { & $adb devices } | Where-Object { $_ -match "`tdevice$" }
  if (!$devices) { throw 'No running emulator/device. Run -Action start first.' }
}
function Wait-Boot {
  Invoke-Native { & $adb wait-for-device } | Out-Null
  for ($i = 0; $i -lt 180; $i++) {
    $booted = (Invoke-Native { & $adb shell getprop sys.boot_completed }) -join ''
    if ($booted.Trim() -eq '1') { return }
    Start-Sleep -Seconds 2
  }
  throw 'Emulator did not finish booting within 6 minutes'
}
function Get-AppPid { ((Invoke-Native { & $adb shell pidof $package }) -join '').Trim() }

switch ($Action) {
  'setup' {
    if (!(Test-Path -LiteralPath $emulator)) { throw "Emulator not installed at $emulator (see HANDOVER emulator protocol)" }
    $sysdir = Join-Path $env:ANDROID_HOME 'system-images/android-35/google_apis/x86_64'
    if (!(Test-Path -LiteralPath (Join-Path $sysdir 'system.img'))) { throw "System image missing: $image (see HANDOVER emulator protocol)" }
    $avdRoot = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android/avd'
    $avdDir = Join-Path $avdRoot "$avd.avd"
    if (!(Test-Path -LiteralPath (Join-Path $avdDir 'config.ini'))) {
      # Written directly instead of avdmanager: from cmdline-tools/unpacked it cannot locate the SDK
      # ("Valid system image paths are: null"), and .bat argument parsing splits the ';' package id.
      New-Item -ItemType Directory -Path $avdDir -Force | Out-Null
      $utf8 = New-Object System.Text.UTF8Encoding($false)
      [IO.File]::WriteAllLines((Join-Path $avdRoot "$avd.ini"),
        [string[]]@('avd.ini.encoding=UTF-8', "path=$avdDir", "path.rel=avd\$avd.avd", 'target=android-35'), $utf8)
      [IO.File]::WriteAllLines((Join-Path $avdDir 'config.ini'), [string[]]@(
        "AvdId=$avd", "avd.ini.displayname=$avd", 'avd.ini.encoding=UTF-8', 'PlayStore.enabled=false',
        'abi.type=x86_64', 'hw.cpu.arch=x86_64', 'hw.cpu.ncore=4', 'hw.ramSize=4096', 'vm.heapSize=512',
        'disk.dataPartition.size=6G', 'hw.device.name=pixel_8', 'hw.lcd.width=1080', 'hw.lcd.height=2400', 'hw.lcd.density=420',
        'hw.keyboard=yes', 'hw.gpu.enabled=yes', 'hw.gpu.mode=auto', 'hw.sdCard=no', 'showDeviceFrame=no',
        'image.sysdir.1=system-images\android-35\google_apis\x86_64\', 'tag.id=google_apis', 'tag.display=Google APIs',
        'target=android-35'), $utf8)
      "AVD $avd created at $avdDir"
    } else { "AVD $avd already exists" }
    Invoke-Native { & $emulator -accel-check } | Out-Host
  }
  'start' {
    if (Invoke-Native { & $adb devices } | Where-Object { $_ -match '^emulator-\d+\s+device$' }) { 'Emulator already running'; break }
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $avd, '-no-boot-anim', '-gpu', 'auto', '-netspeed', 'full', '-netdelay', 'none')
    Wait-Boot
    'Booted'
  }
  'install' {
    Assert-Device
    if (!$Apk) {
      $Apk = (Get-ChildItem (Join-Path $project 'dist-android-apk') -Filter 'masroufy-trial-v*.apk' |
        Sort-Object { [int]([regex]::Match($_.Name, 'v(\d+)').Groups[1].Value) } | Select-Object -Last 1).FullName
    }
    if (!$Apk -or !(Test-Path -LiteralPath $Apk)) { throw 'APK not found' }
    # -r = update in place, keeps app data (the signed-in session) like the owner's phone update
    $result = Invoke-Native { & $adb install -r $Apk }
    $result | Out-Host
    if ($LASTEXITCODE -ne 0 -or -not ($result -match 'Success')) { throw "Install failed for $Apk" }
  }
  'launch' {
    Assert-Device
    Invoke-Native { & $adb shell monkey -p $package -c android.intent.category.LAUNCHER 1 } | Out-Null
    Start-Sleep -Seconds 3
    "pid: $(Get-AppPid)"
  }
  'shot' {
    Assert-Device
    if (!$Out) { $Out = Join-Path $shots ("shot-" + (Get-Date -Format 'HHmmss') + '.png') }
    # screencap via file + pull: piping binary through PowerShell corrupts PNGs
    Invoke-Native { & $adb shell screencap -p /sdcard/masroufy-shot.png } | Out-Null
    Invoke-Native { & $adb pull /sdcard/masroufy-shot.png $Out } | Out-Null
    if (!(Test-Path -LiteralPath $Out)) { throw 'Screenshot failed' }
    $Out
  }
  'ui' {
    Assert-Device
    if (!$Out) { $Out = Join-Path $shots 'ui.xml' }
    Invoke-Native { & $adb shell uiautomator dump /sdcard/masroufy-ui.xml } | Out-Null
    Invoke-Native { & $adb pull /sdcard/masroufy-ui.xml $Out } | Out-Null
    [xml]$doc = Get-Content -LiteralPath $Out -Encoding UTF8
    # WebView content appears as nodes with text/content-desc; bounds give tap coordinates
    $doc.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object {
      $label = if ($_.text) { $_.text } else { $_.'content-desc' }
      "{0}  {1}" -f $_.bounds, $label
    }
  }
  'tap' {
    Assert-Device
    if ($X -lt 0 -or $Y -lt 0) { throw 'tap needs -X and -Y (pixels, from -Action ui bounds)' }
    Invoke-Native { & $adb shell input tap $X $Y } | Out-Null
    "tapped $X,$Y"
  }
  'back' { Assert-Device; Invoke-Native { & $adb shell input keyevent 4 } | Out-Null; 'back' }
  'log' {
    Assert-Device
    $appPid = Get-AppPid
    if ($appPid) { Invoke-Native { & $adb logcat -d -v time --pid=$appPid } | Select-Object -Last 300 }
    else { Invoke-Native { & $adb logcat -d -v time } | Select-String 'masroufy|Capacitor|chromium|AndroidRuntime|FATAL' | Select-Object -Last 200 | ForEach-Object { "$_" } }
  }
  'logclear' { Assert-Device; Invoke-Native { & $adb logcat -c } | Out-Null; 'log cleared' }
  'status' {
    Invoke-Native { & $adb devices } | Out-Host
    $info = Invoke-Native { & $adb shell dumpsys package $package } | Select-String 'versionCode=|versionName=' | Select-Object -First 2
    if ($info) { $info | ForEach-Object { "$_".Trim() } } else { "$package not installed" }
    "app pid: $(Get-AppPid)"
  }
  'stop' { Invoke-Native { & $adb emu kill } | Out-Host; 'stop requested' }
}
