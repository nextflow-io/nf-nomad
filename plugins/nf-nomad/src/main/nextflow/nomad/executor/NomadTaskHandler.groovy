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
import io.nomadproject.client.models.Resources
import io.nomadproject.client.models.TaskGroupSummary
import nextflow.exception.ProcessSubmitException
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.fusion.FusionAwareTask
import nextflow.nomad.NomadConfig
import nextflow.nomad.NomadHelper
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.util.Escape
import nextflow.util.MemoryUnit

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
        nomadService.checkIfRunning(this.jobName)
    }

    @Override
    boolean checkIfCompleted() {
        if (!nomadService.checkIfCompleted(this.jobName)) {
            return false
        }

        state = taskState0(this.jobName)

        final isDone = [
                TaskGroupSummary.SERIALIZED_NAME_COMPLETE,
                TaskGroupSummary.SERIALIZED_NAME_FAILED,
                TaskGroupSummary.SERIALIZED_NAME_LOST].contains(state)

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
        def imageName = task.container
        if (!imageName)
            throw new ProcessSubmitException("Missing container image for process `$task.processor.name`")

        def builder = createBashWrapper(task)
        builder.build()

        this.jobName = NomadHelper.sanitizeName(task.name + "-" + task.hash)

        final launcher = getSubmitCommand(task)

        nomadService.submitTask(this.jobName, task.name, imageName, launcher,
                task.workDir.toAbsolutePath().toString(),
                getEnv(task), getResources(task))


        // submit the task execution
        log.debug "[NOMAD] Submitted task ${task.name} with taskId=${this.jobName}"
        // update the status
        this.status = TaskStatus.SUBMITTED
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
        return ret
    }



    protected Resources getResources(TaskRun task) {
        final taskCfg = task.getConfig()
        final taskCores =  !taskCfg.get("cpus") ? 1 :  taskCfg.get("cpus") as Integer
        final taskMemory = taskCfg.get("memory") ? new MemoryUnit( taskCfg.get("memory") as String ) : new MemoryUnit("300.MB")

        final res = new Resources()
                .cores(taskCores)
                .memoryMB(taskMemory.toMega() as Integer)

        return res
    }

    protected String taskState0(String taskName) {
        final now = System.currentTimeMillis()
        final delta = now - timestamp;
        if (!status || delta >= 1_000) {
            def newState = nomadService.state(jobName)
            log.debug "[NOMAD] Task: $taskName state=$state newState=$newState"
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
        config.jobOpts.deleteOnCompletion
    }
}
