
package nextflow.nomad.executor


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.SysEnv
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.fusion.FusionAwareTask
import nextflow.k8s.model.PodEnv
import nextflow.nomad.NomadHelper
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.model.NomadJobBuilder
import nextflow.nomad.model.NomadTaskEnv
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

    private NomadConfig config

    private Path exitFile

    private Path outputFile

    private Path errorFile

    private long timestamp

    //Status in Nextflow Task terminology
    private TaskStatus status

    //State in Nomad Job terminology
    private String state

    private String jobName

    NomadTaskHandler(TaskRun task, NomadExecutor executor) {
        super(task)
        this.executor = executor
        this.config = executor.getConfig()
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
        executor.service.jobSubmit(createTask(task, jobName))
    }

//
//    protected boolean entrypointOverride() {
//        return executor.getConfig().job().entrypointOverride()
//    }

    protected String createTask(TaskRun task, String jobName) {

        final launcher = getSubmitCommand(task)

        def builder = new NomadJobBuilder()
            .withJobName(jobName)
            .withImageName(task.container)
            .withWorkDir( task.workDir)
            .withArgs(launcher)

        if ( fusionEnabled() ) {
            if( fusionConfig().privileged() )
                builder.withPrivileged(true)
            else {
                builder.withResourcesLimits(["nextflow.io/fuse": 1])
            }

            final env = fusionLauncher().fusionEnv()
            for( Map.Entry<String,String> it : env )
                builder.withEnv(it.key, it.value)
        }


        if( SysEnv.containsKey('NXF_DEBUG') )
            builder.withEnv('NXF_DEBUG', SysEnv.get('NXF_DEBUG'))

        return  builder.buildAsJson()
    }



    @Override
    boolean checkIfRunning() {
        if (!this.jobName || !isSubmitted())
            return false

        state = taskState0(this.jobName)
        // note, include complete status otherwise it hangs if the task
        // completes before reaching this check
        final isAllocated = state == "Running"
        log.debug "[NOMAD] Task status $task.name taskId=${this.jobName}; state=$state running=$isAllocated"
        if (isAllocated)
            this.status = TaskStatus.RUNNING
        return isAllocated
    }


    private Boolean shouldDelete() {
        executor.config.job().deleteJobsOnCompletion
    }


    @Override
    boolean checkIfCompleted() {
        assert this.jobName
        log.trace "[NOMAD] Checking Task completion status  $task.name taskId=${this.jobName}"

        if (isRunning())
            return false

        state = taskState0(this.jobName)

        final isDone = state == "Complete" || state == "Failed" || state == "Lost" || state == "Unknown"
        log.debug "[NOMAD] Task status $task.name taskId=${this.jobName}; state=$state completed=$isDone"

        if (isDone) {
            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            this.status = TaskStatus.COMPLETED
            if (state == "Failed" || state == "Lost" || state == "Unknown")
                task.error = new ProcessUnrecoverableException()

            if (shouldDelete()) {
                executor.service.jobPurge(this.jobName)
            }

            return true
        }

        return false
    }


    /**
     * @return Retrieve the task status caching the result for at least one second
     */
    protected String taskState0(String taskName) {
        final now = System.currentTimeMillis()
        final delta = now - timestamp;
        if (!status || delta >= 1_000) {
            def newState = NomadHelper.filterStatus(executor.service.jobStatus(this.jobName), this.jobName)
            log.trace "[NOMAD] Task: $taskName state=$state newState=$newState"
            if (newState) {
                state = newState
                timestamp = now
            }
        }
        return state
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

    /**
     * @return The workflow execution unique run name
     */
    protected String getRunName() {
        executor.session.runName
    }

//    protected Map<String,String> getMeta(TaskRun task) {
//        final result = new LinkedHashMap<String,String>(10)
//        final labels = executor.config.job().getMeta()
//        if( labels ) {
//            result.putAll(labels)
//        }
//        final resLabels = task.config.getResourceLabels()
//        if( resLabels )
//            result.putAll(resLabels)
//        result.'nextflow.io/app' = 'nextflow'
//        result.'nextflow.io/runName' = getRunName()
//        result.'nextflow.io/taskName' = task.getName()
//        result.'nextflow.io/processName' = task.getProcessor().getName()
//        result.'nextflow.io/sessionId' = "uuid-${executor.getSession().uniqueId}" as String
//        if( task.config.queue )
//            result.'nextflow.io/queue' = task.config.queue
//        return result
//    }


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
