param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [switch]$RequireTriangles
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $InputPath -PathType Leaf)) {
    throw "JT input does not exist: $InputPath"
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}

$inputFullPath = (Resolve-Path -LiteralPath $InputPath).Path
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$execArgs = "--export --output $outputFullPath $inputFullPath"

& mvn exec:java -DskipTests "-Dexec.args=$execArgs"
if ($LASTEXITCODE -ne 0) {
    throw "Voyager export failed with exit code $LASTEXITCODE."
}

if (-not (Test-Path -LiteralPath $outputFullPath -PathType Leaf)) {
    throw "Voyager returned success but did not create an export: $outputFullPath"
}

& "$PSScriptRoot\Validate-Gltf.ps1" -Path $outputFullPath -RequireTriangles:$RequireTriangles
if ($LASTEXITCODE -ne 0) {
    throw "glTF validation failed with exit code $LASTEXITCODE."
}