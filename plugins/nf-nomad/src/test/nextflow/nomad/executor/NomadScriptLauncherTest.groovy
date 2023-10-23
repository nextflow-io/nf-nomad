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

import com.google.common.hash.HashCode
import nextflow.Session
import nextflow.executor.Executor
import nextflow.k8s.K8sWrapperBuilder
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.MemoryUnit
import spock.lang.Specification

import java.nio.file.Files

/**
 *
 * @author Abhinav Sharma  <abhi18av@outlook.com>
 */
class NomadScriptLauncherTest extends Specification {

    def 'test bash wrapper with input'() {
        given:
        def workDir = ('/home/abhinav/projects/nomad-testdir/_volume')

        def CONFIG_MAP = [nomad: [client: [namespace: "default"]]]

        and:
        def exec = Mock(NomadExecutor) {getConfig() >> new NomadConfig(CONFIG_MAP) }
        def svc = Spy(new NomadService(exec))

        def TASK = Mock(TaskRun) {
            getHash() >> HashCode.fromInt(1)
            getContainer() >> 'quay.io/nextflow/rnaseq-nf:v1.1'
            getScript() >> getClass().getResource("/ServiceTest.command.sh").text
            getConfig() >> Mock(TaskConfig) {
                getShell() >> ["bash"]
                getCpus() >> 4
                getMemory() >> new MemoryUnit("400.MB")
                getTime() >> new Duration("55s")
                getWorkDir() >> workDir
            }
            getProcessor() >> Mock(TaskProcessor) {
                getName() >> "svctest"
            }
        }

    }

/*
        when:
        def binding = new NomadScriptLauncher([
                name       : 'Hello 1',
                workDir    : workDir,
                script     : 'echo Hello world!',
                environment: [FOO: 1, BAR: 'any'],
                input      : (['Ciao ciao'] as TaskBean, executor).makeBinding()


        def 'should render command.run script' () {

        given:
        def folder = Files.createTempDirectory('test')
        and:
        def sess = Mock(Session)
        def exec = Mock(Executor)
        def proc = Mock(TaskProcessor) { getSession() >> sess; getExecutor() >> exec }
        def config = new TaskConfig()
        def task = Mock(TaskRun) {
            getName() >> 'foo'
            getConfig() >> config
            getProcessor() >> proc
            getWorkDir() >> folder
            getInputFilesMap() >> [:]
        }

        and:
        def builder = Spy(new K8sWrapperBuilder(task)) { getSecretsEnv() >> null; fixOwnership() >> false }

        when:
        def binding =  builder.makeBinding()

        then:
        binding.header_script == "NXF_CHDIR=${folder}"

        cleanup:
        folder?.deleteDir()
    }
*/

}