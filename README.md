# Reforge — LLM-Directed Refactoring Engine

An IntelliJ IDEA plugin that executes large-scale architectural refactorings headlessly via CLI + YAML config. Designed to be orchestrated by an LLM (via Claude Code skill) for refactorings that would otherwise burn tokens on individual file edits.

## Features

- **Move classes** between packages with full import/reference updating, wildcard pattern matching
- **Extract interfaces** from classes, selecting specific methods
- **Replace dependencies** — swap concrete types with interfaces in fields, constructors, and method parameters
- Operation batching — consecutive same-type operations are grouped for efficiency
- Source root awareness — test classes stay in test source roots
- Multi-pass resolution to handle indexing race conditions
- Automatic cleanup of empty packages after moves
- Dry-run mode to preview changes
- Headless execution (no UI dialogs)
- Claude Code skill for LLM-directed refactoring

## YAML Configuration

```yaml
operations:
  # Move classes to new packages
  - type: move
    target: com.example.app.task.model
    sources:
      - com.example.app.model.Task*          # Task, TaskStatus, TaskPriority + tests

  - type: move
    target: com.example.app.task.service
    sources:
      - com.example.app.service.TaskService*  # TaskService + TaskServiceTest

  # Extract interface from a class
  - type: extract-interface
    class: com.example.app.task.service.TaskService
    interface: com.example.app.task.port.TaskPort
    methods: [findAll, findById, createTask]

  # Replace a concrete dependency with an interface
  - type: replace-dependency
    in: com.example.app.task.controller.TaskController
    replace: com.example.app.task.service.TaskService
    with: com.example.app.task.port.TaskPort
```

### Operation Types

| Type | Description | Fields |
|------|-------------|--------|
| `move` | Move classes to a target package | `target`, `sources` (list of patterns) |
| `extract-interface` | Create interface from class methods | `class`, `interface`, `methods` |
| `replace-dependency` | Replace type references in a class | `in`, `replace`, `with` |

### Wildcards (for move operations)

- `*` — matches within a single segment (e.g., `Task*` matches `Task`, `TaskStatus`)
- `**` — matches zero or more package segments

### Ordering

Operations execute in listed order. Consecutive same-type operations are batched automatically. Recommended order:

1. `move` — restructure packages first
2. `extract-interface` — create abstractions
3. `replace-dependency` — wire up interfaces

## Building

```bash
./gradlew buildPlugin
```

The plugin ZIP is built to `build/distributions/`.

## Usage

### Via Gradle runIde

```bash
# Real run
./gradlew runIde --args="reforge /path/to/project /path/to/reforge.yaml"

# Dry run
./gradlew runIde --args="reforge /path/to/project /path/to/reforge.yaml --dry-run"
```

### Via IntelliJ Command Line

```bash
/path/to/idea reforge /path/to/project /path/to/reforge.yaml [--dry-run]
```

### Via Claude Code Skill

Install the IntelliJ plugin:

```bash
idea installPlugins ch.riesennet.reforge https://raw.githubusercontent.com/notiriel/reforge/main/updatePlugins.xml
```

Install the Claude Code plugin:

```
/plugin marketplace add notiriel/reforge
/plugin install reforge@notiriel-reforge
```

Then use in any project:

```
/reforge restructure the project into domain-driven packages
```

The skill analyzes the codebase, generates the YAML config, executes Reforge, and validates by running tests.

## How It Works

1. Opens the target project in a headless IntelliJ instance
2. Auto-configures project JDK and source roots if missing
3. Waits for indexing to complete
4. Parses YAML config into operation batches
5. For each batch:
   - Parses raw YAML into typed operation specs
   - Waits for smart mode (indexing ready)
   - Executes the operation (move: multi-pass resolve then batch execute; extract/replace: per-spec execution)
   - Saves documents and syncs VFS
6. Prints summary of results

## Testing

### Unit Tests

```bash
./gradlew test              # Run 59 unit tests
./gradlew koverLog          # Print line coverage summary
./gradlew koverHtmlReport   # HTML report → build/reports/kover/html/
```

Unit tests cover all pure logic: YAML config parsing, glob pattern matching, CLI arg parsing, operation batching, `parseSpec()` for all 3 operation types, operation registry, and progress tracking. Coverage is measured with [Kover](https://github.com/Kotlin/kotlinx-kover).

IntelliJ API orchestration (`execute()` methods, PSI manipulation, indexing) is covered by the E2E test.

### E2E Test

A complete Spring Boot test project is included at `src/test/resources/test-project/` (21 source classes, 5 test classes, 46 JUnit 5 tests):

```bash
# Copy test project (runIde modifies files in place)
cp -r src/test/resources/test-project /tmp/test-project

# Run Reforge
./gradlew buildPlugin
./gradlew runIde --args="reforge /tmp/test-project /tmp/test-project/refactor.yaml"
# Expect: 25 moved, 0 failed, 7 packages removed
```

## Requirements

- IntelliJ IDEA 2024.3+ (build 243+)
- Java 21+ (for building)
- Gradle 9+ (or use the included wrapper)

## Tech Stack

- Kotlin 2.3.10
- IntelliJ Platform Gradle Plugin 2.11.0
- IntelliJ Platform SDK (IC 2024.3)
- SnakeYAML 2.2

## License

Apache 2.0
