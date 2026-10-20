param([int]$First = 1, [int]$Last = 9)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$artDir = Join-Path $projectDir 'art-source/independent-items-v3'
$manifest = Get-Content (Join-Path $artDir 'manifest.json') -Raw | ConvertFrom-Json
for ($sheetNumber = $First; $sheetNumber -le $Last; $sheetNumber++) {
    $group = $manifest.groups[$sheetNumber - 1]
    $prefix = if ($sheetNumber -eq 1) { 'objects-01-flat' } else { $group.key }
    $destination = Join-Path $artDir "originals/$prefix.png"
    if (Test-Path -LiteralPath $destination) { Write-Output "$prefix exists"; continue }
    & bl image generate --prompt $group.prompt --size '2048*2048' --n 1 --watermark false --prompt-extend false --out-dir (Join-Path $artDir 'originals') --out-prefix $prefix --timeout 180 --output json 2>&1 |
        Set-Content -Encoding utf8 (Join-Path $artDir "$prefix.log")
    if ($LASTEXITCODE -ne 0) { throw "$prefix failed; see project log" }
    Write-Output "$prefix generated"
}
