param([switch]$JarOnly)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Invoke-Checked {
    param([string]$Exe, [object[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed with code $LASTEXITCODE" }
}
function Clear-BuildDirectory([string]$RelativePath) {
    $target = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $RelativePath))
    if (-not $target.StartsWith($PSScriptRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Outside worktree: $target" }
    if (Test-Path -LiteralPath $target) {
        if ((Get-Item -LiteralPath $target).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Linked directory: $target" }
        Remove-Item -LiteralPath $target -Recurse -Force
    }
}
$candidates = @()
if ($env:STREAMFLIX_JDK) {
    $candidates = @((Join-Path $env:STREAMFLIX_JDK 'bin'), $env:STREAMFLIX_JDK)
} else {
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin' }
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += Split-Path $cmd.Source }
    $candidates += 'C:\Program Files\Android\Android Studio\jbr\bin'
    $candidates += 'C:\Program Files\Tableau\Tableau 2025.1\bin\jre\bin'
}
$javaBin = $candidates | Where-Object {
    (Test-Path (Join-Path $_ 'java.exe')) -and (Test-Path (Join-Path $_ 'javac.exe')) -and (Test-Path (Join-Path $_ 'jar.exe'))
} | Select-Object -First 1
if (-not $javaBin) { throw 'JDK 17+ required. Set STREAMFLIX_JDK to a complete JDK.' }
$javac = Join-Path $javaBin 'javac.exe'
$java = Join-Path $javaBin 'java.exe'
$jar = Join-Path $javaBin 'jar.exe'
$version = (Get-Content VERSION -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'VERSION must be major.minor.patch' }
Write-Host "Java toolchain: $javaBin"
Invoke-Checked $java @('-version')
$depJars = @(& (Join-Path $PSScriptRoot 'setup-dependencies.ps1'))
Clear-BuildDirectory 'build'
New-Item build\classes,build\test-classes,build\lib -ItemType Directory -Force | Out-Null
foreach ($file in $depJars) { Copy-Item -LiteralPath $file -Destination build\lib }
$depCp = $depJars -join ';'
$sources = @(Get-ChildItem src\main\java -Recurse -Filter *.java | Sort-Object FullName | ForEach-Object FullName)
$tests = @(Get-ChildItem src\test\java -Recurse -Filter *.java | Sort-Object FullName | ForEach-Object FullName)
Invoke-Checked $javac (@('--release','17','-encoding','UTF-8','-cp',$depCp,'-d','build\classes') + $sources)
New-Item build\branding -ItemType Directory -Force | Out-Null
Invoke-Checked $java @('-cp',("build\classes;" + $depCp),'dev.streamflix.desktop.IconExporter','build\branding')
$brandIcon = Join-Path $PSScriptRoot 'build\branding\StreamflixDesktop.ico'
if (-not (Test-Path -LiteralPath $brandIcon -PathType Leaf)) { throw 'Streamflix application icon export failed.' }
$classPath = ($depJars | ForEach-Object { 'lib/' + (Split-Path $_ -Leaf) }) -join ' '
$manifest = "Manifest-Version: 1.0`nMain-Class: dev.streamflix.desktop.App`nImplementation-Version: $version`nClass-Path: $classPath`n`n"
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build\MANIFEST.MF'), $manifest, (New-Object Text.UTF8Encoding $false))
Invoke-Checked $jar @('--create','--file','build\streamflix-desktop.jar','--date=2020-01-01T00:00:00Z','--manifest','build\MANIFEST.MF','-C','build\classes','.')
Invoke-Checked $javac (@('--release','17','-encoding','UTF-8','-cp',("build\classes;" + $depCp),'-d','build\test-classes') + $tests)
foreach ($test in @('JsonTest','ProviderFixtureTest','TmdbFixtureTest','TmdbTitleIndexTest','M3uPlaylistTest','M3uLiveProviderTest','SportsResolverTest','SportsDataTest','SportsPlaybackTest','SportsFavoritesTest','ExtractorFixtureTest','DependencySmokeTest','UserDataTest','MpvPlayerTest','MainFrameSearchTest','PlaybackFallbackTest','PlaybackRecoveryTest','SubtitleAggregatorTest','PlaybackServerStatsTest','UpdateServiceTest','ImageDiskCacheTest','DiagnosticsTest','MpvBootstrapTest','BrandAssetsTest','VodSearchCoordinatorTest','ProviderHealthStatsTest','ProviderFallbackCoordinatorTest','CatalogRamCacheTest','NavigationStateTest','EmbeddedPlayerIntegrationTest')) {
    Invoke-Checked $java @('-cp','build\streamflix-desktop.jar;build\test-classes',"dev.streamflix.desktop.$test")
}
Write-Host 'JAR OK: build\streamflix-desktop.jar (keep sibling build\lib directory)'
# Native GUI evaluation only on temporary GitHub Actions branch.
if ($env:GITHUB_ACTIONS -eq 'true' -and $env:RUNNER_OS -eq 'Windows' -and
    $env:GITHUB_REF_NAME -like 'test/windows-native-gui-e2e-*') {
    Write-Host 'Evaluating actual Windows desktop rendering'
    & .\setup-mpv.ps1
    if ($LASTEXITCODE -ne 0) { throw "Pinned mpv setup failed: $LASTEXITCODE" }
    $env:STREAMFLIX_MPV = (Resolve-Path 'tools\mpv\mpv.exe').Path
    $env:STREAMFLIX_E2E_MEDIA = 'av://lavfi:testsrc2=size=640x360:rate=24'
    Invoke-Checked $java @('-Dstreamflix.e2e.output=build\windows-gui-e2e',
        '-cp','build\streamflix-desktop.jar;build\test-classes',
        'dev.streamflix.desktop.WindowsNativeGuiE2E')
}
if ($JarOnly) { return }
$mpvExe = Join-Path $PSScriptRoot 'tools\mpv\mpv.exe'
if ($env:STREAMFLIX_MPV) { $mpvExe = $env:STREAMFLIX_MPV }
if (-not (Test-Path -LiteralPath $mpvExe -PathType Leaf)) { throw 'mpv.exe required. Set STREAMFLIX_MPV or run .\setup-mpv.ps1 explicitly.' }
$jpackage = $env:STREAMFLIX_JPACKAGE
if (-not $jpackage) {
    $jpackageCandidates = @((Join-Path $javaBin 'jpackage.exe'))
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { $jpackageCandidates += $cmd.Source }
    $jpackageCandidates += 'C:\Program Files\Tableau\Tableau 2025.1\bin\jre\bin\jpackage.exe'
    $jpackage = $jpackageCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $jpackage -or -not (Test-Path -LiteralPath $jpackage)) { throw 'jpackage required. Set STREAMFLIX_JPACKAGE to jpackage.exe.' }
Write-Host "Packaging tool: $jpackage"
Invoke-Checked $jpackage @('--version')
New-Item build\package -ItemType Directory -Force | Out-Null
Copy-Item build\streamflix-desktop.jar build\package
Copy-Item build\lib build\package -Recurse
$runtimeImage = [IO.Path]::GetFullPath((Split-Path $javaBin -Parent))
$distAppImage = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'dist\StreamflixDesktop'))
if ($runtimeImage.StartsWith($distAppImage + '\', [StringComparison]::OrdinalIgnoreCase) -or
    $runtimeImage.Equals($distAppImage, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'STREAMFLIX_JDK/runtime-image cannot live inside dist\StreamflixDesktop because packaging recreates that directory.'
}
Clear-BuildDirectory 'dist\StreamflixDesktop'
Invoke-Checked $jpackage @(
    '--type','app-image','--name','StreamflixDesktop','--input','build\package',
    '--main-jar','streamflix-desktop.jar','--main-class','dev.streamflix.desktop.App',
    '--dest','dist','--description','Streamflix Desktop',
    '--vendor','Streamflix Desktop Community Port','--app-version',$version,
    '--icon',$brandIcon,
    '--runtime-image',$runtimeImage
)
$mpvSource = Split-Path ([IO.Path]::GetFullPath($mpvExe)) -Parent
$mpvDest = Join-Path $PSScriptRoot 'dist\StreamflixDesktop\tools\mpv'
New-Item $mpvDest -ItemType Directory -Force | Out-Null
Copy-Item -LiteralPath $mpvExe -Destination (Join-Path $mpvDest 'mpv.exe')
# Copy runtime DLLs and license material, never local player profiles/configs.
Get-ChildItem -LiteralPath $mpvSource -File | Where-Object {
    $_.Extension -eq '.dll' -or $_.Name -eq 'mpv.com' -or $_.Name -match '^(LICENSE|COPYING|Copyright)'
} | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $mpvDest }

$mpvLicenseSource = Join-Path $PSScriptRoot 'third_party\mpv'
$mpvLicenseDest = Join-Path $PSScriptRoot 'dist\StreamflixDesktop\third_party\mpv'
if (-not (Test-Path -LiteralPath $mpvLicenseSource -PathType Container)) {
    throw 'Pinned mpv license/source metadata missing: third_party\mpv'
}
New-Item $mpvLicenseDest -ItemType Directory -Force | Out-Null
Copy-Item -Path (Join-Path $mpvLicenseSource '*') -Destination $mpvLicenseDest -Force

foreach ($file in @('LICENSE','THIRD_PARTY_NOTICES.md','VERSION','dependencies.lock')) {
    Copy-Item -LiteralPath $file -Destination dist\StreamflixDesktop
}
Write-Host 'APP OK: dist\StreamflixDesktop\StreamflixDesktop.exe'