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


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.ProcessSubmitException
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.fusion.FusionAwareTask
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.NomadHelper
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.trace.TraceRecord
import nextflow.util.Escape
import nextflow.SysEnv

import java.nio.file.Path

/**
 * Implements the {@link TaskHandler} interface for Nomad jobs
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
class NomadTaskHandler extends TaskHandler implements FusionAwareTask {

    private final NomadConfig config

    private final NomadService nomadService

    private String jobName

    private String clientName = null

    private String state

    private long timestamp

    private final Path outputFile

    private final Path errorFile

    private final Path exitFile

    NomadTaskHandler(TaskRun task, NomadConfig config, NomadService nomadService) {
        super(task)
        this.config = config
        this.nomadService = nomadService
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
    }

    @Override
    boolean checkIfRunning() {
        if(isActive()) {
            determineClientNode()
        }
        nomadService.checkIfRunning(this.jobName)
    }

    @Override
    boolean checkIfCompleted() {
        if (!nomadService.checkIfDead(this.jobName)) {
            return false
        }

        state = taskState0(this.jobName)

        final isFinished = [
                "complete",
                "failed",
                "dead",
                "lost"].contains(state)

        log.debug "[NOMAD] checkIfCompleted task.name=$task.name; state=$state completed=$isFinished"

        if (isFinished) {
            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            this.status = TaskStatus.COMPLETED
            if (state == "failed" || state == "lost" || state == "unknown")
                task.error = new ProcessUnrecoverableException()

            if (shouldDelete()) {
                nomadService.jobPurge(this.jobName)
            }

            return true
        }

        return false
    }

    @Override
    void kill() {
        nomadService.kill(this.jobName)
    }

    @Override
    void submit() {
        submitTask()
    }

    String submitTask() {
        log.debug "[NOMAD] Submitting task ${task.name} - work-dir=${task.workDirStr}"
        if (!task.container)
            throw new ProcessSubmitException("Missing container image for process `$task.processor.name`")

        def builder = createBashWrapper(task)
        builder.build()

        this.jobName = NomadHelper.sanitizeName(task.hash + "-" + task.name)

        final taskLauncher = getSubmitCommand(task)
        final taskEnv = getEnv(task)
        nomadService.submitTask(this.jobName, task, taskLauncher, taskEnv, debugPath())

        // submit the task execution
        log.debug "[NOMAD] Submitted task ${task.name} with taskId=${this.jobName}"
        // update the status
        this.status = TaskStatus.SUBMITTED
    }

    protected Path debugPath() {
        boolean debug = config.debug()?.getJson()
        return debug ? task.workDir.resolve('.job.json') : null
    }

    protected List<String> getSubmitCommand(TaskRun task) {
        return fusionEnabled()
                ? fusionSubmitCli()
                : classicSubmitCli(task)
    }

    protected List<String> classicSubmitCli(TaskRun task) {
        final result = new ArrayList(BashWrapperBuilder.BASH)
        result.add("${Escape.path(task.workDir)}/${TaskRun.CMD_RUN}".toString())
        return result
    }

    protected BashWrapperBuilder createBashWrapper(TaskRun task) {
        fusionEnabled()
                ? fusionLauncher()
                : new NomadScriptLauncher(task.toTaskBean())
    }

    protected Map<String, String> getEnv(TaskRun task) {
        Map<String, String> ret = [:]
        if (fusionEnabled()) {
            ret += fusionLauncher().fusionEnv()
        }

        //Add debug env variable
        if( SysEnv.containsKey('NXF_DEBUG') )
            ret.put('NXF_DEBUG', SysEnv.get('NXF_DEBUG') )

        return ret
    }

    protected String taskState0(String taskName) {
        final now = System.currentTimeMillis()
        final delta = now - timestamp;
        if (!status || delta >= 1_000) {

            def newState = nomadService.getJobState(jobName)
            log.debug "[NOMAD] Check jobState: jobName=$jobName currentState=$state newState=$newState"

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

    private Boolean shouldDelete() {
        config.jobOpts().deleteOnCompletion
    }



    private void determineClientNode(){
        try {
            if ( !clientName )
                clientName = nomadService.getClientOfJob( jobName )
            log.debug "[NOMAD] determineClientNode: jobName:$jobName; clientName:$clientName"
        } catch ( Exception e ){
            log.warn ("[NOMAD] Unable to get the client name of job $jobName -- see the log file for details", e)
        }
    }

    TraceRecord getTraceRecord() {
        final result = super.getTraceRecord()
        result.put('native_id', jobName)
        result.put( 'hostname', clientName )
        return result
    }

}
