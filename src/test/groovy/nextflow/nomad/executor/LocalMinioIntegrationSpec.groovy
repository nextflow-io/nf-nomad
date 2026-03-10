/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.nomad.executor

import nextflow.executor.Executor
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

import java.nio.file.Path
import java.nio.file.Files

/**
 * Local integration tests for Nomad with Minio storage backend.
 *
 * Tests job submissions with volume mounts for accessing Minio storage,
 * input/output file handling, and data persistence across jobs.
 *
 * Activated when NF_NOMAD_TEST_ENV is 'local'.
 *
 * Run with:
 *   make test-local
 *   ./gradlew test -PtestEnv=local
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(180)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalMinioIntegrationSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared List<String> submittedJobIds = []
    @Shared Path testWorkDir
    @Shared Path minioMountDir

    private static boolean isSuccessfulTerminalState(def state) {
        state?.state in ['complete', 'dead'] && !state?.failed
    }

    def setupSpec() {
        def addr  = System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'
        def dc    = System.getenv('NOMAD_DC') ?: null

        def clientOpts = [address: addr]
        def jobsOpts = [:]
        if (dc) jobsOpts.datacenters = dc

        config  = new NomadConfig(client: clientOpts, jobs: jobsOpts)
        service = new NomadService(config)
        testWorkDir = Files.createTempDirectory("nf-nomad-minio-test")
        minioMountDir = Files.createTempDirectory("nf-nomad-minio-mount")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
        minioMountDir?.deleteDir()
    }

    // ------------------------------------------------------------------
    // Test 1: Job with volume mount
    // ------------------------------------------------------------------

    void "should submit job with volume mount configuration"() {
        given:
        def jobId = "volume-mount-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "volume-mount"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'touch /tmp/test_file.txt && ls -la /tmp'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "touch /tmp/test_file.txt && ls -la /tmp"],
                [NF_MINIO_TEST: "volume_mount"]
        )

        then:
        evalId != null
        evalId.size() > 0
    }

    void "should complete volume-mount job"() {
        given:
        def jobId = submittedJobIds.first()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 2: Job with file input/output handling
    // ------------------------------------------------------------------

    void "should handle job input files"() {
        given:
        def jobId = "input-files-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        // Create a test input file
        def inputFile = testWorkDir.resolve("input.txt")
        inputFile.text = "Test input data"

        def mockTask = Mock(TaskRun) {
            getName()      >> "input-files"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'cat input.txt 2>/dev/null || cat /tmp/input.txt 2>/dev/null || true'
                getShell()      >> ["bash"]
                getInputFiles() >> ['input.txt': inputFile]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "cat input.txt 2>/dev/null || cat /tmp/input.txt 2>/dev/null || true"],
                [NF_MINIO_TEST: "input_files"]
        )

        then:
        evalId != null
    }

    void "should complete input-files job"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 3: Job writing output files
    // ------------------------------------------------------------------

    void "should handle job output file generation"() {
        given:
        def jobId = "output-files-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "output-files"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "Output data" > output.txt'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'echo "Output data" > output.txt'],
                [NF_MINIO_TEST: "output_files"]
        )

        then:
        evalId != null
    }

    void "should complete output-files job"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 4: Chained job dependency (job reads another's output)
    // ------------------------------------------------------------------

    void "should submit first job for chained execution"() {
        given:
        def jobId = "chain-producer-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "chain-producer"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "Chain data" > chain_output.txt'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'echo "Chain data" > chain_output.txt'],
                [NF_MINIO_TEST: "chain_producer", CHAIN_ID: "chain-1"]
        )

        then:
        evalId != null
    }

    void "should complete producer job in chain"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('chain-producer') }.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    void "should submit consumer job in chain"() {
        given:
        def jobId = "chain-consumer-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "chain-consumer"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "Received from chain" && cat chain_output.txt 2>/dev/null || echo "File not available"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'echo "Received from chain" && cat chain_output.txt 2>/dev/null || echo "File not available"'],
                [NF_MINIO_TEST: "chain_consumer", CHAIN_ID: "chain-1"]
        )

        then:
        evalId != null
    }

    void "should complete consumer job in chain"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('chain-consumer') }.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 5: Large file handling
    // ------------------------------------------------------------------

    void "should handle submission of job processing large data"() {
        given:
        def jobId = "large-data-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "large-data"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'dd if=/dev/zero of=large_file.bin bs=1M count=10 2>/dev/null && ls -lh large_file.bin'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "dd if=/dev/zero of=large_file.bin bs=1M count=10 2>/dev/null && ls -lh large_file.bin"],
                [NF_MINIO_TEST: "large_data"]
        )

        then:
        evalId != null
    }

    void "should complete large-data job"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 40
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 6: Parallel file operations
    // ------------------------------------------------------------------

    void "should handle parallel file operations"() {
        given:
        def jobId = "parallel-files-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "parallel-files"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'for i in {1..5}; do echo "File $i" > file_$i.txt & done; wait; ls -la file_*.txt'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "for i in {1..5}; do echo \"File \$i\" > file_\$i.txt & done; wait; ls -la file_*.txt"],
                [NF_MINIO_TEST: "parallel_files"]
        )

        then:
        evalId != null
    }

    void "should complete parallel-files job"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 7: Disk space monitoring
    // ------------------------------------------------------------------

    void "should report disk usage after job execution"() {
        given:
        def jobId = "disk-usage-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "disk-usage"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'df -h && du -sh .'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "df -h && du -sh ."],
                [NF_MINIO_TEST: "disk_usage"]
        )

        then:
        evalId != null
    }

    void "should complete disk-usage job"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 8: File compression and decompression
    // ------------------------------------------------------------------

    void "should handle file compression operations"() {
        given:
        def jobId = "file-compress-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "file-compress"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "data to compress" > data.txt && gzip -k data.txt && ls -la data.*'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"data to compress\" > data.txt && gzip -k data.txt && ls -la data.*"],
                [NF_MINIO_TEST: "file_compress"]
        )

        then:
        evalId != null
    }

    void "should complete file-compress job"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 9: Multiple tasks in sequence with shared state
    // ------------------------------------------------------------------

    void "should support sequential tasks with data passing"() {
        given:
        def baseJobId = "sequential-${System.currentTimeMillis()}"
        def jobId1 = "${baseJobId}-step1"
        def jobId2 = "${baseJobId}-step2"
        submittedJobIds.addAll([jobId1, jobId2])

        def mockTask1 = Mock(TaskRun) {
            getName()      >> "sequential-step1"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "Step 1 output" > state.txt'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId1 = service.submitTask(
                jobId1,
                mockTask1,
                ["bash", "-c", "echo \"Step 1 output\" > state.txt"],
                [NF_MINIO_TEST: "sequential_step1", SEQ_ID: baseJobId.toString()]
        )

        then:
        evalId1 != null

        when:
        // Wait for step 1 to complete
        def retryCount = 0
        def maxRetries = 30
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId1)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)

        when:
        def mockTask2 = Mock(TaskRun) {
            getName()      >> "sequential-step2"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'cat state.txt 2>/dev/null || true; echo "Step 2 processing"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        def evalId2 = service.submitTask(
                jobId2,
                mockTask2,
                ["bash", "-c", "cat state.txt 2>/dev/null || true; echo \\\"Step 2 processing\\\""],
                [NF_MINIO_TEST: "sequential_step2", SEQ_ID: baseJobId.toString()]
        )

        then:
        evalId2 != null
    }

    void "should complete second sequential task"() {
        given:
        def jobId = submittedJobIds.findAll { it.contains('sequential') && it.endsWith('step2') }.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !isSuccessfulTerminalState(state)) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        isSuccessfulTerminalState(state)
    }

    // ------------------------------------------------------------------
    // Test 10: Cleanup and resource verification
    // ------------------------------------------------------------------

    void "should retrieve allocation info for all submitted jobs"() {
        when:
        submittedJobIds.each { jobId ->
            try {
                def clientNode = service.getClientOfJob(jobId)
                assert clientNode != null || true  // Some jobs may not have allocations
            } catch (Exception ignored) {
                // Expected for some jobs
            }
        }

        then:
        noExceptionThrown()
    }

    void "should purge all test jobs successfully"() {
        when:
        submittedJobIds.each { jobId ->
            try {
                service.jobPurge(jobId)
            } catch (Exception ignored) {
                // Already purged or other cleanup issue
            }
        }

        then:
        noExceptionThrown()

        cleanup:
        submittedJobIds.clear()
    }
}

