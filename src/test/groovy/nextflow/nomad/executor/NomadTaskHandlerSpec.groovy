/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 * Copyright 2026-, Incremental Steps Software Solutions OÜ
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
import io.nomadproject.client.model.TaskState
import nextflow.exception.ProcessException

import nextflow.exception.ProcessSubmitException
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.Executor
import nextflow.nomad.config.NomadClientOpts
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.config.NomadJobOpts
import nextflow.processor.*
import nextflow.util.Duration
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit test for Nomad Task Handler
 *
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */

class NomadTaskHandlerSpec extends Specification{

    void "a task should have a container"(){
        given:
        def mockTask = Mock(TaskRun){
            getWorkDir() >> Path.of(".")
            getContainer() >> null
            getProcessor() >> Mock(TaskProcessor)
        }
        def mockConfig = Mock(NomadConfig){
            jobOpts() >> Stub(NomadJobOpts){ driver >> "docker" }
        }
        def mockService = Mock(NomadService)
        def taskHandler = new NomadTaskHandler(mockTask, mockConfig, mockService)

        when:
        taskHandler.submitTask()

        then:
        thrown(ProcessSubmitException)
    }

    void "a task should be created"(){
        given:
        def workDir = Files.createTempDirectory("nf")
        new File(workDir.toFile(), TaskRun.CMD_INFILE).text = "infile"

        def mockTask = Mock(TaskRun){
            getConfig() >> Mock(TaskConfig)
            getWorkDir() >> workDir
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
            }
            toTaskBean() >> Mock(TaskBean) {
                    getWorkDir() >> workDir
                    getScript() >> "theScript"
                    getShell() >> ["bash"]
                    getInputFiles() >> [:]
                    getOutputFiles() >> ['dont_know_why_is_required_in_test']
            }
        }
        def mockConfig = Mock(NomadConfig){
            jobOpts() >> Stub(NomadJobOpts){ driver >> "docker" }
        }
        def mockService = Mock(NomadService)
        def taskHandler = new NomadTaskHandler(mockTask, mockConfig, mockService)

        when:
        def ret = taskHandler.submitTask()

        then:
        ret == TaskStatus.SUBMITTED.name()
    }

    void "should delete only successful jobs when cleanup policy is onSuccess"() {
        given:
        def successTask = taskWithExitStatus(0)
        def failedTask = taskWithExitStatus(1)
        def config = configWithCleanup(NomadJobOpts.CLEANUP_ON_SUCCESS, true)

        def successHandler = new NomadTaskHandler(successTask, config, Mock(NomadService))
        def failedHandler = new NomadTaskHandler(failedTask, config, Mock(NomadService))

        expect:
        successHandler.shouldDelete(new TaskState(failed: false))
        !failedHandler.shouldDelete(new TaskState(failed: false))
        !successHandler.shouldDelete(new TaskState(failed: true))
    }

    void "should honor always and never cleanup policies"() {
        given:
        def task = taskWithExitStatus(0)
        def alwaysHandler = new NomadTaskHandler(task, configWithCleanup(NomadJobOpts.CLEANUP_ALWAYS, false), Mock(NomadService))
        def neverHandler = new NomadTaskHandler(task, configWithCleanup(NomadJobOpts.CLEANUP_NEVER, true), Mock(NomadService))

        expect:
        alwaysHandler.shouldDelete(new TaskState(failed: true))
        !neverHandler.shouldDelete(new TaskState(failed: false))
    }

    void "should detect out-of-memory failures from task events"() {
        given:
        def task = taskWithExitStatus(137)
        def handler = new NomadTaskHandler(task, configWithCleanup(NomadJobOpts.CLEANUP_ALWAYS, true), Mock(NomadService))
        def state = new TaskState(
                failed: true,
                state: 'failed',
                events: [[displayMessage: 'Allocation was OOM killed after memory limit was exceeded']]
        )

        expect:
        handler.isOutOfMemoryFailure(state, 137)
        handler.failureMessage(state, 137).contains('out-of-memory condition')
    }

    void "should create generic failure message when no memory signal exists"() {
        given:
        def task = taskWithExitStatus(2)
        def handler = new NomadTaskHandler(task, configWithCleanup(NomadJobOpts.CLEANUP_ALWAYS, true), Mock(NomadService))
        def state = new TaskState(
                failed: true,
                state: 'dead',
                events: [[displayMessage: 'Task exited with non-zero status']]
        )

        when:
        def message = handler.failureMessage(state, 2)

        then:
        !handler.isOutOfMemoryFailure(state, 2)
        message.contains('Nomad state dead')
        message.contains('exit status 2')
        message.contains('Task exited with non-zero status')
    }

    void "should load nomad allocation metadata into handler fields"() {
        given:
        def task = taskWithExitStatus(0)
        def config = configWithCleanup(NomadJobOpts.CLEANUP_ALWAYS, true)
        def service = Mock(NomadService) {
            getAllocationMetadata('job-123') >> [
                    allocationId: 'alloc-789',
                    nodeId: 'node-456',
                    nodeName: 'worker-a',
                    datacenter: 'dc-west'
            ]
        }
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-123')

        when:
        invokePrivateMethod(handler, 'determineClientNode')

        then:
        getPrivateField(handler, 'clientName') == 'worker-a'
        getPrivateField(handler, 'allocationId') == 'alloc-789'
        getPrivateField(handler, 'nodeId') == 'node-456'
        getPrivateField(handler, 'datacenter') == 'dc-west'
    }

    void "should include nomad identifiers and allocation api hint in failure message"() {
        given:
        def task = taskWithExitStatus(2)
        def config = configWithCleanupAndAddress(NomadJobOpts.CLEANUP_ALWAYS, true, 'http://nomad.example/v1')
        def service = Mock(NomadService) {
            getAllocationMetadata('job-123') >> [
                    allocationId: 'alloc-789',
                    nodeId: 'node-456',
                    nodeName: 'worker-a',
                    datacenter: 'dc-west'
            ]
        }
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-123')
        def state = new TaskState(
                failed: true,
                state: 'dead',
                events: [[displayMessage: 'Task exited with non-zero status']]
        )

        when:
        def message = handler.failureMessage(state, 2)

        then:
        message.contains("job 'job-123'")
        message.contains("allocation 'alloc-789'")
        message.contains("node 'worker-a'")
        message.contains("datacenter 'dc-west'")
        message.contains('Allocation API:')
        message.contains('/allocation/alloc-789')
    }


    void "should write nomad metadata to debug dump when enabled"() {
        given:
        Path workDir = Path.of('/tmp/nf-debug-work')
        def task = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
        }
        def debugCfg = new NomadConfig.NomadDebug([json: true, path: 'debug/job-spec.json'])
        def config = Mock(NomadConfig) {
            debug() >> debugCfg
        }
        def service = Mock(NomadService) {
            getAllocationMetadata('job-123') >> [
                    allocationId: 'alloc-789',
                    nodeId: 'node-456',
                    nodeName: 'worker-a',
                    datacenter: 'dc-west'
            ]
        }
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-123')

        when:
        invokePrivateMethod(handler, 'determineClientNode')

        then:
        1 * service.writeDebugMetadata(workDir.resolve('debug/job-spec.json'), {
            Map payload ->
                payload.nomad_job_id == 'job-123' &&
                        payload.nomad_alloc_id == 'alloc-789' &&
                        payload.nomad_node_id == 'node-456' &&
                        payload.nomad_node_name == 'worker-a' &&
                        payload.nomad_datacenter == 'dc-west'
        })
    }

    void "should respect configured poll interval when fetching task state"() {
        given:
        def opts = Stub(NomadClientOpts) {
            getPollInterval() >> Duration.of('10s')
        }
        def config = Mock(NomadConfig) {
            clientOpts() >> opts
        }
        def service = Mock(NomadService) {
            1 * getTaskState('job-123') >> new TaskState(state: 'running', failed: false)
        }
        def handler = new NomadTaskHandler(taskWithExitStatus(0), config, service)
        setPrivateField(handler, 'jobName', 'job-123')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        handler.taskState0()
        handler.taskState0()

        then:
        noExceptionThrown()
    }

    void "should use recoverable process exception for failed task completion"() {
        given:
        Throwable assignedError = null
        Boolean assignedAborted = null
        Integer assignedExit = null
        def task = Mock(TaskRun) {
            getWorkDir() >> Path.of('.')
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setAborted(_ as Boolean) >> { Boolean value -> assignedAborted = value }
            getAborted() >> { assignedAborted ?: false }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-failed', _ as Long) >> false
            getTaskState('job-failed') >> new TaskState(state: 'failed', failed: true, events: [[displayMessage: 'boom']])
        }
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-failed')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedError instanceof ProcessException
        !(assignedError instanceof ProcessUnrecoverableException)
        !assignedAborted
    }

    void "should resolve debug dump path relative to task workDir"() {
        given:
        Path workDir = Path.of('/tmp/nf-debug-work')
        def task = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
        }
        def debugCfg = new NomadConfig.NomadDebug([json: true, path: 'debug/job-spec.json'])
        def config = Mock(NomadConfig) {
            debug() >> debugCfg
        }
        def handler = new NomadTaskHandler(task, config, Mock(NomadService))

        expect:
        handler.debugPath() == workDir.resolve('debug/job-spec.json')
    }

    void "should keep absolute debug dump path unchanged"() {
        given:
        Path workDir = Path.of('/tmp/nf-debug-work')
        Path dumpPath = Path.of('/tmp/nomad-debug/job.json')
        def task = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
        }
        def debugCfg = new NomadConfig.NomadDebug([path: dumpPath.toString()])
        def config = Mock(NomadConfig) {
            debug() >> debugCfg
        }
        def handler = new NomadTaskHandler(task, config, Mock(NomadService))

        expect:
        handler.debugPath() == dumpPath
    }

    void "should use recoverable process exception for placement failure"() {
        given:
        Throwable assignedError = null
        Boolean assignedAborted = null
        Integer assignedExit = null
        def task = Mock(TaskRun) {
            getWorkDir() >> Path.of('.')
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setAborted(_ as Boolean) >> { Boolean value -> assignedAborted = value }
            getAborted() >> { assignedAborted ?: false }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-placement', _ as Long) >> true
            getTaskState('job-placement') >> new TaskState(state: 'pending', failed: false)
        }
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-placement')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedExit == 1
        assignedError instanceof ProcessException
        !(assignedError instanceof ProcessUnrecoverableException)
        !assignedAborted
    }

    void "trusts remote .exitcode=0 over Nomad alloc-state failure (transient first-attempt 127 → retry pushes back 0)"() {
        // Reproduces the MULTIQC scenario: Nomad reports the alloc as failed
        // (Exit Code 127 from a transient first attempt), but the workdir
        // provider successfully synced back .exitcode=0 from a later retry
        // that did the actual work and pushed back the outputs.
        given:
        Throwable assignedError = null
        Integer assignedExit = null
        def task = Mock(TaskRun) {
            getName() >> 'multiqc-recovered'
            getWorkDir() >> Path.of('.')
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-multiqc', _ as Long) >> false
            // Nomad sees the alloc as dead+failed (the historical 127 attempt)
            getTaskState('job-multiqc') >> new TaskState(state: 'dead', failed: true)
        }
        // synchronizeWorkdirCompletion returns 0 — the worker actually succeeded
        // and pushed back a clean .exitcode despite Nomad's view
        def handler = new TestNomadTaskHandler(task, config, service) {
            @Override protected Integer synchronizeWorkdirCompletion() { 0 }
        }
        setPrivateField(handler, 'jobName', 'job-multiqc')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedExit == 0
        // No error — trust the remote .exitcode over Nomad's failure flag
        assignedError == null
    }

    void "still surfaces error when remote .exitcode is non-zero (real task failure)"() {
        // The reconciliation rule must NOT mask genuine task failures —
        // only suppress when the worker pushed back a clean 0.
        given:
        Throwable assignedError = null
        Integer assignedExit = null
        def task = Mock(TaskRun) {
            getName() >> 'failing-task'
            getWorkDir() >> Path.of('.')
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-failing', _ as Long) >> false
            getTaskState('job-failing') >> new TaskState(state: 'dead', failed: true)
        }
        def handler = new TestNomadTaskHandler(task, config, service) {
            @Override protected Integer synchronizeWorkdirCompletion() { 17 }   // non-zero
        }
        setPrivateField(handler, 'jobName', 'job-failing')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedExit == 17
        assignedError instanceof ProcessException     // genuine failure surfaces
    }

    void "trusts local .exitcode=0 over Nomad alloc-state failure (vanilla shared-FS path, no SPI provider)"() {
        // Reproduces the nextflow-io/hello scenario on a non-SPI cluster: the
        // worker writes `.exitcode = 0` (its bash actually succeeded) but the
        // Nomad alloc lifecycle still ends with a docker exit 1 (post-success
        // cleanup, idle reaper, etc). Without this trust rule the executor
        // raises a ProcessException even though the user's command succeeded.
        given:
        Throwable assignedError = null
        Integer assignedExit = null
        def workDir = Files.createTempDirectory("nf-vanilla-trust")
        def exitFile = workDir.resolve(TaskRun.CMD_EXIT)
        exitFile.text = "0\n"   // worker's actual exit code
        def task = Mock(TaskRun) {
            getName() >> 'sayHello-vanilla'
            getWorkDir() >> workDir
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-vanilla', _ as Long) >> false
            // Nomad sees the alloc as dead+failed (docker exit 1 from cleanup)
            getTaskState('job-vanilla') >> new TaskState(state: 'dead', failed: true)
        }
        // Plain NomadTaskHandler — no SPI override; isWorkdirProviderActive
        // is false, so the vanilla shared-FS path runs.
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-vanilla')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedExit == 0
        assignedError == null   // alloc-state failure suppressed by local .exitcode=0
    }

    void "still surfaces error on vanilla path when local .exitcode is non-zero"() {
        // Vanilla path mirror of the SPI non-zero test: a real failure must
        // still raise even when the local exit-file is readable.
        given:
        Throwable assignedError = null
        Integer assignedExit = null
        def workDir = Files.createTempDirectory("nf-vanilla-fail")
        workDir.resolve(TaskRun.CMD_EXIT).text = "23\n"
        def task = Mock(TaskRun) {
            getName() >> 'sayHello-failed'
            getWorkDir() >> workDir
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-vanilla-fail', _ as Long) >> false
            getTaskState('job-vanilla-fail') >> new TaskState(state: 'dead', failed: true)
        }
        def handler = new NomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-vanilla-fail')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedExit == 23
        assignedError instanceof ProcessException   // non-zero local exit → real failure
    }

    void "should fail completed nomad task when SPI provider's remote exitcode is missing"() {
        given:
        Throwable assignedError = null
        Integer assignedExit = null
        def task = Mock(TaskRun) {
            getName() >> 'spi-missing-exit'
            getWorkDir() >> Path.of('.')
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            setError(_ as Throwable) >> { Throwable value -> assignedError = value }
            getError() >> { assignedError }
            setExitStatus(_ as Integer) >> { Integer value -> assignedExit = value }
            getExitStatus() >> { assignedExit }
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def service = Mock(NomadService) {
            isPlacementFailure('job-spi-no-exit', _ as Long) >> false
            getTaskState('job-spi-no-exit') >> new TaskState(state: 'complete', failed: false)
        }
        def handler = new TestNomadTaskHandler(task, config, service)
        setPrivateField(handler, 'jobName', 'job-spi-no-exit')
        setPrivateField(handler, 'status', TaskStatus.SUBMITTED)

        when:
        def completed = handler.checkIfCompleted()

        then:
        completed
        assignedExit == Integer.MAX_VALUE
        assignedError instanceof ProcessException
        assignedError.message.contains('did not produce a readable remote .exitcode')
        assignedError.message.contains('minio:work/run/hash/.exitcode')
    }

    private TaskRun taskWithExitStatus(int exitStatus) {
        Mock(TaskRun) {
            getWorkDir() >> Path.of('.')
            getExitStatus() >> exitStatus
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
        }
    }

    private NomadConfig configWithCleanup(String cleanup, boolean deleteOnCompletion) {
        def opts = Stub(NomadJobOpts) {
            getCleanup() >> cleanup
            getDeleteOnCompletion() >> deleteOnCompletion
        }
        Mock(NomadConfig) {
            jobOpts() >> opts
        }
    }

    private NomadConfig configWithCleanupAndAddress(String cleanup, boolean deleteOnCompletion, String address) {
        def opts = Stub(NomadJobOpts) {
            getCleanup() >> cleanup
            getDeleteOnCompletion() >> deleteOnCompletion
        }
        Mock(NomadConfig) {
            jobOpts() >> opts
            clientOpts() >> [address: address]
        }
    }

    private static void setPrivateField(Object target, String field, Object value) {
        def f = findField(target.class, field)
        f.setAccessible(true)
        f.set(target, value)
    }

    private static Object getPrivateField(Object target, String field) {
        def f = findField(target.class, field)
        f.setAccessible(true)
        return f.get(target)
    }

    private static Object invokePrivateMethod(Object target, String methodName) {
        def m = target.class.getDeclaredMethod(methodName)
        m.setAccessible(true)
        return m.invoke(target)
    }

    private static java.lang.reflect.Field findField(Class type, String field) {
        Class current = type
        while( current ) {
            try {
                return current.getDeclaredField(field)
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass()
            }
        }
        throw new NoSuchFieldException(field)
    }

    void "should fallback to task event exit code when exit file is empty"() {
        given:
        def workDir = Files.createTempDirectory("nf")
        def exitFile = workDir.resolve(TaskRun.CMD_EXIT)
        exitFile.text = "   " // empty/blank exit file

        def task = Mock(TaskRun) {
            getWorkDir() >> workDir
            getConfig() >> [tag: null]
            getProcessor() >> Mock(TaskProcessor)
            getName() >> "test_task"
        }
        def config = configWithCleanup(NomadJobOpts.CLEANUP_NEVER, false)
        def handler = new NomadTaskHandler(task, config, Mock(NomadService))

        // create state with exitCode in events
        def state = new TaskState(
            state: 'dead',
            events: [
                [type: 'Terminated', exitCode: 143, displayMessage: 'OOM Killed']
            ]
        )
        setPrivateField(handler, 'state', state)

        when:
        int exitStatus = handler.defineExitCode()

        then:
        exitStatus == 143
    }

    private static class TestNomadTaskHandler extends NomadTaskHandler {

        TestNomadTaskHandler(TaskRun task, NomadConfig config, NomadService service) {
            super(task, config, service)
        }

        @Override
        protected boolean isWorkdirProviderActive() {
            return true
        }

        @Override
        protected Integer synchronizeWorkdirCompletion() {
            return null
        }

        @Override
        protected String workdirRemoteExitHint() {
            return 'minio:work/run/hash/.exitcode'
        }
    }
}
