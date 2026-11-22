param([string]$AndroidRoot = 'D:\android_project\DouQiuQiu')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = (Resolve-Path -LiteralPath $AndroidRoot).Path
$version = @{}
Get-Content -LiteralPath (Join-Path $sourceRoot 'app/version.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $version[$matches[1].Trim()] = $matches[2].Trim() }
}
$head = (& git -C $sourceRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Android source is not a Git repository.' }
$remote = @(& git -C $sourceRoot remote -v | Where-Object { $_ -match 'github.com' })
$dirty = @(& git -C $sourceRoot status --porcelain)
$fingerprints = [ordered]@{}
$files = @(
    Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'shared/src/commonMain') -Recurse -File -Filter '*.kt'
    Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'app/src/main/java') -Recurse -File -Filter '*.kt'
    Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'app/src/main/assets/config') -Recurse -File
)
foreach ($file in @($files | Sort-Object FullName)) {
    $relative = $file.FullName.Substring($sourceRoot.Length).TrimStart('\').Replace('\', '/')
    $fingerprints[$relative] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$snapshot = [ordered]@{ schema_version = 1; target = 'Follow Android 0.1.7 development and later updates'; registered_version = $version; head = $head; github_remote = $remote; dirty_worktree = $dirty; source_fingerprints = $fingerprints }
$outputDirectory = Join-Path $projectRoot 'docs/migration'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$outputPath = Join-Path $outputDirectory 'android-baseline.json'
if (Test-Path -LiteralPath $outputPath) {
    $previous = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
    foreach ($entry in $fingerprints.GetEnumerator()) {
        $oldProperty = $previous.source_fingerprints.PSObject.Properties[$entry.Key]
        if ($null -eq $oldProperty -or $oldProperty.Value -ne $entry.Value) { Write-Output ('CHANGED ' + $entry.Key) }
    }
}
$snapshot | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output ('Android baseline: ' + $head + ' / ' + $version.versionName + ' / ' + $fingerprints.Count + ' files')
Write-Output ('Local changes: ' + $dirty.Count)
