param(
    [switch]$SkipDeviceTests
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot 'gradlew.bat'

if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper not found at $gradle"
}

$tasks = @(
    'testDebugUnitTest'
    'lintDebug'
    'assembleDebug'
)

if (-not $SkipDeviceTests) {
    # Runs the Compose TV suite on the connected Android TV emulator/device.
    # Debug builds use com.minova.cinema.debug, so the public signed app and
    # its Plex configuration remain installed and untouched.
    $tasks += 'connectedDebugAndroidTest'
    # Keep promotional recording orchestration out of regression runs.
    $tasks += '-Pandroid.testInstrumentationRunnerArguments.package=com.minova.cinema.ui'
}

Push-Location $projectRoot
try {
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Minova Cinema checks failed with exit code $LASTEXITCODE."
    }
    if (-not $SkipDeviceTests) {
        $adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
        if (-not (Test-Path -LiteralPath $adbPath)) { throw 'ADB not found for startup checks.' }
        $devices = @(& $adbPath devices | Where-Object { $_ -match '\sdevice$' })
        if ($devices.Count -eq 1) {
            $serial = ($devices[0] -split '\s+')[0]
            $testApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
            & $adbPath -s $serial install -r $testApk
            if ($LASTEXITCODE -ne 0) { throw 'Could not install the isolated debug build.' }
            $coldLaunch = & $adbPath -s $serial shell am start -W -S -n 'com.minova.cinema.debug/com.minova.cinema.MainActivity'
            if ($LASTEXITCODE -ne 0 -or -not ($coldLaunch -match 'Status: ok')) {
                throw 'Cold launcher startup failed.'
            }
            $timeLine = $coldLaunch | Where-Object { $_ -match '^TotalTime:' } | Select-Object -First 1
            if (-not $timeLine) { throw 'ADB did not report cold startup timing.' }
            $startupMs = [int](($timeLine -split ':')[1].Trim())
            if ($startupMs -gt 5000) { throw "Cold first-frame startup exceeded 5000 ms: $startupMs ms" }
            Write-Host "Cold debug launcher first frame: $startupMs ms (not Plex library readiness)."
        } else {
            Write-Warning 'Cold launcher check needs exactly one connected device; skipped.'
        }
    }
    Write-Host 'Minova Cinema TV checks passed.' -ForegroundColor Green
} finally {
    Pop-Location
}
