# PoBDiff

A Path of Exile build planner inspired by Path of Building, with a key addition: import a PoB guide alongside your own character and see exactly where you diverge — without one overwriting the other.

Built with Kotlin Multiplatform and Compose Multiplatform, targeting desktop, mobile, and web.

> Generated with Claude's assistance.

---

## Features (Planned)

### v1 — Passive Tree Diff
- Passive tree viewer
- PoB build import
- Character import via PoE API
- Diff view — overlay a guide's passive tree against your character and highlight the delta

### v2 — Skills & Gear
- Skill gem planner
- Item planner with basic theorycrafting
- PoB guide vs. character diff extended to skills and gear

### v3 — Calculations
- Damage and stat calculations (incremental, based on community data and PoB parity)

---

## Game Data

Passive tree and skill data is sourced from the official PoE API and community datasets, versioned alongside the app to support planning against specific league versions.

---

## Architecture

- **`core`** — platform-agnostic game logic and data models, shared across all targets
- **`composeApp`** — desktop and mobile UI built with Compose Multiplatform
- **`server`** *(planned)* — Ktor-based HTTP API backend for the web experience
- **Web frontend** *(TBD)* — may live in this repo or a separate project; stack undecided

---

## Platforms

| Platform       | Status                                |
|----------------|---------------------------------------|
| Desktop (JVM)  | In progress                           |
| Android        | Planned                               |
| iOS            | Planned                               |
| Web            | Planned (via HTTP API + TBD frontend) |

---

## Building

### Prerequisites

- JDK 21
- IntelliJ IDEA (recommended), or Gradle 8.14+ for command-line builds

### Desktop

Run from the IDE using the run configuration in the toolbar, or from the terminal:

**macOS / Linux**
```shell
./gradlew :composeApp:run
```

**Windows**
```shell
.\gradlew.bat :composeApp:run
```

---

## License

Mozilla Public License 2.0 — see [LICENSE](./LICENSE).

---

## Contributing

Contributions are welcome. Open an issue before starting significant work so we can align on approach.

This project is not affiliated with Grinding Gear Games.
