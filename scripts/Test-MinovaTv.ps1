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
}

Push-Location $projectRoot
try {
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Minova Cinema checks failed with exit code $LASTEXITCODE."
    }
    Write-Host 'Minova Cinema TV checks passed.' -ForegroundColor Green
} finally {
    Pop-Location
}
