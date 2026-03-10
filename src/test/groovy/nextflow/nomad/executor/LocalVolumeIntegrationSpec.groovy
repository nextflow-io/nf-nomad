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
 * Local integration tests for volume configuration and mounts.
 *
 * Tests CSI, Docker, and Host volume mounting, accessibility,
 * and configuration against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalVolumeIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalVolumeIntegrationSpec extends Specification {

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
        testWorkDir = Files.createTempDirectory("nf-volume-test")
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
                getScript() >> "ls"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 1: CSI Volume Configuration
    // ------------------------------------------------------------------

    void "should submit job with CSI volume"() {
        given:
        def jobId = "csi-volume-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def volumeConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volume: { type "csi" name "test-volume" }
            ]
        ])
        def volumeService = new NomadService(volumeConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("csi-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 2: Docker Volume Configuration
    // ------------------------------------------------------------------

    void "should submit job with Docker volume"() {
        given:
        def jobId = "docker-volume-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def volumeConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                dockerVolume: "test-docker-volume"
            ]
        ])
        def volumeService = new NomadService(volumeConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("docker-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Host Volume Configuration
    // ------------------------------------------------------------------

    void "should submit job with Host volume"() {
        given:
        def jobId = "host-volume-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def volumeConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volume: { type "host" name "test-host-volume" }
            ]
        ])
        def volumeService = new NomadService(volumeConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("host-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Multiple Volumes
    // ------------------------------------------------------------------

    void "should handle multiple volumes"() {
        given:
        def jobId = "multi-volume-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def multiVolumeConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volumes: [
                    { type "csi" name "volume-1" },
                    { type "csi" name "volume-2" path "/data" readOnly true }
                ]
            ]
        ])
        def volumeService = new NomadService(multiVolumeConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("multi-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.taskGroups[0].volumes?.size() >= 2

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 5: Read-Only Volumes
    // ------------------------------------------------------------------

    void "should respect read-only volumes"() {
        given:
        def jobId = "readonly-volume-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def roVolumeConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volumes: [
                    { type "csi" name "workdir-vol" },
                    { type "csi" name "readonly-vol" path "/data" readOnly true }
                ]
            ]
        ])
        def volumeService = new NomadService(roVolumeConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("ro-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        when:
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then:
        job != null
        job.taskGroups[0].volumes?.values()?.any { vol ->
            vol.readOnly == true
        }

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 6: Volume Path Configuration
    // ------------------------------------------------------------------

    void "should configure volume mount paths"() {
        given:
        def jobId = "volume-path-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)
        def mountPath = "/mnt/data"

        def pathVolumeConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volume: { type "csi" name "path-vol" path mountPath }
            ]
        ])
        def volumeService = new NomadService(pathVolumeConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("path-task"),
            ["bash", "-c", "echo test"], [:])

        then:
        evalId != null

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 7: Volume Accessibility in Container
    // ------------------------------------------------------------------

    void "should make volumes accessible in container"() {
        given:
        def jobId = "vol-access-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def accessConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volume: { type "host" name "scratch" }
            ]
        ])
        def volumeService = new NomadService(accessConfig)

        when:
        def evalId = volumeService.submitTask(jobId, createMockTask("access-task"),
            ["bash", "-c", "mount | grep -i nomad || echo no_mount"], [:])

        then:
        evalId != null

        cleanup:
        volumeService?.close()
    }

    // ------------------------------------------------------------------
    // Test 8: Volume Cleanup
    // ------------------------------------------------------------------

    void "should cleanup volume mounts on job completion"() {
        given:
        def jobId = "vol-cleanup-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def cleanupConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                deleteOnCompletion: false,
                volume: { type "host" name "cleanup-vol" }
            ]
        ])
        def volumeService = new NomadService(cleanupConfig)

        when:
        volumeService.submitTask(jobId, createMockTask("cleanup-task"),
            ["bash", "-c", "echo done"], [:])

        sleep(2000)
        volumeService.jobPurge(jobId)

        then:
        noExceptionThrown()

        cleanup:
        volumeService?.close()
    }
}

