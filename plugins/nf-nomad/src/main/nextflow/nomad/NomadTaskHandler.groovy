/*
 * Copyright 2023, Stellenbosch University, South Africa
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

package nextflow.nomad

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.nomadproject.client.models.TaskState
import nextflow.exception.ProcessUnrecoverableException
import nextflow.executor.BashWrapperBuilder
import nextflow.processor.TaskBean
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
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

    private volatile NomadTaskKey taskKey

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

    NomadService getBatchService() {
        return executor.batchService
    }

    void validateConfiguration() {
        if (!task.container) {
            throw new ProcessUnrecoverableException("No container image specified for process $task.name -- Either specify the container to use in the process definition or with 'process.container' value in your config")
        }
    }

    protected BashWrapperBuilder createBashWrapper() {}

    @Override
    void submit() {
    }

    @Override
    boolean checkIfRunning() {
    }

    @Override
    boolean checkIfCompleted() {
    }

    private Boolean shouldDelete() {
        //executor.config.batch().deleteJobsOnCompletion
    }

    protected void deleteTask(NomadTaskKey taskKey, TaskRun task) {
        if( !taskKey || shouldDelete()==Boolean.FALSE )
            return

        if( !task.isSuccess() && shouldDelete()==null ) {
            // do not delete successfully executed pods for debugging purpose
            return
        }

        try {
            batchService.deleteTask(taskKey)
        }
        catch( Exception e ) {
            log.warn "Unable to cleanup batch task: $taskKey -- see the log file for details", e
        }
    }

    /**
     * @return Retrieve the task status caching the result for at lest one second
     */
    protected TaskState taskState0( NomadTaskKey key) {}

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
        if( !taskKey )
            return
        batchService.terminate(taskKey)
    }

    @Override
    TraceRecord getTraceRecord() {
        def result = super.getTraceRecord()
        if( taskKey ) {
            result.put('native_id', taskKey.keyPair())
            //result.machineInfo = getMachineInfo()
        }
        return result
    }


}
