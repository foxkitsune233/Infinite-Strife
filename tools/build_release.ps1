param(
    [string]$GodotPath = 'D:/GODOT/Godot_v4.7.1-stable_mono_win64/Godot_v4.7.1-stable_win64_console.exe',
    [string]$SigningConfigPath = "$env:APPDATA/Godot/keystores/infinite-strife-godot-release/signing.json"
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $projectRoot 'exports/releases/godot-v0.1.0-preview.1'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$signing = Get-Content -Raw -LiteralPath $SigningConfigPath | ConvertFrom-Json
$env:GODOT_ANDROID_KEYSTORE_RELEASE_PATH = $signing.path
$env:GODOT_ANDROID_KEYSTORE_RELEASE_USER = $signing.user
$env:GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD = $signing.password
try {
    foreach ($target in @(@('Windows Release','InfiniteStrife-Godot-0.1.0-preview.1-windows-x64.exe'), @('Android Release','InfiniteStrife-Godot-0.1.0-preview.1-android.apk'))) {
        $logPath = Join-Path $outputRoot ($target[0].Split(' ')[0] + '-export.log')
        & $GodotPath --headless --path $projectRoot --export-release $target[0] (Join-Path $outputRoot $target[1]) 2>&1 | Set-Content -LiteralPath $logPath
        if ($LASTEXITCODE -ne 0 -or (Get-Content -Raw -LiteralPath $logPath) -match '(?m)(SCRIPT ERROR:|ERROR:)') { throw "Export failed: $logPath" }
    }
    $exe = Join-Path $outputRoot 'InfiniteStrife-Godot-0.1.0-preview.1-windows-x64.exe'
    Compress-Archive -LiteralPath $exe -DestinationPath ($exe -replace '\.exe$', '.zip') -Force
    $files = Get-ChildItem -LiteralPath $outputRoot -File | Where-Object { $_.Extension -in '.apk','.zip' }
    $files | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name } | Set-Content (Join-Path $outputRoot 'SHA256SUMS.txt')
    $files | Select-Object Name,Length
} finally {
    Remove-Item Env:GODOT_ANDROID_KEYSTORE_RELEASE_PATH,Env:GODOT_ANDROID_KEYSTORE_RELEASE_USER,Env:GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD -ErrorAction SilentlyContinue
}
