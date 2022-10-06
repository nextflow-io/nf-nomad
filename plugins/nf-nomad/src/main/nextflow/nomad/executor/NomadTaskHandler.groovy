/*
 * Copyright 2022, Center for Medical Genetics, Ghent
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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.nomadproject.client.models.TaskState
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.processor.TaskBean
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.trace.TraceRecord

import java.nio.file.Path


/**
 * Implements a task handler for Nomad executor
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@Slf4j
@CompileStatic
class NomadTaskHandler extends TaskHandler {

    NomadExecutor executor

    private TaskBean taskBean

    private Path exitFile

    private Path outputFile

    private Path errorFile

    private volatile long timestamp

    private volatile TaskState taskState

    NomadTaskHandler(TaskRun task, NomadExecutor executor) {
        super(task)
        this.executor = executor
        this.taskBean = new TaskBean(task)
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        validateConfiguration()
    }

    /** only for testing purpose - DO NOT USE */
    protected NomadTaskHandler() {}

    NomadExecutor getBatchService() {
        return executor.nomadExecutor
    }

    void validateConfiguration() {
        if (!task.container) {
            throw new ProcessUnrecoverableException("No container image specified for process $task.name -- Either specify the container to use in the process definition or with 'process.container' value in your config")
        }
    }

    protected BashWrapperBuilder createBashWrapper() {
        new NomadScriptLauncher(taskBean, executor)
    }

    @Override
    void submit() {
        log.debug "[NOMAD BATCH] Submitting task $task.name - work-dir=${task.workDirStr}"
        createBashWrapper().build()
        // submit the task execution
        this.taskKey = batchService.submitTask(task)
        log.debug "[NOMAD BATCH] Submitted task $task.name with taskId=$taskKey"
        // update the status
        this.status = TaskStatus.SUBMITTED
    }

    @Override
    boolean checkIfRunning() {
        if (!taskKey || !isSubmitted())
            return false
        final state = taskState0(taskKey)
        // note, include complete status otherwise it hangs if the task
        // completes before reaching this check
        final running = state == TaskState.RUNNING || state == TaskState.COMPLETED
        log.debug "[NOMAD BATCH] Task status $task.name taskId=$taskKey; running=$running"
        if (running)
            this.status = TaskStatus.RUNNING
        return running
    }

    @Override
    boolean checkIfCompleted() {
        assert taskKey
        if (!isRunning())
            return false
        final done = taskState0(taskKey) == TaskState.COMPLETED
        if (done) {
            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            TaskExecutionInformation info = batchService.getTask(taskKey).executionInfo()
            if (info.result() == TaskExecutionResult.FAILURE)
                task.error = new ProcessUnrecoverableException(info.failureInfo().message())
            deleteTask(taskKey, task)
            return true
        }
        return false
    }

    private Boolean shouldDelete() {
        executor.config.batch().deleteJobsOnCompletion
    }

    protected void deleteTask(AzTaskKey taskKey, TaskRun task) {
        if (!taskKey || shouldDelete() == Boolean.FALSE)
            return

        if (!task.isSuccess() && shouldDelete() == null) {
            // do not delete successfully executed pods for debugging purpose
            return
        }

        try {
            batchService.deleteTask(taskKey)
        }
        catch (Exception e) {
            log.warn "Unable to cleanup batch task: $taskKey -- see the log file for details", e
        }
    }

    /**
     * @return Retrieve the task status caching the result for at lest one second
     */
    protected TaskState taskState0(AzTaskKey key) {
        final now = System.currentTimeMillis()
        final delta = now - timestamp;
        if (!taskState || delta >= 1_000) {
            def newState = batchService.getTask(key).state()
            log.trace "[NOMAD BATCH] Task: $key state=$newState"
            if (newState) {
                taskState = newState
                timestamp = now
            }
        }
        return taskState
    }

    protected int readExitFile() {
        try {
            exitFile.text as Integer
        }
        catch (Exception e) {
            log.debug "[NOMAD BATCH] Cannot read exit status for task: `$task.name` | ${e.message}"
            return Integer.MAX_VALUE
        }
    }

    @Override
    void kill() {
        if (!taskKey)
            return
        batchService.terminate(taskKey)
    }

    @Override
    TraceRecord getTraceRecord() {
        def result = super.getTraceRecord()
        if (taskKey) {
            result.put('native_id', taskKey.keyPair())
            result.machineInfo = getMachineInfo()
        }
        return result
    }


}
