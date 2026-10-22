$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$target = Join-Path $PSScriptRoot "tools\mpv"
$archive = Join-Path $target "mpv.7z"
$partial = "$archive.part"
$url = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261021/mpv-x86_64-20261021-git-e471496c56.7z"
$expectedSha256 = "4A355F3E89C83FABC730D33FB4E759DFEC747806ED2287B61A90D1681B83E8D3"

New-Item $target -ItemType Directory -Force | Out-Null

function Test-ArchiveHash {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $expectedSha256
}

if (-not (Test-ArchiveHash $archive)) {
    Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    Write-Host "Downloading pinned mpv runtime..."
    try {
        & curl.exe --fail --location --retry 2 --silent --show-error --output $partial $url
        if ($LASTEXITCODE -ne 0) { throw "mpv download failed" }
        $actualSha256 = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash
        if ($actualSha256 -ne $expectedSha256) {
            throw "mpv SHA-256 mismatch. Expected $expectedSha256, got $actualSha256"
        }
        Move-Item -LiteralPath $partial -Destination $archive -Force
    } finally {
        Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "Verified cached mpv archive SHA-256."
}

Remove-Item -LiteralPath (Join-Path $target "mpv.exe") -Force -ErrorAction SilentlyContinue
Write-Host "Extracting mpv..."
$tar = Get-Command tar.exe -ErrorAction SilentlyContinue
$sevenZip = "C:\Program Files\7-Zip\7z.exe"
if ($tar) {
    & $tar.Source -xf $archive -C $target
    if ($LASTEXITCODE -ne 0) { throw "mpv extraction with tar.exe failed" }
} elseif (Test-Path -LiteralPath $sevenZip) {
    & $sevenZip x $archive "-o$target" -y | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "mpv extraction with 7-Zip failed" }
} else {
    throw "Neither tar.exe nor 7-Zip is available to unpack mpv."
}
$mpvExe = Join-Path $target "mpv.exe"
if (-not (Test-Path -LiteralPath $mpvExe -PathType Leaf)) {
    throw "mpv.exe was not produced"
}
Write-Host "mpv ready: tools\mpv\mpv.exe"
Write-Host "archive_sha256=$expectedSha256"
