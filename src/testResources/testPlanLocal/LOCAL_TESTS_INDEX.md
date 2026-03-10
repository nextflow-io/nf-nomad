# nf-nomad Integration Tests - Index

## 📚 Documentation Map

### For New Users
Start here if you're new to running the local integration tests:
- **[QUICK_START_LOCAL_TESTS.md](../../../QUICK_START_LOCAL_TESTS.md)** - One-liner commands and quick reference

### For Test Execution
Running and managing the tests:
- **[LOCAL_INTEGRATION_TESTS.md](LOCAL_INTEGRATION_TESTS.md)** - Comprehensive testing guide

### For Implementation Details
Understanding the technical architecture:
- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - Complete implementation overview

### For General Testing
Overall testing strategy and test types:
- **[TESTING.md](../../../TESTING.md)** - Complete testing guide (existing, covers all test types)

---

## 🎯 Test Files Structure

### Location
```
src/test/groovy/nextflow/nomad/executor/
```

### New Integration Test Specifications

#### 1. **LocalDockerIntegrationSpec.groovy** (745 lines, 13 tests)
Tests Docker container execution and job lifecycle management.

**Key Tests:**
- Container execution (Ubuntu, Alpine, Python)
- Job lifecycle (submit → running → complete → failed)
- Environment variables
- Resource constraints
- Concurrent execution
- Job operations (kill, purge)

**Run with:**
```bash
./gradlew test -PtestEnv=local --tests LocalDockerIntegrationSpec
```

---

#### 2. **LocalMinioIntegrationSpec.groovy** (746 lines, 10 tests)
Tests file I/O operations and storage backend integration.

**Key Tests:**
- Volume mount configuration
- Input/output file handling
- Chained job execution (producer-consumer)
- Large file operations
- File compression
- Parallel file operations

**Run with:**
```bash
./gradlew test -PtestEnv=local --tests LocalMinioIntegrationSpec
```

---

#### 3. **LocalNomadSchedulingIntegrationSpec.groovy** (706 lines, 10 tests)
Tests advanced Nomad scheduling features and resource allocation.

**Key Tests:**
- Job submission and state polling
- Resource allocation variations
- Batch job submission
- Priority handling
- Metadata preservation
- State transitions

**Run with:**
```bash
./gradlew test -PtestEnv=local --tests LocalNomadSchedulingIntegrationSpec
```

---

## 🚀 Quick Commands

### Run All Local Tests
```bash
make test-local
```

### Run Individual Test Suites
```bash
# Docker tests
./gradlew test -PtestEnv=local --tests LocalDockerIntegrationSpec

# Minio tests
./gradlew test -PtestEnv=local --tests LocalMinioIntegrationSpec

# Scheduling tests
./gradlew test -PtestEnv=local --tests LocalNomadSchedulingIntegrationSpec
```

### With Custom Configuration
```bash
# Custom Nomad address
./gradlew test -PtestEnv=local -PnomadAddr=http://<host>:4646

# With debug logging
./gradlew test -PtestEnv=local -PnxfDebug=1
```

---

## 📊 Test Coverage

| Category | Tests | Focus |
|----------|-------|-------|
| **Docker Integration** | 13 | Container execution, job lifecycle |
| **Minio Storage** | 10 | File I/O, data persistence |
| **Nomad Scheduling** | 10 | Resource allocation, metadata |
| **Total** | **33** | Comprehensive local cluster testing |

---

## ⏱️ Execution Time

- **LocalDockerIntegrationSpec:** 4-6 minutes
- **LocalMinioIntegrationSpec:** 5-8 minutes
- **LocalNomadSchedulingIntegrationSpec:** 5-7 minutes
- **Total Suite:** 15-20 minutes

---

## ✅ Prerequisites

- ✅ Local Nomad running on `http://localhost:4646`
- ✅ Minio container running and accessible
- ✅ Docker daemon running
- ✅ Gradle 6.0+ installed
- ✅ 4GB+ RAM and 5GB free disk space

---

## 🔍 Troubleshooting

### Connection Issues
```bash
# Verify Nomad is accessible
curl http://localhost:4646/ui/

# Override with custom address
./gradlew test -PtestEnv=local -PnomadAddr=http://<host>:4646
```

### Timeout Issues
1. Verify Nomad is responsive
2. Check Docker container resources
3. Review Nomad logs: `nomad status`
4. Increase retry counts if needed

### File Access Issues
1. Verify Minio is running
2. Check volume mount paths
3. Ensure proper permissions
4. Check disk space availability

See [LOCAL_INTEGRATION_TESTS.md](LOCAL_INTEGRATION_TESTS.md) for detailed troubleshooting.

---

## 📖 Documentation Hierarchy

```
QUICK_START_LOCAL_TESTS.md (← Start here for quick reference)
    ↓
LOCAL_INTEGRATION_TESTS.md (← Comprehensive guide for all details)
    ↓
IMPLEMENTATION_SUMMARY.md (← Technical deep dive)
    ↓
TESTING.md (← General testing strategy for all test types)
```

---

## 🔗 Related Resources

- **Source Code:** `src/main/groovy/nextflow/nomad/executor/`
- **Configuration:** `src/main/groovy/nextflow/nomad/config/`
- **Local Setup:** `../../infrastructure/03_automation/035_terraform/local-nomad-minio/`

---

## 📋 Test Execution Checklist

Before running tests:
- [ ] Local Nomad is running
- [ ] Minio container is accessible
- [ ] Docker daemon is running
- [ ] Adequate disk space available
- [ ] `NOMAD_ADDR` environment variable is set (optional, defaults to localhost:4646)

To run tests:
- [ ] Run `make test-local` or `./gradlew test -PtestEnv=local`
- [ ] Monitor test execution
- [ ] Check results in `build/reports/tests/test/`
- [ ] Review any failed tests in logs

---

## 📞 Support

For issues or questions:
1. Check [LOCAL_INTEGRATION_TESTS.md](LOCAL_INTEGRATION_TESTS.md) troubleshooting section
2. Review [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) for technical details
3. Check Nomad logs: `nomad status` and `nomad alloc logs <alloc-id>`
4. Verify local cluster setup in `../../infrastructure/03_automation/035_terraform/local-nomad-minio/`

---

## 📝 License

All tests are part of the nf-nomad plugin and licensed under Apache License 2.0.
See [COPYING](../../../COPYING) for details.

---

**Last Updated:** 2026-03-10
**Status:** ✅ Production Ready

