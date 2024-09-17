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
import io.nomadproject.client.ApiClient
import io.nomadproject.client.ApiException
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.api.VariablesApi
import io.nomadproject.client.model.*
import nextflow.nomad.builders.JobBuilder
import nextflow.nomad.models.ConstraintsBuilder
import nextflow.nomad.models.JobConstraints
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.models.JobSpreads
import nextflow.nomad.models.JobVolume
import nextflow.nomad.models.SpreadsBuilder
import nextflow.processor.TaskRun
import nextflow.util.MemoryUnit
import nextflow.exception.ProcessSubmitException

import java.nio.file.Path

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

    NomadService(NomadConfig config) {
        this.config = config

        //TODO: Accommodate these connection level options in clientOpts()
        final CONNECTION_TIMEOUT_MILLISECONDS = 60000
        final READ_TIMEOUT_MILLISECONDS = 60000
        final WRITE_TIMEOUT_MILLISECONDS = 60000

        apiClient = new ApiClient( connectTimeout: CONNECTION_TIMEOUT_MILLISECONDS, readTimeout: READ_TIMEOUT_MILLISECONDS, writeTimeout: WRITE_TIMEOUT_MILLISECONDS)
        apiClient.basePath = config.clientOpts().address
        log.debug "[NOMAD] Client Address: ${config.clientOpts().address}"

        if( config.clientOpts().token ){
            log.debug "[NOMAD] Client Token: ${config.clientOpts().token?.take(5)}.."
            apiClient.apiKey = config.clientOpts().token
        }
        this.jobsApi = new JobsApi(apiClient)
        this.variablesApi = new VariablesApi(apiClient)
    }


    @Override
    void close() throws IOException {
    }



    String submitTask(String id, TaskRun task, List<String> args, Map<String, String> env, Path saveJsonPath = null) {
        Job job = new JobBuilder()
                .id(id)
                .name(task.name)
                .type("batch")
//                .datacenters(task, this.config.jobOpts().datacenters)
                .namespace(this.config.jobOpts().namespace)
                .taskGroups([JobBuilder.createTaskGroup(task, args, env, this.config.jobOpts())])
                .build()

        JobBuilder.assignDatacenters(task, job)
        JobBuilder.spreads(task, job, this.config.jobOpts())

        JobRegisterRequest jobRegisterRequest = new JobRegisterRequest()
        jobRegisterRequest.setJob(job)

        if (saveJsonPath) try {
            saveJsonPath.text = job.toString()
        } catch (Exception e) {
            log.debug "WARN: unable to save request json -- cause: ${e.message ?: e}"
        }

        try {
            JobRegisterResponse jobRegisterResponse = jobsApi.registerJob(jobRegisterRequest, config.jobOpts().region, config.jobOpts().namespace, null, null)
            return jobRegisterResponse.evalID
        } catch (ApiException apiException) {
            log.debug("[NOMAD] Failed to submit ${job.name} -- Cause: ${apiException.responseBody ?: apiException}", apiException)
            throw new ProcessSubmitException("[NOMAD] Failed to submit ${job.name} -- Cause: ${apiException.responseBody ?: apiException}", apiException)
        } catch (Throwable e) {
            log.debug("[NOMAD] Failed to submit ${job.name} -- Cause: ${e.message ?: e}", e)
            throw new ProcessSubmitException("[NOMAD] Failed to submit ${job.name} -- Cause: ${e.message ?: e}", e)
        }
    }


    String getJobState(String jobId){
        try {
            List<AllocationListStub> allocations = jobsApi.getJobAllocations(jobId, config.jobOpts().region, config.jobOpts().namespace, null, null, null, null, null, null, null, null)
            AllocationListStub last = allocations?.sort {
                it.modifyIndex
            }?.last()
            String currentState = last?.taskStates?.values()?.last()?.state
            log.debug "Task $jobId , state=$currentState"
            currentState ?: "Unknown"
        }catch(Exception e){
            log.debug("[NOMAD] Failed to get jobState ${jobId} -- Cause: ${e.message ?: e}", e)
            "dead"
        }
    }



    boolean checkIfRunning(String jobId){
        try {
            Job job = jobsApi.getJob(jobId, config.jobOpts().region, config.jobOpts().namespace, null, null, null, null, null, null, null)
            log.debug "[NOMAD] checkIfRunning jobID=$job.ID; status=$job.status"
            job.status == "running"
        }catch (Exception e){
            log.debug("[NOMAD] Failed to get jobState ${jobId} -- Cause: ${e.message ?: e}", e)
            false
        }
    }

    boolean checkIfDead(String jobId){
        try{
            Job job = jobsApi.getJob(jobId, config.jobOpts().region, config.jobOpts().namespace, null, null, null, null, null, null, null)
            log.debug "[NOMAD] checkIfDead jobID=$job.ID; status=$job.status"
            job.status == "dead"
        }catch (Exception e){
            log.debug("[NOMAD] Failed to get job ${jobId} -- Cause: ${e.message ?: e}", e)
            true
        }
    }

    void kill(String jobId) {
        purgeJob(jobId, false)
    }

    void jobPurge(String jobId){
        purgeJob(jobId, true)
    }

    protected void purgeJob(String jobId, boolean purge){
        log.debug "[NOMAD] purgeJob with jobId=${jobId}"
        try {
            jobsApi.deleteJob(jobId, config.jobOpts().region, config.jobOpts().namespace, null, null, purge, true)
        }catch(Exception e){
            log.debug("[NOMAD] Failed to delete job ${jobId} -- Cause: ${e.message ?: e}", e)
        }
    }

    String getClientOfJob(String jobId) {
        try{
            List<AllocationListStub> allocations = jobsApi.getJobAllocations(jobId, config.jobOpts().region, config.jobOpts().namespace, null, null, null, null, null, null, null, null)
            if( !allocations ){
                return null
            }
            AllocationListStub jobAllocation = allocations.first()
            return jobAllocation.nodeName
        }catch (Exception e){
            log.debug("[NOMAD] Failed to get job allocations ${jobId} -- Cause: ${e.message ?: e}", e)
            throw new ProcessSubmitException("[NOMAD] Failed to get alloactions ${jobId} -- Cause: ${e.message ?: e}", e)
        }
    }

    String getVariableValue(String key){
        getVariableValue(config.jobOpts().secretOpts?.path, key)
    }

    String getVariableValue(String path, String key){
        var variable = variablesApi.getVariableQuery("$path/$key",
                config.jobOpts().region,
                config.jobOpts().namespace,
                null, null, null, null, null, null, null)
        variable?.items?.find{ it.key == key }?.value
    }

    void setVariableValue(String key, String value){
        setVariableValue(config.jobOpts().secretOpts?.path, key, value)
    }

    void setVariableValue(String path, String key, String value){
        var content = Map.of(key,value)
        var variable = new Variable(path: path, items: content)
        variablesApi.postVariable("$path/$key", variable,
                config.jobOpts().region,
                config.jobOpts().namespace,
                null, null, null)
    }

    List<String> getVariablesList(){
        var listRequest = variablesApi.getVariablesListRequest(
                config.jobOpts().region,
                config.jobOpts().namespace,
                null, null, null, null, null, null, null)
        String path = (config.jobOpts().secretOpts?.path ?: '')+"/"
        listRequest.collect{ it.path - path}
    }

    void deleteVariable(String key){
        deleteVariable(config.jobOpts().secretOpts?.path, key)
    }

    void deleteVariable(String path, String key){
        var variable = new Variable( items: Map.of(key, ""))
        variablesApi.deleteVariable("$path/$key", variable,
                config.jobOpts().region,
                config.jobOpts().namespace,
                null, null, null)
    }
}
