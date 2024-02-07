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

package nextflow.nomad

import io.nomadproject.client.Configuration
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.JobRegisterRequest
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.TaskGroup
import nextflow.Session
import nextflow.nomad.config.NomadConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Specification

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class Scratch extends Specification {

    MockWebServer mockWebServer

    def setup(){
        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup(){
        mockWebServer.shutdown()
    }

    def 'should create a client and submit a job'() {

        given:
        mockWebServer.enqueue(new MockResponse().setBody('{}').addHeader("Content-Type", "application/json"))

        def RANDOM_ID = Math.abs(new Random().nextInt() % 999) + 1
        def NF_TASKJOB_NAME =  "nf-scratch-$RANDOM_ID"

        def session = Mock(Session) {
            getConfig() >> [nomad:
                                    [client:
                                             [
                                                     address : "http://127.0.0.1:${mockWebServer.port}",
                                                     dataCenter: "test"
                                             ]
                                    ]
            ]
        }

        when:
        def clientConfig = NomadConfig.getConfig(session).client()

        def defaultClient = Configuration
                .getDefaultApiClient()
                .setBasePath(clientConfig.address)

        def region = clientConfig.region
        def namespace = clientConfig.namespace
        def dataCenter = clientConfig.dataCenter
        def driver = clientConfig.driver
        def jobType = clientConfig.jobType
        def xNomadToken = clientConfig.token
        def index = 56
        def wait = ""
        def stale = ""
        def prefix = ""
        def tokenAccessor = ""
        def perPage = 56
        def nextToken = ""
        def idempotencyToken = ""

        def result

        and:
        def taskDef = new Task()
                .driver(driver)
                .config([ "image": "quay.io/nextflow/rnaseq-nf:v1.1",
                          "command": "echo", 
                          "args": ["hello-nomad"]])
                .name(NF_TASKJOB_NAME)

        def taskGroup = new TaskGroup()
                .addTasksItem(taskDef)
                .name(NF_TASKJOB_NAME)

        def jobDef = new Job()
                .taskGroups([taskGroup])
                .type(jobType)
                .datacenters([dataCenter])
                .name(NF_TASKJOB_NAME)
                .ID(NF_TASKJOB_NAME)

        println(jobDef)

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

        def apiInstance = new JobsApi(defaultClient)
        result = apiInstance.postJob(NF_TASKJOB_NAME, jobRegisterRequest, region, namespace, xNomadToken, idempotencyToken)
        println result

        def request = mockWebServer.takeRequest()

        then:
        result
        request.method == "POST"
        request.path.startsWith("/v1/job/nf-scratch-")
    }

}
