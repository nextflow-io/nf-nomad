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

import io.nomadproject.client.ApiException
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
 * Local integration tests for job submission and configuration.
 *
 * Converted from MockNomadServiceSpec to test actual job submissions against
 * a local Nomad cluster. Tests job structure, configuration, and basic execution.
 *
 * Activated when NF_NOMAD_TEST_ENV is 'local'.
 *
 * Run with:
 *   make test-local
 *   ./gradlew test -PtestEnv=local --tests LocalJobSubmissionSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalJobSubmissionSpec extends Specification {

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
        testWorkDir = Files.createTempDirectory("nf-job-submission-test")
    }

    def cleanupSpec() {
        submittedJobIds.each { jobId ->
            try { service.jobPurge(jobId) } catch (ignored) {}
        }
        service?.close()
        testWorkDir?.deleteDir()
    }

    // Helper method to retrieve job from Nomad
    protected def getJobFromNomad(String jobId) {
        try {
            def job = jobsApi.getJob(jobId, config.jobOpts().region,
                config.jobOpts().namespace, null, null, null, null, null, null, null)
            return job
        } catch (ApiException ignored) {
            return null
        }
    }

    // Helper method to wait for job state (used in future tests)
    @SuppressWarnings('unused')
    protected void waitForJobState(String jobId, String expectedState, int maxRetries = 30) {
        def retryCount = 0
        while (retryCount < maxRetries) {
            def state = service.getTaskState(jobId)
            if (state?.state == expectedState) {
                return
            }
            sleep(1000)
            retryCount++
        }
    }

    // ------------------------------------------------------------------
    // Test 1: Basic Job Submission
    // Converted from: MockNomadServiceSpec "submit a task"
    // ------------------------------------------------------------------

    void "should submit basic job and verify configuration"() {
        given: "A basic job configuration"
        def jobId = "basic-submit-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def name = "basic-task"
        def image = "ubuntu:22.04"
        def args = ["bash", "-c", "echo hello"]
        def env = [TEST_VAR: "test_value"]

        def mockTask = Mock(TaskRun) {
            getName() >> name
            getContainer() >> image
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
                getScript() >> "echo hello"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when: "Job is submitted to Nomad"
        def evalId = service.submitTask(jobId, mockTask, args, env)

        then: "Evaluation ID is returned"
        evalId != null
        evalId.size() > 0

        when: "Job is retrieved from Nomad"
        sleep(1000) // Give Nomad time to register job
        def job = getJobFromNomad(jobId)

        then: "Job configuration matches expected values"
        job != null
        job.ID == jobId
        job.name == name
        job.type == "batch"
        job.taskGroups != null
        job.taskGroups.size() == 1
        job.taskGroups[0].name == "nf-taskgroup"
        job.taskGroups[0].tasks.size() == 1
        job.taskGroups[0].tasks[0].name == "nf-task"
        job.taskGroups[0].tasks[0].driver == "docker"
        job.taskGroups[0].tasks[0].config.image == image
    }

    // ------------------------------------------------------------------
    // Test 2: Resource Allocation Validation
    // ------------------------------------------------------------------

    void "should validate default resource allocation"() {
        given: "A job with default resources"
        def jobId = "resources-default-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName() >> "resource-task"
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

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "echo test"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job configuration is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Default resources are allocated"
        job != null
        job.taskGroups[0].tasks[0].resources.cores == 1
        job.taskGroups[0].tasks[0].resources.memoryMB == 1024
    }

    // ------------------------------------------------------------------
    // Test 3: Custom Resource Allocation
    // ------------------------------------------------------------------

    void "should apply custom resource allocation"() {
        given: "A job with custom CPU and memory"
        def jobId = "resources-custom-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName() >> "custom-resource-task"
            getContainer() >> "ubuntu:22.04"
            getConfig() >> Mock(TaskConfig) {
                // JobBuilder casts cpus to Integer, so provide a numeric value compatible with that cast
                get("cpus") >> 2.0
                get("memory") >> '2 GB'
            }
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

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "echo test"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job configuration is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Custom resources are allocated"
        job != null
        job.taskGroups[0].tasks[0].resources.cores == 2
        job.taskGroups[0].tasks[0].resources.memoryMB == 2048
    }

    // ------------------------------------------------------------------
    // Test 4: Environment Variables
    // ------------------------------------------------------------------

    void "should pass environment variables to job"() {
        given: "A job with environment variables"
        def jobId = "env-vars-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def env = [
            VAR1: "value1",
            VAR2: "value2",
            PATH: "/custom/path"
        ]

        def mockTask = Mock(TaskRun) {
            getName() >> "env-task"
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
                getScript() >> "env"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when: "Job is submitted with environment variables"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "env"], env)

        then: "Submission succeeds"
        evalId != null

        when: "Job configuration is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Environment variables are configured"
        job != null
        job.taskGroups[0].tasks[0].env != null
        job.taskGroups[0].tasks[0].env.VAR1 == "value1"
        job.taskGroups[0].tasks[0].env.VAR2 == "value2"
    }

    // ------------------------------------------------------------------
    // Test 5: Working Directory Configuration
    // ------------------------------------------------------------------

    void "should configure working directory correctly"() {
        given: "A job with custom working directory"
        def jobId = "workdir-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def workDir = "/custom/work/dir"

        def mockTask = Mock(TaskRun) {
            getName() >> "workdir-task"
            getContainer() >> "ubuntu:22.04"
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> workDir
            getWorkDir() >> Path.of(workDir)
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            toTaskBean() >> Mock(TaskBean) {
                getWorkDir() >> Path.of(workDir)
                getScript() >> "pwd"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "pwd"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job configuration is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Working directory is configured"
        job != null
        job.taskGroups[0].tasks[0].config.work_dir == workDir
    }

    // ------------------------------------------------------------------
    // Test 6: Command and Args Handling
    // ------------------------------------------------------------------

    void "should handle command and arguments correctly"() {
        given: "A job with specific command and args"
        def jobId = "command-args-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def command = "bash"
        def cmdArgs = ["-c", "echo 'test command'"]

        def mockTask = Mock(TaskRun) {
            getName() >> "command-task"
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
                getScript() >> "echo 'test command'"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when: "Job is submitted"
        def fullArgs = [command] + cmdArgs
        def evalId = service.submitTask(jobId, mockTask, fullArgs, [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job configuration is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Command and args are configured"
        job != null
        job.taskGroups[0].tasks[0].config.command == command
        job.taskGroups[0].tasks[0].config.args == cmdArgs
    }

    // ------------------------------------------------------------------
    // Test 7: Job Naming and ID Assignment
    // ------------------------------------------------------------------

    void "should assign correct job ID and name"() {
        given: "A job with specific ID and name"
        def jobId = "custom-id-${System.currentTimeMillis()}"
        def jobName = "custom-job-name"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName() >> jobName
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

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "echo test"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Job ID and name match"
        job != null
        job.ID == jobId
        job.name == jobName
    }

    // ------------------------------------------------------------------
    // Test 8: Task Group Structure Validation
    // ------------------------------------------------------------------

    void "should create correct task group structure"() {
        given: "A basic job"
        def jobId = "taskgroup-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName() >> "taskgroup-task"
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

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "echo test"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job structure is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Task group structure is correct"
        job != null
        job.taskGroups.size() == 1

        def taskGroup = job.taskGroups[0]
        taskGroup.name == "nf-taskgroup"
        taskGroup.count == 1
        taskGroup.tasks.size() == 1

        def task = taskGroup.tasks[0]
        task.name == "nf-task"
        task.driver == "docker"
    }

    // ------------------------------------------------------------------
    // Test 9: Docker Driver Configuration
    // ------------------------------------------------------------------

    void "should configure Docker driver correctly"() {
        given: "A job using Docker driver"
        def jobId = "docker-driver-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def image = "alpine:3.18"

        def mockTask = Mock(TaskRun) {
            getName() >> "docker-task"
            getContainer() >> image
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
                getShell() >> ["sh"]
                getInputFiles() >> [:]
            }
        }

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["sh", "-c", "echo test"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job configuration is retrieved"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Docker configuration is correct"
        job != null

        def task = job.taskGroups[0].tasks[0]
        task.driver == "docker"
        task.config.image == image
        task.config.containsKey('work_dir')
        task.config.containsKey('command')
    }

    // ------------------------------------------------------------------
    // Test 10: Debug JSON Output
    // Converted from: MockNomadServiceSpec "save the job spec if requested"
    // ------------------------------------------------------------------

    void "should save job JSON when debug enabled"() {
        given: "A job with debug JSON output enabled"
        def jobId = "debug-json-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def debugConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [deleteOnCompletion: false]
        ])
        def debugService = new NomadService(debugConfig)

        def outputJson = Files.createTempFile(testWorkDir, "job-", ".json")

        def mockTask = Mock(TaskRun) {
            getName() >> "debug-task"
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

        when: "Job is submitted with JSON output path"
        def evalId = debugService.submitTask(jobId, mockTask,
            ["bash", "-c", "echo test"], [:], outputJson)

        then: "Submission succeeds"
        evalId != null

        and: "JSON file is created"
        outputJson.toFile().exists()
        outputJson.toFile().size() > 0

        and: "JSON contains valid job structure"
        def jsonContent = outputJson.text
        jsonContent.contains("Job {")
        jsonContent.contains("ID:")
        jsonContent.contains("taskGroups:") || jsonContent.contains("TaskGroups:")

        cleanup:
        debugService.jobPurge(jobId)
        debugService.close()
        outputJson.toFile().delete()
    }

    // ------------------------------------------------------------------
    // Test 11: Job Type Validation
    // ------------------------------------------------------------------

    void "should create batch type jobs"() {
        given: "A standard Nextflow job"
        def jobId = "batch-type-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName() >> "batch-task"
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

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask, ["bash", "-c", "echo test"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Job type is checked"
        sleep(1000)
        def job = getJobFromNomad(jobId)

        then: "Job is batch type"
        job != null
        job.type == "batch"
    }

    // ------------------------------------------------------------------
    // Test 12: Job Execution Verification
    // ------------------------------------------------------------------

    void "should successfully execute simple job"() {
        given: "A simple executable job"
        def jobId = "execute-simple-${System.currentTimeMillis()}"
        submittedJobIds.add(jobId)

        def mockTask = Mock(TaskRun) {
            getName() >> "execute-task"
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
                getScript() >> "echo 'execution test'"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when: "Job is submitted"
        def evalId = service.submitTask(jobId, mockTask,
            ["bash", "-c", "echo 'execution test'"], [:])

        then: "Submission succeeds"
        evalId != null

        when: "Wait for job to complete"
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

        then: "Job completes successfully"
        state != null
        state.state in ['complete', 'dead']
    }
}

