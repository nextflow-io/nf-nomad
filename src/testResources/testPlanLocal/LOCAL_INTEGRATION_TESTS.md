# Local Docker-Based Minio and Nomad Integration Tests

## Overview

This document describes the comprehensive integration test suite for testing Nextflow-Nomad plugin with a local Docker-based Minio and Nomad setup (triggered via `make test-local`).

## Test Files

Three new integration test specifications have been implemented:

### 1. **LocalDockerIntegrationSpec.groovy**
Tests basic Docker container execution and job lifecycle management.

**Tests Included:**
- ✅ Ubuntu container with simple echo command
- ✅ Alpine container with file listing
- ✅ Job submission with environment variables
- ✅ Python script execution in container
- ✅ Job failure handling and state detection
- ✅ Multi-command execution in sequence
- ✅ Resource constraints (memory and CPU)
- ✅ Client node allocation retrieval
- ✅ Job kill (stop without purge)
- ✅ Job purge (complete removal)
- ✅ Concurrent job submissions (stress test)
- ✅ Docker image pulling verification
- ✅ Localhost targeting verification

**Key Features:**
- Tests container lifecycle (submit, running, complete, failed states)
- Validates state transitions
- Verifies resource allocation tracking
- Tests job cleanup operations

### 2. **LocalMinioIntegrationSpec.groovy**
Tests file I/O operations, storage backend integration, and data persistence.

**Tests Included:**
- ✅ Volume mount configuration
- ✅ Input file handling
- ✅ Output file generation
- ✅ Chained job execution (producer-consumer pattern)
- ✅ Large file handling (10MB test files)
- ✅ Parallel file operations
- ✅ Disk space monitoring
- ✅ File compression and decompression
- ✅ Sequential task execution with data passing
- ✅ Job allocation and cleanup verification

**Key Features:**
- Tests file mount and access patterns
- Validates data flow between jobs
- Tests large file handling
- Verifies disk usage reporting
- Tests sequential job dependencies

### 3. **LocalNomadSchedulingIntegrationSpec.groovy**
Tests advanced Nomad scheduling features including constraints, resource allocation, and job metadata.

**Tests Included:**
- ✅ Basic job submission and state polling
- ✅ Minimal resource requirements (128MB, 0.25 CPU)
- ✅ Moderate resource requirements (512MB, 1 CPU)
- ✅ Job timeout and deadline handling
- ✅ Batch submission of related jobs
- ✅ Jobs with different priorities (high, normal, low)
- ✅ Job metadata preservation and retrieval
- ✅ Datacenter configuration respect
- ✅ Job restart and recovery scenarios
- ✅ State transition tracking
- ✅ Job completion and cleanup verification

**Key Features:**
- Tests resource request variations
- Validates job metadata handling
- Tests batch submission patterns
- Verifies state transition sequences
- Tests scheduler behavior

## Running the Tests

### Prerequisites

Ensure your local Nomad and Minio setup is running:
```bash
# From the local-nomad-minio terraform directory
cd ../../infrastructure/03_automation/035_terraform/local-nomad-minio/
docker-compose up -d
```

### Run All Local Tests
```bash
make test-local
# or
./gradlew test -PtestEnv=local
```

### Run Specific Test Spec
```bash
# Docker tests only
./gradlew test -PtestEnv=local --tests nextflow.nomad.executor.LocalDockerIntegrationSpec

# Minio tests only
./gradlew test -PtestEnv=local --tests nextflow.nomad.executor.LocalMinioIntegrationSpec

# Scheduling tests only
./gradlew test -PtestEnv=local --tests nextflow.nomad.executor.LocalNomadSchedulingIntegrationSpec
```

### Run with Custom Nomad Address
```bash
./gradlew test -PtestEnv=local -PnomadAddr=http://<custom-host>:4646
```

### Run with Debug Logging
```bash
./gradlew test -PtestEnv=local -PnxfDebug=1
```

## Test Characteristics

### Timing and Timeouts
- **Overall spec timeout:** 180 seconds per spec
- **Individual test timeout:** Inherits from spec timeout
- **Job state polling:** 1-second sleep between polls
- **Max retry attempts:** 30-50 depending on test
- **Expected execution time:** 5-10 minutes for complete suite

### Environment Variables
Each test submission includes metadata variables:
- `NF_TEST`: Test category identifier
- `NF_MINIO_TEST`: Minio-specific test marker
- `TEST_CATEGORY`: Test classification
- `BATCH_ID`: Batch submission identifier
- `SEQ_ID`: Sequential test identifier

### Job Names and Identifiers
All test jobs follow a naming pattern for easy identification:
```
<test-name>-<timestamp>
```

Examples:
- `ubuntu-echo-1710158934000`
- `file-compress-1710158945123`
- `batch-1710158956000-job-1`

## Test Coverage Matrix

| Feature | Docker | Minio | Scheduling |
|---------|--------|-------|-----------|
| Basic submission | ✅ | ✅ | ✅ |
| State polling | ✅ | ✅ | ✅ |
| Container execution | ✅ | - | - |
| File I/O | - | ✅ | - |
| Data chaining | - | ✅ | - |
| Resource constraints | ✅ | - | ✅ |
| Metadata handling | - | - | ✅ |
| Batch operations | ✅ | ✅ | ✅ |
| Job cleanup | ✅ | ✅ | ✅ |

## Troubleshooting

### Job Timeouts
If tests timeout waiting for job completion:
1. Verify Nomad is responsive: `curl http://localhost:4646/ui/`
2. Check Docker container resources
3. Increase retry counts in test specs
4. Check Nomad logs: `nomad status` and `nomad alloc logs <alloc-id>`

### Connection Refused
If you get "Connection refused" errors:
```bash
# Verify Nomad is running on expected port
lsof -i :4646

# Check NOMAD_ADDR environment variable
echo $NOMAD_ADDR

# Override with custom address
./gradlew test -PtestEnv=local -PnomadAddr=http://localhost:4646
```

### Job Submission Failures
If jobs fail to submit:
1. Check Nomad datacenter configuration
2. Verify Docker images are available or can be pulled
3. Check Nomad ACL configuration (if enabled)
4. Review Nomad API error responses in logs

### File Operations Issues
For Minio-related failures:
1. Verify Minio is accessible
2. Check volume mount paths are correct
3. Ensure proper permissions on mounted directories
4. Check disk space availability

## Test Maintenance

### Adding New Tests
1. Create new test method in appropriate spec
2. Follow naming convention: `void "should <action>"`
3. Use `@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })`
4. Add cleanup in `cleanupSpec()` or test cleanup block
5. Document the test in this README

### Updating Test Parameters
Common parameters to adjust:
- `maxRetries`: Increase if jobs take longer to complete
- `sleep()` duration: Adjust polling interval
- Container images: Update if versions change
- Resource requests: Modify for different cluster sizes

### Expected Failure Modes
Tests gracefully handle:
- Jobs already purged before cleanup
- Connection timeouts (with retries)
- Missing Nomad allocations (returns null)
- Docker image pull delays

## Performance Expectations

### Typical Test Execution Times
- **LocalDockerIntegrationSpec**: 4-6 minutes
- **LocalMinioIntegrationSpec**: 5-8 minutes
- **LocalNomadSchedulingIntegrationSpec**: 5-7 minutes
- **Total suite**: 15-20 minutes

### Resource Requirements
- **CPU**: 2+ cores
- **Memory**: 4GB+ RAM
- **Disk**: 5GB free space (for test jobs)
- **Network**: Local access to Nomad/Minio

## CI/CD Integration

### GitLab CI Example
```yaml
test:local:
  script:
    - make test-local
  variables:
    NOMAD_ADDR: "http://localhost:4646"
  timeout: 30m
  tags:
    - docker
```

### GitHub Actions Example
```yaml
- name: Run local integration tests
  run: make test-local
  env:
    NOMAD_ADDR: http://localhost:4646
  timeout-minutes: 30
```

## Related Documentation

- [TESTING.md](../../../TESTING.md) - General testing guide
- [NomadService.groovy](../../main/groovy/nextflow/nomad/executor/NomadService.groovy) - Service implementation
- [NomadConfig.groovy](../../main/groovy/nextflow/nomad/config/NomadConfig.groovy) - Configuration reference
- [local-nomad-minio setup](../../infrastructure/03_automation/035_terraform/local-nomad-minio/) - Local cluster setup

## Contributing

When contributing new tests:
1. Ensure all tests are marked with `@Requires` annotation
2. Follow Spock specification conventions
3. Clean up resources in cleanup blocks
4. Document test purpose and expectations
5. Keep tests focused on single features
6. Use meaningful test names describing what is tested

## License

These tests are part of the nf-nomad plugin and are licensed under the Apache License 2.0.
See [COPYING](../../../COPYING) for details.

