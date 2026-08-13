param(
    [Parameter(Mandatory = $true)]
    [string] $MinovaSource,
    [Parameter(Mandatory = $true)]
    [string] $CinemaSource,
    [Parameter(Mandatory = $true)]
    [string] $OutputDirectory,
    [string] $WebsiteOutputDirectory = ""
)

Add-Type -AssemblyName System.Drawing

function Read-EmbeddedPng([string] $SvgPath) {
    $svg = Get-Content -LiteralPath $SvgPath -Raw
    $match = [regex]::Match($svg, 'base64,([^\"]+)')
    if (-not $match.Success) {
        throw "No embedded PNG was found in $SvgPath"
    }

    $bytes = [Convert]::FromBase64String($match.Groups[1].Value)
    $stream = [IO.MemoryStream]::new($bytes, $false)
    try {
        $source = [Drawing.Bitmap]::FromStream($stream)
        return [Drawing.Bitmap]::new($source)
    }
    finally {
        if ($null -ne $source) { $source.Dispose() }
        $stream.Dispose()
    }
}

function Repair-AlphaMatte(
    [Drawing.Bitmap] $Source,
    [Drawing.Color] $White,
    [Drawing.Color] $Cyan
) {
    # These exported SVGs contain transparent PNGs whose edge RGB values were
    # matted against black. Keep the original alpha coverage but restore each
    # edge pixel to its intended solid logo color. Six transparent pixels of
    # padding prevent texture filtering from sampling outside the artwork.
    $padding = 6
    $result = [Drawing.Bitmap]::new(
        $Source.Width + ($padding * 2),
        $Source.Height + ($padding * 2),
        [Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $result.SetResolution(96, 96)

    for ($y = 0; $y -lt $Source.Height; $y++) {
        for ($x = 0; $x -lt $Source.Width; $x++) {
            $pixel = $Source.GetPixel($x, $y)
            if ($pixel.A -le 2) { continue }

            $isCyan = (($pixel.B - $pixel.R) -gt 18) -or (($pixel.G - $pixel.R) -gt 18)
            $color = if ($isCyan) { $Cyan } else { $White }
            $repaired = [Drawing.Color]::FromArgb($pixel.A, $color.R, $color.G, $color.B)
            $result.SetPixel($x + $padding, $y + $padding, $repaired)
        }
    }
    return $result
}

function Save-Png([Drawing.Bitmap] $Bitmap, [string] $Path) {
    $Bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
if ($WebsiteOutputDirectory) {
    New-Item -ItemType Directory -Force -Path $WebsiteOutputDirectory | Out-Null
}

$minovaEmbedded = Read-EmbeddedPng $MinovaSource
$cinemaEmbedded = Read-EmbeddedPng $CinemaSource
try {
    $minova = Repair-AlphaMatte $minovaEmbedded `
        ([Drawing.Color]::FromArgb(254, 254, 254)) `
        ([Drawing.Color]::FromArgb(0, 211, 245))
    $cinema = Repair-AlphaMatte $cinemaEmbedded `
        ([Drawing.Color]::FromArgb(243, 243, 235)) `
        ([Drawing.Color]::FromArgb(10, 224, 234))
    try {
        Save-Png $minova (Join-Path $OutputDirectory 'minova_wordmark.png')
        Save-Png $cinema (Join-Path $OutputDirectory 'cinema_wordmark.png')
        if ($WebsiteOutputDirectory) {
            Save-Png $minova (Join-Path $WebsiteOutputDirectory 'minova-wordmark.png')
            Save-Png $cinema (Join-Path $WebsiteOutputDirectory 'cinema-wordmark.png')
        }

        # Both names use the same visual width in the lockup. This reads cleanly
        # at both the compact 76dp TV header and large launch-screen scale.
        $combined = [Drawing.Bitmap]::new(
            1000,
            380,
            [Drawing.Imaging.PixelFormat]::Format32bppArgb
        )
        try {
            $graphics = [Drawing.Graphics]::FromImage($combined)
            try {
                $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceOver
                $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality

                $targetWidth = 900
                $minovaHeight = [int][Math]::Round($minova.Height * $targetWidth / $minova.Width)
                $cinemaHeight = [int][Math]::Round($cinema.Height * $targetWidth / $cinema.Width)
                $graphics.DrawImage($minova, 50, 22, $targetWidth, $minovaHeight)
                $graphics.DrawImage($cinema, 50, 202, $targetWidth, $cinemaHeight)
            }
            finally {
                $graphics.Dispose()
            }
            Save-Png $combined (Join-Path $OutputDirectory 'minova_cinema_wordmark.png')
            if ($WebsiteOutputDirectory) {
                Save-Png $combined (Join-Path $WebsiteOutputDirectory 'minova-cinema-wordmark.png')
            }
        }
        finally {
            $combined.Dispose()
        }
    }
    finally {
        $minova.Dispose()
        $cinema.Dispose()
    }
}
finally {
    $minovaEmbedded.Dispose()
    $cinemaEmbedded.Dispose()
}
