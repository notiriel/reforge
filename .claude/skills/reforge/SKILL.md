---
name: reforge
description: Orchestrate large-scale Java/Kotlin refactorings using IntelliJ's refactoring engine. Analyzes codebase structure, generates refactoring config, and executes via headless IntelliJ plugin.
allowed-tools: Read, Grep, Glob, Bash, Write
argument-hint: "[describe the refactoring goal]"
---

# Reforge — LLM-Directed Refactoring Engine

You are orchestrating a large-scale architectural refactoring using the **Reforge** IntelliJ plugin. Reforge executes refactoring operations headlessly via CLI + YAML config, using IntelliJ's refactoring engine for full reference/import updating.

## Prerequisites

Check if the Reforge plugin project is available:

```bash
ls ~/development/intellij-batch-mover/build.gradle.kts
```

If the plugin needs building:

```bash
export SDKMAN_DIR="$HOME/.sdkman" && source "$SDKMAN_DIR/bin/sdkman-init.sh"
cd ~/development/intellij-batch-mover && gradle buildPlugin
```

## Workflow

### 1. Analyze the target project

Before generating config, understand the codebase:

- **Package structure**: `find <project>/src -type d | head -50`
- **Class inventory**: Use Glob to find `**/*.java` or `**/*.kt` files
- **Dependencies between classes**: Use Grep to find import patterns
- **Test structure**: Check `src/test/java` layout

### 2. Design the refactoring plan

Based on the user's goal, determine which operations to use:

| Goal | Operations |
|------|-----------|
| Package reorganization | `move` operations |
| Hexagonal architecture | `move` + `extract-interface` + `replace-dependency` |
| Domain-driven structure | `move` to domain packages |
| Dependency inversion | `extract-interface` + `replace-dependency` |

### 3. Generate `reforge.yaml`

Write a YAML config file in the target project root:

```yaml
operations:
  # Move operations — group related classes together
  - type: move
    target: com.example.app.task.model
    sources:
      - com.example.app.model.Task*          # Wildcards match multiple classes

  - type: move
    target: com.example.app.task.service
    sources:
      - com.example.app.service.TaskService*

  # Extract interface — create ports/interfaces from implementations
  - type: extract-interface
    class: com.example.app.task.service.TaskService
    interface: com.example.app.task.port.TaskPort
    methods: [findAll, findById, createTask]

  # Replace dependency — swap concrete types with interfaces
  - type: replace-dependency
    in: com.example.app.task.controller.TaskController
    replace: com.example.app.task.service.TaskService
    with: com.example.app.task.port.TaskPort
```

**Important ordering rules:**
- Move operations should come FIRST (so classes are in their target packages before extracting interfaces)
- Extract-interface should come BEFORE replace-dependency (interface must exist before it can be used)
- Consecutive same-type operations are batched automatically for efficiency

**Wildcard patterns:**
- `*` matches within a single segment (e.g., `Task*` matches `Task`, `TaskStatus`, `TaskService`)
- `**` matches zero or more package segments (e.g., `com.example.**.*Entity`)
- Test classes matching wildcards automatically go to the test source root

### 4. Execute Reforge

```bash
export SDKMAN_DIR="$HOME/.sdkman" && source "$SDKMAN_DIR/bin/sdkman-init.sh"

# Dry run first to preview
gradle -p ~/development/intellij-batch-mover runIde \
  --args="reforge <project-path> <project-path>/reforge.yaml --dry-run"

# Real run
gradle -p ~/development/intellij-batch-mover runIde \
  --args="reforge <project-path> <project-path>/reforge.yaml"
```

### 5. Validate

After execution, run the project's test suite:

```bash
cd <project-path>
# For Maven projects
mvn clean verify

# For Gradle projects
./gradlew test
```

### 6. Report results

Tell the user:
- How many classes were moved/extracted/replaced
- Whether tests pass
- Any failures and their causes

## Common Refactoring Patterns

### Package reorganization (flat → domain-grouped)

```yaml
operations:
  - type: move
    target: com.example.app.order.model
    sources:
      - com.example.app.model.Order*
  - type: move
    target: com.example.app.order.repository
    sources:
      - com.example.app.repository.Order*
  - type: move
    target: com.example.app.order.service
    sources:
      - com.example.app.service.OrderService*
```

### Hexagonal architecture (add ports)

```yaml
operations:
  # First move to domain structure
  - type: move
    target: com.example.app.order.domain
    sources:
      - com.example.app.model.Order*
  - type: move
    target: com.example.app.order.application
    sources:
      - com.example.app.service.OrderService*

  # Then extract ports
  - type: extract-interface
    class: com.example.app.order.application.OrderService
    interface: com.example.app.order.port.in.OrderUseCase
    methods: [createOrder, findOrder, listOrders]

  # Wire controllers to ports
  - type: replace-dependency
    in: com.example.app.order.adapter.in.OrderController
    replace: com.example.app.order.application.OrderService
    with: com.example.app.order.port.in.OrderUseCase
```

## Troubleshooting

- **"No classes matched"**: Check pattern spelling, ensure the project compiles, try without wildcards first
- **IndexNotReadyException**: Usually auto-retried (3 attempts). If persistent, the project may have compilation errors
- **Tests fail after refactoring**: Check for string-based class references (reflection, Spring config) that don't get updated by IntelliJ's refactoring engine
