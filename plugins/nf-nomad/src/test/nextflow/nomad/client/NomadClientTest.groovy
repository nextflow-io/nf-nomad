/*
 * Copyright 2013-2023, Seqera Labs
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

package nextflow.nomad.client

import nextflow.Session
import nextflow.exception.K8sOutOfCpuException
import nextflow.exception.K8sOutOfMemoryException
import nextflow.exception.NodeTerminationException
import nextflow.k8s.client.K8sClient
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.executor.NomadService
import spock.lang.Specification

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadClientTest extends Specification {


    def 'should create a request'() {

        given:
        def NOMAD_SERVER = "http://nomad.api/v1"
        def NOMAD_SERVER_ENDPOINT = "/status/leader"
        def CONFIG_MAP = [nomad: [client: [server: NOMAD_SERVER, token: "abcdefghijklmnop"]]]

        def session = Mock(Session) {
            getConfig() >> CONFIG_MAP
        }

        def config = NomadConfig.getConfig(session)

        def client = Spy(new NomadClient(config))

        def HTTP_CONN = Mock(HttpURLConnection)

        when:
        client.makeRequest('GET', NOMAD_SERVER_ENDPOINT)

        then:
        1 * client.createConnection0(config.client().server + NOMAD_SERVER_ENDPOINT) >> HTTP_CONN
        1 * HTTP_CONN.setRequestMethod('GET') >> null
        1 * HTTP_CONN.getResponseCode() >> 401
        1 * HTTP_CONN.getErrorStream() >> { new ByteArrayInputStream('{"field_x":"oops.."}'.bytes) }
        def e = thrown(NomadResponseException)
        e.response.field_x == 'oops..'

    }

    def 'should make a GET request'() {
        given:
        def CONFIG_MAP = [nomad: [client: [ token: "abcdefghijklmnop"]]]

        def session = Mock(Session) {
            getConfig() >> CONFIG_MAP
        }

        def config = NomadConfig.getConfig(session)

        def client = Spy(new NomadClient(config))

        when:
        def resp = client.get('/status/leader')

        then:
        1 * client.makeRequest('GET', '/status/leader') >> null
    }


    def 'should make a POST request'() {

        given:
        def CONFIG_MAP = [nomad: [client: [token: "abcdefghijklmnop"]]]

        def session = Mock(Session) {
            getConfig() >> CONFIG_MAP
        }

        def config = NomadConfig.getConfig(session)
        def client = new NomadClient(config)
        def spyClient = Spy(new NomadClient(config))

        when:
//        client.post('/client/metadata', '{ "Meta": { "foo" : "bar" } }')
        spyClient.post('/client/metadata', '{ "Meta": { "foo" : "bar" } }')

        then :
        1 * spyClient.makeRequest ( 'POST', '/client/metadata', '{ "Meta": { "foo" : "bar" } }' ) >> null

    }



    def 'should make a PUT request'() {

        given:
        def CONFIG_MAP = [nomad: [client: [token: "abcdefghijklmnop"]]]

        def session = Mock(Session) {
            getConfig() >> CONFIG_MAP
        }

        def config = NomadConfig.getConfig(session)
        def client = Spy(new NomadClient(config))
        def spyClient = Spy(new NomadClient(config))

        when:
//        client.put('/var/foo', '{ "Path": "foo", "Items": { "foo" : "bar" } }')
        spyClient.put('/var/foo', '{ "Path": "foo", "Items": { "foo" : "bar" } }')

        then :
        1 * spyClient.makeRequest ( 'PUT', '/var/foo', '{ "Path": "foo", "Items": { "foo" : "bar" } }' ) >> null

    }

    def 'should make a DELETE request'() {

        given:
        def CONFIG_MAP = [nomad: [client: [token: "abcdefghijklmnop"]]]

        def session = Mock(Session) {
            getConfig() >> CONFIG_MAP
        }

        def config = NomadConfig.getConfig(session)
        def client = Spy(new NomadClient(config))
        def spyClient = Spy(new NomadClient(config))

        when:
//        client.delete('/var/foo', '{ "Path": "foo", "Items": { "foo" : "bar" } }')
        spyClient.delete('/var/foo', '{ "Path": "foo", "Items": { "foo" : "bar" } }')

        then :
        1 * spyClient.makeRequest ( 'DELETE', '/var/foo', '{ "Path": "foo", "Items": { "foo" : "bar" } }' ) >> null

}


}
