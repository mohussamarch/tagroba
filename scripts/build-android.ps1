param(
  [string]$OutputDirectory = "",
  [string]$PreviousApk = ""
)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskTools = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Documents/Codex/android-build-tools'
if (!$env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem "$taskTools/java" -Directory | Select-Object -First 1).FullName }
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = "$taskTools/sdk" }
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = "$taskTools/gradle" }
if (!$env:MASROUFY_TRIAL_KEYSTORE) { $env:MASROUFY_TRIAL_KEYSTORE = "$taskTools/signing/masroufy-trial.keystore" }
if (!(Test-Path -LiteralPath $env:MASROUFY_TRIAL_KEYSTORE)) { throw 'Trial signing key missing. Restore the original key; never generate a replacement for an update.' }
$env:PATH = "$env:JAVA_HOME/bin;C:/Program Files/nodejs;" + $env:PATH
if (!$OutputDirectory) { $OutputDirectory = Join-Path $taskProject 'dist-android-apk' }
# Windows PowerShell 5.1 turns ANY stderr line of a native command (e.g. Vite's chunk-size
# warning) into a terminating error under 'Stop'. Run natives with 'Continue', return text
# lines, and judge success by $LASTEXITCODE only (checked after every call below).
function Invoke-Native([scriptblock]$Command) {
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & $Command 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $previous }
}
Push-Location $taskProject
try {
  Invoke-Native { npm.cmd run android:sync } | Out-Host
  if ($LASTEXITCODE -ne 0) { throw 'Android web build failed' }
  Push-Location android
  try {
    Invoke-Native { ./gradlew.bat :app:assembleDebug --no-daemon --console=plain } | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
  } finally { Pop-Location }
  $taskApk = Join-Path $taskProject 'android/app/build/outputs/apk/debug/app-debug.apk'
  $taskBuildTools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
  $taskSignature = Invoke-Native { & "$taskBuildTools/apksigner.bat" verify --print-certs $taskApk }
  if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
  $taskCert = ($taskSignature | Select-String '^Signer #1 certificate SHA-256 digest:').Line
  $taskBadging = Invoke-Native { & "$taskBuildTools/aapt.exe" dump badging $taskApk }
  if ($LASTEXITCODE -ne 0) { throw 'APK manifest check failed' }
  $taskPackage = ($taskBadging | Select-String '^package:').Line
  $taskVersionCode = [int]([regex]::Match($taskPackage, "versionCode='(\d+)'").Groups[1].Value)
  if ($PreviousApk) {
    $taskOldSignature = Invoke-Native { & "$taskBuildTools/apksigner.bat" verify --print-certs $PreviousApk }
    if ($LASTEXITCODE -ne 0) { throw 'Previous APK signature invalid' }
    $taskOldCert = ($taskOldSignature | Select-String '^Signer #1 certificate SHA-256 digest:').Line
    if (!$taskCert -or $taskCert -ne $taskOldCert) { throw 'Signing certificate differs: cannot update the installed APK' }
    $taskOldBadging = Invoke-Native { & "$taskBuildTools/aapt.exe" dump badging $PreviousApk }
    if ($LASTEXITCODE -ne 0) { throw 'Previous APK manifest invalid' }
    $taskOldPackage = ($taskOldBadging | Select-String '^package:').Line
    $taskOldCode = [int]([regex]::Match($taskOldPackage, "versionCode='(\d+)'").Groups[1].Value)
    if ($taskVersionCode -le $taskOldCode) { throw 'New versionCode must exceed the installed version' }
    foreach ($taskPackageLine in @($taskPackage,$taskOldPackage)) {
      if ($taskPackageLine -notmatch "name='app.masroufy.personal'") { throw 'Application ID differs' }
    }
  }
  New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
  $taskDestination = Join-Path $OutputDirectory "masroufy-trial-v$taskVersionCode.apk"
  Copy-Item -LiteralPath $taskApk -Destination $taskDestination -Force
  Write-Output "Verified APK: $taskDestination"
  Get-FileHash -LiteralPath $taskDestination -Algorithm SHA256 | Select-Object Algorithm,Hash
} finally { Pop-Location }
