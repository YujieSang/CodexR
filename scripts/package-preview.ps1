param(
    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$BuildToolsVersion = '36.0.0'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $gradleFile = Get-Content -LiteralPath 'app/build.gradle.kts' -Raw
    $version = [regex]::Match($gradleFile, 'versionName\s*=\s*"([A-Za-z0-9.-]+)"').Groups[1].Value
    if (-not $version) { throw 'Could not read the app version.' }
    $buildTools = Join-Path $SdkRoot "build-tools\$BuildToolsVersion"
    $zipalign = Join-Path $buildTools 'zipalign.exe'
    $apksigner = Join-Path $buildTools 'apksigner.bat'
    $aapt = Join-Path $buildTools 'aapt.exe'
    foreach ($tool in @($zipalign, $apksigner, $aapt)) {
        if (-not (Test-Path -LiteralPath $tool)) { throw "Android build tool not found: $tool" }
    }
    # This is a preview signing identity, never a production signing configuration.
    $keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
    if (-not (Test-Path -LiteralPath $keystore)) {
        throw 'Existing development keystore not found. Refusing to create a different upgrade identity implicitly.'
    }
    & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or validation failed.' }

    $dist = Join-Path $root 'dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    $unsigned = Join-Path $root 'app/build/outputs/apk/release/app-release-unsigned.apk'
    $aligned = Join-Path $dist "CodexR-v$version-aligned-unsigned.apk"
    $apk = Join-Path $dist "CodexR-v$version.apk"
    & $zipalign -f -P 16 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    & $apksigner sign --ks $keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apk $aligned
    if ($LASTEXITCODE -ne 0) { throw 'Preview APK signing failed.' }
    & $apksigner verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed.' }
    & $zipalign -c -P 16 4 $apk
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
    $badging = & $aapt dump badging $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata inspection failed.' }
    if ($badging -match 'application-debuggable') { throw 'Refusing to package a debuggable release APK.' }
    $packageLine = $badging | Select-String '^package:'
    if ($packageLine -notmatch "versionName='$([regex]::Escape($version))'") { throw 'APK version does not match source.' }
    if ($packageLine -notmatch "name='com.example.codexmobile'") { throw 'Unexpected application ID.' }
    Write-Output $packageLine
    # Generated distribution metadata, not source/configuration.
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS.txt'), "$hash  $([System.IO.Path]::GetFileName($apk))`n", [System.Text.UTF8Encoding]::new($false))
    Write-Output "Preview APK ready: $apk"
    Write-Output "SHA-256: $hash"
} finally {
    Pop-Location
}
