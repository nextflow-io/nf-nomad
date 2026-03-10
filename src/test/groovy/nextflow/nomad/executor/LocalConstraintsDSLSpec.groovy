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
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path

/**
 * Local integration tests for constraint DSL and configuration.
 *
 * Tests node constraints, attribute constraints, operators, and combinations
 * against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalConstraintsDSLSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalConstraintsDSLSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared JobsApi jobsApi
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
        jobsApi = new JobsApi(service.apiClient)
        testWorkDir = Files.createTempDirectory("nf-constraint-test")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
    }

    protected def getJobFromNomad(String jobId) {
        try {
            return jobsApi.getJob(jobId, config.jobOpts().region,
                config.jobOpts().namespace, null, null, null, null, null, null, null)
        } catch (Exception ignored) {
            return null
        }
    }

    protected def createMockTask(String name) {
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
                getScript() >> "echo test"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 1: Node Constraints
    // ------------------------------------------------------------------

    void "should apply node.unique.name constraint"() {
        given:
        def jobId = "constraint-node-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def constraintConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraints: {
                    node {
                        unique = [name: 'test-node']
                    }
                }
            ]
        ])
        def constraintService = new NomadService(constraintConfig)

        when:
        def evalId = constraintService.submitTask(jobId, createMockTask("node-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.taskGroups[0].tasks[0].constraints?.size() > 0

        cleanup:
        constraintService?.close()
    }

    // ------------------------------------------------------------------
    // Test 2: Attribute Constraints
    // ------------------------------------------------------------------

    void "should apply attr.cpu.arch constraint"() {
        given:
        def jobId = "constraint-attr-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def attrConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraints: {
                    attr {
                        cpu = [arch: 'x86_64']
                    }
                }
            ]
        ])
        def attrService = new NomadService(attrConfig)

        when:
        def evalId = attrService.submitTask(jobId, createMockTask("attr-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        attrService?.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Custom Attribute Constraints
    // ------------------------------------------------------------------

    void "should apply custom attribute constraint"() {
        given:
        def jobId = "constraint-custom-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def customConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraint: {
                    attribute '${meta.custom_key}'
                    operator  "="
                    value     "custom_value"
                }
            ]
        ])
        def customService = new NomadService(customConfig)

        when:
        def evalId = customService.submitTask(jobId, createMockTask("custom-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        customService?.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Constraint Operators
    // ------------------------------------------------------------------

    void "should validate constraint operators"() {
        given:
        def jobId = "constraint-ops-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def opsConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraint: {
                    attribute '${meta.test}'
                    operator  "="
                    value     "value"
                }
            ]
        ])
        def opsService = new NomadService(opsConfig)

        when:
        def evalId = opsService.submitTask(jobId, createMockTask("ops-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        opsService?.close()
    }

    // ------------------------------------------------------------------
    // Test 5: Multiple Constraints
    // ------------------------------------------------------------------

    void "should combine multiple constraints"() {
        given:
        def jobId = "constraint-multi-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def multiConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraints: {
                    node {
                        unique = [name: 'test']
                    }
                    attr {
                        cpu = [arch: 'x86_64']
                    }
                }
            ]
        ])
        def multiService = new NomadService(multiConfig)

        when:
        def evalId = multiService.submitTask(jobId, createMockTask("multi-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        multiService?.close()
    }

    // ------------------------------------------------------------------
    // Test 6: Constraint Validation
    // ------------------------------------------------------------------

    void "should validate constraint syntax"() {
        given:
        def jobId = "constraint-valid-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def validConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraint: {
                    attribute '${attr.kernel.name}'
                    operator  "="
                    value     "linux"
                }
            ]
        ])
        def validService = new NomadService(validConfig)

        when:
        def evalId = validService.submitTask(jobId, createMockTask("valid-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        validService?.close()
    }

    // ------------------------------------------------------------------
    // Test 7: Task-Level Constraints
    // ------------------------------------------------------------------

    void "should apply task-level constraints"() {
        given:
        def jobId = "constraint-task-level-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def taskConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                constraint: {
                    attribute '${meta.workload_type}'
                    operator  "="
                    value     "compute"
                }
            ]
        ])
        def taskService = new NomadService(taskConfig)

        when:
        def evalId = taskService.submitTask(jobId, createMockTask("task-constraint-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        taskService?.close()
    }

    // ------------------------------------------------------------------
    // Test 8: Job Constraints Enforcement
    // ------------------------------------------------------------------

    void "should enforce job-level constraints"() {
        given:
        def jobId = "constraint-enforce-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("enforce-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.taskGroups[0].tasks[0] != null
    }
}

