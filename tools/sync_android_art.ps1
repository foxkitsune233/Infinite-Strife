[CmdletBinding()]
param([string]$AndroidRoot = 'D:\android_project\DouQiuQiu')

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = (Resolve-Path -LiteralPath (Join-Path $AndroidRoot 'app/src/main/assets')).Path
$targetRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'assets/android'))
if ($targetRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $sourceRoot.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $sourceRoot -eq $targetRoot) { throw 'Source and destination must not overlap.' }

function Assert-NoLink([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -Force -LiteralPath $current).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Refusing linked path: $current"
            }
        }
        $parent = Split-Path -Parent $current
        if ($parent -eq $current) { break }
        $current = $parent
    }
}
Assert-NoLink $sourceRoot
Assert-NoLink $targetRoot
foreach ($folder in @('art', 'licenses')) {
    $root = Join-Path $sourceRoot $folder
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw "Missing source: $root" }
    foreach ($entry in @(Get-Item -LiteralPath $root) + @(Get-ChildItem -LiteralPath $root -Force -Recurse)) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked source: $($entry.FullName)" }
    }
}

function Write-JsonIfChanged([string]$Path, $Value) {
    Assert-NoLink $Path
    $json = ($Value | ConvertTo-Json -Depth 20) + "`n"
    if ((Test-Path -LiteralPath $Path) -and [IO.File]::ReadAllText($Path) -ceq $json) { return }
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

$files = @('art', 'licenses') | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $sourceRoot $_) -File -Recurse } | Sort-Object FullName
$records = @()
$copied = 0
$unchanged = 0
$totalBytes = [long]0
$cards = [ordered]@{}
$equipment = [ordered]@{}
foreach ($file in $files) {
    $relative = $file.FullName.Substring($sourceRoot.Length + 1).Replace('\', '/')
    $target = [IO.Path]::GetFullPath((Join-Path $targetRoot $relative))
    if (-not $target.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "Escaping destination: $relative" }
    Assert-NoLink $target
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ((Test-Path -LiteralPath $target -PathType Leaf) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $hash) {
        $unchanged++
    } else {
        [IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
        $copied++
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) { throw "Copy verification failed: $relative" }
    $records += [ordered]@{ source = "app/src/main/assets/$relative"; destination = "assets/android/$relative"; bytes = $file.Length; sha256 = $hash }
    $totalBytes += $file.Length
    if ($relative -cmatch '^art/items/illustrated-v1/([^/]+)\.png$') { $equipment[$Matches[1]] = "res://assets/android/$relative" }
    if ($relative -cmatch '^art/cards/([AUCSRPD][1-6])-v1\.png$') { $cards[$Matches[1]] = "res://assets/android/$relative" }
}
# PaintedItemIcons tries the explicit definition image first, then ItemIconCatalog's glyph.
$iconSource = Join-Path $AndroidRoot 'app/src/main/java/com/example/douqiuqiu/ui/components/ItemIconCatalog.kt'
$iconText = Get-Content -Raw -LiteralPath $iconSource
foreach ($match in [regex]::Matches($iconText, 'putAll\("([a-z0-9_ ]+)", IconGlyph\.([A-Z_]+)')) {
    $glyph = $match.Groups[2].Value
    if ($glyph -eq 'CROSS') { $glyph = 'BOW' }
    if ($equipment.Contains($glyph)) {
        foreach ($id in $match.Groups[1].Value.Split(' ')) {
            if (-not $equipment.Contains($id)) { $equipment[$id] = $equipment[$glyph] }
        }
    }
}
$expansion = Get-Content -Raw -LiteralPath (Join-Path $sourceRoot 'art/cards/v0.1.5/manifest.json') | ConvertFrom-Json
foreach ($entry in $expansion.art) {
    if ($entry.file -cnotmatch '^v0\.1\.5/[A-Z0-9]+\.webp$') { throw "Unsafe card path: $($entry.file)" }
    $path = "res://assets/android/art/cards/$($entry.file)"
    $cards[$entry.id] = $path
    $cards[$entry.artKey] = $path
}
foreach ($path in @($cards.Values) + @($equipment.Values)) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $path.Substring(6)) -PathType Leaf)) { throw "Missing mapped art: $path" }
}
Write-JsonIfChanged (Join-Path $targetRoot 'catalog.json') ([ordered]@{ schema = 1; cards = $cards; equipment = $equipment })
Write-JsonIfChanged (Join-Path $targetRoot 'manifest.json') ([ordered]@{ schema = 1; sourceBase = 'Android repository'; hashAlgorithm = 'SHA256'; files = @($records) })
Write-Output "Android art: files=$($records.Count) copied=$copied unchanged=$unchanged cards=$($cards.Count) equipment=$($equipment.Count) bytes=$totalBytes"
