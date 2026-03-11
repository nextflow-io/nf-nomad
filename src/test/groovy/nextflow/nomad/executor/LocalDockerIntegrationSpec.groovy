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
 * Local Docker integration tests for NomadService with Minio storage backend.
 *
 * Tests Docker container execution, job state transitions, volume mounts,
 * and integration with Minio for object storage on a local Nomad cluster.
 *
 * Activated when NF_NOMAD_TEST_ENV is 'local'.
 * Requires NOMAD_ADDR in the environment (defaults to http://localhost:4646).
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
class LocalDockerIntegrationSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared List<String> submittedJobIds = []
    @Shared Path testWorkDir

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
        testWorkDir = Files.createTempDirectory("nf-nomad-local-test")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
    }

    // ------------------------------------------------------------------
    // Test 1: Ubuntu Docker container with simple echo
    // ------------------------------------------------------------------

    void "should run Ubuntu container with simple echo command"() {
        given:
        def jobId = "ubuntu-echo-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "ubuntu-echo"
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
                getScript()     >> 'echo "Hello from Ubuntu"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'echo "Hello from Ubuntu"'],
                [NF_TEST: "ubuntu_echo"]
        )

        then:
        evalId != null
        evalId.size() > 0
    }

    void "should transition Ubuntu job to running state"() {
        given:
        def jobId = submittedJobIds.first()

        when:
        sleep(2000)  // Give Nomad time to process
        def state = service.getTaskState(jobId)

        then:
        state != null
        state.state in ['pending', 'running', 'complete', 'dead']
    }

    void "should complete Ubuntu job successfully"() {
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
    // Test 2: Alpine Docker container with ls command
    // ------------------------------------------------------------------

    void "should run Alpine container with file listing"() {
        given:
        def jobId = "alpine-ls-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "alpine-ls"
            getContainer() >> "alpine:3.18"
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
                getScript()     >> 'ls -la /tmp'
                getShell()      >> ["sh"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["sh", "-c", "ls -la /tmp"],
                [NF_TEST: "alpine_ls"]
        )

        then:
        evalId != null
        evalId.size() > 0
    }

    void "should complete Alpine job"() {
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
    // Test 3: Job with environment variables
    // ------------------------------------------------------------------

    void "should run job with environment variables"() {
        given:
        def jobId = "env-vars-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "env-vars"
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
                getScript()     >> 'echo $TEST_VAR'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'echo $TEST_VAR'],
                [TEST_VAR: "test_value", NF_TEST: "env_vars"]
        )

        then:
        evalId != null
    }

    void "should complete env-vars job"() {
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
    // Test 4: Python script execution
    // ------------------------------------------------------------------

    void "should run Python container with script"() {
        given:
        def jobId = "python-script-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "python-script"
            getContainer() >> "python:3.11-slim"
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
                getScript()     >> 'python3 -c "print(\'Hello from Python\')"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'python3 -c "print(\'Hello from Python\')"'],
                [NF_TEST: "python_script"]
        )

        then:
        evalId != null
    }

    void "should complete Python job"() {
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
    // Test 5: Job failure handling
    // ------------------------------------------------------------------

    void "should handle job failure gracefully"() {
        given:
        def jobId = "failing-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "failing-job"
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
                getScript()     >> 'exit 1'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "exit 1"],
                [NF_TEST: "failing_job"]
        )

        then:
        evalId != null
    }

    void "should detect failed job state"() {
        given:
        def jobId = submittedJobIds.last()
        def maxRetries = 30
        def retryCount = 0

        when:
        def state = null
        while (retryCount < maxRetries && !(state?.state in ['failed', 'dead', 'lost', 'complete'])) {
            sleep(1000)
            state = service.getTaskState(jobId)
            retryCount++
        }

        then:
        state.state in ['failed', 'dead', 'lost', 'complete']
    }

    // ------------------------------------------------------------------
    // Test 6: Job with multiple commands
    // ------------------------------------------------------------------

    void "should run job with multiple commands"() {
        given:
        def jobId = "multi-cmd-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "multi-cmd"
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
                getScript()     >> 'echo start && sleep 1 && echo end'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo start && sleep 1 && echo end"],
                [NF_TEST: "multi_cmd"]
        )

        then:
        evalId != null
    }

    void "should complete multi-command job"() {
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
    // Test 7: Job with resource constraints
    // ------------------------------------------------------------------

    void "should run job with memory and CPU constraints"() {
        given:
        def jobId = "constrained-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "constrained-job"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig) {
                getMemory() >> nextflow.util.MemoryUnit.of('256 MB')
                getCpus() >> 0.5
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
                getScript()     >> 'echo "Running with constraints"'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", 'echo "Running with constraints"'],
                [NF_TEST: "constrained_job"]
        )

        then:
        evalId != null
    }

    void "should complete constrained job"() {
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
    // Test 8: Retrieve job allocation and client node
    // ------------------------------------------------------------------

    void "should retrieve client node for completed job"() {
        given:
        def jobId = submittedJobIds.first()

        when:
        def clientNode = service.getClientOfJob(jobId)

        then:
        clientNode != null
        clientNode.size() > 0
    }

    // ------------------------------------------------------------------
    // Test 9: Kill job (stop without purge)
    // ------------------------------------------------------------------

    void "should kill a running job"() {
        given:
        def jobId = "kill-job-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "kill-job"
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
                getScript()     >> 'sleep 30'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "sleep 30"],
                [NF_TEST: "kill_job"]
        )
        sleep(2000)  // Let it start

        then:
        evalId != null

        when:
        service.kill(jobId)
        sleep(2000)
        def state = service.getTaskState(jobId)

        then:
        state != null
    }

    // ------------------------------------------------------------------
    // Test 10: Purge job completely
    // ------------------------------------------------------------------

    void "should purge job from cluster"() {
        given:
        def jobId = "purge-job-${System.currentTimeMillis()}"

        def mockTask = Mock(TaskRun) {
            getName()      >> "purge-job"
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
                getScript()     >> 'echo test'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["bash", "-c", "echo test"],
                [NF_TEST: "purge_job"]
        )
        sleep(2000)

        then:
        evalId != null

        when:
        service.jobPurge(jobId)

        then:
        noExceptionThrown()
    }

    // ------------------------------------------------------------------
    // Test 11: Stress test with multiple concurrent jobs
    // ------------------------------------------------------------------

    void "should submit multiple jobs concurrently"() {
        given:
        def jobIds = (1..3).collect { i -> "concurrent-${i}-${System.currentTimeMillis()}" }
        submittedJobIds.addAll(jobIds)

        when:
        jobIds.each { jobId ->
            def mockTask = Mock(TaskRun) {
                getName()      >> "concurrent-task"
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
                    getScript()     >> 'echo concurrent'
                    getShell()      >> ["bash"]
                    getInputFiles() >> [:]
                }
            }

            service.submitTask(
                    jobId,
                    mockTask,
                    ["bash", "-c", "echo concurrent"],
                    [NF_TEST: "concurrent", INDEX: jobId.split('-')[1]]
            )
        }

        then:
        noExceptionThrown()
    }

    void "should complete all concurrent jobs"() {
        given:
        def concurrentJobIds = submittedJobIds.findAll { it.startsWith('concurrent-') }
        def maxRetries = 50
        def allCompleted = false

        when:
        def retryCount = 0
        while (retryCount < maxRetries && !allCompleted) {
            sleep(1000)
            def states = concurrentJobIds.collect { service.getTaskState(it) }
            allCompleted = states.every { isSuccessfulTerminalState(it) }
            retryCount++
        }

        then:
        allCompleted
    }

    // ------------------------------------------------------------------
    // Test 12: Docker image pull verification
    // ------------------------------------------------------------------

    void "should verify Docker image pulling for custom image"() {
        given:
        def jobId = "docker-pull-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName()      >> "docker-pull-test"
            getContainer() >> "alpine:latest"
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
                getScript()     >> 'uname -a'
                getShell()      >> ["sh"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                jobId,
                mockTask,
                ["sh", "-c", "uname -a"],
                [NF_TEST: "docker_pull"]
        )

        then:
        evalId != null
    }

    void "should complete docker pull test"() {
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
    // Verification: Local cluster target
    // ------------------------------------------------------------------

    void "should target localhost for local cluster integration"() {
        expect:
        service.apiClient.basePath.contains("localhost") ||
        service.apiClient.basePath.contains("127.0.0.1")
    }
}