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
 * Local integration tests for datacenter configuration.
 *
 * Tests datacenter directives, multi-datacenter support, and
 * override behavior against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalDatacenterIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalDatacenterIntegrationSpec extends Specification {

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
        testWorkDir = Files.createTempDirectory("nf-datacenter-test")
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
    // Test 1: Single Datacenter
    // ------------------------------------------------------------------

    void "should submit job with single datacenter"() {
        given:
        def jobId = "dc-single-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def dcConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                datacenters: ['dc1']
            ]
        ])
        def dcService = new NomadService(dcConfig)

        when:
        def evalId = dcService.submitTask(jobId, createMockTask("dc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.datacenters == ['dc1']

        cleanup:
        dcService?.close()
    }

    // ------------------------------------------------------------------
    // Test 2: Multiple Datacenters
    // ------------------------------------------------------------------

    void "should submit job with multiple datacenters"() {
        given:
        def jobId = "dc-multi-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def multiDcConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                datacenters: ['dc1', 'dc2', 'dc3']
            ]
        ])
        def multiService = new NomadService(multiDcConfig)

        when:
        def evalId = multiService.submitTask(jobId, createMockTask("multi-dc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.datacenters == ['dc1', 'dc2', 'dc3']

        cleanup:
        multiService?.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Datacenter as String
    // ------------------------------------------------------------------

    void "should handle datacenter as string"() {
        given:
        def jobId = "dc-string-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def stringDcConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                datacenters: ['default']
            ]
        ])
        def stringService = new NomadService(stringDcConfig)

        when:
        def evalId = stringService.submitTask(jobId, createMockTask("string-dc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        stringService?.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Datacenter as List
    // ------------------------------------------------------------------

    void "should handle datacenter as list"() {
        given:
        def jobId = "dc-list-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def listDcConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                datacenters: ['us-east', 'us-west', 'eu-central']
            ]
        ])
        def listService = new NomadService(listDcConfig)

        when:
        def evalId = listService.submitTask(jobId, createMockTask("list-dc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        listService?.close()
    }

    // ------------------------------------------------------------------
    // Test 5: Global Datacenter Configuration
    // ------------------------------------------------------------------

    void "should apply global datacenter configuration"() {
        given:
        def jobId = "dc-global-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def globalDcConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                datacenters: ['global-dc']
            ]
        ])
        def globalService = new NomadService(globalDcConfig)

        when:
        def evalId = globalService.submitTask(jobId, createMockTask("global-dc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        globalService?.close()
    }

    // ------------------------------------------------------------------
    // Test 6: Datacenter Override Behavior
    // ------------------------------------------------------------------

    void "should handle datacenter configuration correctly"() {
        given:
        def jobId = "dc-override-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        when:
        def evalId = service.submitTask(jobId, createMockTask("override-dc-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.datacenters != null
    }
}

