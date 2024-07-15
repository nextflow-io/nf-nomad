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

package nextflow.nomad.models

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import nextflow.executor.Executor
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.executor.NomadService
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit test for Nomad Service
 *
 * Validate requests using a Mock WebServer
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */
class JobConstraintsSpec extends Specification{

    MockWebServer mockWebServer

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    void "submit a task with a node constraint"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        constraints: {
                            node {
                                unique = [name:'test']
                            }
                        }
                ]
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "/a/b/c"
        Map<String, String>env = [test:"test"]

        def mockTask = Mock(TaskRun){
            getName() >> name
            getContainer() >> image
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> workingDir
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
            }
            getWorkDir() >> Path.of(workingDir)
            toTaskBean() >> Mock(TaskBean){
                getWorkDir() >> Path.of(workingDir)
                getScript() >> "theScript"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, mockTask, args, env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].LTarget == '${node.unique.name}'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].RTarget == 'test'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].Operand == '='
    }

    void "submit a task with a config attr constraint"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        constraints: {
                            attr {
                                cpu = [arch:'286']
                            }
                        }
                ]
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "/a/b/c"
        Map<String, String>env = [test:"test"]

        def mockTask = Mock(TaskRun){
            getName() >> name
            getContainer() >> image
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> workingDir
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
            }
            getWorkDir() >> Path.of(workingDir)
            toTaskBean() >> Mock(TaskBean){
                getWorkDir() >> Path.of(workingDir)
                getScript() >> "theScript"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, mockTask, args, env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].LTarget == '${attr.cpu.arch}'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].RTarget == '286'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].Operand == '='
    }

    void "submit a task with an attr constraint"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "/a/b/c"
        Map<String, String>env = [test:"test"]

        def contraints = {
            attr {
                cpu = [arch:'286']
            }
        }

        def mockTask = Mock(TaskRun){
            getName() >> name
            getContainer() >> image
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> workingDir
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
                getConfig() >> Mock(ProcessConfig){
                    get("constraints") >> contraints
                }
            }
            getWorkDir() >> Path.of(workingDir)
            toTaskBean() >> Mock(TaskBean){
                getWorkDir() >> Path.of(workingDir)
                getScript() >> "theScript"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, mockTask, args, env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].LTarget == '${attr.cpu.arch}'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].RTarget == '286'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].Operand == '='
    }

    void "submit a task with a raw attr constraint"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
        )
        def service = new NomadService(config)

        String id = "theId"
        String name = "theName"
        String image = "theImage"
        List<String> args = ["theCommand", "theArgs"]
        String workingDir = "/a/b/c"
        Map<String, String>env = [test:"test"]

        def contraints = {
            attr {
                raw 'platform.aws.instance-type', '=', 'm4.xlarge'
            }
        }

        def mockTask = Mock(TaskRun){
            getName() >> name
            getContainer() >> image
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> workingDir
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
                getConfig() >> Mock(ProcessConfig){
                    get("constraints") >> contraints
                }
            }
            getWorkDir() >> Path.of(workingDir)
            toTaskBean() >> Mock(TaskBean){
                getWorkDir() >> Path.of(workingDir)
                getScript() >> "theScript"
                getShell() >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(["EvalID":"test"]).toString())
                .addHeader("Content-Type", "application/json"));
        when:

        def idJob = service.submitTask(id, mockTask, args, env)
        def recordedRequest = mockWebServer.takeRequest();
        def body = new JsonSlurper().parseText(recordedRequest.body.readUtf8())

        then:
        idJob

        and:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/jobs"

        and:
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].LTarget == '${attr.platform.aws.instance-type}'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].RTarget == 'm4.xlarge'
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].Operand == '='
    }
}
