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
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.nomad.NomadHelper
import nextflow.nomad.client.NomadClient
import nextflow.nomad.client.NomadResponseJson
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.model.NomadJobBuilder
import nextflow.processor.TaskRun

/**
 * Nomad Service
 *
 * Tip: Use the following command to find out your kubernetes master node
 *    nomad node status
 *
 * See
 *   https://developer.hashicorp.com/nomad/api-docs/jobs
 *
 * Useful cheatsheet
 *   https://developer.hashicorp.com/nomad/docs/commands
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
    protected NomadClient getClient() {
        new NomadClient(config)
    }

    @Override
    void close() {
        Global.onCleanup((it) -> client.closeConnection())

    }


    NomadResponseJson jobList(String namespace = "default") {
        final endpoint = "/jobs?$namespace"
        final resp = client.get(endpoint)
        return new NomadResponseJson(resp.stream)
    }

    NomadResponseJson jobSummary(String jobName, String namespace = "default") {
        final endpoint = "/job/${jobName}/summary?$namespace"
        final resp = client.get(endpoint)
        return new NomadResponseJson(resp.stream)
    }

    NomadResponseJson jobStatus(String jobName, String namespace = "default") {
        final endpoint = "/job/${jobName}/summary?$namespace"
        final resp = client.get(endpoint)
        return new NomadResponseJson(resp.stream)
    }

    NomadResponseJson jobPurge(String jobName, String namespace = "default") {
        final endpoint = "/job/$jobName?purge=true"
        final resp = client.delete(endpoint)
        return new NomadResponseJson(resp.stream)
    }

    NomadResponseJson jobSubmit(TaskRun task, String jobName, String namespace = "default") {
        final endpoint = "/jobs?$namespace"
        final resp = client.post(endpoint, createJob(task, jobName))
        return new NomadResponseJson(resp.stream)
    }

    protected static String createJob (TaskRun task, String jobName) {

        def jobJson = new NomadJobBuilder()
            .withJobName(jobName)
            .withImageName(task.container)
            .buildAsJson()

        return  jobJson
    }


//    NomadResponseJson serverStatus() {
//        final endpoint = "/status/leader"
//        final resp = client.get(endpoint)
//        return new NomadResponseJson(resp.stream)
//    }


}

