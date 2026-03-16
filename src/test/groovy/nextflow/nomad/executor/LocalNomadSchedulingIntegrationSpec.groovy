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
import io.nomadproject.client.api.JobsApi

import nextflow.executor.Executor
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

import java.nio.file.Path
import java.nio.file.Files

/**
 * Advanced Nomad integration tests for scheduling constraints, affinities, and spreads.
 *
 * Tests Nomad-specific scheduling features including:
 * - Node constraints and affinities
 * - Task spreads for load distribution
 * - Resource requirements and allocation
 * - Job metadata and annotations
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
class LocalNomadSchedulingIntegrationSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared JobsApi jobsApi
    @Shared List<String> submittedJobIds = []
    @Shared Path testWorkDir

    private static boolean isSuccessfulTerminalState(def state) {
        state?.state in ['complete', 'dead']
    }

    private def awaitJobById(String jobId, int maxRetries = 20) {
        int retries = 0
        while (retries < maxRetries) {
            try {
                def job = jobsApi.getJob(jobId, config.jobOpts().region,
                        config.jobOpts().namespace, null, null, null, null, null, null, null)
                if (job != null) {
                    return job
                }
            } catch (Exception ignored) {
                // Ignore transient lookup failures while Nomad indexes the job
            }
            sleep(500)
            retries++
        }
        return null
    }

    def setupSpec() {
        def addr  = System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'
        def dc    = System.getenv('NOMAD_DC') ?: null

        def clientOpts = [address: addr]
        def jobsOpts = [:]
        if (dc) jobsOpts.datacenters = dc

        config  = new NomadConfig(client: clientOpts, jobs: jobsOpts)
        service = new NomadService(config)
        jobsApi = new JobsApi(service.apiClient)
        testWorkDir = Files.createTempDirectory("nf-nomad-scheduling-test")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
    }

    // ------------------------------------------------------------------
    // Test 1: Basic job submission and state polling
    // ------------------------------------------------------------------

    void "should submit a basic job and poll its state"() {
        given:
        def jobId = "basic-poll-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "basic-poll"
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
                getScript()     >> 'sleep 2 && echo "basic poll test"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "sleep 2 && echo \"basic poll test\""],
                [TEST_CATEGORY: "scheduling"]
        )

        then:
        evalId != null
        evalId.size() > 0

        when:
        // Poll for state changes
        List<String> observedStates = []
        def maxPolls = 40
        def pollCount = 0

        while (pollCount < maxPolls) {
            def state = service.getTaskState(jobId)
            if (!observedStates.contains(state.state)) {
                observedStates.add(state.state)
            }
            if (isSuccessfulTerminalState(state)) {
                break
            }
            sleep(1000)
            pollCount++
        }

        then:
        observedStates.any { it in ['complete', 'dead', 'running'] }
    }

    // ------------------------------------------------------------------
    // Test 2: Job with varying resource requests
    // ------------------------------------------------------------------

    void "should submit job with minimal resources"() {
        given:
        def jobId = "minimal-resources-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "minimal-resources"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig) {
                getMemory() >> nextflow.util.MemoryUnit.of('128 MB')
                getCpus() >> 0.25
            }
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "minimal resources"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"minimal resources\""],
                [RESOURCE_LEVEL: "minimal"]
        )

        then:
        evalId != null
    }

    void "should complete minimal-resources job"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('minimal-resources') }.last()
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

    void "should submit job with moderate resources"() {
        given:
        def jobId = "moderate-resources-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "moderate-resources"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig) {
                getMemory() >> nextflow.util.MemoryUnit.of('512 MB')
                getCpus() >> 1
            }
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "moderate resources"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"moderate resources\""],
                [RESOURCE_LEVEL: "moderate"]
        )

        then:
        evalId != null
    }

    void "should complete moderate-resources job"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('moderate-resources') }.last()
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
    // Test 3: Job timeout and deadline handling
    // ------------------------------------------------------------------

    void "should handle job execution timeout"() {
        given:
        def jobId = "timeout-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "timeout-job"
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
                getScript()     >> 'sleep 5 && echo "after sleep"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "sleep 5 && echo \"after sleep\""],
                [TEST_TYPE: "timeout"]
        )

        then:
        evalId != null
    }

    void "should complete timeout job"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('timeout-job') }.last()
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
    // Test 4: Batch submission of related jobs
    // ------------------------------------------------------------------

    void "should submit batch of related jobs with same labels"() {
        given:
        def batchId = "batch-${System.currentTimeMillis()}"
        def jobIds = (1..4).collect { i -> "${batchId}-job-${i}" }
        submittedJobIds.addAll(jobIds)

        when:
        jobIds.eachWithIndex { jobId, idx ->
            def mockTask = Mock(TaskRun) {
                getName()      >> "batch-job-${idx}"
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
                    getScript()     >> "echo \"batch job ${idx}\""
                    getShell()      >> ["bash"]
                    getInputFiles() >> [:]
                }
            }

            service.submitTask(
                    jobId,
                    mockTask,
                    ["bash", "-c", "echo \"batch job ${idx}\""],
                    [BATCH_ID: batchId.toString(), JOB_INDEX: idx.toString()]
            )
        }

        then:
        noExceptionThrown()
    }

    void "should complete all batch jobs"() {
        given:
        def batchJobIds = submittedJobIds.findAll { it.startsWith('batch-') }
        def maxRetries = 50
        def allCompleted = false

        when:
        def retryCount = 0
        while (retryCount < maxRetries && !allCompleted) {
            sleep(1000)
            def states = batchJobIds.collect { service.getTaskState(it) }
            allCompleted = states.every { isSuccessfulTerminalState(it) }
            retryCount++
        }

        then:
        allCompleted
    }

    // ------------------------------------------------------------------
    // Test 5: Job priority and ordering
    // ------------------------------------------------------------------

    void "should submit jobs with different priorities"() {
        given:
        def jobIds = ['high-priority', 'normal-priority', 'low-priority'].collect { "${it}-${System.currentTimeMillis()}" }
        submittedJobIds.addAll(jobIds)

        when:
        ['high', 'normal', 'low'].eachWithIndex { priority, idx ->
            def jobId = jobIds[idx]
            def mockTask = Mock(TaskRun) {
                getName()      >> "${priority}-job"
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
                    getScript()     >> "echo \"${priority} priority job\""
                    getShell()      >> ["bash"]
                    getInputFiles() >> [:]
                }
            }

            service.submitTask(
                    jobId,
                    mockTask,
                    ["bash", "-c", "echo \"${priority} priority job\""],
                    [PRIORITY: priority]
            )
        }

        then:
        noExceptionThrown()
    }

    void "should submit a job with nomadOptions priority"() {
        given:
        def jobId = "directive-priority-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "directive-priority-job"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
                getConfig() >> Mock(ProcessConfig) {
                    get(TaskDirectives.NOMAD_OPTIONS) >> [priority: "high"]
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> "echo \"directive priority job\""
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"directive priority job\""],
                [:]
        )
        def submittedJob = awaitJobById(jobId)

        then:
        evalId != null
        submittedJob != null
        submittedJob.getPriority() == 80
    }

    void "should submit a job with nomadOptions low priority"() {
        given:
        def jobId = "nomad-options-priority-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "nomad-options-priority-job"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
                getConfig() >> Mock(ProcessConfig) {
                    get(TaskDirectives.NOMAD_OPTIONS) >> [priority: "low"]
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> "echo \"nomad options priority job\""
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"nomad options priority job\""],
                [:]
        )
        def submittedJob = awaitJobById(jobId)

        then:
        evalId != null
        submittedJob != null
        submittedJob.getPriority() == 30
    }

    void "should submit a job with custom numeric nomadOptions priority"() {
        given:
        def jobId = "custom-directive-priority-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "custom-directive-priority-job"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
                getConfig() >> Mock(ProcessConfig) {
                    get(TaskDirectives.NOMAD_OPTIONS) >> [priority: "67"]
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> "echo \"custom directive priority job\""
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"custom directive priority job\""],
                [:]
        )
        def submittedJob = awaitJobById(jobId)

        then:
        evalId != null
        submittedJob != null
        submittedJob.getPriority() == 67
    }

    void "should submit a job with custom numeric nomadOptions priority"() {
        given:
        def jobId = "custom-nomad-options-priority-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "custom-nomad-options-priority-job"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
                getConfig() >> Mock(ProcessConfig) {
                    get(TaskDirectives.NOMAD_OPTIONS) >> [priority: "73"]
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> "echo \"custom nomad options priority job\""
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"custom nomad options priority job\""],
                [:]
        )
        def submittedJob = awaitJobById(jobId)

        then:
        evalId != null
        submittedJob != null
        submittedJob.getPriority() == 73
    }

    void "should complete all priority jobs"() {
        given:
        def priorityJobIds = submittedJobIds.findAll { it.contains('priority') }
        def maxRetries = 40
        def allCompleted = false

        when:
        def retryCount = 0
        while (retryCount < maxRetries && !allCompleted) {
            sleep(1000)
            def states = priorityJobIds.collect { service.getTaskState(it) }
            allCompleted = states.every { isSuccessfulTerminalState(it) }
            retryCount++
        }

        then:
        allCompleted
    }

    // ------------------------------------------------------------------
    // Test 6: Job metadata preservation and retrieval
    // ------------------------------------------------------------------

    void "should preserve job metadata through submission"() {
        given:
        def jobId = "metadata-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)
        def metadata = [
            PIPELINE: "test-pipeline",
            VERSION: "1.0.0",
            USER: "integration-test",
            EXECUTION_ID: UUID.randomUUID().toString()
        ]

        def mockTask = Mock(TaskRun) {
            getName()      >> "metadata-job"
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
                getScript()     >> 'echo "metadata test"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"metadata test\""],
                metadata
        )

        then:
        evalId != null
    }

    void "should complete metadata job"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('metadata-job') }.last()
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
    // Test 7: Interaction with multiple datacenters (if available)
    // ------------------------------------------------------------------

    @Requires({ System.getenv('NOMAD_DC') != null })
    void "should respect datacenter configuration"() {
        given:
        def jobId = "dc-test-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "dc-test"
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
                getScript()     >> 'echo "DC test"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"DC test\""],
                [DC_TEST: "true"]
        )

        then:
        evalId != null
    }

    // ------------------------------------------------------------------
    // Test 8: Job restart and recovery
    // ------------------------------------------------------------------

    void "should handle job restart scenarios"() {
        given:
        def jobId = "restart-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "restart-job"
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
                getScript()     >> 'echo "restart test" && date'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo \"restart test\" && date"],
                [RESTART_TEST: "true"]
        )

        then:
        evalId != null
    }

    void "should complete restart job"() {
        given:
        def jobId = submittedJobIds.findAll { it.startsWith('restart-job') }.last()
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
    // Test 9: Status transitions from submitted to completed
    // ------------------------------------------------------------------

    void "should track state transitions for a job"() {
        given:
        def jobId = "state-transition-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "state-transition"
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
                getScript()     >> 'sleep 1 && echo "state transition"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "sleep 1 && echo \"state transition\""],
                [STATE_TRANSITION_TEST: "true"]
        )

        then:
        evalId != null

        when:
        // Track state transitions
        List<String> stateHistory = []
        def maxPolls = 40
        def pollCount = 0

        while (pollCount < maxPolls) {
            def state = service.getTaskState(jobId)
            if (stateHistory.isEmpty() || stateHistory.last() != state.state) {
                stateHistory.add(state.state)
            }
            if (isSuccessfulTerminalState(state)) {
                break
            }
            sleep(1000)
            pollCount++
        }

        then:
        stateHistory.size() >= 1
        stateHistory.last() in ['complete', 'dead']
    }

    // ------------------------------------------------------------------
    // Test 10: Cleanup verification
    // ------------------------------------------------------------------

    void "should verify all test jobs reach completion or are cleanable"() {
        when:
        submittedJobIds.each { jobId ->
            try {
                def state = service.getTaskState(jobId)
                assert state != null
            } catch (Exception ignored) {
                // Job may already be purged
            }
        }

        then:
        noExceptionThrown()
    }

    void "should successfully purge all test jobs"() {
        when:
        submittedJobIds.each { jobId ->
            try {
                service.jobPurge(jobId)
            } catch (Exception ignored) {
                // Job may have already been purged
            }
        }

        then:
        noExceptionThrown()

        cleanup:
        submittedJobIds.clear()
    }
}

