param(
    [string]$AndroidRoot = 'D:\android_project\DouQiuQiu',
    [string]$KotlinHome = '',
    [string]$SharedJar = '',
    [switch]$Check
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$AndroidRoot = (Resolve-Path -LiteralPath $AndroidRoot).Path
if (-not $SharedJar) { $SharedJar = Join-Path $AndroidRoot 'shared/build/libs/shared-jvm.jar' }
$sharedPrefix = 'shared/src/commonMain/kotlin/com/example/douqiuqiu/'
$compiled = @('content/EquipmentContent.kt', 'content/SchoolEquipmentContent.kt', 'content/ControlEquipmentContent.kt',
    'content/EffectModel.kt', 'content/CardContent.kt', 'inventory/Crafting.kt', 'inventory/Inventory.kt',
    'tower/TowerModels.kt', 'loot/ValuableLootCatalog.kt')
$sourcePaths = @($compiled | ForEach-Object { $sharedPrefix + $_ }) + @(
    'app/src/main/assets/config/inventory.json',
    'app/src/main/java/com/example/douqiuqiu/inventory/InventoryCatalog.kt',
    ($sharedPrefix + 'equipment/EquipmentCombat.kt'), ($sharedPrefix + 'cards/CardCombat.kt'),
    ($sharedPrefix + 'journey/JourneyModels.kt'))
function Fingerprints {
    $result = [ordered]@{}
    foreach ($relative in $sourcePaths) {
        $result[$relative] = (Get-FileHash -LiteralPath (Join-Path $AndroidRoot $relative) -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $result
}
$fingerprints = Fingerprints
$lib = if ($KotlinHome) { Join-Path $KotlinHome 'lib' } else { '' }
$compiler = if ($lib) { Join-Path $lib 'kotlin-compiler.jar' } else { '' }
$stdlib = if ($lib) { Join-Path $lib 'kotlin-stdlib.jar' } else { '' }
if (-not $compiler -or -not (Test-Path -LiteralPath $compiler)) {
    # An installed Kotlin distribution is optional; the Gradle dependency cache is read-only.
    $cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
    $compiler = Get-ChildItem -LiteralPath (Join-Path $cache 'org.jetbrains.kotlin/kotlin-compiler-embeddable') -Recurse -Filter '*.jar' |
        Where-Object Name -NotMatch '-(sources|javadoc)\.jar$' | Sort-Object FullName | Select-Object -Last 1 -ExpandProperty FullName
    if (-not $compiler) { throw 'Supply -KotlinHome or populate the cached Kotlin compiler first; this exporter never runs Gradle.' }
    $version = Split-Path (Split-Path (Split-Path $compiler -Parent) -Parent) -Leaf
    $stdlib = Get-ChildItem -LiteralPath (Join-Path $cache "org.jetbrains.kotlin/kotlin-stdlib/$version") -Recurse -Filter '*.jar' |
        Where-Object Name -NotMatch '-(sources|javadoc)\.jar$' | Select-Object -First 1 -ExpandProperty FullName
    $compilerJars = @(Get-ChildItem -LiteralPath (Join-Path $cache 'org.jetbrains.kotlin') -Recurse -Filter '*.jar' |
        Where-Object { $_.FullName -match "[\\/]$([regex]::Escape($version))[\\/]" -and $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Select-Object -ExpandProperty FullName)
    foreach ($dependency in @('org.jetbrains/annotations', 'org.jetbrains.intellij.deps/trove4j', 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm')) {
        $depPath = Join-Path $cache $dependency
        if (Test-Path -LiteralPath $depPath) { $compilerJars += @(Get-ChildItem -LiteralPath $depPath -Recurse -Filter '*.jar' | Where-Object Name -NotMatch '-(sources|javadoc)\.jar$' | Sort-Object FullName | Select-Object -Last 1 -ExpandProperty FullName) }
    }
    $compilerClasspath = $compilerJars -join ';'
} else { $compilerClasspath = "$lib/*" }
foreach ($required in @($SharedJar, $stdlib)) { if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing prerequisite: $required" } }
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$work = Join-Path $tempRoot ('android-content-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
try {
    $exportJar = Join-Path $work 'export.jar'
    $rawPath = Join-Path $work 'equipment.json'
    $sources = @($compiled | ForEach-Object { Join-Path $AndroidRoot ($sharedPrefix + $_) })
    & java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath "$SharedJar;$stdlib" -d $exportJar @sources (Join-Path $PSScriptRoot 'AndroidContentExport.kt')
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin export compilation failed' }
    # Compiled current sources precede the cached dependency jar at runtime.
    & java -cp "$exportJar;$SharedJar;$stdlib" AndroidContentExport $rawPath
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin catalog evaluation failed' }
    $raw = Get-Content -Raw -Encoding utf8 -LiteralPath $rawPath | ConvertFrom-Json
    $inventory = Get-Content -Raw -Encoding utf8 -LiteralPath (Join-Path $AndroidRoot 'app/src/main/assets/config/inventory.json') | ConvertFrom-Json
    $statsKeys = @('strength','agility','intelligence','health','healthRegen','armor','attackSpeed','manaPercent','manaRegen','attack')
    $items = @($raw.items)
    $recipes = @($raw.recipes) + @($inventory.recipes)
    $inventoryIds = @{}
    # Mirror InventoryCatalog's inventory-first card precedence and then append loot.
    $legacyIds = @($inventory.definitions.id)
    $extraCards = @($raw.card_items | Where-Object { $_.id -notin $legacyIds })
    foreach ($d in (@($inventory.definitions) + $extraCards + @($raw.loot_items))) {
        if ($inventoryIds.ContainsKey($d.id)) { throw "Duplicate inventory ID: $($d.id)" }; $inventoryIds[$d.id] = $true
        if ($items.id -contains $d.id) { throw "Overlapping equipment ID requires explicit precedence review: $($d.id)" }
        $stats = [ordered]@{}
        foreach ($key in $statsKeys) {
            $stats[$key] = 0.0
            if ($d.PSObject.Properties['stats'] -and $d.stats.PSObject.Properties[$key]) { $stats[$key] = [double]$d.stats.$key }
        }
        $row = [ordered]@{ id=$d.id; name=$d.name; kind=$d.kind; width=$d.width; height=$d.height; quality=$d.quality;
            source='inventory.json'; category=$null; qualityTier=$null; stats=$stats; statsDeclared=([bool]$d.PSObject.Properties['stats']);
            price=0; shopStock=0; shopShelf='STAPLE'; minimumShopClears=0; minimumDropClears=0;
            maxStack=1; combatUsable=$false; boundOnAcquire=$false; description=''; requiresReview=$false;
            uniqueGroup=$null; recipe=$null; effects=@(); active_effects=@() }
        foreach ($key in @('price','shopStock','shopShelf','minimumShopClears','minimumDropClears','maxStack','combatUsable','boundOnAcquire')) {
            if ($d.PSObject.Properties[$key]) { $row[$key] = $d.$key }
        }
        if ($d.PSObject.Properties['description']) { $row.description = $d.description }
        if ($d.id -notin $legacyIds) { $row.source = if ($d.kind -eq 'CARD') { 'CardCatalog.confirmedOnly' } else { 'ValuableLootCatalog' } }
        $matching = @($inventory.recipes | Where-Object outputId -EQ $d.id)
        if ($matching.Count -eq 1) { $row.recipe = $matching[0] }
        $items += [pscustomobject]$row
    }
    $ids = @{}
    foreach ($d in $items) {
        if ($ids.ContainsKey($d.id)) { throw "Duplicate equipment ID: $($d.id)" }; $ids[$d.id] = $true
        $actualKeys = if ($d.stats -is [System.Collections.IDictionary]) { @($d.stats.Keys) } else { @($d.stats.PSObject.Properties.Name) }
        if ((($actualKeys | Sort-Object) -join ',') -cne (($statsKeys | Sort-Object) -join ',')) { throw "Unexpected stats keys: $($d.id)" }
    }
    $recipeIds = @{}; $outputs = @{}
    foreach ($r in $recipes) {
        if ($recipeIds.ContainsKey($r.id) -or $outputs.ContainsKey($r.outputId)) { throw "Duplicate recipe/output: $($r.id)" }
        $recipeIds[$r.id] = $true; $outputs[$r.outputId] = $true
        if (-not $ids.ContainsKey($r.outputId)) { throw "Missing output: $($r.outputId)" }
        if (@($r.ingredients.PSObject.Properties).Count -eq 0) { throw "Empty recipe: $($r.id)" }
        foreach ($ingredient in $r.ingredients.PSObject.Properties) {
            if (-not $ids.ContainsKey($ingredient.Name) -or $ingredient.Value -notin 1..20) { throw "Invalid ingredient: $($r.id)/$($ingredient.Name)" }
        }
    }
    $remaining = @{}
    foreach ($r in $recipes) { $remaining[$r.outputId] = @($r.ingredients.PSObject.Properties.Name) }
    while ($remaining.Count -gt 0) {
        $leaves = @($remaining.Keys | Where-Object {
            $dependencies = @($remaining[$_] | Where-Object { $remaining.ContainsKey($_) })
            $dependencies.Count -eq 0
        })
        if ($leaves.Count -eq 0) { throw 'Cyclic equipment recipe dependencies' }
        foreach ($leaf in $leaves) { $remaining.Remove($leaf) }
    }
    if (($fingerprints | ConvertTo-Json -Compress) -cne ((Fingerprints) | ConvertTo-Json -Compress)) { throw 'Android sources changed during export; retry against a stable snapshot' }
    $toolchain = [ordered]@{}
    foreach ($entry in @(@('compiler',$compiler), @('stdlib',$stdlib), @('shared_jvm',$SharedJar))) {
        $toolchain[$entry[0]] = [ordered]@{ name=(Split-Path $entry[1] -Leaf); sha256=(Get-FileHash -LiteralPath $entry[1] -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    $exporterHashes = [ordered]@{}
    foreach ($name in @('export_android_content.ps1', 'AndroidContentExport.kt')) { $exporterHashes["tools/$name"] = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $name) -Algorithm SHA256).Hash.ToLowerInvariant() }
    $document = [ordered]@{ schema_version=1; stats_keys=$statsKeys; source_fingerprints=$fingerprints; exporter_fingerprints=$exporterHashes;
        toolchain=$toolchain; items=@($items | Sort-Object -Property id -CaseSensitive); recipes=@($recipes | Sort-Object -Property id -CaseSensitive);
        cards=@($raw.cards); schools=@($raw.schools); synergy_tiers=@($raw.synergy_tiers) }
    $json = ($document | ConvertTo-Json -Depth 100).Replace("`r`n", "`n") + "`n"
    $destination = Join-Path $projectRoot 'data/android/equipment.json'
    if ($Check) {
        if (-not (Test-Path -LiteralPath $destination) -or [IO.File]::ReadAllText($destination) -cne $json) { throw 'Equipment export differs. Regenerate and review data/android/equipment.json.' }
        Write-Output 'PASS reproducible export: checked-in JSON matches current sources and toolchain'
    } else {
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        [IO.File]::WriteAllText($destination, $json, [Text.UTF8Encoding]::new($false))
    }
    Write-Output ("Equipment={0} Recipes={1}; all IDs and ingredient/output references validated" -f $items.Count,$recipes.Count)
} finally {
    $resolvedWork = [IO.Path]::GetFullPath($work)
    if ($resolvedWork.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedWork -Leaf) -match '^android-content-[0-9a-f]{32}$') {
        Remove-Item -LiteralPath $resolvedWork -Recurse -Force
    }
}
