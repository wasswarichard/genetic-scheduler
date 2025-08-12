Tests overview and how to run

Prerequisites
- Prolog: SWI-Prolog (swipl) on PATH, for Prolog tests.
- Haskell: GHC/RunHaskell (runhaskell) on PATH, for Haskell tests.
- Java: JDK for compiling and running the simple main-based unit test.

Quick run (Prolog + Haskell)
- bash tests/run_all.sh

Prolog tests
- swipl -q -s prolog/test_constraints.pl
  This consults prolog/constraints.pl, asserts test facts, checks:
  - A valid schedule passes
  - Overbooking is rejected
  - Dependency violation is rejected
  Exits with status code 0 on success.

Haskell tests
- bash tests/test_haskell.sh
  Pipes sample-request.json to haskell/GeneticSchedule.hs and performs simple checks
  for presence of bestSchedule and fitness fields.

Java unit (no build tool)
- Compile:
  javac -cp src/main/java src/test/java/com/example/scheduler/service/SchedulerServiceUnitTest.java
- Run:
  java -cp src/main/java:src/test/java com.example.scheduler.service.SchedulerServiceUnitTest
  This test uses reflection to invoke private helpers in SchedulerService to validate
  JSON serialization and assignment extraction using mocked data.

Notes
- The Java service’s schedule() method calls external tools; to keep tests lightweight,
  the unit test avoids executing external processes.
- These tests are smoke-level checks suitable for this skeleton; they can be replaced
  later with proper Gradle/Maven, HUnit/QuickCheck, and PlUnit suites.