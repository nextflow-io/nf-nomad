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

package nextflow.nomad.executor

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.nomadproject.client.ApiClient
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.JobRegisterRequest
import io.nomadproject.client.models.JobRegisterResponse
import io.nomadproject.client.models.JobSummary
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.TaskGroup
import io.nomadproject.client.models.TaskGroupSummary
import nextflow.nomad.NomadConfig

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
        ApiClient apiClient = new ApiClient()
        apiClient.basePath = config.clientOpts.address
        if( config.clientOpts.token ){
            apiClient.apiKey = config.clientOpts.token
        }
        this.jobsApi = new JobsApi(apiClient);
    }

    @Override
    void close() throws IOException {
    }

    String submitTask(String id, String name, String image,
                      List<String> args,
                      String workingDir,
                      Map<String, String>env){
        Job job = new Job();
        job.ID = id
        job.name = name
        job.type = "batch"
        job.datacenters = this.config.jobOpts.datacenters
        job.namespace = this.config.jobOpts.namespace

        job.taskGroups = [createTaskGroup(id, name, image, args, workingDir, env)]

        JobRegisterRequest jobRegisterRequest = new JobRegisterRequest();
        jobRegisterRequest.setJob(job);
        JobRegisterResponse jobRegisterResponse = jobsApi.registerJob(jobRegisterRequest, config.jobOpts.region, config.jobOpts.namespace, null, null)
        jobRegisterResponse.evalID
    }

    TaskGroup createTaskGroup(String id, String name, String image, List<String> args, String workingDir, Map<String, String>env){
        def task = createTask(id, image, args, workingDir, env)
        def taskGroup = new TaskGroup(
                name: "group",
                tasks: [ task ]
        )
        return taskGroup
    }

    Task createTask(String id, String image, List<String> args, String workingDir, Map<String, String>env) {
        def task = new Task(
                name: "nf-task",
                driver: "docker",
                config: [
                        image: image,
                        privileged: true,
                        work_dir: workingDir,
                        command: args.first(),
                        args: args.tail(),
                ] as Map<String,Object>,
                env: env
        )
        if( config.jobOpts.dockerVolume){
            String destinationDir = workingDir.split(File.separator).dropRight(2).join(File.separator)
            task.config.mount = [
                    type : "volume",
                    target : destinationDir,
                    source : config.jobOpts.dockerVolume,
                    readonly : false
            ]
        }
        task
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
