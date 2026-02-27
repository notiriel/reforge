# CLAUDE.md

## Project Overview

**Reforge** — an extensible IntelliJ IDEA plugin (Kotlin) that performs large-scale architectural refactorings via CLI using YAML configuration. Runs headlessly — no UI dialogs. Designed to be orchestrated by an LLM via Claude Code skill.

## Build Commands

```bash
./gradlew buildPlugin    # Build the plugin
./gradlew runIde         # Run IntelliJ sandbox with plugin
./gradlew clean          # Clean build artifacts
```

If the Gradle wrapper jar is missing, regenerate it with the system Gradle:

```bash
gradle wrapper
```

SDKMAN is used for Java/Gradle. Initialize with:

```bash
export SDKMAN_DIR="/Users/stefan/.sdkman" && source "/Users/stefan/.sdkman/bin/sdkman-init.sh"
```

## Running Reforge

### Via wrapper script (recommended)

The `reforge` wrapper script launches IntelliJ headlessly without opening a window or conflicting with a running IDE:

```bash
# Real run
reforge /path/to/project /path/to/config.yaml

# Dry run
reforge /path/to/project /path/to/config.yaml --dry-run
```

Install the wrapper: `cp scripts/reforge.sh ~/.local/bin/reforge && chmod +x ~/.local/bin/reforge`

Set `IDEA_HOME` to override IntelliJ location (macOS: the .app bundle, Linux: the installation dir).

### Via Gradle runIde (development)

```bash
# Real run
./gradlew runIde --args="reforge /path/to/project /path/to/config.yaml"

# Dry run
./gradlew runIde --args="reforge /path/to/project /path/to/config.yaml --dry-run"
```

Or with the system Gradle directly:

```bash
gradle runIde --args="reforge /path/to/project /path/to/config.yaml"
```

## Project Structure

```
src/main/kotlin/ch/riesennet/reforge/
├── ReforgeStarter.kt                  # ApplicationStarter entry point (CLI parsing, orchestration, batching)
├── ReforgeConfig.kt                   # YAML parsing → List<RawOperation>
├── ProgressReporter.kt                # Stderr output formatting
├── ClassResolver.kt                   # Glob pattern matching for class resolution
├── infrastructure/
│   ├── ProjectSetup.kt                # JDK auto-config, source root auto-config
│   ├── IndexingHelper.kt              # waitForSmartMode, IndexNotReadyException detection
│   └── VfsHelper.kt                   # syncRefresh, saveAllDocuments utilities
├── operation/
│   ├── Operation.kt                   # Operation interface
│   ├── OperationSpec.kt               # Base interface for operation specs
│   ├── OperationResult.kt             # Success/failure/skipped per-item results
│   └── OperationRegistry.kt           # type string → Operation factory
└── operations/
    ├── move/
    │   ├── MoveOperation.kt           # Resolve-all-then-move, empty package cleanup
    │   ├── MoveSpec.kt                # target: String, sources: List<String>
    │   └── HeadlessMoveProcessor.kt   # Subclass exposing findUsages()+execute()
    ├── extract/
    │   ├── ExtractInterfaceOperation.kt  # Creates interface, adds implements clause
    │   └── ExtractInterfaceSpec.kt    # class, interface, methods
    └── replace/
        ├── ReplaceDependencyOperation.kt  # Replaces type references + imports
        └── ReplaceDependencySpec.kt   # in, replace, with
```

## Architecture

### Operation Interface

Each operation type implements `Operation`:
- `parseSpec(raw: Map<String, Any>): OperationSpec` — parse YAML into typed spec
- `execute(project, specs, reporter, dryRun): List<OperationResult>` — execute a batch

### Orchestrator Flow (ReforgeStarter)

1. Parse YAML → raw operation list
2. Group consecutive same-type operations into batches
3. For each batch:
   - `registry.get(type).parseSpec()` for each raw entry
   - `IndexingHelper.waitForSmartMode()`
   - `operation.execute(project, specs, reporter, dryRun)`
   - `VfsHelper.saveAllAndSync()`
4. Print summary

### Key IntelliJ APIs

- `ApplicationStarter` — Headless CLI entry point
- `DumbService.waitForSmartMode()` — Wait for indexing (must NOT be called on EDT)
- `JavaPsiFacade.findClass()` — Find class by qualified name
- `AllClassesSearch` — Find classes by glob pattern
- `MoveClassesOrPackagesProcessor` — Execute move refactoring with reference updating
- `WriteCommandAction` — Create directories/modify PSI in write context
- `VirtualFileManager.syncRefresh()` — Flush VFS changes to disk
- `ReadAction.compute` — Thread-safe PSI access from non-EDT threads
- `ProjectRootManager.fileIndex.getSourceRootForFile()` — Determine source root (main vs test)

### Key Design Decisions

- **HeadlessMoveProcessor**: Subclass of `MoveClassesOrPackagesProcessor` that exposes protected `findUsages()` and `execute()` methods, bypassing UI dialogs.
- **Resolve-all-then-move**: MoveOperation resolves all patterns across multiple passes (up to 5) WITHOUT moving. Only after all patterns are resolved does the single move phase execute.
- **Source root awareness**: Each class is moved under the same source root it originated from. Test classes stay in test source roots.
- **Operation batching**: Consecutive same-type operations are grouped. This allows MoveOperation to do multi-pass resolve across all move specs in the batch.
- **JDK/source root auto-configuration**: Fresh sandbox handling extracted to `ProjectSetup`.
- **IndexNotReadyException retry**: Each move retries up to 3 times with 2s delays.
- **Stderr for all output**: Gradle only shows stderr from `runIde`.
- **`-Djava.awt.headless=true`**: Prevents any UI dialogs from opening during headless execution.

## Testing

### Unit Tests

```bash
gradle test           # Run unit tests
gradle koverLog       # Print line coverage percentage
gradle koverHtmlReport  # Generate HTML coverage report (build/reports/kover/html/)
gradle koverXmlReport   # Generate XML coverage report
```

59 unit tests across 8 test files covering all pure logic (config parsing, pattern matching, arg parsing, batching, `parseSpec()` methods, registry lookups). Coverage tool: [Kover](https://github.com/Kotlin/kotlinx-kover) (JetBrains' Kotlin coverage tool).

**Not unit-tested** (covered by E2E): `Operation.execute()` implementations, `HeadlessMoveProcessor`, infrastructure classes (`ProjectSetup`, `VfsHelper`, `IndexingHelper`), `ReforgeStarter.run()`/`openProject()`/`closeProject()`.

**Note on E2E coverage instrumentation**: JaCoCo/Kover agents cannot instrument `runIde` — IntelliJ's `PathClassLoader` bypasses `ClassFileTransformer`, and the Kover agent disrupts indexing. Unit test coverage via Kover works because test classes are loaded directly from the classpath, not through the plugin sandbox.

### Test Files

```
src/test/kotlin/ch/riesennet/reforge/
├── ReforgeConfigTest.kt                              # YAML parsing
├── ClassResolverTest.kt                              # Glob pattern → regex
├── ReforgeStarterTest.kt                             # parseArgs(), groupIntoBatches()
├── ProgressReporterTest.kt                           # Counter tracking, hasFailures()
├── OperationRegistryTest.kt                          # Type lookup, knownTypes()
└── operations/
    ├── move/MoveOperationParseSpecTest.kt            # Move spec parsing
    ├── extract/ExtractInterfaceOperationParseSpecTest.kt  # Extract spec parsing
    └── replace/ReplaceDependencyOperationParseSpecTest.kt # Replace spec parsing
```

### E2E Test

E2E testing is **manual** — there is no automated E2E test harness. IntelliJ's `PathClassLoader` prevents JaCoCo/Kover instrumentation of `runIde`, and the plugin requires a full IntelliJ sandbox to exercise PSI operations, move refactoring, and import fixing.

**Setup:** The E2E test project exists in two locations:

1. **Canonical copy** — `src/test/resources/test-project/` (checked into this repo, source of truth)
2. **Working copy** — `~/development/test-project/` (a separate Git repo used for running E2E tests)

The working copy is a standalone Git repo so it can be reset with `git checkout`/`git clean` between runs. Keep both copies in sync: edit the canonical copy, then copy changes to the working copy before running E2E tests.

**What it covers:** `Operation.execute()` implementations, `HeadlessMoveProcessor`, infrastructure classes (`ProjectSetup`, `VfsHelper`, `IndexingHelper`), `ReforgeStarter.run()`/`openProject()`/`closeProject()`, stale import fixing, missing import fixing for same-package splits, visibility fixing, wildcard/specific deduplication.

**What to verify after a run:**
- Reforge output: expected move/skip/fail counts, no errors
- `mvn clean verify` in the test project: all 56 tests pass, no compilation errors
- Structure: classes end up in the correct target packages

**Maintaining E2E coverage:** When a bug is fixed, add classes to the test project that exercise the same failure mode. Translate the bug scenario into the task-manager domain — do not use class or package names from the original bug report. The goal is regression coverage: if the fix breaks, the E2E refactoring should produce compile errors or test failures.

**Regression scenarios currently covered:**
- Import preservation with heavy `java.util.*` usage (ReportService)
- MapStruct `@Mapper` annotation handling (TaskMapper, ProjectMapper)
- Cross-domain dependencies (ReportService referencing multiple domains)
- Wildcard/specific conflict deduplication (TaskAssignmentService claimed by specific rule, then matched again by `service.*` catch-all)
- Exact FQN resolution with sub-package wildcard catch-all (TaskDeadlineScheduler, ProjectMilestoneTracker claimed by exact FQN, then matched again by `service.scheduling.*` catch-all — package assertion test catches wrong placement)
- Same-package split imports (Task↔Project cross-references, GlobalExceptionHandler↔exception classes — all were same-package, now different packages)

### Test Dependencies

- JUnit Jupiter 5.11.4
- JUnit 4.13.2 + Vintage Engine (required by IntelliJ Platform test runner)
- Kover 0.9.1

## Dependencies

- IntelliJ Platform SDK (IC 2024.3, build 243)
- Java plugin (`com.intellij.java`)
- SnakeYAML 2.2
- Kotlin 2.3.10
- IntelliJ Platform Gradle Plugin 2.11.0

## Config Format

```yaml
operations:
  - type: move
    target: com.example.app.task.model
    sources:
      - com.example.app.model.Task*

  - type: extract-interface
    class: com.example.app.task.service.TaskService
    interface: com.example.app.task.port.TaskPort
    methods: [findAll, findById, createTask]

  - type: replace-dependency
    in: com.example.app.task.controller.TaskController
    replace: com.example.app.task.service.TaskService
    with: com.example.app.task.port.TaskPort
```

Operations execute in listed order. Consecutive same-type operations are batched.

Wildcards (move only): `*` matches within a segment, `**` matches zero or more package segments. Test classes matched by wildcards are automatically placed in the corresponding test source root.

## Test Project

A Spring Boot test project exists at `~/development/test-project/` for end-to-end testing. It is a task manager backend with 28 Java source classes and 8 test classes across 9 packages with 56 JUnit 5 tests.

### Running an end-to-end test

```bash
# 1. Sync canonical copy to working copy (if test-project files changed)
cp -r src/test/resources/test-project/src/ ~/development/test-project/src/
cp src/test/resources/test-project/refactor.yaml ~/development/test-project/refactor.yaml

# 2. Reset the working copy to a clean state
cd ~/development/test-project
git checkout -- src/ && git clean -fd src/
git checkout refactor.yaml

# 3. Build and run Reforge
cd <reforge-project-dir>
gradle buildPlugin && gradle runIde --args="reforge ~/development/test-project ~/development/test-project/refactor.yaml"

# 4. Verify output:
#    - 35 moved (28 source + 8 test - 1 TaskManagerApplication), 0 failed, 0 skipped
#    - 7 "already claimed" dedup messages (scheduling + service catch-alls)
#    - 8 missing imports added for same-package splits
#    - 3 packages removed (model, exception, config)

# 5. Run tests on the refactored project
cd ~/development/test-project
mvn clean verify
# Expect: Tests run: 56, Failures: 0, Errors: 0
```

### Test project structure (original)

```
src/main/java/com.example.taskmanager
  ├── config/          DataInitializer
  ├── controller/      TaskController, ProjectController
  ├── dto/             CreateTaskRequest, TaskResponse, UpdateTaskRequest,
  │                    CreateProjectRequest, ProjectResponse
  ├── exception/       TaskNotFoundException, DependencyNotMetException,
  │                    ProjectNotFoundException, GlobalExceptionHandler
  ├── mapper/          TaskMapper, ProjectMapper
  ├── model/           Task, TaskStatus, TaskPriority, Project
  ├── repository/      TaskRepository, ProjectRepository
  ├── service/         TaskService, ProjectService, ReportService,
  │                    TaskAssignmentService
  └── service/
      └── scheduling/  TaskDeadlineScheduler, ProjectMilestoneTracker,
                       SchedulingUtils

src/test/java/com.example.taskmanager
  ├── controller/      TaskControllerTest, ProjectControllerTest
  ├── mapper/          TaskMapperTest
  ├── repository/      TaskRepositoryTest
  ├── service/         TaskServiceTest, ProjectServiceTest, ReportServiceTest
  └── service/
      └── scheduling/  TaskDeadlineSchedulerTest
```

### Test project structure (after refactoring)

```
src/main/java/com.example.taskmanager
  ├── task/
  │   ├── model/       Task, TaskStatus, TaskPriority
  │   ├── repository/  TaskRepository
  │   ├── service/     TaskService, TaskAssignmentService
  │   ├── controller/  TaskController
  │   ├── dto/         CreateTaskRequest, TaskResponse, UpdateTaskRequest
  │   ├── exception/   TaskNotFoundException, DependencyNotMetException
  │   ├── mapper/      TaskMapper
  │   └── scheduling/  TaskDeadlineScheduler
  ├── project/
  │   ├── model/       Project
  │   ├── repository/  ProjectRepository
  │   ├── service/     ProjectService
  │   ├── controller/  ProjectController
  │   ├── dto/         CreateProjectRequest, ProjectResponse
  │   ├── exception/   ProjectNotFoundException
  │   ├── mapper/      ProjectMapper
  │   └── scheduling/  ProjectMilestoneTracker
  └── common/
      ├── exception/   GlobalExceptionHandler
      ├── config/      DataInitializer
      ├── service/     ReportService
      └── scheduling/  SchedulingUtils

src/test/java/com.example.taskmanager
  ├── task/
  │   ├── repository/  TaskRepositoryTest
  │   ├── service/     TaskServiceTest
  │   ├── controller/  TaskControllerTest
  │   └── mapper/      TaskMapperTest
  ├── project/
  │   ├── service/     ProjectServiceTest
  │   └── controller/  ProjectControllerTest
  └── common/
      ├── service/     ReportServiceTest
      └── scheduling/  TaskDeadlineSchedulerTest
```

## Claude Code Plugin

Reforge is distributed as a Claude Code plugin. The plugin manifest is at `.claude-plugin/plugin.json` and the skill is at `skills/reforge/SKILL.md`.

### Installation (for users)

Install the IntelliJ plugin:

```bash
idea installPlugins ch.riesennet.reforge https://raw.githubusercontent.com/notiriel/reforge/main/updatePlugins.xml
```

Install the Claude Code plugin:

```
/plugin marketplace add https://github.com/notiriel/reforge.git
/plugin install reforge@notiriel-reforge
```

Then use in any project:

```
/reforge restructure the project into hexagonal architecture
```

The skill analyzes the codebase, generates YAML config, executes Reforge, and validates with tests.

### Releasing

Three files carry the version and must be updated together:

1. `build.gradle.kts` — `version = "X.Y.Z"` (drives the built zip filename and patched `plugin.xml`)
2. `updatePlugins.xml` — `version="X.Y.Z"` (IntelliJ custom plugin repository descriptor)

Release steps:

```bash
# 1. Bump version in build.gradle.kts and updatePlugins.xml
# 2. Clean build
./gradlew clean buildPlugin
# 3. Commit, push
git add build.gradle.kts updatePlugins.xml <other changed files>
git commit -m "Bump version to X.Y.Z"
git push origin main
# 4. Create GitHub release with the plugin zip (asset must be named reforge.zip)
gh release create vX.Y.Z build/distributions/reforge-X.Y.Z.zip#reforge.zip \
  --title "vX.Y.Z" --notes "Release notes here"
```

The `updatePlugins.xml` points to `releases/latest/download/reforge.zip`, so naming the asset `reforge.zip` (via `#reforge.zip` suffix) is required for existing installations to auto-discover updates.
