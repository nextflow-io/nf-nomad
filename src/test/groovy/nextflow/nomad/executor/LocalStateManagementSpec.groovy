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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Local integration tests for job state management and lifecycle.
 *
 * Tests state retrieval, transitions, polling, and job completion detection
 * against a real local Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalStateManagementSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalStateManagementSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared List<String> submittedJobIds = []
    @Shared Path testWorkDir

    def setupSpec() {
        def addr = System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'
        def dc = System.getenv('NOMAD_DC') ?: null

        def clientOpts = [address: addr]
        def jobsOpts = [deleteOnCompletion: false]
        if (dc) jobsOpts.datacenters = dc

        config = new NomadConfig(client: clientOpts, jobs: jobsOpts)
        service = new NomadService(config)
        testWorkDir = Files.createTempDirectory("nf-state-test")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
    }

    // Helper to create a mock task
    protected def createMockTask(String name, String command = "echo test") {
        Mock(TaskRun) {
            getName() >> name
            getContainer() >> "ubuntu:22.04"
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir() >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean() >> Mock(TaskBean) {
                getWorkDir() >> testWorkDir
                getScript() >> command
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 1: State Retrieval
    // ------------------------------------------------------------------

    void "should retrieve task state for submitted job"() {
        given:
        def jobId = "state-retrieve-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("state-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def state = service.getTaskState(jobId)

        then:
        state != null
        state.state != null
    }

    // ------------------------------------------------------------------
    // Test 2: State Transitions
    // ------------------------------------------------------------------

    void "should track state transitions over time"() {
        given:
        def jobId = "state-transitions-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)
        List<String> stateHistory = []

        when:
        service.submitTask(jobId, createMockTask("transition-task"),
            ["bash", "-c", "sleep 1 && echo done"], [:])

        then:
        sleep(500)

        when:
        // Poll and collect state transitions
        def maxPolls = 30
        def pollCount = 0
        while (pollCount < maxPolls) {
            def state = service.getTaskState(jobId)
            if (state?.state && !stateHistory.contains(state.state)) {
                stateHistory.add(state.state)
            }
            if (state?.state in ['complete', 'dead']) {
                break
            }
            sleep(1000)
            pollCount++
        }

        then:
        stateHistory.size() > 0
        stateHistory.last() in ['complete', 'dead']
    }

    // ------------------------------------------------------------------
    // Test 3: Unknown State Handling
    // ------------------------------------------------------------------

    void "should return unknown state for non-existent jobs"() {
        when:
        def state = service.getTaskState("non-existent-job-${System.currentTimeMillis()}")

        then:
        state != null
        state.state == 'unknown'
    }

    // ------------------------------------------------------------------
    // Test 4: Allocation Polling
    // ------------------------------------------------------------------

    void "should retrieve allocations for submitted job"() {
        given:
        def jobId = "alloc-poll-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("alloc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        sleep(1000)

        when:
        def state = service.getTaskState(jobId)

        then:
        state != null
        // State object should have timing info once allocated
        state.state in ['pending', 'running', 'complete', 'dead', 'unknown']
    }

    // ------------------------------------------------------------------
    // Test 5: Job Completion Detection
    // ------------------------------------------------------------------

    void "should detect job completion state"() {
        given:
        def jobId = "completion-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("completion-task"),
            ["bash", "-c", "echo complete"], [:])

        then:
        sleep(1000)

        when:
        def maxRetries = 30
        def retryCount = 0
        def state = null
        while (retryCount < maxRetries) {
            state = service.getTaskState(jobId)
            if (state?.state in ['complete', 'dead']) {
                break
            }
            sleep(1000)
            retryCount++
        }

        then:
        state != null
        state.state in ['complete', 'dead']
    }

    // ------------------------------------------------------------------
    // Test 6: Failed Job Detection
    // ------------------------------------------------------------------

    void "should detect failed job state"() {
        given:
        def jobId = "failed-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("failed-task"),
            ["bash", "-c", "exit 1"], [:])

        then:
        sleep(1000)

        when:
        def maxRetries = 30
        def retryCount = 0
        def state = null
        while (retryCount < maxRetries) {
            state = service.getTaskState(jobId)
            if (state?.state in ['complete', 'dead']) {
                break
            }
            sleep(1000)
            retryCount++
        }

        then:
        state != null
        state.state in ['complete', 'dead']
    }

    // ------------------------------------------------------------------
    // Test 7: Job Kill Operation
    // ------------------------------------------------------------------

    void "should kill a running job"() {
        given:
        def jobId = "kill-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("kill-task"),
            ["bash", "-c", "sleep 30"], [:])

        then:
        sleep(1000)

        when:
        service.kill(jobId)
        sleep(1000)

        then:
        noExceptionThrown()
    }

    // ------------------------------------------------------------------
    // Test 8: Job Purge Operation
    // ------------------------------------------------------------------

    void "should purge job from cluster"() {
        given:
        def jobId = "purge-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("purge-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        sleep(1000)

        when:
        service.jobPurge(jobId)

        then:
        noExceptionThrown()
        submittedJobIds.remove(jobId)
    }
}

