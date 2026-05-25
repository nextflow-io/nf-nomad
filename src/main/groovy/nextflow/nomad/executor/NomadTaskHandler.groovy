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


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.nomadproject.client.model.TaskState
import nextflow.exception.ProcessException
import nextflow.exception.ProcessSubmitException
import nextflow.executor.BashWrapperBuilder
import nextflow.executor.ScriptFileCopyStrategy
import nextflow.fusion.FusionAwareTask
import nextflow.nomad.builders.JobBuilder
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.NomadHelper
import nextflow.nomad.executor.spi.DistributedWorkdirProvider
import nextflow.nomad.executor.spi.DistributedWorkdirProviderFactory
import nextflow.nomad.executor.spi.NoopDistributedWorkdirProvider
import nextflow.plugin.Plugins
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
    private final DistributedWorkdirProvider workdirProvider
    private List<String> preparedSubmitCommand
    private Map<String, String> preparedSubmitEnv
    private List<NomadLifecycleTaskSpec> preparedLifecycleTasks

    NomadTaskHandler(TaskRun task, NomadConfig config, NomadService nomadService) {
        this(task, config, nomadService, Collections.emptyMap(), null)
    }

    NomadTaskHandler(TaskRun task, NomadConfig config, NomadService nomadService, Map sessionConfig, Path sessionWorkDir) {
        super(task)
        this.config = config
        this.nomadService = nomadService
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        this.workdirProvider = selectWorkdirProvider(task, sessionConfig, sessionWorkDir)
        this.preparedSubmitCommand = null
        this.preparedSubmitEnv = Collections.emptyMap()
        this.preparedLifecycleTasks = Collections.emptyList()
    }

    /**
     * Pick the active distributed-workdir provider for a task. Transfer-tool
     * plugins register a
     * {@link DistributedWorkdirProviderFactory} via PF4J's
     * {@code @Extension}, and we discover them through Nextflow's
     * {@code Plugins.getExtensions} — which is classloader-aware (unlike
     * a raw {@code Class.forName}) and works across PF4J's per-plugin
     * classloader isolation.
     *
     * <p>Selection rule: take the first registered factory whose
     * {@link DistributedWorkdirProviderFactory#isEnabled} returns true.
     * Order is determined by PF4J — typically plugin load order. If none
     * is active, fall back to {@link NoopDistributedWorkdirProvider} and
     * let the executor assume a shared filesystem.</p>
     */
    protected static DistributedWorkdirProvider selectWorkdirProvider(TaskRun task, Map sessionConfig, Path sessionWorkDir) {
        try {
            List<DistributedWorkdirProviderFactory> factories =
                    Plugins.getExtensions(DistributedWorkdirProviderFactory) ?: Collections.emptyList()
            for( DistributedWorkdirProviderFactory factory : factories ) {
                try {
                    if( factory.isEnabled(sessionConfig) ) {
                        log.debug "[NOMAD] selectWorkdirProvider: picking `${factory.name()}` for task `${task?.name}`"
                        return factory.create(task, sessionConfig, sessionWorkDir)
                    }
                }
                catch (Throwable t) {
                    log.warn "[NOMAD] DistributedWorkdirProviderFactory `${factory?.name()}` failed isEnabled/create — skipping: ${t.message ?: t}"
                }
            }
        }
        catch (Throwable t) {
            log.debug "[NOMAD] selectWorkdirProvider: extension lookup failed (Plugins subsystem unavailable?), falling back to noop: ${t.message ?: t}"
        }
        return new NoopDistributedWorkdirProvider()
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
            Integer remoteExit = null
            if( isWorkdirProviderActive() && !fusionEnabled() ) {
                try {
                    remoteExit = synchronizeWorkdirCompletion()
                }
                catch (Exception e) {
                    task.exitStatus = Integer.MAX_VALUE
                    task.stdout = outputFile
                    task.stderr = errorFile
                    status = TaskStatus.COMPLETED
                    task.error = new ProcessException("[NOMAD] Failed to synchronize ${workdirProvider.name()} remote artifacts: ${e.message ?: e}")
                    determineClientNode()
                    return true
                }
            }
            // finalize the task
            task.exitStatus = remoteExit != null ? remoteExit : defineExitCode()
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED

            // Reconciliation: Nomad's alloc state and the worker's `.exitcode`
            // file are two independent signals that occasionally disagree.
            // The most common cases:
            //   - SPI path: a transient Docker first-attempt fails
            //     (image-pull race, exit 127), then a retry succeeds and pushes
            //     back `.exitcode = 0` plus all expected outputs. Nomad's events
            //     still show the historical 127.
            //   - Vanilla shared-FS path: the worker writes `.exitcode = 0` but
            //     the alloc lifecycle still ends with a non-zero docker exit
            //     (post-success cleanup, idle timeout, sigterm during reaping).
            //
            // Rule: when the worker reported success via *either* signal — the
            // SPI provider's remote `.exitcode` (remoteExit) OR the local
            // exit-file read by defineExitCode (task.exitStatus, populated
            // above) — trust the worker over Nomad's alloc-state failure flag.
            // Log a warning so the discrepancy stays visible for ops, but
            // don't kill the workflow.
            //
            // Only suppress when exitStatus is exactly 0. Any non-zero — from
            // either source — means the user task genuinely failed; surface
            // that as the error. defineExitCode returns Integer.MAX_VALUE when
            // it couldn't read any signal, which trivially fails the `== 0`
            // check, so a missing exit-file does NOT spuriously suppress.
            final boolean trustWorkerSuccess =
                    (remoteExit != null && remoteExit == 0) ||
                    (remoteExit == null && task.exitStatus == 0)
            if ( !state || state.failed ) {
                if( trustWorkerSuccess ) {
                    final String src = remoteExit != null
                            ? "${workdirProvider.name()} remote .exitcode"
                            : 'local .exitcode'
                    log.warn "[NOMAD] task `${task.name}` reported Nomad alloc-state failure but ${src} = 0; trusting the worker exit code"
                } else {
                    task.error = new ProcessException(failureMessage(state, task.exitStatus as Integer))
                }
            }
            if( isWorkdirProviderActive() && remoteExit == null && task.exitStatus == Integer.MAX_VALUE && task.error == null ) {
                task.error = new ProcessException(
                        "[NOMAD] ${workdirProvider.name()} did not produce a readable remote .exitcode at `${workdirRemoteExitHint()}` for task `${task.name}`"
                )
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
        final driver = JobBuilder.resolveDriver(task, config.jobOpts())
        if (NomadExecutor.isTaskDriverContainerNative(driver) && !task.container)
            throw new ProcessSubmitException("[NOMAD] Missing container image for process `$task.processor.name`")

        def builder = createBashWrapper(task)
        builder.build()

        if( isWorkdirProviderActive() && !fusionEnabled() ) {
            prepareWorkdirProvider()
        }

        def hash = task.hash?.toString() ?: UUID.randomUUID().toString()
        // New naming scheme: nf-<short-session>-<short-task>-<process>
        // (See NomadHelper.childJobName for rationale; replaces legacy
        // sanitizeName(hash + "-" + task.name) which collided across
        // concurrent sessions running the same pipeline.)
        def sessionId = task.processor?.session?.uniqueId?.toString() ?: ''
        def processName = task.processor?.name ?: task.name
        this.jobName = NomadHelper.childJobName(sessionId, hash, processName)

        final taskLauncher = getSubmitCommand(task)
        final taskEnv = getEnv(task)
        nomadService.submitTask(this.jobName, task, taskLauncher, taskEnv, preparedLifecycleTasks, debugPath())
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
        if( preparedSubmitCommand ) {
            return preparedSubmitCommand
        }
        return fusionEnabled()
                ? fusionSubmitCli()
                : classicSubmitCli(task)
    }

    protected List<String> classicSubmitCli(TaskRun task) {
        final driver = JobBuilder.resolveDriver(task, config.jobOpts())
        final result = new ArrayList(BashWrapperBuilder.BASH)
        if (NomadExecutor.isTaskDriverContainerNative(driver)) {
            // Container-native drivers (docker, podman): absolute path inside container
            result.add("${Escape.path(task.workDir)}/${TaskRun.CMD_RUN}".toString())
        } else {
            // Non-container-native drivers (pbs, slurm, raw_exec, exec):
            // use relative path — abc-hpc-bridge cd's to work_dir first
            result.add(TaskRun.CMD_RUN)
        }
        return result
    }

    protected BashWrapperBuilder createBashWrapper(TaskRun task) {
        if( fusionEnabled() ) {
            return fusionLauncher()
        }
        if( isWorkdirProviderActive() ) {
            // When the provider stages externally (e.g. sidecar mode),
            // skip stage-in/out in .command.run; otherwise keep staging enabled
            // (the provider's bootstrap script runs .command.run as-is).
            final boolean stagingDisabled = workdirProvider.isExternallyStaged()
            def strategy = workdirProvider.createCopyStrategy(stagingDisabled)
            if( strategy != null ) {
                return new BashWrapperBuilder(task.toTaskBean(), (ScriptFileCopyStrategy)strategy)
            }
        }
        return new NomadScriptLauncher(task.toTaskBean())
    }

    protected Map<String, String> getEnv(TaskRun task) {
        Map<String, String> ret = [:]
        if (fusionEnabled()) {
            ret += fusionLauncher().fusionEnv()
        }

        // Add Nomad-specific debug env variable
        if( SysEnv.containsKey('NF_NOMAD_DEBUG') )
            ret.put('NF_NOMAD_DEBUG', SysEnv.get('NF_NOMAD_DEBUG') )
        if( preparedSubmitEnv ) {
            ret += preparedSubmitEnv
        }

        // Identity correlation envs — Nextflow + Nomad-native signals plus
        // any harness-supplied env vars (via nomad.jobs.identityEnvPassthrough
        // and nomad.jobs.secretEnvPassthrough). Both lists flow to task env;
        // only identityEnvPassthrough is mirrored into Job.Meta (see
        // NomadService.buildIdentityMeta). Use secretEnvPassthrough for
        // secrets (AWS keys, registry creds) that must not be readable via
        // `nomad job inspect`.
        List<String> envOnly = new ArrayList<>()
        envOnly.addAll(config?.jobOpts()?.identityEnvPassthrough ?: Collections.<String>emptyList())
        envOnly.addAll(config?.jobOpts()?.secretEnvPassthrough ?: Collections.<String>emptyList())
        ret += buildIdentityEnv(task, envOnly)

        return ret
    }

    /** Identity envs to propagate to worker tasks (mirrors NomadService.buildIdentityMeta). */
    protected static Map<String, String> buildIdentityEnv(TaskRun task, List<String> passthroughVars) {
        Map<String, String> e = new LinkedHashMap<>()
        // Harness-supplied passthrough — pure pass-through of the head's value
        // under the same name on the worker.
        for( String key : (passthroughVars ?: Collections.<String>emptyList()) ) {
            String v = SysEnv.get(key)
            if( v != null && !v.isEmpty() ) e.put(key, v)
        }
        // Nextflow session-level signals + per-task coordinates so the worker
        // can emit a structured log line ([nf-task] start session=… task=…).
        try {
            def sess = task?.processor?.session
            if( sess != null ) {
                def sid = sess.uniqueId?.toString()
                if( sid ) e.put('NF_SESSION_ID', sid)
                def name = sess.runName?.toString()
                if( name ) e.put('NF_SESSION_NAME', name)
            }
        } catch (Throwable ignored) { /* skip */ }
        if( task?.processor?.name ) e.put('NF_PROCESS_NAME', task.processor.name)
        if( task?.hash ) e.put('NF_TASK_HASH', task.hash.toString())
        // Head's own Nomad ID/alloc — useful for child→head joins in logs
        for( String key : ['NOMAD_JOB_ID','NOMAD_ALLOC_ID'] ) {
            String v = SysEnv.get(key)
            if( v != null && !v.isEmpty() ) e.put('NF_HEAD_' + (key == 'NOMAD_JOB_ID' ? 'JOB_ID' : 'ALLOC_ID'), v)
        }
        return e
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

    protected int defineExitCode() {
        try {
            def text = exitFile?.text?.trim()
            if (text) {
                return text as Integer
            }
        }
        catch (Exception e) {
            log.debug "[NOMAD] Cannot read exit status from file for task: `$task.name` | ${e.message}"
        }

        try {
            if (state) {
                List events = readListProperty(state, 'events')
                if (events) {
                    for (Object event : events) {
                        def exitCode = readStringProperty(event, 'exitCode')
                        if (exitCode != null && exitCode.isInteger()) {
                            return exitCode as Integer
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            log.debug "[NOMAD] Cannot read exit status from task events for task: `$task.name` | ${e.message}"
        }

        log.warn "[NOMAD] Cannot determine exit status for task: `$task.name`"
        return Integer.MAX_VALUE
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

    protected boolean isWorkdirProviderActive() {
        return workdirProvider.isEnabled()
    }

    protected void prepareWorkdirProvider() {
        workdirProvider.prepare()
        preparedSubmitCommand = workdirProvider.submitCommand
        preparedSubmitEnv = workdirProvider.submitEnv
        preparedLifecycleTasks = workdirProvider.lifecycleTasks
    }

    protected Integer synchronizeWorkdirCompletion() {
        return workdirProvider.synchronizeCompletion()
    }

    protected String workdirRemoteExitHint() {
        return workdirProvider.remoteExitHint
    }
}
