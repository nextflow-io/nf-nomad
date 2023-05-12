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

    Map<TaskProcessor, String> allJobIds = new HashMap<>(50)

    synchronized String getOrCreateJob(TaskRun task) {
        final mapKey = task.processor
        if (allJobIds.containsKey(mapKey)) {
            return allJobIds[mapKey]
        }

        // create a batch job
        final jobId = makeJobId(task)

//        apply(() -> client .jobOperations() .createJob(jobId, poolInfo))

        // add to the map
        allJobIds[mapKey] = jobId
        return jobId
    }


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


//    NomadTaskKey submitTask(TaskRun task) {
//        final jobId = getOrCreateJob(task)
//        runTask(jobId, task)
//    }
//
//    NomadTaskKey runTask(String jobId, TaskRun task) {
//        final taskToAdd = createTaskJob(jobId, task)
//        //apply(() -> client.taskOperations().createTask(jobId, taskToAdd))
//        return new NomadTaskKey(jobId, taskToAdd.id())
//    }
//

    protected createTaskJob(String jobId, TaskRun task) {

        final container = task.getContainer()
        if (!container)
            throw new IllegalArgumentException("Missing container image for process: $task.name")

        final taskId = "nf-${task.hash.toString()}"

        log.trace "[NOMAD] Submitting task: $taskId, cpus=${task.config.getCpus()}, mem=${task.config.getMemory() ?: '-'}"

        def region = config.client().region
        def namespace = config.client().namespace
        def dataCenter = config.client().dataCenter
        def driver = config.client().driver
        def jobType = config.client().jobType
        def xNomadToken = config.client().token
        def idempotencyToken = ""

        def taskDef = new Task()
                .driver(driver)
                .config([ "image": task.container,
                          "command": task.script,
                          "args": ["hello-nomad"]])
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

        def jobRegisterRequest = new JobRegisterRequest()
                .job(jobDef)
                .region(region)
                .secretID(xNomadToken)
                .namespace(namespace)
                .enforceIndex(false)
                .evalPriority(10)
                .jobModifyIndex(1)
                .policyOverride(true)
                .preserveCounts(false)

        def apiInstance = new JobsApi(client)
        def response = apiInstance.postJob(taskId, jobRegisterRequest, region, namespace, xNomadToken, idempotencyToken)

        return response

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

    String makeJobId(TaskRun task) {
        final name = task
                .processor
                .name
                .trim()
                .replaceAll(/[^a-zA-Z0-9-_]+/, '_')

        final String key = "job-${Rnd.hex()}-${name}"

        //FIXME Perhaps NOT needed
        // Nomad job max len is 64 characters, however we keep it a bit shorter
        // because the jobId + taskId composition must be less then 100
        final MAX_LEN = 62i
        return key.size() > MAX_LEN ? key.substring(0, MAX_LEN) : key
    }

}