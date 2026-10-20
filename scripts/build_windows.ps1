#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$TargetDirectory,
    [string]$JavaHome,
    [string]$Makensis,
    [switch]$SkipCompile
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$launcherVersion = [regex]::Match([IO.File]::ReadAllText((Join-Path $projectRoot 'Cargo.toml')), '(?m)^version\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?)"\s*$').Groups[1].Value
if (!$launcherVersion) { throw 'The launcher version is missing from Cargo.toml.' }
$buildDirectory = Join-Path $projectRoot 'build'
New-Item -ItemType Directory -Force -Path $buildDirectory | Out-Null
if (!$TargetDirectory) {
    $TargetDirectory = Join-Path $env:LOCALAPPDATA 'ArcadiumBuild/rust-target'
}
$TargetDirectory = [IO.Path]::GetFullPath($TargetDirectory)
$env:CARGO_TARGET_DIR = $TargetDirectory
$env:SQLX_OFFLINE = 'true'

$vswhere = Join-Path ([Environment]::GetEnvironmentVariable('ProgramFiles(x86)')) 'Microsoft Visual Studio/Installer/vswhere.exe'
if (!(Test-Path -LiteralPath $vswhere)) { throw 'Visual Studio C++ build tools are required.' }
$vsRoot = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (!$vsRoot) { throw 'Install the Visual Studio Desktop development with C++ workload.' }
$vcvars = Join-Path $vsRoot 'VC/Auxiliary/Build/vcvars64.bat'
$vcEnvironment = & cmd /d /s /c "call ""$vcvars"" >nul && set"
if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the MSVC environment.' }
foreach ($row in $vcEnvironment) {
    $separator = $row.IndexOf('=')
    if ($separator -gt 0) {
        [Environment]::SetEnvironmentVariable($row.Substring(0, $separator), $row.Substring($separator + 1), 'Process')
    }
}
if (!$Makensis) {
    $nsisCommand = Get-Command makensis.exe -ErrorAction SilentlyContinue
    if ($nsisCommand) { $Makensis = $nsisCommand.Source }
    else { $Makensis = Join-Path ([Environment]::GetEnvironmentVariable('ProgramFiles(x86)')) 'NSIS/makensis.exe' }
}
if (!(Test-Path -LiteralPath $Makensis)) { throw 'Install NSIS 3 or provide -Makensis.' }

Push-Location $projectRoot
try {
    if (!$SkipCompile) {
        if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
        if (!$env:JAVA_HOME) { throw 'Provide -JavaHome pointing to a JDK 25 installation.' }
        & python (Join-Path $projectRoot 'scripts/build_client.py') --java-home $env:JAVA_HOME
        if ($LASTEXITCODE -ne 0) { throw 'Source client component build failed.' }
        & cargo build --release --locked --target x86_64-pc-windows-msvc -p arcadium_app --bin Arcadium
        if ($LASTEXITCODE -ne 0) { throw 'Launcher release build failed.' }
    }
    $binary = Join-Path $TargetDirectory 'x86_64-pc-windows-msvc/release/Arcadium.exe'
    if (!(Test-Path -LiteralPath $binary)) { throw "Release binary is missing: $binary" }
    $stage = Join-Path $buildDirectory ('package/windows-x64-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    Copy-Item -LiteralPath $binary -Destination (Join-Path $stage 'Arcadium.exe')
    $componentsDirectory = Join-Path $stage 'components'
    New-Item -ItemType Directory -Force -Path $componentsDirectory | Out-Null
    & python (Join-Path $projectRoot 'scripts/package_client.py') --verify-components
    if ($LASTEXITCODE -ne 0) { throw 'The source component catalogs failed verification.' }
    $distributionLocks = @('distribution/oneclient-1.8.9.lock.json', 'distribution/arcadium-1.7.10.lock.json')
    $minecraftVersions = @()
    foreach ($lockPath in $distributionLocks) {
        $componentCatalog = Get-Content -LiteralPath (Join-Path $projectRoot $lockPath) -Raw | ConvertFrom-Json
        $minecraftVersions += $componentCatalog.minecraftVersion
        if (!$componentCatalog.arcadiumComponents.Count) { throw "Client components are missing: $lockPath" }
        foreach ($component in $componentCatalog.arcadiumComponents) {
            $componentPath = Join-Path $projectRoot "packages/arcadium_common/assets/distribution/$($component.file)"
            if ((Get-FileHash -LiteralPath $componentPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $component.sha256) {
                throw "Client component hash mismatch: $($component.file)"
            }
            Copy-Item -LiteralPath $componentPath -Destination $componentsDirectory
        }
    }
    foreach ($file in @('LICENSE', 'NOTICE.md', 'README.md')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $file) -Destination $stage
    }
    $licenseDirectory = Join-Path $stage 'licenses'
    New-Item -ItemType Directory -Force -Path $licenseDirectory | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'licenses') |
        Copy-Item -Recurse -Destination $licenseDirectory
    & python (Join-Path $projectRoot 'scripts/package_client.py') --prepare-legacy-notices (Join-Path $licenseDirectory 'legacy-mods')
    if ($LASTEXITCODE -ne 0) { throw 'Legacy mod license notices could not be prepared.' }
    Copy-Item -LiteralPath 'packages/arcadium_app/assets/fonts/JetBrainsMono/OFL.txt' -Destination (Join-Path $licenseDirectory 'JetBrainsMono-OFL.txt')
    Copy-Item -LiteralPath 'packages/arcadium_app/assets/fonts/Poppins/OFL.txt' -Destination (Join-Path $licenseDirectory 'Poppins-OFL.txt')
    $nsisLicense = Join-Path (Split-Path -Parent $Makensis) 'COPYING'
    if (Test-Path -LiteralPath $nsisLicense) {
        Copy-Item -LiteralPath $nsisLicense -Destination (Join-Path $licenseDirectory 'NSIS-COPYING.txt')
    }

    $redistRoot = Join-Path $vsRoot 'VC/Redist/MSVC'
    $crt = Get-ChildItem -LiteralPath $redistRoot -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'x64/Microsoft.VC143.CRT' } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if (!$crt) { throw 'The MSVC x64 redistributable DLL directory is missing.' }
    Get-ChildItem -LiteralPath $crt -Filter '*.dll' -File | Copy-Item -Destination $stage

    & cargo metadata --offline --locked --format-version 1 --filter-platform x86_64-pc-windows-msvc > (Join-Path $buildDirectory 'cargo-metadata.json')
    if ($LASTEXITCODE -ne 0) { throw 'Cargo license metadata could not be read.' }
    $inventory = Get-Content -LiteralPath (Join-Path $buildDirectory 'cargo-metadata.json') -Raw | ConvertFrom-Json -AsHashtable
    $notices = [Text.StringBuilder]::new()
    [void]$notices.AppendLine('Arcadium Client third-party dependency notices')
    [void]$notices.AppendLine('External dependencies keep their own licenses. This inventory includes build dependencies.')
    foreach ($package in ($inventory.packages | Sort-Object { $_['name'] }, { $_['version'] })) {
        if ($package.name -like 'arcadium_*') { continue }
        [void]$notices.AppendLine()
        [void]$notices.AppendLine("=== $($package.name) $($package.version) ===")
        [void]$notices.AppendLine("License: $($package.license)")
        [void]$notices.AppendLine("Repository: $($package.repository)")
        [void]$notices.AppendLine("Authors: $($package.authors -join ', ')")
        $manifestDirectory = Split-Path -Parent $package.manifest_path
        $noticeFiles = @(Get-ChildItem -LiteralPath $manifestDirectory -File |
            Where-Object { $_.Name -match '^(LICENSE|COPYING|NOTICE|COPYRIGHT)([._-].*)?$' } |
            ForEach-Object { $_.FullName })
        if ($package.license_file) {
            $noticeFiles += Join-Path $manifestDirectory $package.license_file
        }
        foreach ($noticeFile in ($noticeFiles | Sort-Object -Unique)) {
            if (Test-Path -LiteralPath $noticeFile -PathType Leaf) {
                [void]$notices.AppendLine([IO.File]::ReadAllText($noticeFile))
            }
        }
    }
    [void]$notices.AppendLine('Microsoft Visual C++ runtime DLLs are Microsoft redistributable components, separately licensed from Arcadium source.')
    [void]$notices.AppendLine('https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files')
    [IO.File]::WriteAllText((Join-Path $licenseDirectory 'Rust-dependencies.txt'), $notices.ToString(), [Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath 'distribution/oneclient-1.8.9.lock.json' -Destination (Join-Path $stage 'distribution-lock.json')
    Copy-Item -LiteralPath 'distribution/arcadium-1.7.10.lock.json' -Destination (Join-Path $stage 'distribution-lock-1.7.10.json')

    $installerDirectory = Join-Path $buildDirectory 'installers'
    New-Item -ItemType Directory -Force -Path $installerDirectory | Out-Null
    $output = Join-Path $installerDirectory "ArcadiumClientSetup-$launcherVersion-x64.exe"
    $updateFiles = Join-Path $buildDirectory 'managed-update-files.nsh'
    $updateEntries = @('!macro ArcadiumManagedUpdateFiles action')
    foreach ($managedFile in (Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName)) {
        $relative = [IO.Path]::GetRelativePath($stage, $managedFile.FullName)
        $updateEntries += ('!insertmacro ${action} "' + $relative + '"')
    }
    $updateEntries += '!insertmacro ${action} "Uninstall.exe"'
    $updateEntries += '!macroend'
    $updateEntries += '!macro ArcadiumManagedUpdateDirectories action'
    foreach ($managedDirectory in (Get-ChildItem -LiteralPath $stage -Recurse -Directory | Sort-Object { $_.FullName.Length } -Descending)) {
        $relative = [IO.Path]::GetRelativePath($stage, $managedDirectory.FullName)
        $updateEntries += ('!insertmacro ${action} "' + $relative + '"')
    }
    $updateEntries += '!macroend'
    [IO.File]::WriteAllLines($updateFiles, $updateEntries, [Text.UTF8Encoding]::new($false))
    & $Makensis "/DARCADIUM_VERSION=$launcherVersion" "/DSTAGEDIR=$stage" "/DOUTPUT=$output" "/DUPDATEFILES=$updateFiles" "/DICON=$(Join-Path $projectRoot 'packages/arcadium_app/icons/icon.ico')" (Join-Path $PSScriptRoot 'windows-installer.nsi')
    if ($LASTEXITCODE -ne 0) { throw 'NSIS installer compilation failed.' }
    $packageFiles = Get-ChildItem -LiteralPath $stage -Recurse -File | ForEach-Object {
        [ordered]@{
            path = [IO.Path]::GetRelativePath($stage, $_.FullName)
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    [ordered]@{
        product = 'Arcadium Client'
        minecraftVersion = '1.8.9'
        minecraftVersions = @($minecraftVersions)
        launcherVersion = $launcherVersion
        platform = 'windows-x64'
        installerSha256 = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
        files = @($packageFiles)
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $installerDirectory 'package-manifest.json') -Encoding utf8NoBOM
    Write-Output "Installer: $output"
} finally {
    Pop-Location
}
