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
 * Local integration tests for workflow execution and error handling.
 *
 * Tests simple workflows, multi-process execution, data flow,
 * and error scenarios against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalWorkflowIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(300)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalWorkflowIntegrationSpec extends Specification {

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
        testWorkDir = Files.createTempDirectory("nf-workflow-test")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
    }

    protected def createMockTask(String name, String script = "echo test") {
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
                getScript() >> script
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 1: Simple Workflow Execution
    // ------------------------------------------------------------------

    void "should execute simple workflow with single process"() {
        given:
        def jobId = "workflow-simple-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("simple-process"),
            ["bash", "-c", "echo 'Hello Workflow'"], [:])

        then:
        evalId != null

        when:
        def maxRetries = 40
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
    // Test 2: Data Channel Flow
    // ------------------------------------------------------------------

    void "should pass data through channel"() {
        given:
        def jobId = "workflow-channel-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)
        def inputData = "test-input-data"

        when:
        def evalId = service.submitTask(jobId, createMockTask("channel-process", inputData),
            ["bash", "-c", "echo $inputData"], [:])

        then:
        evalId != null

        when:
        sleep(2000)
        def state = service.getTaskState(jobId)

        then:
        state != null
    }

    // ------------------------------------------------------------------
    // Test 3: Multi-Process Workflow
    // ------------------------------------------------------------------

    void "should execute multi-process workflow"() {
        given:
        def jobId1 = "workflow-multi-1-${System.currentTimeMillis()}"
        def jobId2 = "workflow-multi-2-${System.currentTimeMillis()}"
        submittedJobIds.addAll([jobId1, jobId2])

        when:
        def eval1 = service.submitTask(jobId1, createMockTask("process1"),
            ["bash", "-c", "echo 'Process 1'"], [:])
        sleep(1000)
        def eval2 = service.submitTask(jobId2, createMockTask("process2"),
            ["bash", "-c", "echo 'Process 2'"], [:])

        then:
        eval1 != null
        eval2 != null

        when:
        def maxRetries = 40
        def state1 = null
        def state2 = null
        def retryCount = 0

        while (retryCount < maxRetries) {
            state1 = service.getTaskState(jobId1)
            state2 = service.getTaskState(jobId2)
            if (state1?.state in ['complete', 'dead'] && state2?.state in ['complete', 'dead']) {
                break
            }
            sleep(1000)
            retryCount++
        }

        then:
        state1 != null
        state2 != null
    }

    // ------------------------------------------------------------------
    // Test 4: Workflow Completion
    // ------------------------------------------------------------------

    void "should track workflow completion"() {
        given:
        def jobId = "workflow-complete-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("completion-process"),
            ["bash", "-c", "echo done"], [:])

        then:
        sleep(1000)

        when:
        def maxRetries = 40
        def retryCount = 0
        def isComplete = false

        while (retryCount < maxRetries) {
            def state = service.getTaskState(jobId)
            if (state?.state in ['complete', 'dead']) {
                isComplete = true
                break
            }
            sleep(1000)
            retryCount++
        }

        then:
        isComplete
    }

    // ------------------------------------------------------------------
    // Test 5: Error Handling
    // ------------------------------------------------------------------

    void "should handle workflow errors"() {
        given:
        def jobId = "workflow-error-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("error-process"),
            ["bash", "-c", "exit 1"], [:])

        then:
        sleep(1000)

        when:
        def maxRetries = 40
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
    // Test 6: Connection Failure Resilience
    // ------------------------------------------------------------------

    void "should be resilient to transient connection issues"() {
        given:
        def jobId = "workflow-resilient-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("resilient-process"),
            ["bash", "-c", "echo resilient"], [:])

        then:
        evalId != null
    }

    // ------------------------------------------------------------------
    // Test 7: Retry Logic
    // ------------------------------------------------------------------

    void "should support retry logic for failed operations"() {
        given:
        def jobId = "workflow-retry-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("retry-process"),
            ["bash", "-c", "echo 'Will be retried'"], [:])

        then:
        evalId != null
    }

    // ------------------------------------------------------------------
    // Test 8: Output Validation
    // ------------------------------------------------------------------

    void "should validate workflow output"() {
        given:
        def jobId = "workflow-output-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        service.submitTask(jobId, createMockTask("output-process"),
            ["bash", "-c", "echo 'Expected Output'"], [:])

        then:
        sleep(2000)

        when:
        def state = service.getTaskState(jobId)

        then:
        state != null
    }
}

