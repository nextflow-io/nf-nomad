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
import io.nomadproject.client.api.AclApi
import io.nomadproject.client.api.JobsApi
import io.nomadproject.client.api.NodesApi
import io.nomadproject.client.auth.ApiKeyAuth
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.JobPlanRequest
import io.nomadproject.client.models.JobRegisterRequest
import io.nomadproject.client.models.JobValidateRequest
import io.nomadproject.client.models.JobsParseRequest
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.TaskGroup
import nextflow.nomad.config.NomadClientOpts
import nextflow.nomad.config.NomadConfig

import java.util.function.Predicate
import com.google.common.hash.HashCode
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.MemoryUnit
import spock.lang.Specification

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadClientTest extends Specification {
    def DEV_TOKEN_ACCESSOR = "c7dcbc2f-8384-6990-f3aa-095336dada7a";
    def DEV_TOKEN_SECRET = "85a3fce7-52f4-18c6-1045-5a40be79ed20";

    def 'should create a client and check status'() {

        given:
        def defaultClient = Configuration
                .getDefaultApiClient()
                .setBasePath(NomadClientOpts.DEFAULT_BASE_PATH)


        def region = "";
        def namespace = "";
        def index = 56;
        def wait = "";
        def stale = "";
        def prefix = "";
        def tokenAccessor = DEV_TOKEN_ACCESSOR
        def xNomadToken = DEV_TOKEN_SECRET
        def perPage = 56;
        def nextToken = "";
        def idempotencyToken = ""
        def result

//        def apiInstance = new AclApi(defaultClient);
//        def result = apiInstance.getACLToken(tokenAccessor, region, namespace, index, wait, stale, prefix, xNomadToken, perPage, nextToken);

//        def apiInstance = new NodesApi(defaultClient);
//        def result = apiInstance.getNodes(region, namespace, index, wait, stale, prefix, xNomadToken, perPage, nextToken, true);


//        def apiInstance = new JobsApi(defaultClient);
//        def result = apiInstance.getJobs(region, namespace, index, wait, stale, prefix, xNomadToken, perPage, nextToken);


        def taskDef = new Task()
                .driver("exec")
                .config(["command": "/bin/http-echo"])
                .name("task-name")

        def taskGroup = new TaskGroup()
                .addTasksItem(taskDef)
                .name("task-group")

        def jobDef = new Job()
                .type("batch")
                .datacenters(["dc1"])
                .name("http-echo")
                .taskGroups([taskGroup])
                .ID("temp-job-id")

        def apiInstance = new JobsApi(defaultClient);

        def jobValidateRequest = new JobValidateRequest()
                .job(jobDef)
                .namespace(namespace)
                .secretID(DEV_TOKEN_ACCESSOR)
                .region(region)


        result = apiInstance.postJobValidateRequest(jobValidateRequest, region, namespace, xNomadToken, idempotencyToken);
        println(result);

//        def jobRegisterRequest = new JobRegisterRequest()
//                .job(jobDef)
//                .region(region)
//                .namespace(namespace)
//                .secretID(DEV_TOKEN_SECRET)
//
//        result = apiInstance.postJob(jobDef.name, jobRegisterRequest, region, namespace, xNomadToken, idempotencyToken)


//        def jobPlan = new JobPlanRequest()
//                .secretID(DEV_TOKEN_SECRET)
//                .job(jobDef)
//
//        result = apiInstance.postJobPlan(jobDef.name, jobPlan, region, namespace, xNomadToken, idempotencyToken)


        println(result);

    }

}
