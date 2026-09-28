[CmdletBinding()]
param(
    [string]$DeviceSerial = "",
    [string]$OutputPath = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = "com.minova.cinema.debug"
$testPackageName = "com.minova.cinema.debug.test"
$testClass = "com.minova.cinema.showcase.TrailerShowcaseAutomation"
$remoteCapture = "/sdcard/minova_trailer_raw.mp4"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB was not found at $adb. Install Android SDK Platform-Tools first."
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $projectRoot "recordings\minova_cinema_trailer_fresh.mp4"
}
$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $commandArguments = @()
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $commandArguments += @("-s", $DeviceSerial)
    }
    $commandArguments += $Arguments
    & $adb @commandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: adb $($commandArguments -join ' ')"
    }
}

function Read-Setting([string]$Namespace, [string]$Name) {
    $value = Invoke-Adb shell settings get $Namespace $Name
    return ($value | Select-Object -Last 1).Trim()
}

function Restore-Setting([string]$Namespace, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq "null") {
        Invoke-Adb shell settings delete $Namespace $Name | Out-Null
    } else {
        Invoke-Adb shell settings put $Namespace $Name $Value | Out-Null
    }
}

Push-Location $projectRoot
$recordProcess = $null
$instrumentProcess = $null
$testSucceeded = $false
$originalSettings = @{}
try {
    $devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
    if ($devices.Count -ne 1 -and [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        throw "Connect exactly one Android TV/emulator or pass -DeviceSerial."
    }

    if (-not $SkipBuild) {
        & .\gradlew.bat assembleDebug assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw "Debug showcase build failed." }
    }

    $debugApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if (-not (Test-Path -LiteralPath $debugApk) -or -not (Test-Path -LiteralPath $testApk)) {
        throw "Showcase APKs are missing. Run without -SkipBuild first."
    }

    Invoke-Adb install -r $debugApk | Out-Host
    Invoke-Adb install -r $testApk | Out-Host

    # Clean only the debug/test packages. The user's installed release build,
    # Plex login and living-room settings are never touched.
    Invoke-Adb shell pm clear $packageName | Out-Host
    Invoke-Adb shell pm clear $testPackageName | Out-Host
    Invoke-Adb shell am force-stop $packageName
    Invoke-Adb shell rm -f $remoteCapture

    foreach ($entry in @(
        @{ Namespace = "global"; Name = "heads_up_notifications_enabled"; Value = "0" },
        @{ Namespace = "global"; Name = "window_animation_scale"; Value = "1.0" },
        @{ Namespace = "global"; Name = "transition_animation_scale"; Value = "1.0" },
        @{ Namespace = "global"; Name = "animator_duration_scale"; Value = "1.0" }
    )) {
        $key = "$($entry.Namespace):$($entry.Name)"
        $originalSettings[$key] = Read-Setting $entry.Namespace $entry.Name
        Invoke-Adb shell settings put $entry.Namespace $entry.Name $entry.Value | Out-Null
    }
    Invoke-Adb shell cmd statusbar collapse | Out-Null
    Invoke-Adb logcat -c

    # Arm the test in the background. Its Activity remains on a black frame and
    # creates a private ready marker; the actual intro does not begin until the
    # recorder is running and the script writes the go marker.
    $instrumentArguments = @()
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $instrumentArguments += @("-s", $DeviceSerial)
    }
    $instrumentArguments += @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", $testClass,
        "-e", "waitForCaptureSignal", "true",
        "$testPackageName/androidx.test.runner.AndroidJUnitRunner"
    )
    $instrumentStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $instrumentStartInfo.FileName = $adb
    $instrumentStartInfo.Arguments = $instrumentArguments -join " "
    $instrumentStartInfo.UseShellExecute = $false
    $instrumentStartInfo.CreateNoWindow = $true
    $instrumentStartInfo.RedirectStandardOutput = $true
    $instrumentStartInfo.RedirectStandardError = $true
    $instrumentProcess = [System.Diagnostics.Process]::new()
    $instrumentProcess.StartInfo = $instrumentStartInfo
    if (-not $instrumentProcess.Start()) { throw "Could not start trailer instrumentation." }

    $readyDeadline = [DateTime]::UtcNow.AddSeconds(20)
    $captureReady = $false
    do {
        if ($instrumentProcess.HasExited) { break }
        $probeArguments = @()
        if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
            $probeArguments += @("-s", $DeviceSerial)
        }
        $probeArguments += @("shell", "run-as", $packageName, "ls", "cache/showcase_ready")
        & $adb @probeArguments *> $null
        $captureReady = $LASTEXITCODE -eq 0
        if (-not $captureReady) { Start-Sleep -Milliseconds 100 }
    } while ([DateTime]::UtcNow -lt $readyDeadline)
    if (-not $captureReady) {
        throw "Trailer showcase did not arm within 20 seconds."
    }

    $recordArguments = @()
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $recordArguments += @("-s", $DeviceSerial)
    }
    $recordArguments += @(
        "shell", "-tt", "screenrecord",
        "--size", "1920x1080",
        "--bit-rate", "24000000",
        "--time-limit", "120",
        $remoteCapture
    )
    $recordStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $recordStartInfo.FileName = $adb
    $recordStartInfo.Arguments = $recordArguments -join " "
    $recordStartInfo.UseShellExecute = $false
    $recordStartInfo.CreateNoWindow = $true
    $recordStartInfo.RedirectStandardInput = $true
    $recordProcess = [System.Diagnostics.Process]::new()
    $recordProcess.StartInfo = $recordStartInfo
    if (-not $recordProcess.Start()) { throw "Could not start Android screenrecord." }
    Start-Sleep -Milliseconds 350

    $screenRecordPid = Invoke-Adb shell pidof screenrecord
    if ([string]::IsNullOrWhiteSpace(($screenRecordPid | Out-String))) {
        throw "Android screenrecord did not start."
    }

    Invoke-Adb -Arguments @(
        "shell", "run-as", $packageName, "touch", "cache/showcase_go"
    ) | Out-Null

    if (-not $instrumentProcess.WaitForExit(90000)) {
        $instrumentProcess.Kill()
        throw "Trailer showcase instrumentation exceeded 90 seconds."
    }
    $instrumentationText = $instrumentProcess.StandardOutput.ReadToEnd()
    $instrumentationError = $instrumentProcess.StandardError.ReadToEnd()
    $instrumentationText | Out-Host
    if (-not [string]::IsNullOrWhiteSpace($instrumentationError)) {
        $instrumentationError | Out-Host
    }
    if ($instrumentationText -match "FAILURES!!!" -or
        $instrumentationText -notmatch "OK \(1 test\)") {
        throw "Trailer showcase instrumentation reported a failure."
    }
    $testSucceeded = $true
}
finally {
    if ($null -ne $instrumentProcess -and -not $instrumentProcess.HasExited) {
        try { $instrumentProcess.Kill() } catch { Write-Warning $_ }
    }
    if ($null -ne $recordProcess -and -not $recordProcess.HasExited) {
        # The PTY converts Ctrl+C into SIGINT inside Android. This lets
        # screenrecord write the MP4 moov atom before it exits.
        try {
            $recordProcess.StandardInput.Write([char]3)
            $recordProcess.StandardInput.Flush()
            $recordProcess.StandardInput.Close()
        } catch { Write-Warning $_ }
        if (-not $recordProcess.WaitForExit(10000)) {
            $recordProcess.Kill()
        }
    }
    Start-Sleep -Seconds 2

    if ($testSucceeded) {
        Invoke-Adb pull $remoteCapture $OutputPath | Out-Host
    }
    try { Invoke-Adb shell am force-stop $packageName | Out-Null } catch { Write-Warning $_ }
    try { Invoke-Adb shell rm -f $remoteCapture | Out-Null } catch { Write-Warning $_ }

    foreach ($key in $originalSettings.Keys) {
        $parts = $key.Split(":", 2)
        try { Restore-Setting $parts[0] $parts[1] $originalSettings[$key] } catch { Write-Warning $_ }
    }
    Pop-Location
}

if (-not $testSucceeded) {
    throw "Trailer showcase automation failed; no recording was exported."
}

$capture = Get-Item -LiteralPath $OutputPath
if ($capture.Length -lt 1MB) {
    throw "The capture is unexpectedly small ($($capture.Length) bytes)."
}
Write-Host "Trailer showcase captured successfully: $($capture.FullName)"
Write-Host "Size: $([math]::Round($capture.Length / 1MB, 2)) MB"
