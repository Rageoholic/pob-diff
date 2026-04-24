# Known Warnings

Warnings we are aware of and have chosen not to fix, with reasoning.

## Gradle build files — `compose.*` shorthand deprecation

**Files:** `core/build.gradle.kts`, `composeApp/build.gradle.kts`

**Warning:** `'val runtime: String' is deprecated. Specify dependency directly.` (and similar for `material3`,
`foundation`, `ui`, `uiTooling`, `components.resources`)

**Reason:** The Compose Multiplatform Gradle plugin shorthands (`compose.runtime`, `compose.material3`, etc.) perform
two things that direct Maven coordinates cannot yet replicate cleanly in `commonMain`: per-platform artifact routing (
expanding to the correct `-jvm`/`-android`/etc. artifact per target) and internal BOM version coherence. JetBrains has
not yet published the KMP metadata artifacts in a form that makes direct coordinates equally ergonomic for multiplatform
`commonMain` declarations. Since this project is designed to be multiplatform-ready, the shorthands are the correct tool
for now. Revisit when JetBrains ships proper KMP metadata for these modules.
