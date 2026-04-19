# Repository Guidelines

## Project Structure & Module Organization

OXY Arena is a NeoForge Minecraft mod targeting Minecraft 1.21.1 and Java 21. Main Java sources live in `src/main/java/com/example/oxyarena`, grouped by feature area: `item`, `event`, `serverevent`, `registry`, `network`, `client`, `entity`, `block`, `loot`, and `worldgen`. Runtime resources live in `src/main/resources`; use `assets/oxyarena` for models, textures, sounds, shaders, particles, and language files, and `data/oxyarena` for recipes, loot tables, tags, configured features, placed features, and custom data. Gradle and NeoForge configuration are in `build.gradle`, `settings.gradle`, and `gradle.properties`.

## Build, Test, and Development Commands

Use the included Gradle wrapper from the repository root:

- `./gradlew.bat compileJava` checks Java compilation.
- `./gradlew.bat build` builds the mod jar and processes resources.
- `./gradlew.bat runClient` starts a local Minecraft client for manual testing.
- `./gradlew.bat runServer` starts a local dedicated server with `--nogui`.
- `./gradlew.bat runGameTestServer` runs registered NeoForge game tests.
- `./gradlew.bat runData` regenerates data into `src/generated/resources`.

On non-Windows shells, use `./gradlew` instead of `./gradlew.bat`.

## Coding Style & Naming Conventions

Java is compiled as UTF-8 and targets Java 21. Follow the existing style: 4-space indentation, braces on the same line, `PascalCase` classes, `camelCase` fields and methods, and `UPPER_SNAKE_CASE` constants. Keep mod registries in `registry/` and name them `ModItems`, `ModBlocks`, `ModEntityTypes`, etc. Resource identifiers must remain lowercase snake case, matching `mod_id=oxyarena`; example: `assets/oxyarena/models/item/black_diamond_sword.json`.

## Testing Guidelines

There is no dedicated `src/test` tree at present. Validate normal changes with `compileJava` and, when behavior changes, `runClient` or `runServer`. For mechanics that can be automated, add NeoForge GameTests under the mod namespace and run `runGameTestServer`. For asset or data changes, verify JSON paths, registry names, and in-game loading.

## Commit & Pull Request Guidelines

Recent history uses short, descriptive commits, often with Conventional Commit-style prefixes such as `feat:`. Prefer imperative summaries like `feat: add airdrop crate loot table` or `fix: prevent grapple desync`. Pull requests should describe gameplay impact, list validation commands run, link related issues, and include screenshots or clips for UI, rendering, texture, shader, or animation changes.

## Agent-Specific Instructions

Do not commit generated Gradle output from `build/`, `.gradle/`, `runs/`, or IDE folders. Keep changes scoped to the requested feature and avoid unrelated refactors while editing gameplay systems.
