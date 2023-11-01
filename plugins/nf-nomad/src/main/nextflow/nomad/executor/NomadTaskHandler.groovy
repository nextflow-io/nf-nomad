
package nextflow.nomad.executor


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.fusion.FusionAwareTask
import nextflow.nomad.NomadHelper
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.trace.TraceRecord
import nextflow.util.Escape

import java.nio.file.Path

/**
 * Implements the {@link TaskHandler} interface for Nomad jobs
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
class NomadTaskHandler extends TaskHandler implements FusionAwareTask {

    private NomadExecutor executor

    private Path exitFile

    private Path outputFile

    private Path errorFile

    private long timestamp

    private TaskStatus status

    private String jobName

    NomadTaskHandler(TaskRun task, NomadExecutor executor) {
        super(task)
        this.executor = executor
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        validateConfiguration()
    }

    /** only for testing purpose - DO NOT USE */
    protected NomadTaskHandler() {}

    NomadService getService() {
        return executor.service
    }


    void validateConfiguration() {
        if (!task.container) {
            throw new ProcessUnrecoverableException("[NOMAD] No container image specified for process $task.name -- Either specify the container to use in the process definition or with 'process.container' value in your config")
        }
    }

    protected BashWrapperBuilder createBashWrapper() {
        fusionEnabled()
            ? fusionLauncher()
            : new NomadScriptLauncher(task.toTaskBean())
    }

    protected List<String> classicSubmitCli(TaskRun task) {
        final result = new ArrayList(BashWrapperBuilder.BASH)
        result.add("${Escape.path(task.workDir)}/${TaskRun.CMD_RUN}".toString())
        return result
    }

    protected List<String> getSubmitCommand(TaskRun task) {
        return fusionEnabled()
            ? fusionSubmitCli()
            : classicSubmitCli(task)
    }


    @Override
    void submit() {
        log.debug "[NOMAD] Submitting task ${task.name} - work-dir=${task.workDirStr}"
        createBashWrapper().build()

        this.jobName = NomadHelper.sanitizeName(task.name + "-" + task.hash)

        submitTask(task, jobName)

        // submit the task execution
        log.debug "[NOMAD] Submitted task ${task.name} with taskId=${this.jobName}"
        // update the status
        this.status = TaskStatus.SUBMITTED
    }

    String submitTask(TaskRun task, String jobName) {
        executor.service.jobSubmit(task, jobName)
    }

    @Override
    boolean checkIfRunning() {
        if (!this.jobName || !isSubmitted())
            return false
        final state = taskState0(this.jobName)
        // note, include complete status otherwise it hangs if the task
        // completes before reaching this check
        final running = state == TaskStatus.RUNNING || state == TaskStatus.SUBMITTED
        log.debug "[NOMAD] Task status $task.name taskId=${this.jobName}; running=$running"
        if (running)
            this.status = TaskStatus.RUNNING
        return running
    }

    @Override
    boolean checkIfCompleted() {
        assert this.jobName
        if (!isRunning())
            return false
        final completed = executor.service.jobStatus(this.jobName) == TaskStatus.COMPLETED
        log.debug "[NOMAD] Task status $task.name taskId=${this.jobName}; completed=$completed"
        if (completed) {
            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            def info = executor.service.jobStatus(this.jobName)
            if (info == TaskStatus.COMPLETED)
                task.error = new ProcessUnrecoverableException()
            executor.service.jobPurge(this.jobName)
            return true
        }
    }


    /**
     * @return Retrieve the task status caching the result for at lest one second
     */
    protected TaskStatus taskState0(String taskName) {
        final now = System.currentTimeMillis()
        final delta = now - timestamp;
        if (!status || delta >= 1_000) {
            def resp = executor.service.jobStatus(taskName)
            def newState =  NomadHelper.mapJobToTaskStatus(resp, taskName)
            log.debug "[NOMAD] Task: $taskName state=$newState"
            if (newState) {
                status = newState
                timestamp = now
            }
        }
        return status
    }

    protected int readExitFile() {
        try {
            exitFile.text as Integer
        }
        catch (Exception e) {
            log.debug "[NOMAD] Cannot read exit status for task: `$task.name` | ${e.message}"
            return Integer.MAX_VALUE
        }
    }

    @Override
    void kill() {
        if (!this.jobName)
            return
        executor.service.jobPurge(this.jobName)
    }

    @Override
    TraceRecord getTraceRecord() {
        def result = super.getTraceRecord()
        return result
    }

}
