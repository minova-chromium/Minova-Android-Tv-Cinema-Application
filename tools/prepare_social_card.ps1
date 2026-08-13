param(
    [Parameter(Mandatory = $true)] [string] $BackgroundSource,
    [Parameter(Mandatory = $true)] [string] $WordmarkSource,
    [Parameter(Mandatory = $true)] [string] $OutputPath
)

Add-Type -AssemblyName System.Drawing

$background = [Drawing.Bitmap]::FromFile($BackgroundSource)
$wordmark = [Drawing.Bitmap]::FromFile($WordmarkSource)
$canvas = [Drawing.Bitmap]::new(1200, 630, [Drawing.Imaging.PixelFormat]::Format32bppArgb)

try {
    $graphics = [Drawing.Graphics]::FromImage($canvas)
    try {
        $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit

        $graphics.DrawImage($background, [Drawing.Rectangle]::new(0, 0, 1200, 630))

        # A subtle left scrim preserves legibility while keeping the generated
        # screening-room image visible and untouched on the television side.
        $scrim = [Drawing.Drawing2D.LinearGradientBrush]::new(
            [Drawing.Point]::new(0, 0),
            [Drawing.Point]::new(760, 0),
            [Drawing.Color]::FromArgb(238, 5, 7, 10),
            [Drawing.Color]::FromArgb(12, 5, 7, 10)
        )
        try { $graphics.FillRectangle($scrim, 0, 0, 760, 630) } finally { $scrim.Dispose() }

        $wordmarkWidth = 330
        $wordmarkHeight = [int][Math]::Round($wordmark.Height * $wordmarkWidth / $wordmark.Width)
        $graphics.DrawImage($wordmark, 72, 84, $wordmarkWidth, $wordmarkHeight)

        $white = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#F7FAFC'))
        $cyan = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#16D8E4'))
        $muted = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#A7B5C5'))
        $display = [Drawing.Font]::new('Bahnschrift', 38, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
        $small = [Drawing.Font]::new('Segoe UI', 17, [Drawing.FontStyle]::Regular, [Drawing.GraphicsUnit]::Pixel)
        try {
            $graphics.DrawString('YOUR PLEX LIBRARY.', $display, $white, 72, 325)
            $graphics.DrawString('CENTER STAGE.', $display, $cyan, 72, 373)
            $graphics.DrawString('ANDROID TV  /  LOCAL STREAMING  /  SOURCE ON GITHUB', $small, $muted, 74, 452)
            $line = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#16D8E4'), 2)
            try { $graphics.DrawLine($line, 74, 496, 240, 496) } finally { $line.Dispose() }
        }
        finally {
            $white.Dispose(); $cyan.Dispose(); $muted.Dispose(); $display.Dispose(); $small.Dispose()
        }
    }
    finally { $graphics.Dispose() }

    $directory = Split-Path -Parent $OutputPath
    if ($directory) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
    $canvas.Save($OutputPath, [Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $canvas.Dispose(); $wordmark.Dispose(); $background.Dispose()
}
