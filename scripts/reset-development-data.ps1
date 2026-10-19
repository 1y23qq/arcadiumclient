#Requires -Version 7.0
[CmdletBinding()]
param([switch]$DefaultProfiles)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildRoot = Join-Path $projectRoot 'build'
$targets = @(
    (Join-Path $buildRoot 'development/launcher'),
    (Join-Path $buildRoot 'visual-check/data'),
    (Join-Path $buildRoot 'verification'),
    (Join-Path $buildRoot 'installer-smoke-data')
)
if ($DefaultProfiles) {
    $targets += @(
        (Join-Path $env:APPDATA 'Arcadium/ArcadiumClient'),
        (Join-Path $env:APPDATA 'Arcadium/ArcadiumClient-dev'),
        (Join-Path $env:LOCALAPPDATA 'Arcadium/ArcadiumClient'),
        (Join-Path $env:LOCALAPPDATA 'Arcadium/ArcadiumClient-dev'),
        (Join-Path $env:LOCALAPPDATA 'ArcadiumClientBeta')
    )
}
$approvedTargets = @($targets | ForEach-Object { [IO.Path]::GetFullPath($_) })
$cache = Join-Path $buildRoot 'download-cache'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
foreach ($target in $approvedTargets) {
    if (!(Test-Path -LiteralPath $target)) { continue }
    $resolved = (Resolve-Path -LiteralPath $target).ProviderPath
    if (!$approvedTargets.Contains($resolved)) { throw "Unexpected resolved reset path: $resolved" }
    if ((Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Refusing to reset a redirected profile: $resolved"
    }
    $metadata = Join-Path $resolved 'metadata'
    if (Test-Path -LiteralPath $metadata) {
        foreach ($kind in @('packages', 'libraries', 'assets', 'versions')) {
            $source = Join-Path $metadata $kind
            if (Test-Path -LiteralPath $source) {
                Copy-Item -LiteralPath $source -Destination $cache -Recurse -Force
            }
        }
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
    Write-Output "Reset profile: $resolved"
}
