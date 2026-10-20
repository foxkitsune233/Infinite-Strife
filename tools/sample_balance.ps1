param(
    [ValidateSet('boxes', 'runs')][string]$Mode = 'boxes',
    [ValidateRange(1,10000)][int]$Samples = 100,
    [string]$KotlinHome = 'D:\Android project\plugins\Kotlin\kotlinc',
    [string]$Case = '',
    [switch]$CompileOnly
)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
Push-Location -LiteralPath $projectDir
try {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    $pythonPath = if ($pythonCmd) { $pythonCmd.Source } else { Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' }
    if (-not (Test-Path -LiteralPath $pythonPath)) { throw 'Python is required to export the actual inventory.json catalog' }
    & $pythonPath tools/export_balance_catalog.py
    if ($LASTEXITCODE -ne 0) { throw 'Catalog export failed' }
    & ./gradlew.bat --offline :shared:jvmJar
    if ($LASTEXITCODE -ne 0) { throw 'shared JVM compilation failed' }
    $libDir = Join-Path $KotlinHome 'lib'
    if (-not (Test-Path -LiteralPath (Join-Path $libDir 'kotlin-compiler.jar'))) { throw 'Set -KotlinHome to an installed Kotlin compiler directory' }
    $sharedJar = Join-Path $projectDir 'shared/build/libs/shared-jvm.jar'
    $stdlib = Join-Path $libDir 'kotlin-stdlib.jar'
    $outDir = Join-Path $projectDir 'tools/build'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $samplerJar = Join-Path $outDir 'balance-sampler.jar'
    & java -cp "$libDir/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$sharedJar;$stdlib" -d $samplerJar tools/BalanceSampler.kt
    if ($LASTEXITCODE -ne 0) { throw 'Sampler compilation failed' }
    if ($CompileOnly) { Write-Output 'Sampler compiled; simulation not executed'; return }
    & java -cp "$samplerJar;$sharedJar;$stdlib" BalanceSampler $Mode $Samples $projectDir $Case
    if ($LASTEXITCODE -ne 0) { throw 'Sampling failed; preserve the partial CSV as evidence' }
} finally { Pop-Location }
