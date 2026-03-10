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

import groovy.util.logging.Slf4j
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.config.NomadJobOpts
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.executor.Executor
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

import java.nio.file.Path
import java.nio.file.Files

/**
 * Local integration tests for placement failure detection.
 *
 * Tests that jobs exceeding resource requirements are properly detected as failed
 * when failOnPlacementFailure is enabled. This prevents Nextflow from indefinitely
 * waiting for jobs that cannot be scheduled due to insufficient node resources.
 *
 * Activated when NF_NOMAD_TEST_ENV is 'local'.
 * Requires NOMAD_ADDR in the environment (defaults to http://localhost:4646).
 *
 * Run with:
 *   make test-local
 *   ./gradlew test -PtestEnv=local --tests LocalPlacementFailureIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@Timeout(180)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalPlacementFailureIntegrationSpec extends Specification {

    @Shared NomadConfig configWithPlacementFailure
    @Shared NomadService serviceWithFailure
    @Shared NomadConfig configWithoutPlacementFailure
    @Shared NomadService serviceWithoutFailure
    @Shared List<String> submittedJobIds = []
    @Shared Path testWorkDir

    def setupSpec() {
        log.info("[TEST] Setting up LocalPlacementFailureIntegrationSpec")

        testWorkDir = Files.createTempDirectory("nf-nomad-placement-test-")
        log.info("[TEST] Test work directory: $testWorkDir")

        // Config with placement failure detection enabled (short timeout for testing)
        configWithPlacementFailure = new NomadConfig([
                client: [address: System.getenv("NOMAD_ADDR") ?: "http://localhost:4646"],
                jobs: [
                        failOnPlacementFailure: true,
                        placementFailureTimeout: '5s'  // 5 seconds for testing
                ]
        ])
        serviceWithFailure = new NomadService(configWithPlacementFailure)

        // Config without placement failure detection (for comparison)
        configWithoutPlacementFailure = new NomadConfig([
                client: [address: System.getenv("NOMAD_ADDR") ?: "http://localhost:4646"],
                jobs: [
                        failOnPlacementFailure: false
                ]
        ])
        serviceWithoutFailure = new NomadService(configWithoutPlacementFailure)

        log.info("[TEST] Setup complete")
    }

    def cleanupSpec() {
        log.info("[TEST] Cleaning up LocalPlacementFailureIntegrationSpec")

        // Clean up submitted jobs
        submittedJobIds.each { jobId ->
            try {
                serviceWithFailure.jobPurge(jobId)
                log.info("[TEST] Purged job: $jobId")
            } catch (Exception e) {
                log.warn("[TEST] Failed to purge job $jobId: ${e.message}")
            }
        }

        // Clean up test directory
        if (testWorkDir?.toFile()?.exists()) {
            testWorkDir.toFile().deleteDir()
            log.info("[TEST] Cleaned up test directory: $testWorkDir")
        }
    }

    void "placement failure detection is disabled by default"() {
        when:
        def hasFailure = serviceWithoutFailure.isPlacementFailure("test-job-id", System.currentTimeMillis() - 120_000L)

        then:
        !hasFailure
        log.info("[TEST] Confirmed: placement failure detection is disabled by default")
    }

    void "placement failure detection is enabled when configured"() {
        when:
        boolean isEnabled = configWithPlacementFailure.jobOpts().failOnPlacementFailure

        then:
        isEnabled
        log.info("[TEST] Confirmed: placement failure detection is enabled")
    }

    void "placement failure timeout uses configured value"() {
        when:
        def timeout = configWithPlacementFailure.jobOpts().placementFailureTimeout

        then:
        timeout.millis == 5_000L
        log.info("[TEST] Confirmed: placement failure timeout is 5 seconds")
    }

    void "job submitted with excessive resource requirements"() {
        given:
        def jobId = "placement-test-excessive-${System.nanoTime()}"
        submittedJobIds.add(jobId)
        def excessiveTaskConfig = Mock(TaskConfig) {
            get('cpus') >> 10_000
            get('memory') >> '10 TB'
        }

        def mockTask = Mock(TaskRun) {
            getName()      >> "excessive-resources"
            getContainer() >> "ubuntu:latest"
            getConfig()    >> excessiveTaskConfig
            getWorkDirStr() >> testWorkDir.toString()
            getWorkDir()   >> testWorkDir
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> testWorkDir
                getScript()     >> 'echo "test"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = serviceWithFailure.submitTask(
                jobId,
                mockTask,
                ['/bin/bash', '-c', "echo 'test'"],
                [:]
        )
        log.info("[TEST] Submitted job with resources: $jobId")

        then:
        evalId != null
        evalId.size() > 0
        log.info("[TEST] Job submitted successfully with ID: $evalId (cpus=10000, memory=10 TB)")
    }

    void "placement failure is not triggered immediately"() {
        given:
        def jobId = submittedJobIds.last()
        def submissionTime = System.currentTimeMillis()

        when:
        sleep(1_000)  // Wait 1 second
        def hasFailure = serviceWithFailure.isPlacementFailure(jobId, submissionTime)

        then:
        !hasFailure  // Should not fail before timeout (5 seconds)
        log.info("[TEST] Confirmed: placement failure not triggered before timeout")
    }

    void "placement failure is triggered after timeout exceeded"() {
        given:
        def jobId = submittedJobIds.last()
        def submissionTime = System.currentTimeMillis() - 12_000L  // Simulate submission older than timeout

        when:
        sleep(1_000)  // Give Nomad time to record scheduling state/allocation status
        def hasFailure = serviceWithFailure.isPlacementFailure(jobId, submissionTime)

        then:
        hasFailure
        log.info("[TEST] Confirmed: placement failure detected after timeout exceeded")
    }

    void "normally scheduled jobs are not marked as placement failures"() {
        given:
        def jobId = "placement-test-normal-${System.nanoTime()}"
        def submissionTime = System.currentTimeMillis()

        def mockTask = Mock(TaskRun) {
            getName()      >> "normal-job"
            getContainer() >> "ubuntu:latest"
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
                getScript()     >> 'echo "success"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = serviceWithFailure.submitTask(
                jobId,
                mockTask,
                ['/bin/bash', '-c', "echo 'success'"],
                [:]
        )
        submittedJobIds.add(jobId)
        log.info("[TEST] Submitted normal job: $jobId")

        sleep(2_000)  // Wait for job to be placed
        def hasFailure = serviceWithFailure.isPlacementFailure(jobId, submissionTime)

        then:
        !hasFailure  // Normal jobs should get placed and not trigger failure
        log.info("[TEST] Confirmed: normal job was not marked as placement failure")
    }

    void "placement failure timeout respects environment variable override"() {
        given:
        def env = ['NOMAD_PLACEMENT_FAILURE_TIMEOUT': '30s']

        when:
        def opts = new NomadJobOpts([failOnPlacementFailure: true], env)

        then:
        opts.placementFailureTimeout.millis == 30_000L
        log.info("[TEST] Confirmed: environment variable override sets timeout to 30 seconds")
    }

    void "placement failure timeout respects config override"() {
        given:
        def config = new NomadConfig([
                client: [address: System.getenv("NOMAD_ADDR") ?: "http://localhost:4646"],
                jobs: [
                        failOnPlacementFailure: true,
                        placementFailureTimeout: '90s'
                ]
        ])

        when:
        def timeout = config.jobOpts().placementFailureTimeout

        then:
        timeout.millis == 90_000L
        log.info("[TEST] Confirmed: config setting overrides default timeout to 90 seconds")
    }
}






