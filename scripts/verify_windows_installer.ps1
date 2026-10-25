#Requires -Version 7.0
param([string]$Version = '0.2.7')
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifestPath = Join-Path $projectRoot 'build/installers/package-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$installer = Join-Path $projectRoot "build/installers/ArcadiumClientSetup-$Version-x64.exe"
if ($manifest.launcherVersion -ne $Version -or (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.installerSha256) {
    throw 'Installer version or checksum mismatch'
}
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) 'ArcadiumInstallerChecks'))
$target = [IO.Path]::GetFullPath((Join-Path $fixtureRoot ([IO.Path]::GetRandomFileName())))
if (!$target.StartsWith($fixtureRoot + [IO.Path]::DirectorySeparatorChar) -or (Test-Path -LiteralPath $target)) {
    throw 'Invalid installer fixture'
}
$install = Start-Process -FilePath $installer -ArgumentList @('/S','/TESTINSTALL',"/D=$target") -WindowStyle Hidden -Wait -PassThru
if ($install.ExitCode -ne 0) { throw 'Installer failed' }
foreach ($entry in $manifest.files) {
    $path = [IO.Path]::GetFullPath((Join-Path $target $entry.path))
    if (!$path.StartsWith($target + [IO.Path]::DirectorySeparatorChar) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
        throw "Installed payload mismatch: $($entry.path)"
    }
}
$components = @()
foreach ($lockPath in @('distribution/oneclient-1.8.9.lock.json', 'distribution/arcadium-1.7.10.lock.json', 'distribution/oneclient-1.21.11.lock.json', 'distribution/oneclient-26.2.lock.json')) {
    $catalog = Get-Content -LiteralPath (Join-Path $projectRoot $lockPath) -Raw | ConvertFrom-Json
    $components += @($catalog.arcadiumComponents)
}
foreach ($component in $components) {
    if ((Get-FileHash -LiteralPath (Join-Path $target "components/$($component.file)") -Algorithm SHA256).Hash.ToLowerInvariant() -ne $component.sha256) {
        throw 'The installer contains an obsolete client component'
    }
}
$binary = Join-Path $target 'Arcadium.exe'
$existingLaunchers = @(Get-Process -Name Arcadium -ErrorAction SilentlyContinue | Where-Object {
    $_.MainWindowTitle -eq 'Arcadium Client' -and $_.Path -ne $binary
} | ForEach-Object { @{ id=$_.Id; started=$_.StartTime; path=$_.Path } })
$startupBehavior = 'new window'
$env:ARCADIUM_DATA_DIR = Join-Path $fixtureRoot ([guid]::NewGuid().ToString('N') + '-profile')
$launcher = Start-Process -FilePath $binary -WindowStyle Hidden -PassThru
try {
    $deadline = (Get-Date).AddSeconds(40)
    do { Start-Sleep -Milliseconds 400; $launcher.Refresh() } until ($launcher.MainWindowTitle -eq 'Arcadium Client' -or $launcher.HasExited -or (Get-Date) -gt $deadline)
    if ($launcher.MainWindowTitle -ne 'Arcadium Client') {
        $forwarded = $launcher.HasExited -and $launcher.ExitCode -eq 0 -and $existingLaunchers.Count -gt 0
        $sameInstance = $false
        foreach ($existing in $existingLaunchers) {
            $current = Get-Process -Id $existing.id -ErrorAction SilentlyContinue
            if ($current -and $current.StartTime -eq $existing.started -and $current.Path -eq $existing.path -and $current.MainWindowTitle -eq 'Arcadium Client') {
                $sameInstance = $true
            }
        }
        if (!$forwarded -or !$sameInstance -or (Test-Path -LiteralPath $env:ARCADIUM_DATA_DIR)) { throw 'Installed launcher did not open or forward to the existing instance' }
        $startupBehavior = 'forwarded to existing window'
    }
} finally {
    $owned = Get-CimInstance Win32_Process -Filter "ProcessId=$($launcher.Id)"
    if ($owned -and $owned.ExecutablePath -eq $binary) { Stop-Process -Id $launcher.Id -Force }
    Remove-Item Env:ARCADIUM_DATA_DIR
}
& python (Join-Path $PSScriptRoot 'smoke_native_helpers.py') --executable $binary --output (Join-Path $projectRoot "build/windows-$Version-native-helper-tests.json")
if ($LASTEXITCODE -ne 0) { throw 'Installed native helper checks failed' }
$nativeHelperReport = Get-Content -LiteralPath (Join-Path $projectRoot "build/windows-$Version-native-helper-tests.json") -Raw | ConvertFrom-Json
if (!$nativeHelperReport.passed) { throw 'Installed native helper checks did not pass' }
$uninstall = Start-Process -FilePath (Join-Path $target 'Uninstall.exe') -ArgumentList @('/S','/TESTINSTALL') -WindowStyle Hidden -Wait -PassThru
if ($uninstall.ExitCode -ne 0 -or (Test-Path -LiteralPath $target)) { throw 'Isolated uninstall failed' }
@{ version=$Version; filesVerified=$manifest.files.Count; clientComponentsVerified=$components.Count; launcherWindow='Arcadium Client'; startupBehavior=$startupBehavior; nativeHelperChecks=$nativeHelperReport.checks.Count; uninstallVerified=$true; installerSha256=$manifest.installerSha256 } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot "build/windows-$Version-installer-checks.json") -Encoding utf8NoBOM
Write-Output 'Verified installer payload, current components, native launcher and isolated uninstall'
