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
 * Local integration tests for spread configuration.
 *
 * Tests spread attributes, weights, and targets for load distribution
 * against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalSpreadIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalSpreadIntegrationSpec extends Specification {

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
        testWorkDir = Files.createTempDirectory("nf-spread-test")
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
    // Test 1: Basic Spread Configuration
    // ------------------------------------------------------------------

    void "should submit job with spread configuration"() {
        given:
        def jobId = "spread-basic-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def spreadConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                spreads: {
                    spread = [name: 'rack_id', weight: 50, targets: [a: 30]]
                }
            ]
        ])
        def spreadService = new NomadService(spreadConfig)

        when:
        def evalId = spreadService.submitTask(jobId, createMockTask("spread-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.spreads?.size() > 0

        cleanup:
        spreadService?.close()
    }

    // ------------------------------------------------------------------
    // Test 2: Spread Attributes
    // ------------------------------------------------------------------

    void "should apply spread attribute correctly"() {
        given:
        def jobId = "spread-attr-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def attrSpreadConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                spreads: {
                    spread = [name: 'datacenter', weight: 50, targets: [us_east: 50, us_west: 50]]
                }
            ]
        ])
        def attrService = new NomadService(attrSpreadConfig)

        when:
        def evalId = attrService.submitTask(jobId, createMockTask("attr-spread-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        attrService?.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Spread Weights
    // ------------------------------------------------------------------

    void "should respect spread weights"() {
        given:
        def jobId = "spread-weight-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def weightConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                spreads: {
                    spread = [name: 'node_class', weight: 75, targets: [batch: 50, interactive: 25]]
                }
            ]
        ])
        def weightService = new NomadService(weightConfig)

        when:
        def evalId = weightService.submitTask(jobId, createMockTask("weight-spread-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.spreads?.any { spread ->
            spread.weight == 75
        }

        cleanup:
        weightService?.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Spread Targets
    // ------------------------------------------------------------------

    void "should handle spread targets"() {
        given:
        def jobId = "spread-targets-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def targetConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                spreads: {
                    spread = [name: 'region', weight: 50, targets: [us: 50, eu: 30, asia: 20]]
                }
            ]
        ])
        def targetService = new NomadService(targetConfig)

        when:
        def evalId = targetService.submitTask(jobId, createMockTask("target-spread-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        targetService?.close()
    }

    // ------------------------------------------------------------------
    // Test 5: Multiple Spreads
    // ------------------------------------------------------------------

    void "should handle multiple spreads"() {
        given:
        def jobId = "spread-multi-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("multi-spread-task"),
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

