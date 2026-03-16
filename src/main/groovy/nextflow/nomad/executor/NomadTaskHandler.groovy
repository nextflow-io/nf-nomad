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
import io.nomadproject.client.model.TaskState
import nextflow.exception.ProcessException
import nextflow.exception.ProcessSubmitException
import nextflow.executor.BashWrapperBuilder
import nextflow.fusion.FusionAwareTask
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.NomadHelper
import nextflow.nomad.util.NomadLogging
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.trace.TraceRecord
import nextflow.util.Escape
import nextflow.SysEnv
import org.codehaus.groovy.runtime.InvokerHelper
import org.threeten.bp.OffsetDateTime

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
    private String allocationId = null

    private String nodeId = null

    private String datacenter = null

    private TaskState state

    private long timestamp

    private long submissionTime = 0L

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


//-------------------------------------------------
//
// NOTE: From https://github.com/hashicorp/nomad/blob/6a41dc7b2f1fdbbc5a20ed267b4ad25fc2a14489/api/jobs.go#L1263-L1287
//
//-------------------------------------------------
//        type JobChildrenSummary struct {
//            Pending int64
//            Running int64
//            Dead    int64
//        }
//-------------------------------------------------
//        type TaskGroupSummary struct {
//            Queued   int
//            Complete int
//            Failed   int
//            Running  int
//            Starting int
//            Lost     int
//            Unknown  int
//        }
//-------------------------------------------------


    @Override
    boolean checkIfRunning() {
        if( !jobName ) throw new IllegalStateException("[NOMAD] Missing Nomad Job name -- cannot check if running")
        if(isSubmitted()) {
            def state = taskState0()

            NomadLogging.logJobState(log, jobName, state?.state, [task: task.name])

            // if a state exists, include an array of states to determine task status
            if( state?.state && ( ["running","pending","starting"].contains(state.state))){
                this.status = TaskStatus.RUNNING
                determineClientNode()
                return true
            }
        }
        return false
    }

    @Override
    boolean checkIfCompleted() {
        if( !jobName ) throw new IllegalStateException("[NOMAD] Missing Nomad Job name -- cannot check if running")

        def state = taskState0()

        NomadLogging.logJobState(log, jobName, state?.state, [task: task.name])

        // Check for placement failure if configured
        if (nomadService.isPlacementFailure(jobName, submissionTime)) {
            task.exitStatus = 1
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            task.error = new ProcessException("[NOMAD] Job placement failed - no suitable nodes with available resources")
            determineClientNode()
            return true
        }

        // if a state exists, include an array of states to determine task status
        if( state?.state && ( ["dead","complete","failed","lost"].contains(state.state))){
            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            if ( !state || state.failed ) {
                task.error = new ProcessException(failureMessage(state, task.exitStatus as Integer))
            }
            if (shouldDelete(state)) {
                nomadService.jobPurge(this.jobName)
            }

            updateTimestamps(state?.startedAt, state?.finishedAt)
            determineClientNode()
            return true
        }

        return false
    }

    @Override
    void kill() {
        super.kill()
        nomadService.kill(this.jobName)
    }

    @Override
    void killTask(){
        kill()
    }

    @Override
    void submit() {
        submitTask()
    }

    String submitTask() {
        if (!task.container)
            throw new ProcessSubmitException("[NOMAD] Missing container image for process `$task.processor.name`")

        def builder = createBashWrapper(task)
        builder.build()

        def hash = task.hash?.toString() ?: UUID.randomUUID().toString()
        this.jobName = NomadHelper.sanitizeName(hash + "-" + task.name)

        final taskLauncher = getSubmitCommand(task)
        final taskEnv = getEnv(task)
        nomadService.submitTask(this.jobName, task, taskLauncher, taskEnv, debugPath())
        writeDebugMetadataSnapshot()

        // Record submission time for placement failure detection
        this.submissionTime = System.currentTimeMillis()

        // submit the task execution
        NomadLogging.logTaskSubmission(log, task.name, this.jobName, task.container, task.workDirStr)
        // update the status
        this.status = TaskStatus.SUBMITTED
    }

    protected Path debugPath() {
        def debug = config.debug()
        if( debug == null || !debug.getEnabled() ) {
            return null
        }
        String fileName = debug.getPath() ?: '.job.json'
        Path path = Path.of(fileName)
        return path.isAbsolute() ? path : task.workDir.resolve(path)
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

        // Add Nomad-specific debug env variable
        if( SysEnv.containsKey('NF_NOMAD_DEBUG') )
            ret.put('NF_NOMAD_DEBUG', SysEnv.get('NF_NOMAD_DEBUG') )

        return ret
    }

    protected TaskState taskState0() {
        final now = System.currentTimeMillis()
        final delta = now - timestamp
        final long pollMillis = config?.clientOpts()?.pollInterval?.millis ?: 1_000L
        if (!status || delta >= pollMillis) {

            def newState = nomadService.getTaskState(jobName)
            if (newState && state?.state != newState.state) {
                NomadLogging.logStateTransition(log, jobName, state?.state, newState?.state)
            }

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
            log.warn "[NOMAD] Cannot read exit status for task: `$task.name` | ${e.message}"
            return Integer.MAX_VALUE
        }
    }

    protected Boolean shouldDelete(TaskState state) {
        final cleanup = config.jobOpts().cleanup
        if( cleanup == nextflow.nomad.config.NomadJobOpts.CLEANUP_ALWAYS ) {
            return true
        }
        if( cleanup == nextflow.nomad.config.NomadJobOpts.CLEANUP_NEVER ) {
            return false
        }
        if( cleanup == nextflow.nomad.config.NomadJobOpts.CLEANUP_ON_SUCCESS ) {
            Integer exitStatus = task.exitStatus as Integer
            return !(state?.failed ?: false) && exitStatus != null && exitStatus == 0
        }
        return config.jobOpts().deleteOnCompletion
    }

    protected String failureMessage(TaskState state, Integer exitStatus) {
        if( isOutOfMemoryFailure(state, exitStatus) ) {
            final hint = nomadFailureHint()
            final base = '[NOMAD] Task failed due to an out-of-memory condition. Increase process memory or review `nomadOptions.resources.memoryMax`.'
            return hint ? "${base} ${hint}" : base
        }

        final stateName = state?.state ?: 'unknown'
        final String exitPart = exitStatus != null && exitStatus != Integer.MAX_VALUE
                ? " with exit status ${exitStatus}"
                : ''
        final String details = taskEventSummary(state)
        final String base = details
                ? "[NOMAD] Task failed in Nomad state ${stateName}${exitPart}. ${details}"
                : "[NOMAD] Task failed in Nomad state ${stateName}${exitPart}."
        final hint = nomadFailureHint()
        return hint ? "${base} ${hint}" : base
    }

    protected boolean isOutOfMemoryFailure(TaskState state, Integer exitStatus) {
        final summary = taskEventSummary(state)?.toLowerCase()
        final hasMemorySignal = summary && (
                summary.contains('out of memory') ||
                        summary.contains('oom') ||
                        summary.contains('memory limit') ||
                        summary.contains('memory exhausted') ||
                        summary.contains('evict')
        )
        if( hasMemorySignal ) {
            return true
        }
        return (exitStatus == 137 || exitStatus == 247) && summary?.contains('memory')
    }

    protected String taskEventSummary(TaskState state) {
        List events = readListProperty(state, 'events')
        if( !events ) {
            return null
        }
        List<String> rendered = new ArrayList<>()
        for( Object event : events ) {
            String message = readStringProperty(event, 'displayMessage') ?: readStringProperty(event, 'message')
            if( !message ) {
                message = event?.toString()
            }
            if( message ) {
                rendered.add(message.trim())
            }
        }
        return rendered ? rendered.join(' | ') : null
    }

    private static List readListProperty(Object target, String property) {
        try {
            Object value = InvokerHelper.getProperty(target, property)
            return value instanceof List ? (List)value : null
        } catch (Throwable ignored) {
            return null
        }
    }

    private static String readStringProperty(Object target, String property) {
        try {
            String value = InvokerHelper.getProperty(target, property)?.toString()?.trim()
            return value ?: null
        } catch (Throwable ignored) {
            return null
        }
    }

    protected String nomadFailureHint() {
        if( jobName && (!allocationId || !clientName || !nodeId || !datacenter) ) {
            determineClientNode()
        }

        List<String> targets = new ArrayList<>()
        if( jobName ) {
            targets.add("job '" + jobName + "'")
        }
        if( allocationId ) {
            targets.add("allocation '" + allocationId + "'")
        }
        if( clientName ) {
            targets.add("node '" + clientName + "'")
        }
        if( datacenter ) {
            targets.add("datacenter '" + datacenter + "'")
        }
        if( !targets ) {
            return null
        }

        final apiUrl = allocationApiUrl()
        final prefix = "[NOMAD] Inspect ${targets.join(', ')}."
        return apiUrl ? "${prefix} Allocation API: ${apiUrl}" : prefix
    }

    protected String allocationApiUrl() {
        final base = config?.clientOpts()?.address?.toString()?.trim()
        if( !base || !allocationId ) {
            return null
        }
        String normalized = base.endsWith('/') ? base.substring(0, base.length()-1) : base
        return "${normalized}/allocation/${allocationId}"
    }

    private void determineClientNode(){
        try {
            if( !jobName ) {
                return
            }
            if( !clientName || !allocationId || !nodeId || !datacenter ) {
                Map<String, String> metadata = nomadService.getAllocationMetadata(jobName)
                if( metadata ) {
                    clientName = metadata.get('nodeName') ?: clientName
                    allocationId = metadata.get('allocationId') ?: allocationId
                    nodeId = metadata.get('nodeId') ?: nodeId
                    datacenter = metadata.get('datacenter') ?: datacenter
                }
            }
            writeDebugMetadataSnapshot()
            if (NomadLogging.isDebugEnabled()) {
                log.info "[NOMAD] determineClientNode: jobName:$jobName ; clientName:$clientName ; allocationId:$allocationId ; nodeId:$nodeId ; datacenter:$datacenter"
            }
        } catch ( Exception e ){
            if (NomadLogging.isDebugEnabled()) {
                log.info ("[NOMAD] Unable to get the client name of job $jobName -- awaiting for a client to be assigned.")
            }
        }
    }

    TraceRecord getTraceRecord() {
        if( jobName && (!clientName || !allocationId || !nodeId || !datacenter) ) {
            determineClientNode()
        }
        final result = super.getTraceRecord()
        if( jobName ) {
            result.put('native_id', jobName)
        }
        if( clientName ) {
            result.put('hostname', clientName)
        }
        return result
    }

    protected void writeDebugMetadataSnapshot() {
        Path dumpPath = debugPath()
        if( dumpPath == null || !jobName ) {
            return
        }
        nomadService.writeDebugMetadata(dumpPath, [
                nomad_job_id    : jobName,
                nomad_alloc_id  : allocationId,
                nomad_node_id   : nodeId,
                nomad_node_name : clientName,
                nomad_datacenter: datacenter
        ])
    }

    void updateTimestamps(OffsetDateTime start, OffsetDateTime end){
        try {
            startTimeMillis = start.toInstant().toEpochMilli()
            completeTimeMillis = end.toInstant().toEpochMilli()
        } catch( Exception e ) {
            // Only update if startTimeMillis hasn't already been set.
            // If startTimeMillis _has_ been set, then both startTimeMillis
            // and completeTimeMillis will have been set with the normal
            // TaskHandler mechanism, so there's no need to reset them here.
            if (!startTimeMillis) {
                startTimeMillis = System.currentTimeMillis()
                completeTimeMillis = System.currentTimeMillis()
            }
        }
    }
}
