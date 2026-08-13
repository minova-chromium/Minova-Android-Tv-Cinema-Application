param(
    [string] $KeytoolPath = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
    [string] $DistinguishedName = "CN=Minova Cinema, OU=Minova, O=Minova, L=Brussels, C=BE"
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot 'release-signing'
$keystorePath = Join-Path $signingDirectory 'minova-cinema-release.jks'
$propertiesPath = Join-Path $projectRoot 'keystore.properties'

if ((Test-Path $keystorePath) -or (Test-Path $propertiesPath)) {
    throw 'Release signing material already exists. Refusing to overwrite it.'
}
if (-not (Test-Path $KeytoolPath)) {
    throw "keytool was not found at $KeytoolPath"
}

function New-RandomPassword([int] $Length = 40) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%_-'
    $bytes = [byte[]]::new($Length)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null
$storePassword = New-RandomPassword
$keyPassword = New-RandomPassword

try {
    & $KeytoolPath -genkeypair -v `
        -keystore $keystorePath `
        -storetype PKCS12 `
        -storepass $storePassword `
        -alias minova-cinema `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -keypass $keyPassword `
        -dname $DistinguishedName | Out-Null

    $utf8NoBom = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllLines($propertiesPath, @(
        'storeFile=release-signing/minova-cinema-release.jks'
        "storePassword=$storePassword"
        'keyAlias=minova-cinema'
        "keyPassword=$storePassword"
    ), $utf8NoBom)

    $backupNotice = Join-Path $signingDirectory 'BACK_UP_THESE_FILES.txt'
    [IO.File]::WriteAllLines($backupNotice, @(
        'BACK UP minova-cinema-release.jks and the project keystore.properties file together.'
        'Future Android updates must be signed with this same key.'
        'Neither file is tracked by Git.'
    ), $utf8NoBom)

    Write-Output "Created release signing material in $signingDirectory. Passwords were not printed."
}
catch {
    if (Test-Path $keystorePath) { Remove-Item -LiteralPath $keystorePath }
    if (Test-Path $propertiesPath) { Remove-Item -LiteralPath $propertiesPath }
    throw
}
