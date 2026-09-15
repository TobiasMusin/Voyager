param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('windows', 'linux')]
    [string]$Platform,

    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifactName = "voyager-$Version-$Platform-x64"
$stageDirectory = Join-Path 'target' $artifactName
$releaseDirectory = 'release'
$nativeImage = if ($Platform -eq 'windows') { 'target\Voyager.exe' } else { 'target/Voyager' }
$converterName = "$artifactName-converter"

Remove-Item -Recurse -Force $stageDirectory -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stageDirectory | Out-Null
Copy-Item $nativeImage (Join-Path $stageDirectory (Split-Path $nativeImage -Leaf))
Copy-Item 'README.md' (Join-Path $stageDirectory 'README.md')
Copy-Item 'LICENCE' (Join-Path $stageDirectory 'LICENCE')
Get-ChildItem 'target' -File -Filter '*.dll' |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $stageDirectory $_.Name) }

$classifier = "natives-$Platform"
$lwjglModules = 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl'
foreach ($module in $lwjglModules) {
    $nativeJar = Get-ChildItem "$HOME/.m2/repository/org/lwjgl/$module/*/$module-*-${classifier}.jar" |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($null -eq $nativeJar) {
        throw "Could not find native dependency for $module ($classifier)."
    }
    $extractDirectory = Join-Path $stageDirectory ".extract-$module"
    [System.IO.Compression.ZipFile]::ExtractToDirectory($nativeJar.FullName, $extractDirectory)
    Get-ChildItem $extractDirectory -Recurse -File -Filter '*.dll' |
        ForEach-Object { Move-Item $_.FullName (Join-Path $stageDirectory $_.Name) -Force }
    Remove-Item -Recurse -Force $extractDirectory
}

New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
Compress-Archive -Path $nativeImage -DestinationPath (Join-Path $releaseDirectory "$converterName.zip") -Force
Compress-Archive -Path "$stageDirectory/*" -DestinationPath (Join-Path $releaseDirectory "$artifactName.zip") -Force