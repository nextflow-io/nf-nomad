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
package nextflow.nomad.builders

import nextflow.nomad.config.NomadConfig
import nextflow.nomad.config.NomadJobOpts
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Specification

/**
 * Unit test for Nomad JobBuilder
 *
 * @author : Abhinav Sharma <abhi18av@outlook.com>
 */


class JobBuilderSpec extends Specification {

    def "test JobBuilder withId method"() {
        given:
        def jobBuilder = new JobBuilder()

        when:
        def jb = jobBuilder
                .withId("test-id")
                .build()

        then:
        jb.ID == "test-id"
    }


    def "test createTask method"() {
        given:
        def jobOpts = Mock(NomadJobOpts)
        def taskRun = Mock(TaskRun)
        def args = ["arg1", "arg2"]
        def env = ["key": "value"]

        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getConfig() >> [cpus: 2, memory: "1GB"]

        when:
        def task = JobBuilder.createTask(taskRun, args, env, jobOpts)

        then:
        task.name == "nf-task"
        task.driver == "docker"
        task.config.image == "test-container"
        task.config.command == "arg1"
        task.config.args == ["arg2"]
        task.env == env
        task.resources.cores == 2
        task.resources.memoryMB == 1024
    }


    def "test createTaskGroup method"() {
        given:
        def volumes = [{ type "csi" path "/container/path"}]

        def jobOpts = Mock(NomadJobOpts)

        def taskRun = Mock(TaskRun)
        def args = ["arg1", "arg2"]
        def env = ["key": "value"]

        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getConfig() >> [cpus: 2, memory: "1GB"]

        when:
        def taskGroup = JobBuilder.createTaskGroup(taskRun, args, env, jobOpts)

        then:
        taskGroup.name == "group"
        taskGroup.tasks.size() == 1
        taskGroup.tasks[0].name == "nf-task"
        taskGroup.tasks[0].config.image == "test-container"
        taskGroup.tasks[0].config.command == "arg1"
        taskGroup.tasks[0].config.args == ["arg2"]
        taskGroup.tasks[0].env == env
    }

    def "test priority in task"(){
        given:
        def taskRun = Mock(TaskRun)
        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getProcessor() >> Mock(TaskProcessor) {
            getConfig() >> [priority: 666]
        }

        when:
        def job = new JobBuilder().withPriority(taskRun).build()

        then:
        job.priority == 666
    }

}
