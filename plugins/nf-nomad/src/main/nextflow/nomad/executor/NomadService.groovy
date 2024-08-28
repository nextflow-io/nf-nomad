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
import nextflow.nomad.models.ConstraintsBuilder
import nextflow.nomad.models.JobConstraints
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.models.JobVolume
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

    protected Resources getResources(TaskRun task) {
        final DEFAULT_CPUS = 1
        final DEFAULT_MEMORY = "500.MB"

        final taskCfg = task.getConfig()
        final taskCores =  !taskCfg.get("cpus") ? DEFAULT_CPUS :  taskCfg.get("cpus") as Integer
        final taskMemory = taskCfg.get("memory") ? new MemoryUnit( taskCfg.get("memory") as String ) : new MemoryUnit(DEFAULT_MEMORY)

        final res = new Resources()
                .cores(taskCores)
                .memoryMB(taskMemory.toMega() as Integer)

        return res
    }

    @Override
    void close() throws IOException {
    }

    String submitTask(String id, TaskRun task, List<String> args, Map<String, String>env, Path saveJsonPath=null){
        Job job = new Job();
        job.ID = id
        job.name = task.name
        job.type = "batch"
        job.datacenters = this.config.jobOpts().datacenters
        job.namespace = this.config.jobOpts().namespace

        job.taskGroups = [createTaskGroup(task, args, env)]

        assignDatacenters(task, job)

        JobRegisterRequest jobRegisterRequest = new JobRegisterRequest()
        jobRegisterRequest.setJob(job)

        if( saveJsonPath ) try {
            saveJsonPath.text = job.toString()
        }
        catch( Exception e ) {
            log.debug "WARN: unable to save request json -- cause: ${e.message ?: e}"
        }


        try {
            JobRegisterResponse jobRegisterResponse = jobsApi.registerJob(jobRegisterRequest, config.jobOpts().region, config.jobOpts().namespace, null, null)
            jobRegisterResponse.evalID
        } catch( ApiException apiException){
            log.debug("[NOMAD] Failed to submit ${job.name} -- Cause: ${apiException.responseBody ?: apiException}", apiException)
            throw new ProcessSubmitException("[NOMAD] Failed to submit ${job.name} -- Cause: ${apiException.responseBody ?: apiException}", apiException)
        } catch (Throwable e) {
            log.debug("[NOMAD] Failed to submit ${job.name} -- Cause: ${e.message ?: e}", e)
            throw new ProcessSubmitException("[NOMAD] Failed to submit ${job.name} -- Cause: ${e.message ?: e}", e)
        }

    }

    TaskGroup createTaskGroup(TaskRun taskRun, List<String> args, Map<String, String>env){
        //NOTE: Force a single-allocation with no-retries per nomad job definition
        final TASK_RESCHEDULE_ATTEMPTS = 0
        final TASK_RESTART_ATTEMPTS = 0

        final ReschedulePolicy taskReschedulePolicy  = new ReschedulePolicy().attempts(TASK_RESCHEDULE_ATTEMPTS)
        final RestartPolicy taskRestartPolicy  = new RestartPolicy().attempts(TASK_RESTART_ATTEMPTS)

        def task = createTask(taskRun, args, env)
        def taskGroup = new TaskGroup(
                name: "group",
                tasks: [ task ],
                reschedulePolicy: taskReschedulePolicy,
                restartPolicy: taskRestartPolicy
        )


        if( config.jobOpts().volumeSpec ) {
            taskGroup.volumes = [:]
            config.jobOpts().volumeSpec.eachWithIndex { volumeSpec , idx->
                if (volumeSpec && volumeSpec.type == JobVolume.VOLUME_CSI_TYPE) {
                    taskGroup.volumes["vol_${idx}".toString()] = new VolumeRequest(
                            type: volumeSpec.type,
                            source: volumeSpec.name,
                            attachmentMode: volumeSpec.attachmentMode,
                            accessMode: volumeSpec.accessMode,
                            readOnly: volumeSpec.readOnly,
                    )
                }

                if (volumeSpec && volumeSpec.type == JobVolume.VOLUME_HOST_TYPE) {
                    taskGroup.volumes["vol_${idx}".toString()] = new VolumeRequest(
                            type: volumeSpec.type,
                            source: volumeSpec.name,
                            readOnly: volumeSpec.readOnly,
                    )
                }
            }
        }
        return taskGroup
    }

    Task createTask(TaskRun task, List<String> args, Map<String, String>env) {
        final DRIVER = "docker"
        final DRIVER_PRIVILEGED = true

        final imageName = task.container
        final workingDir = task.workDir.toAbsolutePath().toString()
        final taskResources = getResources(task)


        def taskDef = new Task(
                name: "nf-task",
                driver: DRIVER,
                resources: taskResources,
                config: [
                        image     : imageName,
                        privileged: DRIVER_PRIVILEGED,
                        work_dir  : workingDir,
                        command   : args.first(),
                        args      : args.tail(),
                ] as Map<String, Object>,
                env: env,
        )

        volumes(task, taskDef, workingDir)
        affinity(task, taskDef)
        constraint(task, taskDef)
        constraints(task, taskDef)
        secrets(task, taskDef)
        return taskDef
    }

    protected Task volumes(TaskRun task, Task taskDef, String workingDir){
        if( config.jobOpts().dockerVolume){
            String destinationDir = workingDir.split(File.separator).dropRight(2).join(File.separator)
            taskDef.config.mount = [
                    type : "volume",
                    target : destinationDir,
                    source : config.jobOpts().dockerVolume,
                    readonly : false
            ]
        }

        if( config.jobOpts().volumeSpec){
            taskDef.volumeMounts = []
            config.jobOpts().volumeSpec.eachWithIndex { volumeSpec, idx ->
                String destinationDir = volumeSpec.workDir ?
                        workingDir.split(File.separator).dropRight(2).join(File.separator) : volumeSpec.path
                taskDef.volumeMounts.add new VolumeMount(
                        destination: destinationDir,
                        volume: "vol_${idx}".toString()
                )
            }
        }

        taskDef
    }

    protected Task affinity(TaskRun task, Task taskDef) {
        if (config.jobOpts().affinitySpec) {
            def affinity = new Affinity()
            if (config.jobOpts().affinitySpec.attribute) {
                affinity.ltarget(config.jobOpts().affinitySpec.attribute)
            }

            affinity.operand(config.jobOpts().affinitySpec.operator ?: "=")

            if (config.jobOpts().affinitySpec.value) {
                affinity.rtarget(config.jobOpts().affinitySpec.value)
            }
            if (config.jobOpts().affinitySpec.weight != null) {
                affinity.weight(config.jobOpts().affinitySpec.weight)
            }
            taskDef.affinities([affinity])
        }
        taskDef
    }

    protected Task constraint(TaskRun task, Task taskDef){
        if( config.jobOpts().constraintSpec ){
            def constraint = new Constraint()
            if(config.jobOpts().constraintSpec.attribute){
                constraint.ltarget(config.jobOpts().constraintSpec.attribute)
            }

            constraint.operand(config.jobOpts().constraintSpec.operator ?: "=")

            if(config.jobOpts().constraintSpec.value){
                constraint.rtarget(config.jobOpts().constraintSpec.value)
            }
            taskDef.constraints([constraint])
        }

        taskDef
    }

    protected Task constraints(TaskRun task, Task taskDef){
        def constraints = [] as List<Constraint>

        if( config.jobOpts().constraintsSpec ){
            def list = ConstraintsBuilder.constraintsSpecToList(config.jobOpts().constraintsSpec)
            constraints.addAll(list)
        }

        if( task.processor?.config?.get(TaskDirectives.CONSTRAINTS) &&
                task.processor?.config?.get(TaskDirectives.CONSTRAINTS) instanceof Closure) {
            Closure closure = task.processor?.config?.get(TaskDirectives.CONSTRAINTS) as Closure
            JobConstraints constraintsSpec = JobConstraints.parse(closure)
            def list = ConstraintsBuilder.constraintsSpecToList(constraintsSpec)
            constraints.addAll(list)
        }

        if( constraints.size()) {
            taskDef.constraints(constraints)
        }
        taskDef
    }

    protected Task secrets(TaskRun task, Task taskDef){
        if( config.jobOpts()?.secretOpts?.enabled) {
            def secrets = task.processor?.config?.get(TaskDirectives.SECRETS)
            if (secrets) {
                Template template = new Template(envvars: true, destPath: "/secrets/nf-nomad")
                String secretPath = config.jobOpts()?.secretOpts?.path
                String tmpl = secrets.collect { String name ->
                    "${name}={{ with nomadVar \"$secretPath/${name}\" }}{{ .${name} }}{{ end }}"
                }.join('\n').stripIndent()
                template.embeddedTmpl(tmpl)
                taskDef.addTemplatesItem(template)
            }
        }
        taskDef
    }

    protected Job assignDatacenters(TaskRun task, Job job){
        def datacenters = task.processor?.config?.get(TaskDirectives.DATACENTERS)
        if( datacenters ){
            if( datacenters instanceof List<String>) {
                job.datacenters( datacenters as List<String>)
                return job;
            }
            if( datacenters instanceof Closure) {
                String str = datacenters.call().toString()
                job.datacenters( [str])
                return job;
            }
            job.datacenters( [datacenters.toString()] as List<String>)
            return job
        }
        job
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
