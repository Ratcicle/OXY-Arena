# PlayerAnimator Pipeline

OXY's custom player animation runtime is inactive for now. Its schema, loader,
debug command, and renderer classes remain in the repository as experimental
reference code, but they are no longer registered during client startup and the
Ghost Saber no longer sends the old OXY animation payload.

The active direction for player skill animations is Player Animator
(`playeranimator`), already present in the development client through Better
Combat. OXY declares a client-side dependency on Player Animator so animation
code can be added deliberately instead of relying on the library being present
by accident.

The compile-time API dependency is declared through KosmX's Maven as
`dev.kosmx.player-anim:player-animation-lib-forge:${player_animator_version}`.
Runtime testing still needs the Player Animator mod present in the client mod
set, either directly or through the local Better Combat setup.

## Asset Location

Player Animator scans this resource directory:

```text
assets/<namespace>/player_animations/
```

For OXY assets, use:

```text
src/main/resources/assets/oxyarena/player_animations/
```

Do not use `player_animation` in the singular; Player Animator logs that as the
wrong directory. The previous OXY draft directory
`assets/oxyarena/animations/player/` is not part of the active runtime.

## Naming Contract

Player Animator registers animations from the animation name inside the exported
file, not from the file path alone. The first Phantom Saber animation should be
exported so its registered id becomes:

```text
oxyarena:phantom_saber_slash
```

The matching constant is
`ModPlayerAnimations.GHOST_SABER_PHANTOM_SABER_SLASH`.

## Runtime Bridge

`OxyPlayerAnimatorBridge` is the client-side bridge for playing exported
Player Animator assets. It:

- looks up `PlayerAnimationRegistry.getAnimation(animationId)`;
- gets the local player's `AnimationStack` through `PlayerAnimationAccess`;
- plays the animation on a dedicated OXY layer;
- enables first-person rendering through `FirstPersonMode.THIRD_PERSON_MODEL`;
- is triggered from the server-confirmed Ghost Saber activation path through
  `PlayerAnimationPlayPayload`.

Keep this bridge player-only. Item, block, entity, and custom OXY schema support
remain out of scope until the gameplay content path is moving again.
