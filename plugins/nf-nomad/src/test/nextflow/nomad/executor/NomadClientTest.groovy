/*
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

import io.nomadproject.client.Configuration
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.JobRegisterRequest
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.TaskGroup
import spock.lang.Specification

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadClientTest extends Specification {

    def DEV_NOMAD_TOKEN_ACCESSOR = System.getenv("DEV_NOMAD_TOKEN_ACCESSOR")
    def DEV_NOMAD_TOKEN_SECRET = System.getenv("DEV_NOMAD_TOKEN_SECRET")
    def DEV_NOMAD_BASE_PATH = System.getenv("DEV_NOMAD_BASE_PATH")

    def 'should create a client and submit a job'() {

        given:
        def defaultClient = Configuration
                .getDefaultApiClient()
                .setBasePath(DEV_NOMAD_BASE_PATH)


        def region = "";
        def namespace = "";
        def index = 56;
        def wait = "";
        def stale = "";
        def prefix = "";
        def tokenAccessor = DEV_NOMAD_TOKEN_ACCESSOR
        def xNomadToken = DEV_NOMAD_TOKEN_SECRET
        def perPage = 56;
        def nextToken = "";
        def idempotencyToken = ""
        def result

        and:
        def taskDef = new Task()
                .driver("exec")
                .config(["command": "/bin/echo", "args": ["hello-nomad"]])
                .name("task-name")

        def taskGroup = new TaskGroup()
                .addTasksItem(taskDef)
                .name("task-group")

        def jobDef = new Job()
                .taskGroups([taskGroup])
                .type("batch")
                .datacenters(["dc1"])
                .name("hello-nomad")
                .ID("hello-nomad")

        def jobRegisterRequest = new JobRegisterRequest()
                .job(jobDef)
                .enforceIndex(false)
                .evalPriority(10)
                .jobModifyIndex(1)
                .namespace("")
                .policyOverride(true)
                .preserveCounts(false)
                .region("")
                .secretID(xNomadToken)


        def apiInstance = new JobsApi(defaultClient);
        result = apiInstance.postJob("hello-nomad", jobRegisterRequest, region, namespace, xNomadToken, idempotencyToken)

        println(result);

    }

}
