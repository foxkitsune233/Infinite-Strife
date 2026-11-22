param(
    [string]$GodotPath = '',
    [string[]]$TestNames = @(),
    [int]$TimeoutSeconds = 45
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($GodotPath)) {
    if ($env:GODOT_BIN) { $GodotPath = $env:GODOT_BIN }
    elseif (Get-Command godot -ErrorAction SilentlyContinue) { $GodotPath = (Get-Command godot).Source }
    else { $GodotPath = Join-Path (Split-Path -Parent $projectRoot) 'Godot_v4.7.1-stable_mono_win64/Godot_v4.7.1-stable_win64_console.exe' }
}
$logDirectory = Join-Path $PSScriptRoot 'build'
if (-not (Test-Path -LiteralPath $GodotPath -PathType Leaf)) { throw "Godot executable not found: $GodotPath" }
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$tests = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Filter 'test_*.gd' | Sort-Object Name)
if ($TestNames.Count -gt 0) {
    $TestNames = @($TestNames | ForEach-Object { $_ -split ',' })
    $tests = @($tests | Where-Object { $_.Name -in $TestNames })
    if ($tests.Count -ne $TestNames.Count) { throw 'One or more requested tests do not exist.' }
}
if ($tests.Count -eq 0) { throw 'No tests selected.' }
$results = @()
foreach ($test in $tests) {
    $logPath = Join-Path $logDirectory ($test.BaseName + '.log')
    if (Test-Path -LiteralPath $logPath) { Remove-Item -LiteralPath $logPath }
    # GUI executables must be waited on explicitly; LASTEXITCODE is not reliable here.
    $process = Start-Process -FilePath $GodotPath -ArgumentList @('--headless', '--path', ('"' + $projectRoot + '"'), '--log-file', ('"' + $logPath + '"'), '--script', ('res://tests/' + $test.Name)) -PassThru -WindowStyle Hidden
    $timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
    if ($timedOut) { $process.Kill(); $process.WaitForExit() }
    $logText = if (Test-Path -LiteralPath $logPath) { Get-Content -Raw -LiteralPath $logPath } else { '' }
    $passed = -not $timedOut -and $process.ExitCode -eq 0 -and $logText -notmatch '(?m)(SCRIPT ERROR:|ERROR:|CrashHandlerException)'
    $results += [PSCustomObject]@{ test = $test.Name; passed = $passed; exit_code = $process.ExitCode; timeout = $timedOut; log = $logPath }
    Write-Output (('{0} {1} (exit={2})' -f $(if ($passed) { 'PASS' } else { 'FAIL' }), $test.Name, $process.ExitCode))
    if (-not $passed) { Write-Output $logText }
}
$report = [ordered]@{ generated_utc = [DateTime]::UtcNow.ToString('o'); godot = $GodotPath; total = $results.Count; failed = @($results | Where-Object { -not $_.passed }).Count; results = $results }
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $logDirectory 'migration-verification.json') -Encoding utf8
Write-Output ("Tests={0} Failed={1}" -f $report.total, $report.failed)
if ($report.failed -gt 0) { exit 1 }
