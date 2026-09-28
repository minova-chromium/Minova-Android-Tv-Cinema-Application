[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-z0-9-]+$')][string]$Name,
    [Parameter(Mandatory = $true)][string]$Sequence,
    [string]$DeviceSerial = 'emulator-5554',
    [switch]$ColdLaunch
)

# This captures the INSTALLED REAL APPLICATION. No showcase Activity, fixture
# library, fabricated playback badges, or app-data reset is used.
$ErrorActionPreference = 'Stop'
$adbCinema = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $projectRoot 'recordings\real-trailer\raw'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$remoteFile = "/sdcard/minova-real-$Name.mp4"
$outputFile = Join-Path $destination "$Name.mp4"
if (Test-Path -LiteralPath $outputFile) { throw "Capture already exists: $outputFile" }
function Adb([string[]]$Command) {
    & $adbCinema -s $DeviceSerial @Command
    if ($LASTEXITCODE -ne 0) { throw 'ADB command failed.' }
}
$savedSettings = @{}
$recorder = $null
$finished = $false
try {
    foreach ($setting in @('heads_up_notifications_enabled', 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')) {
        $savedSettings[$setting] = (Adb @('shell', 'settings', 'get', 'global', $setting) | Out-String).Trim()
        $value = if ($setting -eq 'heads_up_notifications_enabled') { '0' } else { '1.0' }
        Adb @('shell', 'settings', 'put', 'global', $setting, $value) | Out-Null
    }
    Adb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    if ($ColdLaunch) { Adb @('shell', 'am', 'force-stop', 'com.minova.cinema') }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $adbCinema
    $startInfo.Arguments = "-s $DeviceSerial shell -tt screenrecord --size 1920x1080 --bit-rate 24000000 --time-limit 120 $remoteFile"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $recorder = [Diagnostics.Process]::new()
    $recorder.StartInfo = $startInfo
    if (-not $recorder.Start()) { throw 'Could not start screen recording.' }
    Start-Sleep -Milliseconds 400
    if ($ColdLaunch) { Adb @('shell', 'am', 'start', '-n', 'com.minova.cinema/.MainActivity') | Out-Null }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    foreach ($step in $Sequence.Split(',')) {
        $parts = $step.Trim().Split(':', 2)
        switch ($parts[0]) {
            'wait' { Start-Sleep -Milliseconds ([int](1000 * [double]::Parse($parts[1], [Globalization.CultureInfo]::InvariantCulture))) }
            'key' {
                Write-Host ('{0:N2}s D-pad {1}' -f $clock.Elapsed.TotalSeconds, $parts[1])
                Adb @('shell', 'input', 'keyevent', $parts[1]) | Out-Null
            }
            default { throw "Unknown capture action: $step" }
        }
    }
    $finished = $true
} finally {
    if ($null -ne $recorder -and -not $recorder.HasExited) {
        $recorder.StandardInput.Write([char]3)
        $recorder.StandardInput.Flush()
        $recorder.StandardInput.Close()
        if (-not $recorder.WaitForExit(10000)) { $recorder.Kill(); $finished = $false }
    }
    Start-Sleep -Seconds 2
    if ($finished) {
        Adb @('pull', $remoteFile, $outputFile) | Out-Host
        Adb @('shell', 'rm', '-f', $remoteFile) | Out-Null
        Adb @('shell', 'screencap', '-p', '/sdcard/minova-real-review.png') | Out-Null
        Adb @('pull', '/sdcard/minova-real-review.png', (Join-Path $destination "$Name-end.png")) | Out-Null
        Adb @('shell', 'rm', '-f', '/sdcard/minova-real-review.png') | Out-Null
    }
    foreach ($setting in $savedSettings.Keys) {
        $previous = $savedSettings[$setting]
        if ($previous -eq 'null') { Adb @('shell', 'settings', 'delete', 'global', $setting) | Out-Null }
        else { Adb @('shell', 'settings', 'put', 'global', $setting, $previous) | Out-Null }
    }
}
if (-not $finished) { throw 'Capture did not complete. Remote partial file retained for recovery.' }
Write-Host "Real app footage: $outputFile"
