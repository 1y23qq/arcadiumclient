#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Launcher)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$launcherPath = (Resolve-Path -LiteralPath $Launcher).ProviderPath
if ([IO.Path]::GetFileName($launcherPath) -ne 'Arcadium.exe') { throw 'Select an Arcadium.exe build.' }
if (Get-Process Arcadium -ErrorAction SilentlyContinue) { throw 'Close Arcadium before resetting development data.' }
if (Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object MainWindowTitle -like 'Arcadium Client*') { throw 'Close the Arcadium game before resetting development data.' }
& (Join-Path $PSScriptRoot 'reset-development-data.ps1') -DefaultProfiles
$profile = Join-Path $projectRoot 'build/development/launcher'
$metadata = Join-Path $profile 'metadata'
New-Item -ItemType Directory -Force -Path $metadata | Out-Null
$cache = Join-Path $projectRoot 'build/download-cache'
foreach ($kind in @('packages', 'libraries', 'assets', 'versions')) {
    $source = Join-Path $cache $kind
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $metadata -Recurse -Force }
}
$env:ARCADIUM_DATA_DIR = $profile
$env:ARCADIUM_COMPONENTS_DIR = Join-Path $projectRoot 'packages/arcadium_common/assets/distribution'
Start-Process -FilePath $launcherPath -WorkingDirectory $projectRoot -WindowStyle Hidden
