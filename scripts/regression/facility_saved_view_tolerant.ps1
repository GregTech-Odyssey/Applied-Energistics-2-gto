$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$logic = Join-Path $root "src\main\java\gto_ae\helpers\facility_management\FacilityManagement.java"
$pos = Join-Path $root "src\main\java\gto_ae\api\util\DirectionalGlobalPos.java"
$logicText = Get-Content -Raw -LiteralPath $logic
$posText = Get-Content -Raw -LiteralPath $pos

if ($logicText -notmatch "tryReadSavedViewEntry") {
    throw "FacilityManagement should isolate savedView entry parsing"
}
if ($logicText -notmatch "AELog\.warn") {
    throw "Bad savedView entries should be logged and skipped"
}
if ($logicText -notmatch "continue;") {
    throw "Bad savedView entries should not abort the full savedView load"
}
if ($posText -notmatch "ResourceLocation\.tryParse") {
    throw "DirectionalGlobalPos should tolerate invalid dimension resource locations"
}
if ($posText -notmatch "Direction\.values\(\)\.length") {
    throw "DirectionalGlobalPos should validate persisted side ordinal"
}
if ($posText -notmatch "/\*\*[\s\S]*readFromTag") {
    throw "DirectionalGlobalPos readFromTag should document malformed-tag behavior"
}

Write-Host "Facility savedView tolerance regression passed."
