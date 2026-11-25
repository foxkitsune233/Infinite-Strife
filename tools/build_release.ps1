param(
    [string]$GodotPath = 'D:/GODOT/Godot_v4.7.1-stable_mono_win64/Godot_v4.7.1-stable_win64_console.exe',
    [string]$SigningConfigPath = "$env:APPDATA/Godot/keystores/infinite-strife-godot-release/signing.json",
    [string]$Version = '0.1.0-preview.2',
    [string]$AndroidSdkPath = 'D:/Android/Sdk',
    [ValidateSet('Windows Release','Android Release')][string[]]$Targets = @('Windows Release','Android Release')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $projectRoot "exports/releases/godot-v$Version"
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$signing = Get-Content -Raw -LiteralPath $SigningConfigPath | ConvertFrom-Json
$env:GODOT_ANDROID_KEYSTORE_RELEASE_PATH = $signing.path
$env:GODOT_ANDROID_KEYSTORE_RELEASE_USER = $signing.user
$env:GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD = $signing.password
try {
    foreach ($target in @(@('Windows Release',"InfiniteStrife-Godot-$Version-windows-x64.exe"), @('Android Release',"InfiniteStrife-Godot-$Version-android.apk"))) {
        if ($target[0] -notin $Targets) { continue }
        $logPath = Join-Path $outputRoot ($target[0].Split(' ')[0] + '-export.log')
        & $GodotPath --headless --path $projectRoot --export-release $target[0] (Join-Path $outputRoot $target[1]) 2>&1 | Set-Content -LiteralPath $logPath
        if ($LASTEXITCODE -ne 0 -or (Get-Content -Raw -LiteralPath $logPath) -match '(?m)(SCRIPT ERROR:|ERROR:)') { throw "Export failed: $logPath" }
        if ($target[0] -eq 'Android Release') {
            $buildTools = Get-ChildItem (Join-Path $AndroidSdkPath 'build-tools') -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
            $apk = Join-Path $outputRoot $target[1]
            & (Join-Path $buildTools.FullName 'apksigner.bat') verify $apk
            if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
            $manifest = & (Join-Path $buildTools.FullName 'aapt.exe') dump xmltree $apk AndroidManifest.xml
            if ($LASTEXITCODE -ne 0 -or ($manifest | Out-String) -notmatch 'android:screenOrientation.*\(type 0x10\)0x1(?:\s|$)') { throw 'APK manifest is not locked to portrait' }
            $manifest | Set-Content (Join-Path $outputRoot 'Android-manifest.txt')
        }
    }
    $exe = Join-Path $outputRoot "InfiniteStrife-Godot-$Version-windows-x64.exe"
    if ('Windows Release' -in $Targets) { Compress-Archive -LiteralPath $exe -DestinationPath ($exe -replace '\.exe$', '.zip') -Force }
    $files = Get-ChildItem -LiteralPath $outputRoot -File | Where-Object { $_.Extension -in '.apk','.zip' }
    $files | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name } | Set-Content (Join-Path $outputRoot 'SHA256SUMS.txt')
    $files | Select-Object Name,Length
} finally {
    Remove-Item Env:GODOT_ANDROID_KEYSTORE_RELEASE_PATH,Env:GODOT_ANDROID_KEYSTORE_RELEASE_USER,Env:GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD -ErrorAction SilentlyContinue
}
