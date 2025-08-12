# Polyglot Scheduling System (Java + Haskell + Prolog)

A modular, extensible scheduling engine with:
- Java (Spring Boot) REST API for JSON input/output and orchestration
- Haskell executable for schedule generation (GA-ready; currently deterministic baseline)
- Prolog (SWI-Prolog) constraints for hard validation (capacity, overlaps, dependencies)

This repo is designed to be easy to run locally while keeping the architecture production-friendly.

## Architecture Overview

- Java
  - `Application` – Spring Boot entry point
  - `SchedulerController` – HTTP endpoints
    - POST `/api/scheduler/generate` – generate a schedule
    - GET `/api/scheduler/health` – health and environment diagnostics
  - `SchedulerService` – orchestrates Haskell and Prolog calls
    - Serializes request to JSON (minimal builder)
    - Invokes Haskell scheduler via a configurable command
    - Optionally validates with Prolog via JPL (if available)
    - Returns combined result (Haskell output, validation flag, diagnostics)
  - Models: `TaskInput`, `ResourceInput`

- Haskell
  - `haskell/GeneticSchedule.hs`
    - Reads JSON from stdin `{ tasks, resources }`
    - Produces a feasible schedule honoring capacity/dependencies
    - Outputs `{ bestSchedule: [...], fitness: number }`
    - Stub fitness function (shorter schedule + priority)

- Prolog
  - `prolog/constraints.pl`
    - Facts: `task/4`, `resource/2`, `depends/2`
    - `valid_schedule/0` succeeds only if: no overbooking, dependency order respected, no overlaps when capacity=1, slot/duration sanity
  - `prolog/test_constraints.pl` – smoke tests

## Robustness and "Senior" Features

- Input validation (Java):
  - Non-null lists, positive task durations, valid resource IDs, positive capacities
- Haskell process hardening (Java):
  - Configurable command and script path via env vars
  - Process timeout with forceful termination
  - Helpful error messages if command/script is missing
- Diagnostics endpoint `/api/scheduler/health`:
  - Reports presence of JPL, runhaskell, swipl, and script path
- Graceful degradation:
  - If JPL/SWI-Prolog not present, the API still returns the Haskell schedule with `valid=false` and an explanatory field
- Minimal dependencies:
  - No build tool is required to run the skeleton and tests locally (you can still add Gradle/Maven later)

## Quick Start

Prerequisites (best-effort):
- Java 11+ (for running the Spring app)
- Haskell toolchain (GHC) for `runhaskell`
- SWI-Prolog (optional, for validation via JPL or running Prolog tests)

Run Haskell scheduler by itself:
```
runhaskell haskell/GeneticSchedule.hs < sample-request.json
```

Run Prolog tests:
```
swipl -q -s prolog/test_constraints.pl
```

Run both quick tests:
```
bash tests/run_all.sh
```

Run Java unit-style tests (no Maven/Gradle):
```
javac -cp src/main/java src/test/java/com/example/scheduler/service/SchedulerServiceUnitTest.java
java -cp src/main/java:src/test/java com.example.scheduler.service.SchedulerServiceUnitTest
```

Run the Spring Boot API (you may need to add your own build tool later):
- If using an IDE (IntelliJ), run `com.example.scheduler.Application` main class.
- Then POST the sample request:
```
curl -X POST http://localhost:8080/api/scheduler/generate \
  -H 'Content-Type: application/json' \
  --data-binary @sample-request.json
```
Health/diagnostics:
```
curl http://localhost:8080/api/scheduler/health
```

## Configuration

Environment variables (read by `SchedulerService`):
- `SCHEDULER_HS_CMD` – default `runhaskell`
- `SCHEDULER_HS_SCRIPT` – default `haskell/GeneticSchedule.hs`
- `SCHEDULER_HS_TIMEOUT_SEC` – default `30`

## Data Model

TaskInput
- `taskId: Int`
- `duration: Int (>0)`
- `priority: Int`
- `requiredResource: String`
- `dependsOn: [Int]`

ResourceInput
- `resourceId: String`
- `capacityPerSlot: Int (>0)`

Haskell output
- `bestSchedule: [{ taskId, timeSlot, resourceId }]`
- `fitness: number`

## Development and Testing

- Replace the deterministic scheduler with a real GA:
  - Chromosome: sequence of assignments
  - Multi-objective fitness: minimize makespan, penalties for capacity violations, reward priorities, + Prolog penalty
  - Use JSON stdin/stdout contract unchanged
- Swap naive JSON handling in Java for Jackson (add a build tool)
- Add proper Gradle/Maven build with:
  - Spring Boot Starter Web
  - Jackson
  - JUnit tests
  - JPL dependency for runtime validation
- Add containerization (Docker) and CI (GitHub Actions) to run Haskell/Prolog/Java tests in matrix jobs

## Roadmap and Ideas (Senior-level)

- Genetic Algorithm
  - NSGA-II or multi-objective GA with elitism
  - Adaptive mutation based on schedule diversity and constraint slack
  - Seeding with topological sort to ensure dependency feasibility from generation 0
- Constraint Integration
  - Make Prolog validation pluggable; add cost/penalty function available to GA
  - Optionally embed a CP-SAT solver (e.g., OR-Tools) for hybrid approaches
- Observability
  - Structured logging (JSON) and correlation IDs across Haskell/Java calls
  - Metrics endpoint (Micrometer/Prometheus) for request counts, latencies, failures
- Resilience
  - Circuit breaker around Haskell and Prolog calls
  - Retry with backoff for transient failures
- Performance
  - Compile Haskell to a native binary and call it directly to reduce startup time
  - Stream-oriented JSON to support large inputs
- Security & Governance
  - Input size limits and validation (max tasks/resources)
  - Threat model for external command execution; sandbox Haskell process
- UX and Extensibility
  - Schema definitions (OpenAPI) for inputs/outputs
  - Versioned API and backward compatibility guarantees

## Repository Layout
```
├── haskell/GeneticSchedule.hs
├── prolog/
│   ├── constraints.pl
│   └── test_constraints.pl
├── sample-request.json
├── src/main/java/com/example/scheduler/
│   ├── Application.java
│   ├── model/
│   │   ├── ResourceInput.java
│   │   └── TaskInput.java
│   ├── service/
│   │   └── SchedulerService.java
│   └── web/
│       └── SchedulerController.java
├── src/test/java/com/example/scheduler/service/SchedulerServiceUnitTest.java
└── tests/
    ├── README.md
    ├── run_all.sh
    └── test_haskell.sh
```

## License

Apache-2.0 or MIT – choose the one that suits your org (not included by default).
