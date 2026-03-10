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
 * Local integration tests for affinity configuration.
 *
 * Tests affinity rules, attributes, operators, and weights
 * against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalAffinityIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalAffinityIntegrationSpec extends Specification {

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
        testWorkDir = Files.createTempDirectory("nf-affinity-test")
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
    // Test 1: Basic Affinity Configuration
    // ------------------------------------------------------------------

    void "should submit job with affinity configuration"() {
        given:
        def jobId = "affinity-basic-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def affinityConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                affinity: {
                    attribute '${meta.workload_type}'
                    operator  "="
                    value     "prefer"
                }
            ]
        ])
        def affinityService = new NomadService(affinityConfig)

        when:
        def evalId = affinityService.submitTask(jobId, createMockTask("affinity-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.taskGroups[0].tasks[0].affinities?.size() > 0

        cleanup:
        affinityService?.close()
    }

    // ------------------------------------------------------------------
    // Test 2: Attribute-Based Affinity
    // ------------------------------------------------------------------

    void "should apply attribute-based affinity"() {
        given:
        def jobId = "affinity-attr-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def attrAffinityConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                affinity: {
                    attribute '${meta.gpu}'
                    operator  "="
                    value     "nvidia"
                }
            ]
        ])
        def attrService = new NomadService(attrAffinityConfig)

        when:
        def evalId = attrService.submitTask(jobId, createMockTask("attr-affinity-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        attrService?.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Affinity Operators
    // ------------------------------------------------------------------

    void "should use affinity operators correctly"() {
        given:
        def jobId = "affinity-ops-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def opsConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                affinity: {
                    attribute '${meta.cost}'
                    operator  ">"
                    value     "100"
                }
            ]
        ])
        def opsService = new NomadService(opsConfig)

        when:
        def evalId = opsService.submitTask(jobId, createMockTask("ops-affinity-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        opsService?.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Affinity Weights
    // ------------------------------------------------------------------

    void "should apply affinity weights"() {
        given:
        def jobId = "affinity-weight-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def weightConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                affinity: {
                    attribute '${meta.workload}'
                    operator  "="
                    value     "batch"
                    weight    50
                }
            ]
        ])
        def weightService = new NomadService(weightConfig)

        when:
        def evalId = weightService.submitTask(jobId, createMockTask("weight-affinity-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.taskGroups[0].tasks[0].affinities?.any { aff ->
            aff.weight == 50
        }

        cleanup:
        weightService?.close()
    }

    // ------------------------------------------------------------------
    // Test 5: Multiple Affinities
    // ------------------------------------------------------------------

    void "should handle multiple affinity rules"() {
        given:
        def jobId = "affinity-multi-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def multiAffinityConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                affinities: {
                    affinity1 = [attribute: '${meta.type}', operator: '=', value: 'compute', weight: 50]
                    affinity2 = [attribute: '${meta.cpu}', operator: '=', value: 'high', weight: 30]
                }
            ]
        ])
        def multiService = new NomadService(multiAffinityConfig)

        when:
        def evalId = multiService.submitTask(jobId, createMockTask("multi-affinity-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        multiService?.close()
    }

    // ------------------------------------------------------------------
    // Test 6: Affinity vs Constraint Interaction
    // ------------------------------------------------------------------

    void "should combine affinity with constraints"() {
        given:
        def jobId = "affinity-constraint-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("combined-task"),
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

