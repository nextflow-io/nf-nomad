# Testing Guide — nf-nomad

## Overview

Tests are organised into three categories, controlled by the `NF_NOMAD_TEST_ENV`
environment variable (or the Gradle property `-PtestEnv`):

| Category        | `testEnv` value | What runs                                      |
|-----------------|----------------|-------------------------------------------------|
| **Unit**        | _(empty)_      | Pure unit tests — always run, no network needed |
| **Mock**        | `mock`         | Unit tests **+** MockWebServer-based specs      |
| **Integration** | `local`        | Unit tests **+** live tests against local Nomad |
| **Integration** | `oci`          | Unit tests **+** live tests against OCI cluster |

## Local Integration Tests (Implemented)

Extensive integration tests for local Docker-based Minio and Nomad setup (triggered via `make test-local`):

**Test Coverage:** 33 comprehensive tests across three specifications
- **Docker/Container Tests:** 13 tests for job lifecycle, environment vars, resource constraints
- **Minio/Storage Tests:** 10 tests for file I/O, persistence, data passing
- **Nomad Scheduling Tests:** 10 tests for resource allocation, metadata, state transitions

**Estimated Duration:** 15-20 minutes

### Local Integration Test Specs

#### LocalDockerIntegrationSpec (13 tests)
Tests Docker container execution and job lifecycle management:
- Container execution with various images (Ubuntu, Alpine, Python)
- Job state transitions (pending → running → complete)
- Environment variable handling
- Resource constraints (memory and CPU)
- Job operations (kill, purge, concurrent submissions)
- Client node allocation retrieval

#### LocalMinioIntegrationSpec (10 tests)
Tests file I/O operations and storage backend integration:
- Volume mount configuration and input/output file handling
- Chained job execution (producer-consumer pattern)
- Large file handling (10MB test files)
- Parallel file operations
- File compression and decompression
- Data persistence across task boundaries
- Disk space monitoring
- Job allocation and cleanup verification

#### LocalNomadSchedulingIntegrationSpec (10 tests)
Tests advanced Nomad scheduling features and resource allocation:
- Minimal and moderate resource requirements
- Batch job submission
- Job priority levels and metadata handling
- Job state transitions and restart scenarios
- Datacenter-specific configuration
- Resource allocation tracking

## Test Spec Files

### Unit tests (always run, no annotation gate)

These test internal logic without any Nomad cluster or mock server:

- `config/NomadConfigSpec.groovy` — NomadConfig parsing and defaults
- `config/NomadJobOptsSpec.groovy` — Job options (volumes, affinities, constraints, spreads)
- `config/NomadJobConstraintsSpec.groovy` — Constraint DSL parsing
- `builders/JobBuilderSpec.groovy` — Job/TaskGroup/Task builder logic
- `executor/NomadTaskHandlerSpec.groovy` — Task handler submit logic

### Mock tests (`@Requires NF_NOMAD_TEST_ENV == 'mock'`)

These use OkHttp `MockWebServer` to validate HTTP requests without a real cluster:

- `executor/MockNomadServiceSpec.groovy` — Service request/response validation
- `executor/MockNomadSecretServiceSpec.groovy` — Secrets (variables) API requests
- `models/MockJobConstraintsSpec.groovy` — Constraint serialisation in job payloads
- `MockNomadDSLSpec.groovy` — End-to-end DSL / plugin integration with mock server

### Integration tests (`@Requires NF_NOMAD_TEST_ENV in ['oci', 'local']`)

These run against a **live** Nomad cluster:

- `executor/NomadServiceIntegrationSpec.groovy` — Connectivity, job submit, state polling, purge

Some methods inside this spec have additional per-cluster gates:

- `@Requires NF_NOMAD_TEST_ENV == 'oci'` — OCI-specific checks (e.g. token auth)
- `@Requires NF_NOMAD_TEST_ENV == 'local'` — Local-specific checks (e.g. localhost address)

## How It Works

### Environment variable: `NF_NOMAD_TEST_ENV`

Spock's `@Requires` annotation reads this variable at class/method load time to
decide which specs to run. The value is set in one of two ways:

1. **Gradle property** (preferred): `./gradlew test -PtestEnv=mock`
2. **Shell env var**: `NF_NOMAD_TEST_ENV=mock ./gradlew test`

The Gradle property takes precedence. See `build.gradle` for the resolution logic.

### Gradle property overrides

You can override Nomad-related test environment values directly when invoking Gradle:

- `-PnomadAddr=...` → sets `NOMAD_ADDR`
- `-PnomadToken=...` → sets `NOMAD_TOKEN`
- `-PnomadDc=...` → sets `NOMAD_DC`
- `-PnomadRegion=...` → sets `NOMAD_REGION`
- `-PnomadNamespace=...` → sets `NOMAD_NAMESPACE`
- `-PnxfDebug=...` → sets `NXF_DEBUG`

These overrides work with `local`, `oci`, `mock`, and default unit test runs.

### Cluster connection variables

When `testEnv` is `oci` or `local`, the following env vars are forwarded from
your shell into the test JVM:

- `NOMAD_ADDR` — Nomad HTTP API address (defaults to `http://localhost:4646` for `local`)
- `NOMAD_TOKEN` — ACL token (required for `oci`, not needed for `local` dev agent)
- `NOMAD_DC` — Datacenter name (optional)

When `testEnv` is empty or `mock`, safe dummy values are injected instead
(`NOMAD_ADDR=http://test-nf-nomad`, `NOMAD_DC=dc-test`).

## Quick Start

### Run unit tests only (default)

```shell
make test
# or: ./gradlew test
```

### Run unit + mock tests

```shell
make test-mock
# or: ./gradlew test -PtestEnv=mock
```

### Run integration tests against local Nomad

Ensure the local cluster is running
(see `../../infrastructure/03_automation/035_terraform/local-nomad-minio/`):

```shell
# Fish
set -gx NOMAD_ADDR http://localhost:4646

make test-local
# or: ./gradlew test -PtestEnv=local
```

To target a non-default local endpoint, override it explicitly:

```shell
./gradlew test -PtestEnv=local -PnomadAddr=http://<host>:4646
```

You can combine other overrides in the same command, for example:

```shell
./gradlew test -PtestEnv=local -PnomadAddr=http://localhost:4646 -PnomadDc=dc1 -PnxfDebug=1
```

### Run integration tests against OCI Nomad

Source the generated env from the OCI terraform module first:

```shell
# Fish
source ../../infrastructure/03_automation/035_terraform/oci-vm-nomad/generated/env.fish

# Bash/Zsh
source ../../infrastructure/03_automation/035_terraform/oci-vm-nomad/generated/env.sh

make test-oci
# or: ./gradlew test -PtestEnv=oci
```

Example with explicit overrides:

```shell
./gradlew test -PtestEnv=oci -PnomadAddr=http://<oci-host>:4646 -PnomadToken=<token> -PnomadNamespace=nf-nomad
```

### Run a single spec

```shell
make test class=nextflow.nomad.executor.NomadServiceIntegrationSpec
# with testEnv:
./gradlew test -PtestEnv=local --tests nextflow.nomad.executor.NomadServiceIntegrationSpec
```

### Run real rnaseq pipeline validation (manual)

Use this to exercise the plugin with a pinned external pipeline (`nextflow-io/rnaseq-nf`):

```shell
cd validation
./run-rnaseq-nf.sh
```

The script stores run artifacts (`trace`, `report`, `timeline`, `nextflow.log`) under
`validation/nomad_temp/scratchdir/real-pipelines/rnaseq-nf/` and fails if key process
or output assertions are not satisfied.

## Target Clusters

### local-nomad-minio

- **Location**: `../../infrastructure/03_automation/035_terraform/local-nomad-minio/`
- **Nomad address**: `http://localhost:4646`
- **Auth**: None (dev agent)
- Nomad runs natively on macOS; MinIO runs in Docker

### oci-vm-nomad

- **Location**: `../../infrastructure/03_automation/035_terraform/oci-vm-nomad/`
- **Nomad address**: Public IP of OCI server on port 4646
- **Auth**: ACL bootstrap token (set via `NOMAD_TOKEN`)
- Remote cluster on Oracle Cloud with MinIO on the server node

## Adding New Tests

- **Unit test**: No annotation needed — just extend `Specification`.
- **Mock test**: Add `@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'mock' })` at class level.
- **Integration test**: Add `@Requires({ System.getenv('NF_NOMAD_TEST_ENV') in ['oci', 'local'] })`
  at class level. Use per-method `@Requires` for cluster-specific assertions.
