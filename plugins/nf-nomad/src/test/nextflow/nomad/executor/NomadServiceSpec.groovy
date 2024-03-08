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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import nextflow.nomad.NomadConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Specification

/**
 * Unit test for Nomad Service
 *
 * Validate requests using a Mock WebServer
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */
class NomadServiceSpec extends Specification{

    MockWebServer mockWebServer

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    void "submit a task"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ]
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "theWorkingDir"
        Map<String, String>env = [test:"test"]

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, name, image, args, workingDir,env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job
        body.Job.ID == id
        body.Job.Name == name
        body.Job.Datacenters == []
        body.Job.Type == "batch"
        body.Job.TaskGroups.size() == 1
        body.Job.TaskGroups[0].Name == "group"
        body.Job.TaskGroups[0].Tasks.size() == 1
        body.Job.TaskGroups[0].Tasks[0].Name == "nf-task"
        body.Job.TaskGroups[0].Tasks[0].Driver == "docker"
        body.Job.TaskGroups[0].Tasks[0].Config.image == image
        body.Job.TaskGroups[0].Tasks[0].Config.work_dir == workingDir
        body.Job.TaskGroups[0].Tasks[0].Config.command == args[0]
        body.Job.TaskGroups[0].Tasks[0].Config.args == args.drop(1)

        !body.Job.TaskGroups[0].Tasks[0].Config.mount
    }

    void "submit a task with docker volume"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        dockerVolume:'test'
                ]
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "a/b/c"
        Map<String, String>env = [test:"test"]

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, name, image, args, workingDir,env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job
        body.Job.ID == id
        body.Job.Name == name
        body.Job.Datacenters == []
        body.Job.Type == "batch"
        body.Job.TaskGroups.size() == 1
        body.Job.TaskGroups[0].Name == "group"
        body.Job.TaskGroups[0].Tasks.size() == 1
        body.Job.TaskGroups[0].Tasks[0].Name == "nf-task"
        body.Job.TaskGroups[0].Tasks[0].Driver == "docker"
        body.Job.TaskGroups[0].Tasks[0].Config.image == image
        body.Job.TaskGroups[0].Tasks[0].Config.work_dir == workingDir
        body.Job.TaskGroups[0].Tasks[0].Config.command == args[0]
        body.Job.TaskGroups[0].Tasks[0].Config.args == args.drop(1)

        body.Job.TaskGroups[0].Tasks[0].Config.mount == [type:"volume", target:"a", source:"test", readonly:false]
    }

    void "should check the state"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ]
        )
        def service = new NomadService(config)

        when:
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json"));

        def state = service.state("theId")
        def recordedRequest = mockWebServer.takeRequest();

        then:
        recordedRequest.method == "GET"
        recordedRequest.path == "/v1/job/theId/summary"

        and:
        state == "Unknown"

        when:
        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["JobID":"test","Summary":[
                        test:[Starting:1]
                ]]).toString())
                .addHeader("Content-Type", "application/json"));

        state = service.state("theId")
        recordedRequest = mockWebServer.takeRequest();

        then:
        recordedRequest.method == "GET"
        recordedRequest.path == "/v1/job/theId/summary"

        and:
        state == "Starting"

    }

    void "submit a task with a volume"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        volume: { type "csi" name "test" }
                ]
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "a/b/c"
        Map<String, String>env = [test:"test"]

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, name, image, args, workingDir,env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job
        body.Job.ID == id
        body.Job.Name == name
        body.Job.Datacenters == []
        body.Job.Type == "batch"
        body.Job.TaskGroups.size() == 1
        body.Job.TaskGroups[0].Name == "group"
        body.Job.TaskGroups[0].Tasks.size() == 1
        body.Job.TaskGroups[0].Tasks[0].Name == "nf-task"
        body.Job.TaskGroups[0].Tasks[0].Driver == "docker"
        body.Job.TaskGroups[0].Tasks[0].Config.image == image
        body.Job.TaskGroups[0].Tasks[0].Config.work_dir == workingDir
        body.Job.TaskGroups[0].Tasks[0].Config.command == args[0]
        body.Job.TaskGroups[0].Tasks[0].Config.args == args.drop(1)

        body.Job.TaskGroups[0].Volumes.size() == 1
        body.Job.TaskGroups[0].Volumes['test'] == [AccessMode:"multi-node-multi-writer", AttachmentMode:"file-system", Source:"test", Type:"csi"]
        body.Job.TaskGroups[0].Tasks[0].VolumeMounts.size() == 1
        body.Job.TaskGroups[0].Tasks[0].VolumeMounts[0] == [Destination:"a", Volume:"test"]

    }

        void "should send the token"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}",
                        token: "1234"
                ],
                jobs:[
                        dockerVolume:'test'
                ]
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "a/b/c"
        Map<String, String>env = [test:"test"]

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, name, image, args, workingDir,env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"
        recordedRequest.headers.values('X-Nomad-Token').first()=='1234'
    }
}
