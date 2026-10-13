# build_apk.ps1
# -----------------------------------------------------------------------------
# Standalone PowerShell build script for Double Power Key (former VivoToMirPay).
#
# Usage:
#   .\build_apk.ps1              # builds release APK signed with debug key by default
#   .\build_apk.ps1 -Debug       # builds debug APK
#   .\build_apk.ps1 -Sign <keystore> -StorePass <pass> -Alias <keyAlias> -KeyPass <pass>
#
# Requirements:
#   * JDK 17+ on PATH (java/javac)
#   * Android SDK with platforms;android-35 and build-tools;35.0.0
#   * Either Gradle on PATH (will run `gradle` directly) OR no Gradle — the script
#     falls back to `gradlew.bat` (Gradle Wrapper, already in repo) which downloads
#     the right Gradle version on first run.
#
# Output:
#   app\build\outputs\apk\release\app-release.apk     (default)
#   app\build\outputs\apk\debug\app-debug.apk         (with -Debug)
# -----------------------------------------------------------------------------

[CmdletBinding()]
param(
    [switch]$Debug,
    [string]$Sign,
    [string]$StorePass,
    [string]$Alias,
    [string]$KeyPass
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "== Double Power Key build script ==" -ForegroundColor Cyan
Write-Host "Repo root: $Root"

if (-not $env:ANDROID_HOME) {
    Write-Host "ANDROID_HOME not set — relying on gradlew to find Android SDK via local.properties." -ForegroundColor Yellow
}

# Pick Gradle command: prefer system gradle, else use the bundled wrapper.
function Resolve-GradleCommand {
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $wrapper = Join-Path $Root "gradlew.bat"
    if (Test-Path $wrapper) { return $wrapper }
    throw "Gradle not found on PATH and gradlew.bat is missing from $Root"
}

$gradle = Resolve-GradleCommand
Write-Host "Using Gradle: $gradle"

# Set signing env vars if user passed explicit keystore info.
if ($Sign) {
    $env:VVTM_KEYSTORE = $Sign
    $env:VVTM_KEYSTORE_PASSWORD = $StorePass
    $env:VVTM_KEY_ALIAS = $Alias
    $env:VVTM_KEY_PASSWORD = $KeyPass
    Write-Host "Using custom keystore: $Sign (alias $Alias)"
}

$task = if ($Debug) { "assembleDebug" } else { "assembleRelease" }
Write-Host "Running task: $task"

& $gradle -p $Root $task
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed."
}

$outDir = Join-Path $Root (if ($Debug) { "app\build\outputs\apk\debug" } else { "app\build\outputs\apk\release" })
$apkName = if ($Debug) { "app-debug.apk" } else { "app-release.apk" }
$apkPath = Join-Path $outDir $apkName

if (Test-Path $apkPath) {
    Write-Host ""
    Write-Host "Build OK. APK:" -ForegroundColor Green
    Write-Host "  $apkPath" -ForegroundColor Green
} else {
    throw "APK not found at expected path: $apkPath"
}
