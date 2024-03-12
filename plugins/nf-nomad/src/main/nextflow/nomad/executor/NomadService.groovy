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
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.JobRegisterRequest
import io.nomadproject.client.models.JobRegisterResponse
import io.nomadproject.client.models.JobSummary
import io.nomadproject.client.models.ReschedulePolicy
import io.nomadproject.client.models.Resources
import io.nomadproject.client.models.RestartPolicy
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.TaskGroup
import io.nomadproject.client.models.TaskGroupSummary
import io.nomadproject.client.models.VolumeMount
import io.nomadproject.client.models.VolumeRequest
import nextflow.nomad.NomadConfig
import nextflow.processor.TaskRun
import nextflow.util.MemoryUnit

/**
 * Nomad Service
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@Slf4j
@CompileStatic
class NomadService implements Closeable{

    private final NomadConfig config

    private final JobsApi jobsApi

    NomadService(NomadConfig config) {
        this.config = config

        final CONNECTION_TIMEOUT_MILLISECONDS = 60000
        final READ_TIMEOUT_MILLISECONDS = 60000
        final WRITE_TIMEOUT_MILLISECONDS = 60000

        ApiClient apiClient = new ApiClient( connectTimeout: CONNECTION_TIMEOUT_MILLISECONDS, readTimeout: READ_TIMEOUT_MILLISECONDS, writeTimeout: WRITE_TIMEOUT_MILLISECONDS)
        apiClient.basePath = config.clientOpts.address
        log.debug "[NOMAD] Client Address: ${config.clientOpts.address}"

        if( config.clientOpts.token ){
            log.debug "[NOMAD] Client Token: ${config.clientOpts.token?.take(5)}.."
            apiClient.apiKey = config.clientOpts.token
        }
        this.jobsApi = new JobsApi(apiClient);
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

    String submitTask(String id, TaskRun task, List<String> args, Map<String, String>env){
        Job job = new Job();
        job.ID = id
        job.name = task.name
        job.type = "batch"
        job.datacenters = this.config.jobOpts.datacenters
        job.namespace = this.config.jobOpts.namespace

        job.taskGroups = [createTaskGroup(task, args, env)]

        JobRegisterRequest jobRegisterRequest = new JobRegisterRequest();
        jobRegisterRequest.setJob(job);
        JobRegisterResponse jobRegisterResponse = jobsApi.registerJob(jobRegisterRequest, config.jobOpts.region, config.jobOpts.namespace, null, null)
        jobRegisterResponse.evalID
    }

    TaskGroup createTaskGroup(TaskRun taskRun, List<String> args, Map<String, String>env){
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


        if( config.jobOpts.volumeSpec.type == NomadConfig.VOLUME_CSI_TYPE){
            taskGroup.volumes = [:]
            taskGroup.volumes[config.jobOpts.volumeSpec.name]= new VolumeRequest(
                    type: config.jobOpts.volumeSpec.type,
                    source: config.jobOpts.volumeSpec.name,
                    attachmentMode: "file-system",
                    accessMode: "multi-node-multi-writer"
            )
        }

        if( config.jobOpts.volumeSpec.type == NomadConfig.VOLUME_HOST_TYPE){
            taskGroup.volumes = [:]
            taskGroup.volumes[config.jobOpts.volumeSpec.name]= new VolumeRequest(
                    type: config.jobOpts.volumeSpec.type,
                    source: config.jobOpts.volumeSpec.name,
            )
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
                        image: imageName,
                        privileged: DRIVER_PRIVILEGED,
                        work_dir: workingDir,
                        command: args.first(),
                        args: args.tail(),
                ] as Map<String,Object>,
                env: env,
        )
        if( config.jobOpts.dockerVolume){
            String destinationDir = workingDir.split(File.separator).dropRight(2).join(File.separator)
            taskDef.config.mount = [
                    type : "volume",
                    target : destinationDir,
                    source : config.jobOpts.dockerVolume,
                    readonly : false
            ]
        }

        if( config.jobOpts.volumeSpec){
            String destinationDir = workingDir.split(File.separator).dropRight(2).join(File.separator)
            taskDef.volumeMounts = [ new VolumeMount(
                    destination: destinationDir,
                    volume: config.jobOpts.volumeSpec.name
            )]
        }
        taskDef
    }


    String state(String jobId){
        JobSummary summary = jobsApi.getJobSummary(jobId, config.jobOpts.region, config.jobOpts.namespace, null, null, null, null, null, null, null)
        TaskGroupSummary taskGroupSummary = summary?.summary?.values()?.first()
        switch (taskGroupSummary){
            case {taskGroupSummary?.starting }:
                return TaskGroupSummary.SERIALIZED_NAME_STARTING
            case {taskGroupSummary?.complete }:
                return TaskGroupSummary.SERIALIZED_NAME_COMPLETE
            case {taskGroupSummary?.failed }:
                return TaskGroupSummary.SERIALIZED_NAME_FAILED
            case {taskGroupSummary?.lost }:
                return TaskGroupSummary.SERIALIZED_NAME_LOST
            case {taskGroupSummary?.queued }:
                return TaskGroupSummary.SERIALIZED_NAME_QUEUED
            case {taskGroupSummary?.running }:
                return TaskGroupSummary.SERIALIZED_NAME_RUNNING
            default:
                TaskGroupSummary.SERIALIZED_NAME_UNKNOWN
        }
    }



    boolean checkIfRunning(String jobId){
        Job job = jobsApi.getJob(jobId, config.jobOpts.region, config.jobOpts.namespace, null, null, null, null, null, null, null)
        job.status == "running"
    }

    boolean checkIfCompleted(String jobId){
        Job job = jobsApi.getJob(jobId, config.jobOpts.region, config.jobOpts.namespace, null, null, null, null, null, null, null)
        job.status == "dead"
    }

    void kill(String jobId) {
        purgeJob(jobId, false)
    }

    void jobPurge(String jobId){
        purgeJob(jobId, true)
    }

    protected void purgeJob(String jobId, boolean purge){
        jobsApi.deleteJob(jobId,config.jobOpts.region, config.jobOpts.namespace,null,null,purge, true)
    }
}
