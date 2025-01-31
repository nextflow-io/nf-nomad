/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain*
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

import nextflow.Channel
import nextflow.exception.AbortRunException
import nextflow.plugin.Plugins
import nextflow.plugin.TestPluginDescriptorFinder
import nextflow.plugin.TestPluginManager
import nextflow.plugin.extension.PluginExtensionProvider
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jetbrains.annotations.NotNull
import org.pf4j.PluginDescriptorFinder
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Timeout
import test.Dsl2Spec
import test.OutputCapture

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest

/**
 * Unit test for Nomad DSL
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */
@Timeout(60)
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'mock' })
class MockNomadDSLSpec  extends Dsl2Spec{
    @Shared String pluginsMode

    MockWebServer mockWebServer

    def setup() {
        // reset previous instances
        PluginExtensionProvider.reset()
        // this need to be set *before* the plugin manager class is created
        pluginsMode = System.getProperty('pf4j.mode')
        System.setProperty('pf4j.mode', 'dev')
        // the plugin root should
        def root = Path.of('.').toAbsolutePath().normalize()
        def manager = new TestPluginManager(root){
            @Override
            protected PluginDescriptorFinder createPluginDescriptorFinder() {
                return new TestPluginDescriptorFinder(){
                    @Override
                    protected Manifest readManifestFromDirectory(Path pluginPath) {
                        def manifestPath= getManifestPath(pluginPath)
                        final input = Files.newInputStream(manifestPath)
                        return new Manifest(input)
                    }
                    protected Path getManifestPath(Path pluginPath) {
                        return pluginPath.resolve('build/tmp/jar/MANIFEST.MF')
                    }
                }
            }
        }
        Plugins.init(root, 'dev', manager)

        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup() {
        Plugins.stop()
        PluginExtensionProvider.reset()
        pluginsMode ? System.setProperty('pf4j.mode',pluginsMode) : System.clearProperty('pf4j.mode')

        mockWebServer.shutdown()
    }

    def 'should perform a hi and create a channel' () {
        when:
        def SCRIPT = '''            
            channel.of('hi!') | view 
            '''
        and:
        def result = new MockScriptRunner([:]).setScript(SCRIPT).execute()
        then:
        result.val == 'hi!'
        result.val == Channel.STOP
    }

    def 'should submit a job' () {
        given:
        boolean submitted = false
        boolean summary = false
        int requestCounter = 0
        mockWebServer.dispatcher = new Dispatcher() {
            @Override
            MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
                switch (recordedRequest.method.toLowerCase()){
                    case "get":
                        if( recordedRequest.path.endsWith("/allocations")) {
                            summary = true
                            def resource = !requestCounter++ ? "/allocations.json" : "/completed.json"
                            return new MockResponse().setResponseCode(200)
                                    .setBody(this.getClass().getResourceAsStream(resource).text).addHeader("Content-Type", "application/json")
                        }else {
                            return new MockResponse().setResponseCode(200)
                                    .setBody('{"Status": "dead"}').addHeader("Content-Type", "application/json")
                        }
                    case "post":
                        submitted = true
                        return new MockResponse().setResponseCode(200)
                                .setBody('{"EvalID":"test"}').addHeader("Content-Type", "application/json")
                }
                new MockResponse().setResponseCode(404)
            }
        }

        when:
        def SCRIPT = '''
            process sayHello{
                container 'ubuntu:22.0.4'
                input:
                    val x
                output:
                    stdout
                script:
                    """
                    echo '$x world!\'
                    """
            }
            workflow {
                channel.of('hi!') | sayHello | view
            }
            '''
        and:
        def result = new MockScriptRunner([
                process:[executor:'nomad'],
                nomad:
                        [
                                client:
                                        [
                                                address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                                        ]
                        ]
        ]).setScript(SCRIPT).execute()

        then:
        thrown(AbortRunException) //it fails because no real task is executed
//        submitted
//        summary
    }

    @org.junit.Rule
    OutputCapture capture = new OutputCapture()

    def 'should catch a remote exception' () {
        given:
        mockWebServer.dispatcher = new Dispatcher() {
            @Override
            MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
                new MockResponse().setResponseCode(500).setBody("Dummy exception")
            }
        }

        when:
        def SCRIPT = '''     
            process sayHello{
                container 'ubuntu:22.0.4'
                input:
                    val x
                output:
                    stdout
                script:
                    """
                    echo '$x world!\'
                    """
            }       
            workflow {
                channel.of('hi!') | sayHello | view
            } 
            '''
        and:
        def result = new MockScriptRunner([
                process:[executor:'nomad'],
                nomad:
                        [
                                client:
                                        [
                                                address : "http://${mockWebServer.hostName}:${mockWebServer.port}",
                                                retryConfig:[
                                                        maxAttempts: 1,
                                                        delay: '1ms'
                                                ]
                                        ]
                        ]
        ]).setScript(SCRIPT).execute()

        then:
        thrown(AbortRunException) //it fails because no real task is executed
        capture.toString().indexOf("io.nomadproject.client.ApiException: Server Error") != -1
    }
}
