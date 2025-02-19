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
import nextflow.executor.Executor
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Requires
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

@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'mock' })
class MockNomadServiceSpec extends Specification{

    MockWebServer mockWebServer

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup() {
        mockWebServer.shutdown()
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

        def state = service.getTaskState("theId")
        def recordedRequest = mockWebServer.takeRequest();

        then:
        recordedRequest.method == "GET"
        recordedRequest.path == "/v1/job/theId/allocations"

        and:
        state.state == "unknown"

        when:
        mockWebServer.enqueue(new MockResponse()
                .setBody(this.getClass().getResourceAsStream("/allocations.json").text)
                .addHeader("Content-Type", "application/json"));

        state = service.getTaskState("theId")
        recordedRequest = mockWebServer.takeRequest();

        then:
        recordedRequest.method == "GET"
        recordedRequest.path == "/v1/job/theId/allocations"

        and:
        state.state == "running"

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
        Map<String, String>env = [test:"test"]

        def mockTask = Mock(TaskRun){
            getName() >> name
            getContainer() >> image
            getConfig() >> Mock(TaskConfig)
            getWorkDirStr() >> "theWorkingDir"
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
            }
            getWorkDir() >> Path.of("/tmp")
            toTaskBean() >> Mock(TaskBean){
                getWorkDir() >> Path.of("/tmp")
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
        body.Job
        body.Job.ID == id
        body.Job.Name == name
        body.Job.Type == "batch"
        body.Job.TaskGroups.size() == 1
        body.Job.TaskGroups[0].Name == "nf-taskgroup"
        body.Job.TaskGroups[0].Tasks.size() == 1
        body.Job.TaskGroups[0].Tasks[0].Name == "nf-task"
        body.Job.TaskGroups[0].Tasks[0].Resources.Cores == 1
        body.Job.TaskGroups[0].Tasks[0].Resources.MemoryMB == 500
        body.Job.TaskGroups[0].Tasks[0].Driver == "docker"
        body.Job.TaskGroups[0].Tasks[0].Config.image == image
        body.Job.TaskGroups[0].Tasks[0].Config.work_dir == "/tmp"
        body.Job.TaskGroups[0].Tasks[0].Config.command == args[0]
        body.Job.TaskGroups[0].Tasks[0].Config.args == args.drop(1)

        !body.Job.TaskGroups[0].Tasks[0].Config.mount
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
        body.Job
        body.Job.ID == id
        body.Job.Name == name
        body.Job.Type == "batch"
        body.Job.TaskGroups.size() == 1
        body.Job.TaskGroups[0].Name == "nf-taskgroup"
        body.Job.TaskGroups[0].Tasks.size() == 1
        body.Job.TaskGroups[0].Tasks[0].Name == "nf-task"
        body.Job.TaskGroups[0].Tasks[0].Resources.Cores == 1
        body.Job.TaskGroups[0].Tasks[0].Resources.MemoryMB == 500
        body.Job.TaskGroups[0].Tasks[0].Driver == "docker"
        body.Job.TaskGroups[0].Tasks[0].Config.image == image
        body.Job.TaskGroups[0].Tasks[0].Config.work_dir == workingDir
        body.Job.TaskGroups[0].Tasks[0].Config.command == args[0]
        body.Job.TaskGroups[0].Tasks[0].Config.args == args.drop(1)

        body.Job.TaskGroups[0].Volumes.size() == 1
        body.Job.TaskGroups[0].Volumes['vol_0'] == [AccessMode:"multi-node-multi-writer", AttachmentMode:"file-system", Source:"test", Type:"csi", ReadOnly:false]
        body.Job.TaskGroups[0].Tasks[0].VolumeMounts.size() == 1
        body.Job.TaskGroups[0].Tasks[0].VolumeMounts[0] == [Destination:"/a", Volume:"vol_0"]

    }

    void "submit a task with an affinity"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        affinity: {
                            attribute '${meta.my_custom_value}'
                            operator  ">"
                            value     "3"
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
        body.Job.TaskGroups[0].Tasks[0].Affinities[0].LTarget == '${meta.my_custom_value}'
    }

    void "submit a task with a constraint"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        constraint: {
                            attribute '${meta.my_custom_value}'
                            operator  ">"
                            value     "3"
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
        body.Job.TaskGroups[0].Tasks[0].Constraints[0].LTarget == '${meta.my_custom_value}'
    }

    void "save the job spec if requested"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                debug:[
                        json: true
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

        def outputJson = Files.createTempFile("nomad",".json")
        when:

        def idJob = service.submitTask(id, mockTask, args, env, outputJson)

        then:
        idJob

        and:
        outputJson.text.indexOf(" Job {") != -1
    }

    void "submit a task with a datacenter directive"(){
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
                    get("datacenters") >> datacenter
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
        body.Job.Datacenters == valid

        where:
        datacenter      | valid
        "test"          | ["test"]
        ['b','a']       | ['b', 'a']
        1               | ['1']
        ({ 'a'*10 })    | ['aaaaaaaaaa']
    }

    void "submit a task with a spread"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs:[
                        spreads : {
                            spread = [name:'test', weight:50, targets:['a':30]]
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
        body.Job.Spreads[0].Attribute == 'test'
        body.Job.Spreads[0].Weight == 50
        body.Job.Spreads[0].SpreadTarget.first().Value == 'a'
        body.Job.Spreads[0].SpreadTarget.first().Percent == 30
    }
}
