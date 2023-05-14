package nextflow.nomad.executor
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

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.nomadproject.client.ApiClient
import io.nomadproject.client.Configuration
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.JobRegisterRequest
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.TaskGroup
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.Rnd

/**
 * Implements Nomad operations for Nextflow executor
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@Slf4j
@CompileStatic
class NomadService implements Closeable {

    NomadConfig config

    NomadService(NomadExecutor executor) {
        assert executor
        this.config = executor.config
    }

    @Memoized
    protected ApiClient getClient() {
        createNomadClient()
    }

    protected ApiClient createNomadClient() {
        log.debug "[NOMAD] Executor options=${config.client()}"

        // Create Nomad client
        if (!config.client().token)
            throw new IllegalArgumentException("Missing Nomad apiToken -- Specify it in the nextflow.config file")

        final client = Configuration
                .getDefaultApiClient()
                .setBasePath(config.client().address)

//FIXME
//        Global.onCleanup((it)->client.protocolLayer().restClient().close())

        return client
    }


    static String makeJobId(TaskRun task) {
        final PREFIX = "nf"
        final RANDOM = Rnd.hex()

        final name = task
                .processor
                .name
                .trim()
                .replaceAll(/[^a-zA-Z0-9-_]+/, '_')

        final String key = "$PREFIX-${name}-$RANDOM"

        //FIXME Perhaps NOT needed
        // Nomad job max len is 64 characters, however we keep it a bit shorter
        // because the jobId + taskId composition must be less then 100
        final MAX_LEN = 62i
        return key.size() > MAX_LEN ? key.substring(0, MAX_LEN) : key
    }


    Map<TaskProcessor, String> allJobIds = new HashMap<>(50)

    synchronized String getOrRunJob(TaskRun task) {

        //If job doesn't already exist
        final mapKey = task.processor
        if (allJobIds.containsKey(mapKey)) {
            return allJobIds[mapKey]
        }

        //If job doesn't already exist

        //- create a job ID
        final newJobId = makeJobId(task)
        //- add to the map
        allJobIds[mapKey] = newJobId


        //- create a job def
        def jobDef = createJobDef(newJobId,task)
        //- run the job
        runJob(jobDef, task)

        return new NomadTaskKey(jobDef.name, jobDef.name)
    }


    NomadTaskKey runJob(Job taskJob, TaskRun task) {

        def region = config.client().region
        def namespace = config.client().namespace
        def xNomadToken = config.client().token
        def idempotencyToken = ""

        def jobRegisterRequest = new JobRegisterRequest()
                .job(taskJob)
                .region(region)
                .secretID(xNomadToken)
                .namespace(namespace)
                .enforceIndex(false)
                .evalPriority(10)
                .jobModifyIndex(1)
                .policyOverride(true)
                .preserveCounts(false)

        def apiInstance = new JobsApi(client)

        def response = apiInstance.postJob(taskJob.name,
                jobRegisterRequest,
                region,
                namespace,
                xNomadToken,
                idempotencyToken)


        return new NomadTaskKey(taskJob.name, taskJob.name)
    }


    protected Job createJobDef(String taskId, TaskRun task) {

        final container = task.getContainer()
        if (!container)
            throw new IllegalArgumentException("Missing container image for process: $task.name")

        log.trace "[NOMAD] Submitting task: $taskId, cpus=${task.config.getCpus()}, mem=${task.config.getMemory() ?: '-'}"


        def dataCenter = config.client().dataCenter
        def driver = config.client().driver
        def jobType = config.client().jobType

        def taskDef = new Task()
                .driver(driver)
                .config(["image"  : task.container,
                         "command": task.script,
                         "args"   : ["hello-nomad"]])
                .name(taskId)

        def taskGroup = new TaskGroup()
                .addTasksItem(taskDef)
                .name(taskId)

        def jobDef = new Job()
                .taskGroups([taskGroup])
                .type(jobType)
                .datacenters([dataCenter])
                .name(taskId)
                .ID(taskId)


        return jobDef

    }




    void terminate(NomadTaskKey key) {
        //apply(() -> client.taskOperations().terminateTask(key.jobId, key.taskId))
    }

    void deleteTask(NomadTaskKey key) {
        //apply(() -> client.taskOperations().deleteTask(key.jobId, key.taskId))
    }

    @Override
    void close() throws IOException {

    }


}