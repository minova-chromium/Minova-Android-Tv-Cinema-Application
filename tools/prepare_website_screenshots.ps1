param(
    [Parameter(Mandatory = $true)] [string] $SourceDirectory,
    [Parameter(Mandatory = $true)] [string] $OutputDirectory
)

Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$captures = [ordered]@{
    'minova-home.png' = 'app-home.jpg'
    'minova-search-results.png' = 'app-search.jpg'
    'minova-resume-detail.png' = 'app-detail.jpg'
    'minova-seasons.png' = 'app-seasons.jpg'
    'minova-cast.png' = 'app-cast.jpg'
    'minova-playback-settings.png' = 'app-playback-settings.jpg'
}

$jpegEncoder = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object { $_.MimeType -eq 'image/jpeg' } |
    Select-Object -First 1
$encoderParameters = [Drawing.Imaging.EncoderParameters]::new(1)
$encoderParameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new(
    [Drawing.Imaging.Encoder]::Quality,
    [long] 88
)

try {
    foreach ($capture in $captures.GetEnumerator()) {
        $sourcePath = Join-Path $SourceDirectory $capture.Key
        if (-not (Test-Path $sourcePath)) { throw "Missing screenshot: $sourcePath" }

        $source = [Drawing.Bitmap]::FromFile($sourcePath)
        $result = [Drawing.Bitmap]::new(1920, 1080, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
        try {
            $graphics = [Drawing.Graphics]::FromImage($result)
            try {
                $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.DrawImage($source, 0, 0, 1920, 1080)
            }
            finally { $graphics.Dispose() }

            $outputPath = Join-Path $OutputDirectory $capture.Value
            $result.Save($outputPath, $jpegEncoder, $encoderParameters)
        }
        finally {
            $result.Dispose()
            $source.Dispose()
        }
    }
}
finally {
    $encoderParameters.Dispose()
}
