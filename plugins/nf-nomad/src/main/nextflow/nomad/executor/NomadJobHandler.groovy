/*
 * Copyright 2013-2023, Seqera Labs
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
import nextflow.cloud.types.CloudMachineInfo
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.fusion.FusionAwareTask

//import nextflow.nomad.client.JobUnschedulableException

import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.trace.TraceRecord

import java.nio.file.Path

/**
 * Implements the {@link TaskHandler} interface for Nomad jobs
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
class NomadJobHandler extends TaskHandler implements FusionAwareTask {

    NomadExecutor executor

    private Path exitFile

    private Path outputFile

    private Path errorFile

    private volatile long timestamp

    private volatile TaskStatus taskStatus

    private volatile String taskKey

    NomadJobHandler(TaskRun task, NomadExecutor executor) {
        super(task)
        this.executor = executor
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        validateConfiguration()
    }

    /** only for testing purpose - DO NOT USE */
    protected NomadJobHandler() { }

    NomadService getNomadService() {
        return executor.service
    }

    void validateConfiguration() {
        if (!task.container) {
            throw new ProcessUnrecoverableException("No container image specified for process $task.name -- Either specify the container to use in the process definition or with 'process.container' value in your config")
        }
    }

    protected BashWrapperBuilder createBashWrapper() {
        fusionEnabled()
                ? fusionLauncher()
                : new NomadScriptLauncher(task.toTaskBean(), executor)
    }

    @Override
    void submit() {
        log.debug "[NOMAD] Submitting task $task.name - work-dir=${task.workDirStr}"
        createBashWrapper().build()
        // submit the task execution
        this.taskKey = nomadService.jobSubmit(task)
        log.debug "[NOMAD] Submitted task $task.name with taskId=$taskKey"
        // update the status
        this.status = TaskStatus.SUBMITTED
    }

    @Override
    boolean checkIfRunning() {

        return false

//        if( !taskKey || !isSubmitted() )
//            return false
//        final state = taskState0(taskKey)
//        // note, include complete status otherwise it hangs if the task
//        // completes before reaching this check
//        final running = state==TaskStatus.RUNNING || state==TaskStatus.COMPLETED
//        log.debug "[NOMAD] Task status $task.name taskId=$taskKey; running=$running"
//        if( running )
//            this.status = TaskStatus.RUNNING
//        return running
    }

    @Override
    boolean checkIfCompleted() {
//        assert taskKey
//        if( !isRunning() )
//            return false
//        final done = taskState0(taskKey)==TaskStatus.COMPLETED
//        if( done ) {
//            // finalize the task
//            task.exitStatus = readExitFile()
//            task.stdout = outputFile
//            task.stderr = errorFile
//            status = TaskStatus.COMPLETED
//            TaskExecutionInformation info = nomadService.jobSummary(taskKey).executionInfo()
//            if (info.result() == TaskExecutionResult.FAILURE)
//                task.error = new ProcessUnrecoverableException(info.failureInfo().message())
//            nomadService.jobPurge(taskKey, task)
//            return true
//        }
        return false
    }

    private Boolean shouldDelete() {
        executor.config.client().deleteJobsOnCompletion
    }

//    protected void deleteTask(AzTaskKey taskKey, TaskRun task) {
//        if( !taskKey || shouldDelete()==Boolean.FALSE )
//            return
//
//        if( !task.isSuccess() && shouldDelete()==null ) {
//            // preserve failed tasks for debugging purposes, unless deletion is explicitly enabled
//            return
//        }
//
//        try {
//            nomadService.jobPurge(taskKey)
//        }
//        catch( Exception e ) {
//            log.warn "Unable to cleanup batch task: $taskKey -- see the log file for details", e
//        }
//    }

//    /**
//     * @return Retrieve the task status caching the result for at lest one second
//     */
//    protected TaskState taskState0(String key) {
//        final now = System.currentTimeMillis()
//        final delta =  now - timestamp;
//        if( !taskStatus || delta >= 1_000) {
//            def newState = nomadService.jobStatus(key).state()
//            log.trace "[AZURE BATCH] Task: $key state=$newState"
//            if( newState ) {
//                taskStatus = newState as TaskStatus
//                timestamp = now
//            }
//        }
//        return taskStatus
//    }

    protected int readExitFile() {
        try {
            exitFile.text as Integer
        }
        catch( Exception e ) {
            log.debug "[NOMAD] Cannot read exit status for task: `$task.name` | ${e.message}"
            return Integer.MAX_VALUE
        }
    }

    @Override
    void kill() {
        if( !taskKey )
            return
        nomadService.jobPurge(taskKey)
    }

    @Override
    TraceRecord getTraceRecord() {
        def result = super.getTraceRecord()
        return result
    }

}
