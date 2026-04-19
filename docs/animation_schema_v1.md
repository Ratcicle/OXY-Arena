# OXY Animation Schema v1

This document defines the first JSON contract for OXY Arena player animations.
It is intentionally small: v1 proves the path from Blockbench keyframes to an
in-game player pose without committing the runtime to item, block, entity, IK,
or external animation-library details.

## Resource Location

Official OXY animation files should live under:

```text
assets/oxyarena/animations/player/<feature>/<animation>.json
```

Example:

```text
assets/oxyarena/animations/player/ghost_saber/diagonal_slash.json
```

Player animation files are visual client resources, so they belong in
`assets/oxyarena`, not `data/oxyarena`.

## Required Fields

The official v1 format requires these top-level fields:

- `schema_version`: must be `1`.
- `type`: must be `"oxyarena:player_animation"` for v1.
- `length`: animation duration in seconds; must be greater than `0`.
- `animations`: non-empty list of animation tracks.

Each track requires:

- `bone`: target player bone name.
- `target`: transform channel, one of `"rotation"`, `"position"`, or `"scale"`.
- `keyframes`: non-empty list of keyframes.

Each keyframe requires:

- `timestamp`: keyframe time in seconds.
- `target`: array of exactly three numbers, `[x, y, z]`.

The v1 units are fixed by the schema:

- time uses seconds;
- rotation uses degrees;
- position uses model units;
- scale uses multipliers.

Values are authored/exported in the convention produced by the supported
Blockbench exporter. Rotation values stay in degrees in JSON and are converted
to radians by the runtime adapter before rendering.

When applying v1 player animation values to Minecraft's vanilla `ModelPart`
pose space, the current player adapter keeps X as authored and mirrors these
channels:

- `position.y`
- `position.z`
- `rotation.y`
- `rotation.z`

Vanilla `HumanoidModel.ArmPose.ITEM` also changes held-item arms before custom
animation is applied. For reference, vanilla item pose sets the arm X rotation
to `xRot * 0.5 - PI / 10`, which adds an 18 degree forward bias and halves the
previous arm swing. The v1 player renderer suppresses that vanilla arm pose for
arms controlled by an active OXY animation, so authored arm tracks are not
mixed with the default held-item pose.

The first-person MVP uses the same active animation instance and model patch
through `RenderArmEvent`. It replaces the vanilla first-person arm pass when
that arm is rendered and the sampled pose contains the matching `rightArm` or
`leftArm` track. Normal first-person item rendering is not retargeted in v1;
dedicated item bones such as `rightItem` are reserved for a later schema/runtime
pass.

## Optional Fields And Defaults

The loader should apply these defaults when optional fields are absent:

```json
{
  "loop": "none",
  "apply": {
    "base": "vanilla",
    "mode": "additive",
    "blend_in": 0.05,
    "blend_out": 0.12,
    "priority": 100,
    "mask": []
  }
}
```

Supported `loop` values:

- `"none"`: play once, then stop affecting the model.
- `"loop"`: repeat until the controller stops the instance.
- `"hold"`: play once, then hold the final sampled pose.

Supported `apply.base` values:

- `"vanilla"`: apply this animation after the vanilla player pose is prepared.

Supported `apply.mode` values:

- `"additive"`: add the sampled transform on top of the base pose.
- `"replace"`: replace the target channel for affected bones.

If `apply.mask` is empty or absent, the loader should derive the mask from the
track bone names.

## Supported Player Bones

The v1 player bone whitelist is:

- `head`
- `body`
- `rightArm`
- `leftArm`
- `rightLeg`
- `leftLeg`

The render implementation maps these names to the matching `HumanoidModel`
parts.

## Interpolation

`interpolation` is optional on keyframes. If absent, the loader should use
`"linear"`.

Supported values:

- `"linear"`
- `"step"`
- `"bezier"`

For v1, `"bezier"` means a standard smooth cubic interpolation chosen by the
runtime. Custom Bezier handles are not part of v1, because the current
Blockbench JSON export only provides the interpolation name.

## Validation Rules

The loader should reject files that violate required structure. It should also
validate:

- `schema_version == 1`;
- `type == "oxyarena:player_animation"`;
- `length > 0`;
- each `timestamp` is within `0..length`;
- each keyframe `target` has exactly three numeric values;
- each `bone` is in the v1 player bone whitelist;
- each track `target` is one of the supported transform channels;
- each `interpolation` is one of the supported interpolation values;
- `blend_in` and `blend_out` are greater than or equal to `0`;
- `priority` is an integer.

If keyframes are not sorted by `timestamp`, the loader may sort them and log a
warning. Duplicate timestamps in the same track should be rejected.

## Official Example

```json
{
  "schema_version": 1,
  "type": "oxyarena:player_animation",
  "length": 1.0,
  "loop": "none",
  "apply": {
    "base": "vanilla",
    "mode": "additive",
    "blend_in": 0.05,
    "blend_out": 0.12,
    "priority": 100,
    "mask": ["rightArm"]
  },
  "animations": [
    {
      "bone": "rightArm",
      "target": "position",
      "keyframes": [
        {
          "timestamp": 0.0,
          "target": [0.0, 0.0, 3.0],
          "interpolation": "bezier"
        }
      ]
    },
    {
      "bone": "rightArm",
      "target": "rotation",
      "keyframes": [
        {
          "timestamp": 0.0,
          "target": [-94.5821, 5.9439, 52.2619],
          "interpolation": "bezier"
        },
        {
          "timestamp": 0.25,
          "target": [-174.5821, 5.9439, 52.2619],
          "interpolation": "bezier"
        },
        {
          "timestamp": 0.79167,
          "target": [2.9179, 5.9439, 52.2619],
          "interpolation": "bezier"
        }
      ]
    }
  ]
}
```

## Raw Blockbench Import

The loader may accept raw JSON exported by the Blockbench `animation_to_json`
plugin as an import format. Raw imports are not the official OXY schema and may
omit `schema_version`, `type`, `loop`, and `apply`.

When reading raw imports, the loader should treat the file as v1-compatible
input and apply the defaults from this document. Final committed OXY animation
assets should use the official schema wrapper.

## Forking The Export Plugin

The plugin fork should happen after this schema is stable enough to test in the
runtime. The fork should export the official wrapper directly instead of only
the raw `length` and `animations` payload.

The first fork target should add:

- `schema_version`;
- `type`;
- `loop`;
- `apply.base`;
- `apply.mode`;
- `apply.blend_in`;
- `apply.blend_out`;
- `apply.priority`;
- `apply.mask`.

Future schema versions can add richer curves, timeline events, item/block
targets, or external adapter metadata without changing the v1 contract.
