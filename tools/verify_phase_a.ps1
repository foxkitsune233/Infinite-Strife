Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$godot = 'D:\GODOT\Godot_v4.6.1-stable_mono_win64\Godot_v4.6.2-stable_win64.exe'
$logDirectory = Join-Path $PSScriptRoot 'build'
$logFile = Join-Path $logDirectory 'phase-a-verify.txt'
$windowsArtifact = Join-Path $projectRoot 'exports\windows\InfiniteStrife.exe'
$androidArtifact = Join-Path $projectRoot 'exports\android\InfiniteStrife-debug.apk'

if (-not (Test-Path $godot)) { throw "Godot executable not found: $godot" }
New-Item -ItemType Directory -Force $logDirectory | Out-Null

$lines = @("Godot phase A verification", "generated_utc=$([DateTime]::UtcNow.ToString('o'))")
foreach ($test in @('res://tests/test_project_boot.gd', 'res://tests/test_asset_register.gd', 'res://tests/test_battle_engine.gd', 'res://tests/test_battle_screen.gd')) {
    & $godot --headless --path $projectRoot --script $test
    $exitCode = if (Test-Path variable:LASTEXITCODE) { $LASTEXITCODE } else { 0 }
    if ($exitCode -ne 0) { throw "Test failed: $test" }
    $lines += "test=pass $test"
}

$projectText = Get-Content (Join-Path $projectRoot 'project.godot') -Raw
if ($projectText -notmatch 'run/main_scene="res://scenes/MainMenu.tscn"') { throw 'Main scene is not configured.' }
if ((Get-Content (Join-Path $projectRoot 'assets\ASSET_REGISTER.md') -Raw) -match '\|[ \t]*\|') { throw 'Asset register contains a blank table cell.' }

foreach ($artifact in @($windowsArtifact, $androidArtifact)) {
    if (-not (Test-Path $artifact)) { throw "Missing export artifact: $artifact" }
    $hash = (Get-FileHash $artifact -Algorithm SHA256).Hash
    $lines += "artifact=$artifact sha256=$hash"
}

& $godot --version | ForEach-Object { $lines += "godot=$_" }
$lines | Set-Content -LiteralPath $logFile -Encoding utf8
Write-Output "Phase A verification passed: $logFile"
