$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$sourcePath = Join-Path $repoRoot 'src\main\java\gto_ae\helpers\facility_management\FacilityManagement.java'
$source = Get-Content -Raw $sourcePath

if ($source -cmatch 'filterMode\s*=\s*IO\.valueOf') {
    throw 'deserializeNBT should not assign filterMode with direct IO.valueOf; invalid or missing saved data must fall back safely.'
}

if ($source -cmatch 'workingStatus\s*=\s*WorkingStatus\.valueOf') {
    throw 'deserializeNBT should not assign workingStatus with direct WorkingStatus.valueOf; invalid saved data must not crash loading.'
}

foreach ($key in @('TAG_IO_FILTER_INV', 'TAG_FACILITY_FILTER_INV', 'TAG_FILTER_MODE', 'TAG_HAS_WORKING_STATUS', 'TAG_WORKING_STATUS', 'TAG_SAVED_VIEW')) {
    if ($source -cnotmatch "private static final String $key") {
        throw "Expected NBT key constant $key."
    }
}

if ($source -cnotmatch 'private static IO readFilterMode') {
    throw 'Expected readFilterMode helper for tolerant filter mode deserialization.'
}

if ($source -cnotmatch 'private static WorkingStatus readWorkingStatus') {
    throw 'Expected readWorkingStatus helper for tolerant working status deserialization.'
}

Write-Host 'FacilityManagement tolerant NBT regression check passed.'
