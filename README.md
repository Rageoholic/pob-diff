# Epoch Path Builder

A build planner for [Last Epoch](https://www.lastepoch.com/), inspired by Path of Building. Plan your passive tree, skill specializations, and gear — then follow a guide without losing track of where your character actually is.

Built with Kotlin Multiplatform and Compose Multiplatform, targeting desktop, mobile, and web.

> Generated with Claude's assistance.

---

## Features (Planned)

### v1 — Trees
- Passive tree planner (includes mastery trees)
- Skill specialization tree planner

### v2 — Character & Tracking
- Character import — sync your character from Last Epoch (via API where available, otherwise manual entry)
- Quest passive tracker — track which quest rewards you've claimed
- Item import — pull in your character's current gear
- Idol import — pull in your character's current idols
- Custom item creation — manually define items for theorycrafting

### v3 — Itemization
- Item planner — full slot management and gear theorycrafting
- Idol planner
- Basic crafting simulation

### v4 — Guides
- Guide import — import a build guide and overlay it on your own character
- Guide vs. character diff view — see exactly where your character diverges from a guide without one overwriting the other

### v5 — Everything Else
- Blessings (Monolith)
- Faction reputation and rewards
- Damage and stat calculations (scope TBD — depends on community data availability; see Last Epoch Discord)

---

## Game Data

Tree, skill, and game calculation data is versioned alongside the app, allowing planning against specific patch versions. Approach inspired by epoch-tools and Path of Building.

Data sourcing is TBD — likely extracted game files or a community-maintained dataset.

---

## Architecture

The project is split into modules:

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

This project is not affiliated with Eleventh Hour Games.