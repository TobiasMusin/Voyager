param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [switch]$RequireTriangles
)

$ErrorActionPreference = 'Stop'

function Add-ValidationError([System.Collections.Generic.List[string]]$Errors, [string]$Message) {
    $Errors.Add($Message)
}

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "glTF file does not exist: $Path"
}

if ([System.IO.Path]::GetExtension($Path).ToLowerInvariant() -ne '.gltf') {
    throw "Only JSON .gltf files are supported by this validator: $Path"
}

try {
    $document = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
} catch {
    throw "Invalid JSON glTF: $($_.Exception.Message)"
}

$errors = [System.Collections.Generic.List[string]]::new()
if ($document.asset.version -ne '2.0') {
    Add-ValidationError $errors 'asset.version must equal 2.0.'
}

$buffers = @($document.buffers)
if ($buffers.Count -ne 1) {
    Add-ValidationError $errors "Expected exactly one embedded buffer; found $($buffers.Count)."
}

$bytes = $null
if ($buffers.Count -ge 1) {
    $uri = [string]$buffers[0].uri
    $prefix = 'data:application/octet-stream;base64,'
    if (-not $uri.StartsWith($prefix, [System.StringComparison]::Ordinal)) {
        Add-ValidationError $errors 'buffers[0].uri must be an application/octet-stream base64 data URI.'
    } else {
        try {
            $bytes = [Convert]::FromBase64String($uri.Substring($prefix.Length))
            if ($bytes.Length -ne [int]$buffers[0].byteLength) {
                Add-ValidationError $errors "Decoded buffer length $($bytes.Length) differs from declared byteLength $($buffers[0].byteLength)."
            }
        } catch {
            Add-ValidationError $errors "buffers[0].uri has invalid base64 data: $($_.Exception.Message)"
        }
    }
}

$bufferViews = @($document.bufferViews)
$accessors = @($document.accessors)
$positionAccessorIndices = [System.Collections.Generic.HashSet[int]]::new()
foreach ($accessorIndex in 0..($accessors.Count - 1)) {
    if ($accessors.Count -eq 0) { break }
    $accessor = $accessors[$accessorIndex]
    if ($null -eq $accessor.bufferView -or [int]$accessor.bufferView -lt 0 -or [int]$accessor.bufferView -ge $bufferViews.Count) {
        Add-ValidationError $errors "accessors[$accessorIndex] references an invalid bufferView."
        continue
    }
    if ($accessor.componentType -ne 5126 -or $accessor.type -ne 'VEC3') {
        continue
    }
    $view = $bufferViews[[int]$accessor.bufferView]
    $byteOffset = if ($null -eq $view.byteOffset) { 0 } else { [int]$view.byteOffset }
    $expectedLength = [int]$accessor.count * 12
    if ([int]$view.byteLength -ne $expectedLength) {
        Add-ValidationError $errors "accessors[$accessorIndex] needs $expectedLength bytes but its bufferView declares $($view.byteLength)."
        continue
    }
    if ($null -eq $bytes -or $byteOffset -lt 0 -or $byteOffset + $expectedLength -gt $bytes.Length) {
        Add-ValidationError $errors "accessors[$accessorIndex] position bytes are outside the decoded buffer."
        continue
    }
    $actualMin = [float[]]@([float]::PositiveInfinity, [float]::PositiveInfinity, [float]::PositiveInfinity)
    $actualMax = [float[]]@([float]::NegativeInfinity, [float]::NegativeInfinity, [float]::NegativeInfinity)
    for ($vertexIndex = 0; $vertexIndex -lt [int]$accessor.count; $vertexIndex++) {
        for ($component = 0; $component -lt 3; $component++) {
            $value = [BitConverter]::ToSingle($bytes, $byteOffset + ($vertexIndex * 12) + ($component * 4))
            if ([float]::IsNaN($value) -or [float]::IsInfinity($value)) {
                Add-ValidationError $errors "accessors[$accessorIndex] contains a non-finite value at vertex $vertexIndex."
                break
            }
            $actualMin[$component] = [Math]::Min($actualMin[$component], $value)
            $actualMax[$component] = [Math]::Max($actualMax[$component], $value)
        }
    }
    if (@($accessor.min).Count -ne 3 -or @($accessor.max).Count -ne 3) {
        Add-ValidationError $errors "accessors[$accessorIndex] must provide three-element min and max bounds."
        continue
    }
    for ($component = 0; $component -lt 3; $component++) {
        if ([Math]::Abs($actualMin[$component] - [float]$accessor.min[$component]) -gt 0.0001 -or [Math]::Abs($actualMax[$component] - [float]$accessor.max[$component]) -gt 0.0001) {
            Add-ValidationError $errors "accessors[$accessorIndex] min/max do not match decoded position bytes."
            break
        }
    }
}

$pointPrimitiveCount = 0
$trianglePrimitiveCount = 0
foreach ($mesh in @($document.meshes)) {
    foreach ($primitive in @($mesh.primitives)) {
        if ($null -eq $primitive.attributes.POSITION) {
            Add-ValidationError $errors 'A mesh primitive does not define POSITION.'
        } elseif ([int]$primitive.attributes.POSITION -lt 0 -or [int]$primitive.attributes.POSITION -ge $accessors.Count) {
            Add-ValidationError $errors 'A mesh primitive references an invalid POSITION accessor.'
        } else {
            $positionAccessorIndices.Add([int]$primitive.attributes.POSITION) | Out-Null
        }
        $mode = if ($null -eq $primitive.mode) { 4 } else { [int]$primitive.mode }
        if ($mode -eq 0) { $pointPrimitiveCount++ }
        if ($mode -eq 4) {
            $trianglePrimitiveCount++
            if ($null -eq $primitive.indices) {
                Add-ValidationError $errors 'A triangle primitive has no index accessor.'
            } elseif ($null -ne $primitive.attributes.POSITION -and [int]$primitive.attributes.POSITION -ge 0 -and [int]$primitive.attributes.POSITION -lt $accessors.Count) {
                $indexAccessorIndex = [int]$primitive.indices
                if ($indexAccessorIndex -lt 0 -or $indexAccessorIndex -ge $accessors.Count) {
                    Add-ValidationError $errors 'A triangle primitive references an invalid index accessor.'
                    continue
                }
                $indexAccessor = $accessors[$indexAccessorIndex]
                if ($indexAccessor.type -ne 'SCALAR' -or @($indexAccessor.componentType) -notcontains 5123 -and @($indexAccessor.componentType) -notcontains 5125) {
                    Add-ValidationError $errors "accessors[$indexAccessorIndex] must be UNSIGNED_SHORT or UNSIGNED_INT SCALAR indices."
                    continue
                }
                if ($indexAccessor.count % 3 -ne 0) {
                    Add-ValidationError $errors "accessors[$indexAccessorIndex] index count must be divisible by three."
                    continue
                }
                $indexViewIndex = [int]$indexAccessor.bufferView
                if ($indexViewIndex -lt 0 -or $indexViewIndex -ge $bufferViews.Count) {
                    Add-ValidationError $errors "accessors[$indexAccessorIndex] references an invalid bufferView."
                    continue
                }
                $indexSize = if ($indexAccessor.componentType -eq 5123) { 2 } else { 4 }
                $indexView = $bufferViews[$indexViewIndex]
                $indexOffset = if ($null -eq $indexView.byteOffset) { 0 } else { [int]$indexView.byteOffset }
                $indexByteLength = [int]$indexAccessor.count * $indexSize
                if ([int]$indexView.byteLength -ne $indexByteLength -or $null -eq $bytes -or $indexOffset -lt 0 -or $indexOffset + $indexByteLength -gt $bytes.Length) {
                    Add-ValidationError $errors "accessors[$indexAccessorIndex] index bytes are outside its bufferView or decoded buffer."
                    continue
                }
                $positionCount = [int]$accessors[[int]$primitive.attributes.POSITION].count
                $nonDegenerateTriangle = $false
                for ($indexOffsetInAccessor = 0; $indexOffsetInAccessor -lt [int]$indexAccessor.count; $indexOffsetInAccessor++) {
                    $byteIndex = $indexOffset + ($indexOffsetInAccessor * $indexSize)
                    $indexValue = if ($indexSize -eq 2) { [BitConverter]::ToUInt16($bytes, $byteIndex) } else { [BitConverter]::ToUInt32($bytes, $byteIndex) }
                    if ($indexValue -ge $positionCount) {
                        Add-ValidationError $errors "accessors[$indexAccessorIndex] index $indexOffsetInAccessor is outside POSITION count $positionCount."
                        break
                    }
                    if ($indexOffsetInAccessor % 3 -eq 2) {
                        $first = if ($indexSize -eq 2) { [BitConverter]::ToUInt16($bytes, $byteIndex - (2 * $indexSize)) } else { [BitConverter]::ToUInt32($bytes, $byteIndex - (2 * $indexSize)) }
                        $second = if ($indexSize -eq 2) { [BitConverter]::ToUInt16($bytes, $byteIndex - $indexSize) } else { [BitConverter]::ToUInt32($bytes, $byteIndex - $indexSize) }
                        if ($first -ne $second -and $second -ne $indexValue -and $first -ne $indexValue) { $nonDegenerateTriangle = $true }
                    }
                }
                if (-not $nonDegenerateTriangle) {
                    Add-ValidationError $errors "accessors[$indexAccessorIndex] contains no non-degenerate triangle."
                }
            }
        }
        if ($RequireTriangles -and $mode -ne 4) {
            Add-ValidationError $errors "Expected TRIANGLES mode (4), found mode $mode."
        }
    }
}

if ($pointPrimitiveCount + $trianglePrimitiveCount -eq 0) {
    Add-ValidationError $errors 'Expected at least one mesh primitive.'
}

if ($RequireTriangles -and $trianglePrimitiveCount -eq 0) {
    Add-ValidationError $errors 'Expected at least one triangle primitive.'
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Valid glTF: $Path ($trianglePrimitiveCount triangle primitive(s), $pointPrimitiveCount point primitive(s))."