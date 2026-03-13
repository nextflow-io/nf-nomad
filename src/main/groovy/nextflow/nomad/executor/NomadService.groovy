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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.nomadproject.client.ApiClient
import io.nomadproject.client.ApiException
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.api.VariablesApi
import io.nomadproject.client.model.*
import nextflow.nomad.builders.JobBuilder
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.util.NomadLogging
import nextflow.processor.TaskRun
import nextflow.exception.ProcessSubmitException
import org.codehaus.groovy.runtime.InvokerHelper
import org.threeten.bp.OffsetDateTime
import java.nio.file.Files

import java.nio.file.Path
import java.util.concurrent.TimeoutException

/**
 * Nomad Service
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@Slf4j
@CompileStatic
class NomadService implements Closeable{

    NomadConfig config
    ApiClient apiClient
    JobsApi jobsApi
    VariablesApi variablesApi
    FailsafeExecutor safeExecutor
    private long lastSubmitAt = 0L

    NomadService(NomadConfig config) {
        this.config = config

        final CONNECTION_TIMEOUT_MILLISECONDS = config.clientOpts().connectionTimeout
        final READ_TIMEOUT_MILLISECONDS = config.clientOpts().readTimeout
        final WRITE_TIMEOUT_MILLISECONDS = config.clientOpts().writeTimeout

        apiClient = new ApiClient( connectTimeout: CONNECTION_TIMEOUT_MILLISECONDS, readTimeout: READ_TIMEOUT_MILLISECONDS, writeTimeout: WRITE_TIMEOUT_MILLISECONDS)
        apiClient.basePath = config.clientOpts().address
        NomadLogging.logConfiguration(log, "Client", [
            address: config.clientOpts().address,
            connectionTimeout: config.clientOpts().connectionTimeout,
            readTimeout: config.clientOpts().readTimeout
        ])

        if( config.clientOpts().token ){
            NomadLogging.logConfiguration(log, "Authentication", [
                token: config.clientOpts().token?.take(5) + '..'
            ])
            apiClient.apiKey = config.clientOpts().token
        }
        this.jobsApi = new JobsApi(apiClient)
        this.variablesApi = new VariablesApi(apiClient)

        this.safeExecutor = new FailsafeExecutor(config.clientOpts().retryConfig)
    }


    @Override
    void close() throws IOException {
    }



    String submitTask(String id, TaskRun task, List<String> args, Map<String, String> env, Path saveJsonPath = null) {
        long startTime = System.currentTimeMillis()
        NomadTaskOptionsResolver.validate(task)

        Job job = new JobBuilder()
                .withId(id)
                .withName(task.name)
                .withType("batch")
                .withDatacenters(this.config.jobOpts().datacenters)
                .withNamespace(this.config.jobOpts().namespace)
                .withTaskGroups([JobBuilder.createTaskGroup(task, args, env, this.config.jobOpts())])
                .build()

        JobBuilder.assignDatacenters(task, job)
        JobBuilder.spreads(task, job, this.config.jobOpts())
        applyNamespace(task, job)
        applyMeta(task, job)

        // Apply priority if specified in env parameter
        if (env && env.containsKey('PRIORITY')) {
            Integer priority = JobBuilder.resolvePriority(env.get('PRIORITY'))
            if (priority != null) {
                job.priority(priority)
            }
        }

        // Apply priority if specified in task configuration
        def priorityFromConfig = NomadTaskOptionsResolver.priority(task)
        if (priorityFromConfig && !job.priority) {
            Integer priority = JobBuilder.resolvePriority(priorityFromConfig.toString())
            if (priority != null) {
                job.priority(priority)
            }
        }

        JobRegisterRequest jobRegisterRequest = new JobRegisterRequest()
        jobRegisterRequest.setJob(job)

        if (saveJsonPath) {
            writeDebugDump(saveJsonPath, [
                    nomad_job_id    : id,
                    nomad_alloc_id  : null,
                    nomad_node_id   : null,
                    nomad_node_name : null,
                    nomad_datacenter: null,
                    task_name       : task.name,
                    generated_at    : OffsetDateTime.now().toString(),
                    job_spec        : job.toString()
            ])
        }

        try {
            applySubmitThrottle()
            String evalId = safeExecutor.apply {
                JobRegisterResponse jobRegisterResponse = jobsApi.registerJob(jobRegisterRequest,
                        config.jobOpts().region, job.namespace ?: config.jobOpts().namespace,
                        null, null)
                jobRegisterResponse.evalID
            }

            long elapsed = System.currentTimeMillis() - startTime
            NomadLogging.logTiming(log, "Job submission for $id", elapsed)
            NomadLogging.logJobState(log, id, "submitted", [name: task.name])

            return evalId
        } catch (ApiException apiException) {
            NomadLogging.logError(log, "submitTask", id, apiException,
                    [taskName: task.name, statusCode: apiException.code])
            throw new ProcessSubmitException("[NOMAD] Failed to submit ${job.name} -- Cause: ${apiException.responseBody ?: apiException}", apiException)
        } catch (Throwable e) {
            NomadLogging.logError(log, "submitTask", id, e, [taskName: task.name])
            throw new ProcessSubmitException("[NOMAD] Failed to submit ${job.name} -- Cause: ${e.message ?: e}", e)
        }
    }

    protected synchronized void applySubmitThrottle() {
        final long throttle = config.jobOpts().submitThrottle?.millis ?: 0L
        if( throttle <= 0L ) {
            return
        }
        final long now = System.currentTimeMillis()
        final long elapsed = now - lastSubmitAt
        final long waitMillis = throttle - elapsed
        if( waitMillis > 0L ) {
            try {
                Thread.sleep(waitMillis)
            }
            catch( InterruptedException e ) {
                Thread.currentThread().interrupt()
                throw new ProcessSubmitException("[NOMAD] Submit throttling interrupted", e)
            }
        }
        lastSubmitAt = System.currentTimeMillis()
    }

    void writeDebugMetadata(Path saveJsonPath, Map<String, ?> metadata) {
        if( saveJsonPath == null || metadata == null || metadata.isEmpty() ) {
            return
        }
        Map<String, Object> payload = readDebugDump(saveJsonPath)
        payload.putAll(metadata.collectEntries { k, v -> [(k?.toString()): v] } as Map<String, Object>)
        writeDebugDump(saveJsonPath, payload)
    }

    protected Map<String, Object> readDebugDump(Path saveJsonPath) {
        if( saveJsonPath == null || !Files.exists(saveJsonPath) ) {
            return new LinkedHashMap<>()
        }
        String content = saveJsonPath.text
        if( !content?.trim() ) {
            return new LinkedHashMap<>()
        }
        try {
            def parsed = new JsonSlurper().parseText(content)
            if( parsed instanceof Map ) {
                return new LinkedHashMap<>((Map<String, Object>)parsed)
            }
        }
        catch (Exception ignored) {
            // fallback to preserving previous content as raw spec text
        }
        return new LinkedHashMap<>([job_spec: content])
    }

    protected void writeDebugDump(Path saveJsonPath, Map<String, Object> payload) {
        if( saveJsonPath == null ) {
            return
        }
        try {
            if( saveJsonPath.parent ) {
                Files.createDirectories(saveJsonPath.parent)
            }
            saveJsonPath.text = JsonOutput.prettyPrint(JsonOutput.toJson(payload ?: Collections.emptyMap()))
        }
        catch (Exception e) {
            log.debug "WARN: unable to save request json -- cause: ${e.message ?: e}"
        }
    }

    private static TaskState stateFromAllocation(AllocationListStub allocation) {
        String status = allocation?.clientStatus?.toLowerCase()
        if( !status ) {
            return null
        }
        boolean failed = status in ['failed', 'lost']
        boolean finished = status in ['complete', 'dead', 'failed', 'lost']
        return new TaskState(
                state: status,
                failed: failed,
                finishedAt: finished ? OffsetDateTime.now() : null
        )
    }

    private static TaskState pendingState() {
        new TaskState(state: 'pending', failed: false)
    }

    private static TaskState unknownState() {
        new TaskState(state: 'unknown', failed: false, finishedAt: OffsetDateTime.now())
    }

    private static boolean isTransientStateError(Throwable error) {
        ApiException apiException = findApiException(error)
        if( apiException != null ) {
            int code = apiException.code
            return code == 0 || code in [408, 429, 500, 502, 503, 504]
        }
        if( error instanceof IOException || error.cause instanceof IOException ) {
            return true
        }
        if( error instanceof TimeoutException || error.cause instanceof TimeoutException ) {
            return true
        }
        return false
    }

    private TaskState emptyAllocationState(String jobId) {
        if( !jobExists(jobId) ) {
            return unknownState()
        }
        return pendingState()
    }

    private boolean jobExists(String jobId) {
        try {
            Job job = safeExecutor.apply {
                jobsApi.getJob(jobId, config.jobOpts().region, config.jobOpts().namespace,
                        null, null, null, null, null, null, null)
            }
            return job != null
        }
        catch (ApiException e) {
            if( e.code == 404 ) {
                return false
            }
            if( isTransientStateError(e) ) {
                return true
            }
            throw e
        }
        catch (Exception e) {
            if( isTransientStateError(e) ) {
                return true
            }
            throw e
        }
    }


    TaskState getTaskState(String jobId){
        try {
            List<AllocationListStub> allocations = safeExecutor.apply {
                jobsApi.getJobAllocations(jobId, config.jobOpts().region, config.jobOpts().namespace,
                        null, null, null, null, null, null,
                        null, null)
            }
            if( !allocations ) {
                return emptyAllocationState(jobId)
            }
            AllocationListStub last = allocations ? allocations.sort {
                it.modifyIndex
            }?.last() : null
            TaskState currentState = last?.taskStates?.values()?.last() ?: stateFromAllocation(last)
            NomadLogging.logJobState(log, jobId, currentState?.state)
            currentState ?: pendingState()
        }catch(ApiException e){
            NomadLogging.logError(log, "getTaskState", jobId, e, [statusCode: e.code])
            if( isTransientStateError(e) ) {
                return pendingState()
            }
            unknownState()
        }catch(Exception e){
            NomadLogging.logError(log, "getTaskState", jobId, e)
            if( isTransientStateError(e) ) {
                return pendingState()
            }
            unknownState()
        }
    }



    void kill(String jobId) {
        purgeJob(jobId, false)
    }

    void jobPurge(String jobId){
        purgeJob(jobId, true)
    }

    protected void purgeJob(String jobId, boolean purge){
        if (NomadLogging.isDebugEnabled()) {
            log.info "[NOMAD] purgeJob with jobId=${jobId}"
        }
        try {
            safeExecutor.apply {
                jobsApi.deleteJob(jobId, config.jobOpts().region, config.jobOpts().namespace,
                        null, null, purge, true)
            }
        }catch(Exception e){
            NomadLogging.logError(log, "purgeJob", jobId, e)
        }
    }

    String getClientOfJob(String jobId) {
        Map<String, String> metadata = getAllocationMetadata(jobId)
        return metadata.get('nodeName')
    }

    Map<String, String> getAllocationMetadata(String jobId) {
        try{
            List<AllocationListStub> allocations = safeExecutor.apply {
                jobsApi.getJobAllocations(jobId, config.jobOpts().region, config.jobOpts().namespace,
                        null, null, null, null, null, null,
                        null, null)
            }
            if( !allocations ){
                return Collections.emptyMap()
            }
            AllocationListStub jobAllocation = allocations.sort {
                it.modifyIndex
            }?.last()
            if( !jobAllocation ) {
                return Collections.emptyMap()
            }

            String allocationId = readStringProperty(jobAllocation, 'id') ?: readStringProperty(jobAllocation, 'ID')
            String nodeId = readStringProperty(jobAllocation, 'nodeID') ?: readStringProperty(jobAllocation, 'NodeID')
            String nodeName = jobAllocation.nodeName
            String datacenter = readStringProperty(jobAllocation, 'datacenter') ?: readStringProperty(jobAllocation, 'Datacenter')
            NomadLogging.logAllocationDetails(log, jobId, [
                allocationId: allocationId,
                nodeId: nodeId,
                nodeName: jobAllocation.nodeName,
                datacenter: datacenter,
                clientStatus: jobAllocation.clientStatus,
                desiredStatus: jobAllocation.desiredStatus,
                modifyIndex: jobAllocation.modifyIndex
            ])

            Map<String, String> result = new LinkedHashMap<>()
            if( allocationId ) result.put('allocationId', allocationId)
            if( nodeId ) result.put('nodeId', nodeId)
            if( nodeName ) result.put('nodeName', nodeName)
            if( datacenter ) result.put('datacenter', datacenter)
            return result
        }catch (Exception e){
            NomadLogging.logError(log, "getClientOfJob", jobId, e)
            throw new ProcessSubmitException("[NOMAD] Failed to get alloactions ${jobId} -- Cause: ${e.message ?: e}", e)
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

    String getVariableValue(String key){
        getVariableValue(config.jobOpts().secretOpts?.path, key)
    }

    String getVariableValue(String path, String key){
        try {
            var variable = safeExecutor.apply {
                variablesApi.getVariableQuery("$path/$key",
                        config.jobOpts().region,
                        config.jobOpts().namespace,
                        null, null, null, null, null, null, null)
            }
            return variable?.items?.find{ it.key == key }?.value
        } catch (Exception e) {
            final apiException = findApiException(e)
            if( apiException?.code == 404 ) {
                return null
            }
            throw e
        }
    }

    private static ApiException findApiException(Throwable error) {
        Throwable current = error
        while( current ) {
            if( current instanceof ApiException ) {
                return (ApiException)current
            }
            current = current.cause
        }
        return null
    }

    void setVariableValue(String key, String value){
        setVariableValue(config.jobOpts().secretOpts?.path, key, value)
    }

    void setVariableValue(String path, String key, String value){
        var content = Map.of(key,value)
        var variable = new Variable(path: path, items: content)
        safeExecutor.apply {
            variablesApi.postVariable("$path/$key", variable,
                    config.jobOpts().region,
                    config.jobOpts().namespace,
                    null, null, null)
        }
    }

    List<String> getVariablesList(){
        var listRequest = safeExecutor.apply {
            variablesApi.getVariablesListRequest(
                    config.jobOpts().region,
                    config.jobOpts().namespace,
                    null, null, null, null,
                    null, null, null)
        }
        String path = (config.jobOpts().secretOpts?.path ?: '')+"/"
        listRequest.collect{ it.path - path}
    }

    void deleteVariable(String key){
        deleteVariable(config.jobOpts().secretOpts?.path, key)
    }

    void deleteVariable(String path, String key){
        var variable = new Variable( items: Map.of(key, ""))
        safeExecutor.apply {
            variablesApi.deleteVariable("$path/$key", variable,
                    config.jobOpts().region,
                    config.jobOpts().namespace,
                    null, null, null)
        }
    }

    /**
     * Check if a job has failed to be placed on any node due to resource constraints
     * Returns true if the allocation has no node assignment and is still in a waiting state
     *
     * @param jobId The job ID
     * @param submissionTime The time the job was submitted (in milliseconds)
     * @return true if placement failure is detected, false otherwise
     */
    boolean isPlacementFailure(String jobId, long submissionTime) {
        if (!config.jobOpts().failOnPlacementFailure) {
            return false
        }

        try {
            List<AllocationListStub> allocations = safeExecutor.apply {
                jobsApi.getJobAllocations(jobId, config.jobOpts().region, config.jobOpts().namespace,
                        null, null, null, null, null, null,
                        null, null)
            }
            // Check if timeout has been exceeded
            long elapsedTime = System.currentTimeMillis() - submissionTime
            long timeout = config.jobOpts().placementFailureTimeout.millis
            boolean timeoutExceeded = elapsedTime >= timeout

            AllocationListStub lastAllocation = allocations ? allocations.sort {
                it.modifyIndex
            }?.last() : null

            if (!lastAllocation) {
                if (NomadLogging.isTraceEnabled()) {
                    log.info "[NOMAD-TRACE] Placement check for $jobId: hasNoAllocation=true, " +
                            "timeoutExceeded=$timeoutExceeded, elapsedTime=${elapsedTime}ms, timeout=${timeout}ms"
                }
                if (timeoutExceeded) {
                    log.warn "[NOMAD] Job $jobId appears to have failed placement (no allocations after " +
                            "${elapsedTime}ms). This may indicate insufficient resources on available nodes."
                    return true
                }
                return false
            }

            // Check if allocation has no node assignment (indicates placement failure)
            boolean hasNoNode = !lastAllocation.nodeName || lastAllocation.nodeName.isEmpty()

            // Check if allocation is in a waiting state (pending, queued, etc.)
            boolean isWaiting = lastAllocation.clientStatus &&
                    ['pending', 'queued', 'allocating'].contains(lastAllocation.clientStatus.toLowerCase())

            if (NomadLogging.isTraceEnabled()) {
                log.info "[NOMAD-TRACE] Placement check for $jobId: hasNoNode=$hasNoNode, isWaiting=$isWaiting, " +
                        "timeoutExceeded=$timeoutExceeded, elapsedTime=${elapsedTime}ms, " +
                        "timeout=${timeout}ms"
            }

            if (hasNoNode && isWaiting && timeoutExceeded) {
                log.warn "[NOMAD] Job $jobId appears to have failed placement (no node assignment after " +
                        "${elapsedTime}ms). This may indicate insufficient resources on available nodes."
                return true
            }

            return false
        } catch (Exception e) {
            NomadLogging.logError(log, "isPlacementFailure", jobId, e)
            return false
        }
    }

    protected void applyNamespace(TaskRun task, Job job) {
        def namespace = NomadTaskOptionsResolver.namespace(task)
        if( namespace ) {
            job.namespace(namespace.toString())
        }
    }

    protected void applyMeta(TaskRun task, Job job) {
        Map<String, String> effectiveMeta = [:]
        if( config.jobOpts().meta ) {
            effectiveMeta.putAll(config.jobOpts().meta)
        }
        Map<String, String> processMeta = NomadTaskOptionsResolver.meta(task) as Map<String, String>
        if( processMeta ) {
            effectiveMeta.putAll(processMeta)
        }
        if( effectiveMeta ) {
            job.meta(effectiveMeta)
        }
    }
}
