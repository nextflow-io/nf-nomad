//package nextflow.nomad.executor
//
//import io.nomadproject.client.api.JobsApi
//import io.nomadproject.client.models.Job
//import io.nomadproject.client.models.JobRegisterRequest
//import io.nomadproject.client.models.Task
//import io.nomadproject.client.models.TaskGroup
//import nextflow.nomad.config.NomadConfig
//
///*
// * Copyright 2023, Stellenbosch University, South Africa
// * Copyright 2022, Center for Medical Genetics, Ghent
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//
///**
// * Model a Nomad job definition
// *
// * @author Abhinav Sharma <abhi18av@outlook.com>
// */
//
//class NomadTaskJob {
//
//    def RANDOM_ID = Math.abs(new Random().nextInt() % 999) + 1
//    def TASK_NAME = "task-name-$RANDOM_ID"
//    def TASK_GROUP_NAME = "task-group-$RANDOM_ID"
//    def JOB_NAME = "job-$RANDOM_ID"
//
//
//    def clientConfig = NomadConfig.getConfig(session).client()
//
//    def defaultClient = new NomadClient(clientConfig)
//
//        def defaultClient = Configuration
//                .getDefaultApiClient()
//                .setBasePath(NOMAD_ADDR)
//
//    def region = clientConfig.region
//    def namespace = clientConfig.namespace
//    def dataCenter = clientConfig.dataCenter
//    def driver = clientConfig.driver
//    def jobType = clientConfig.jobType
//    def xNomadToken = NOMAD_TOKEN
//    def index = 56
//    def wait = ""
//    def stale = ""
//    def prefix = ""
//    def tokenAccessor = ""
//    def perPage = 56
//    def nextToken = ""
//    def idempotencyToken = ""
//
//    def result
//
//    def taskDef = new Task()
//            .driver(driver)
//            .config([ "image": "quay.io/nextflow/rnaseq-nf:v1.1",
//                      "command": "echo",
//                      "args": ["hello-nomad"]])
//            .name(TASK_NAME)
//
//    def taskGroup = new TaskGroup()
//            .addTasksItem(taskDef)
//            .name(TASK_GROUP_NAME)
//
//    def jobDef = new Job()
//            .taskGroups([taskGroup])
//            .type(jobType)
//            .datacenters([dataCenter])
//            .name(JOB_NAME)
//            .ID(JOB_NAME)
//
//}
