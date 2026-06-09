$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$file = Join-Path $root "src\main\java\gto_ae\client\gui\me\facility_management\FacilityManagementScreen.java"
$text = Get-Content -Raw -LiteralPath $file

if ($text -notmatch "import java\.util\.Locale;") {
    throw "FacilityManagementScreen should import Locale"
}
if ($text -notmatch "searchField\.getValue\(\)\.toLowerCase\(Locale\.ROOT\)") {
    throw "Search field should normalize with Locale.ROOT"
}
if ($text -notmatch "matchesSearch\(") {
    throw "FacilityManagementScreen should use a helper for search matching"
}
if ($text -match "\.toLowerCase\(\)") {
    throw "FacilityManagementScreen should not use locale-sensitive toLowerCase()"
}

Write-Host "Facility search locale regression passed."
