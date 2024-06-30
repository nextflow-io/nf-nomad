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

import nextflow.exception.ProcessSubmitException
import nextflow.executor.Executor
import nextflow.nomad.config.NomadConfig
import nextflow.processor.*
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit test for Nomad Task Handler
 *
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */
class NomadTaskHandlerSpec extends Specification{

    void "a task should have a container"(){
        given:
        def mockTask = Mock(TaskRun){
            getWorkDir() >> Path.of(".")
            getContainer() >> null
            getProcessor() >> Mock(TaskProcessor)
        }
        def mockConfig = Mock(NomadConfig)
        def mockService = Mock(NomadService)
        def taskHandler = new NomadTaskHandler(mockTask, mockConfig, mockService)

        when:
        taskHandler.submitTask()

        then:
        thrown(ProcessSubmitException)
    }

    void "a task should be created"(){
        given:
        def workDir = Files.createTempDirectory("nf")
        new File(workDir.toFile(), TaskRun.CMD_INFILE).text = "infile"

        def mockTask = Mock(TaskRun){
            getConfig() >> Mock(TaskConfig)
            getWorkDir() >> workDir
            getContainer() >> "ubuntu"
            getProcessor() >> Mock(TaskProcessor){
                getExecutor() >> Mock(Executor){
                    isFusionEnabled() >> false
                }
            }
            toTaskBean() >> Mock(TaskBean) {
                    getWorkDir() >> workDir
                    getScript() >> "theScript"
                    getShell() >> ["bash"]
                    getInputFiles() >> [:]
                    getOutputFiles() >> ['dont_know_why_is_required_in_test']
            }
        }
        def mockConfig = Mock(NomadConfig)
        def mockService = Mock(NomadService)
        def taskHandler = new NomadTaskHandler(mockTask, mockConfig, mockService)

        when:
        def ret = taskHandler.submitTask()

        then:
        ret == TaskStatus.SUBMITTED.name()
    }
}
