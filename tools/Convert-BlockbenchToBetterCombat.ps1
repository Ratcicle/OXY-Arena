[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$AnimationName,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [string]$Author = "OXY Arena",
    [string]$Description = "",
    [int]$TicksPerSecond = 20,
    [Nullable[int]]$BeginTick = $null,
    [Nullable[int]]$EndTick = $null,
    [Nullable[int]]$StopTick = $null,
    [switch]$DegreesOutput
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-PropertyValue {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function Get-StringValue {
    param($Value)
    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [string]) {
        return $Value
    }

    return [string]$Value
}

function Try-ParseDouble {
    param($Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [double] -or $Value -is [float] -or $Value -is [decimal]) {
        return [double]$Value
    }

    if ($Value -is [int] -or $Value -is [long]) {
        return [double]$Value
    }

    $parsed = 0.0
    $style = [System.Globalization.NumberStyles]::Float
    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    if ([double]::TryParse(([string]$Value), $style, $culture, [ref]$parsed)) {
        return $parsed
    }

    return $null
}

function Convert-ToTick {
    param(
        [Parameter(Mandatory = $true)][double]$TimeSeconds
    )

    return [int][Math]::Round($TimeSeconds * $TicksPerSecond)
}

function Get-EasingName {
    param($Keyframe)

    $interpolation = (Get-StringValue (Get-PropertyValue $Keyframe "interpolation"))
    if ([string]::IsNullOrWhiteSpace($interpolation)) {
        return "EASEINOUTQUAD"
    }

    switch ($interpolation.ToLowerInvariant()) {
        "linear" { return "LINEAR" }
        "step" { return "CONSTANT" }
        "hold" { return "CONSTANT" }
        "bezier" { return "EASEINOUTQUAD" }
        "catmullrom" { return "EASEINOUTQUAD" }
        default { return "EASEINOUTQUAD" }
    }
}

function Resolve-BetterCombatBoneName {
    param(
        [Parameter(Mandatory = $true)][string]$Name
    )

    $normalized = ($Name -replace "[^a-zA-Z0-9]", "").ToLowerInvariant()
    switch ($normalized) {
        "head" { return "head" }
        "torso" { return "torso" }
        "body" { return "torso" }
        "rightarm" { return "rightArm" }
        "armright" { return "rightArm" }
        "rarm" { return "rightArm" }
        "leftarm" { return "leftArm" }
        "armleft" { return "leftArm" }
        "larm" { return "leftArm" }
        "rightleg" { return "rightLeg" }
        "legright" { return "rightLeg" }
        "rleg" { return "rightLeg" }
        "leftleg" { return "leftLeg" }
        "legleft" { return "leftLeg" }
        "lleg" { return "leftLeg" }
        "rightitem" { return "rightItem" }
        "itemright" { return "rightItem" }
        "leftitem" { return "leftItem" }
        "itemleft" { return "leftItem" }
        "torsobend" { return "torso_bend" }
        "rightarmbend" { return "rightArm_bend" }
        "leftarmbend" { return "leftArm_bend" }
        "rightlegbend" { return "rightLeg_bend" }
        "leftlegbend" { return "leftLeg_bend" }
        default { return $null }
    }
}

function Resolve-AnimatorName {
    param(
        [Parameter(Mandatory = $true)][string]$AnimatorId,
        [Parameter(Mandatory = $true)]$Animator,
        [Parameter(Mandatory = $true)][hashtable]$NameLookup
    )

    $inlineName = Get-StringValue (Get-PropertyValue $Animator "name")
    if (-not [string]::IsNullOrWhiteSpace($inlineName)) {
        return $inlineName
    }

    if ($NameLookup.ContainsKey($AnimatorId)) {
        return $NameLookup[$AnimatorId]
    }

    return $AnimatorId
}

function Add-NodeNamesToLookup {
    param(
        $Nodes,
        [Parameter(Mandatory = $true)][hashtable]$Lookup
    )

    foreach ($node in @($Nodes)) {
        if ($null -eq $node) {
            continue
        }

        if ($node -is [string]) {
            continue
        }

        $uuid = Get-StringValue (Get-PropertyValue $node "uuid")
        $name = Get-StringValue (Get-PropertyValue $node "name")
        if (-not [string]::IsNullOrWhiteSpace($uuid) -and -not [string]::IsNullOrWhiteSpace($name)) {
            $Lookup[$uuid] = $name
        }

        $children = Get-PropertyValue $node "children"
        if ($null -ne $children) {
            Add-NodeNamesToLookup -Nodes $children -Lookup $Lookup
        }
    }
}

function Get-AnimatorLookup {
    param(
        [Parameter(Mandatory = $true)]$Project
    )

    $lookup = @{}
    Add-NodeNamesToLookup -Nodes (Get-PropertyValue $Project "groups") -Lookup $lookup
    Add-NodeNamesToLookup -Nodes (Get-PropertyValue $Project "elements") -Lookup $lookup
    Add-NodeNamesToLookup -Nodes (Get-PropertyValue $Project "outliner") -Lookup $lookup
    return $lookup
}

function Get-KeyframeComponents {
    param(
        [Parameter(Mandatory = $true)]$Keyframe
    )

    $firstPoint = $null
    $dataPoints = Get-PropertyValue $Keyframe "data_points"
    if ($null -ne $dataPoints) {
        $points = @($dataPoints)
        if ($points.Count -gt 0) {
            $firstPoint = $points[0]
        }
    }

    if ($null -eq $firstPoint) {
        $firstPoint = $Keyframe
    }

    return @{
        x = Try-ParseDouble (Get-PropertyValue $firstPoint "x")
        y = Try-ParseDouble (Get-PropertyValue $firstPoint "y")
        z = Try-ParseDouble (Get-PropertyValue $firstPoint "z")
    }
}

function Convert-PositionAxis {
    param(
        [Parameter(Mandatory = $true)][string]$Axis,
        [Parameter(Mandatory = $true)][double]$Value
    )

    switch ($Axis) {
        "x" { return @{ key = "x"; value = $Value } }
        "y" { return @{ key = "y"; value = $Value } }
        "z" { return @{ key = "z"; value = $Value } }
        default { return $null }
    }
}

function Convert-RotationAxis {
    param(
        [Parameter(Mandatory = $true)][string]$Axis,
        [Parameter(Mandatory = $true)][double]$ValueDegrees
    )

    $convertedValue = if ($DegreesOutput) {
        $ValueDegrees
    } else {
        $ValueDegrees * [Math]::PI / 180.0
    }

    switch ($Axis) {
        "x" { return @{ key = "pitch"; value = $convertedValue } }
        "y" { return @{ key = "yaw"; value = $convertedValue } }
        "z" { return @{ key = "roll"; value = $convertedValue } }
        default { return $null }
    }
}

function New-MoveObject {
    param(
        [Parameter(Mandatory = $true)][int]$Tick,
        [Parameter(Mandatory = $true)][string]$Easing,
        [Parameter(Mandatory = $true)][string]$BoneName,
        [Parameter(Mandatory = $true)][string]$ChannelName,
        [Parameter(Mandatory = $true)][double]$Value
    )

    $roundedValue = [Math]::Round($Value, 6)
    $bonePayload = [ordered]@{}
    $bonePayload[$ChannelName] = $roundedValue

    $move = [ordered]@{
        tick = $Tick
        easing = $Easing
        turn = 0
    }
    $move[$BoneName] = $bonePayload
    return [pscustomobject]$move
}

function Get-AnimationByName {
    param(
        [Parameter(Mandatory = $true)]$Project,
        [Parameter(Mandatory = $true)][string]$RequestedName
    )

    foreach ($animation in @((Get-PropertyValue $Project "animations"))) {
        $name = Get-StringValue (Get-PropertyValue $animation "name")
        if ($name -eq $RequestedName) {
            return $animation
        }
    }

    throw "Animation '$RequestedName' was not found in '$InputPath'."
}

if (-not (Test-Path -LiteralPath $InputPath)) {
    throw "Input file not found: $InputPath"
}

$project = Get-Content -LiteralPath $InputPath -Raw -Encoding UTF8 | ConvertFrom-Json
$formatVersion = Get-PropertyValue $project "meta"
$formatVersionValue = $null
if ($null -ne $formatVersion) {
    $formatVersionValue = Get-StringValue (Get-PropertyValue $formatVersion "format_version")
}

$animation = Get-AnimationByName -Project $project -RequestedName $AnimationName
$lookup = Get-AnimatorLookup -Project $project
$moves = New-Object System.Collections.Generic.List[object]
$maxObservedTick = 0
$minObservedTick = [int]::MaxValue

$animators = Get-PropertyValue $animation "animators"
if ($null -eq $animators) {
    throw "Animation '$AnimationName' does not contain any animators."
}

foreach ($animatorProperty in $animators.PSObject.Properties) {
    $animatorId = $animatorProperty.Name
    $animator = $animatorProperty.Value
    $resolvedName = Resolve-AnimatorName -AnimatorId $animatorId -Animator $animator -NameLookup $lookup
    $betterCombatBone = Resolve-BetterCombatBoneName -Name $resolvedName

    if ([string]::IsNullOrWhiteSpace($betterCombatBone)) {
        continue
    }

    $keyframes = @((Get-PropertyValue $animator "keyframes"))
    foreach ($keyframe in $keyframes) {
        $channel = (Get-StringValue (Get-PropertyValue $keyframe "channel"))
        if ($channel -notin @("rotation", "position")) {
            continue
        }

        $timeValue = Try-ParseDouble (Get-PropertyValue $keyframe "time")
        if ($null -eq $timeValue) {
            continue
        }

        $tick = Convert-ToTick -TimeSeconds $timeValue
        if ($tick -le 0) {
            continue
        }

        $easing = Get-EasingName -Keyframe $keyframe
        $components = Get-KeyframeComponents -Keyframe $keyframe

        foreach ($axis in @("x", "y", "z")) {
            $componentValue = $components[$axis]
            if ($null -eq $componentValue) {
                continue
            }

            $converted = if ($channel -eq "rotation") {
                Convert-RotationAxis -Axis $axis -ValueDegrees $componentValue
            } else {
                Convert-PositionAxis -Axis $axis -Value $componentValue
            }

            if ($null -eq $converted) {
                continue
            }

            $targetBone = $betterCombatBone
            $targetChannel = $converted.key
            if ($targetChannel -eq "bend" -or $targetBone.EndsWith("_bend")) {
                $targetBone = $targetBone -replace "_bend$", ""
                $targetChannel = "bend"
            }

            $moves.Add((New-MoveObject -Tick $tick -Easing $easing -BoneName $targetBone -ChannelName $targetChannel -Value $converted.value))
            $maxObservedTick = [Math]::Max($maxObservedTick, $tick)
            $minObservedTick = [Math]::Min($minObservedTick, $tick)
        }
    }
}

if ($moves.Count -eq 0) {
    throw "No supported keyframes were found. Make sure the Blockbench bones are named like head/torso/rightArm/etc."
}

$sortedMoves = $moves | Sort-Object `
    @{ Expression = { $_.tick } }, `
    @{ Expression = { $_.PSObject.Properties.Name[3] } }, `
    @{ Expression = { ($_.PSObject.Properties.Value | Select-Object -Last 1 | Get-Member -MemberType NoteProperty | Select-Object -ExpandProperty Name -First 1) } }

$lengthSeconds = Try-ParseDouble (Get-PropertyValue $animation "length")
$computedEndTick = if ($null -ne $lengthSeconds) {
    Convert-ToTick -TimeSeconds $lengthSeconds
} else {
    $maxObservedTick
}

if ($minObservedTick -eq [int]::MaxValue) {
    $minObservedTick = 1
}

$finalBeginTick = if ($null -ne $BeginTick) { [int]$BeginTick } else { $minObservedTick }
$finalEndTick = if ($null -ne $EndTick) { [int]$EndTick } else { [Math]::Max($computedEndTick, $maxObservedTick) }
$finalStopTick = if ($null -ne $StopTick) { [int]$StopTick } else { [Math]::Max($finalEndTick + 10, $maxObservedTick) }
$loopValue = Get-StringValue (Get-PropertyValue $animation "loop")

$outputName = [System.IO.Path]::GetFileNameWithoutExtension($OutputPath)
if ([string]::IsNullOrWhiteSpace($Description)) {
    $Description = "Converted from Blockbench animation '$AnimationName'"
}

$outputObject = [ordered]@{
    name = $outputName
    author = $Author
    description = $Description
    emote = [ordered]@{
        isLoop = if ($loopValue -eq "loop") { "true" } else { "false" }
        returnTick = 0
        beginTick = $finalBeginTick
        endTick = $finalEndTick
        stopTick = $finalStopTick
        degrees = [bool]$DegreesOutput
        moves = @($sortedMoves)
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$json = $outputObject | ConvertTo-Json -Depth 100
Set-Content -LiteralPath $OutputPath -Value $json -Encoding UTF8

Write-Host "Converted Blockbench animation '$AnimationName' to '$OutputPath'."
if ($null -ne $formatVersionValue) {
    Write-Host "Detected Blockbench format version: $formatVersionValue"
}
Write-Host "Moves exported: $($sortedMoves.Count)"
