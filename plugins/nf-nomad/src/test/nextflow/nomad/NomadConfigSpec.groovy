/*
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

import spock.lang.Specification

/**
 * Unit test for Nomad Config
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */
class NomadConfigSpec extends Specification {

    void "should create a default config"() {
        given:
        def config = new NomadConfig()

        expect:
        config.jobOpts
        config.clientOpts
    }

    void "should use localhost as default address"() {
        given:
        def config = new NomadConfig([:])

        expect:
        config.clientOpts.address == "http://127.0.0.1:4646/v1"
    }

    void "should use address if provided"() {
        given:
        def config = new NomadConfig([
                client: [address: "http://nomad"]
        ])

        expect:
        config.clientOpts.address == "http://nomad/v1"
    }

    void "should normalize address if provided"() {
        given:
        def config = new NomadConfig([
                client: [address: "http://nomad/"]
        ])

        expect:
        config.clientOpts.address == "http://nomad/v1"
    }

    void "should use token if provided"() {
        given:
        def config = new NomadConfig([
                client: [token: "theToken"]
        ])

        expect:
        config.clientOpts.token == "theToken"
    }

    void "should use an empty list if no datacenters is provided"() {
        given:
        def config = new NomadConfig([
                jobs: [:]
        ])

        expect:
        !config.jobOpts.datacenters.size()
    }

    void "should use datacenters #dc with size #size if provided"() {
        given:
        def config = new NomadConfig([
                jobs: [datacenters: dc]
        ])

        expect:
        config.jobOpts.datacenters.size() == size

        where:
        dc             | size
        []             | 0
        "dc1"          | 1
        ['dc1']        | 1
        "dc1,dc2"      | 2
        ['dc1', 'dc2'] | 2
        ['dc1', 'dc1'] | 1
    }

    void "should use region if provided"() {
        given:
        def config = new NomadConfig([
                jobs: [region: "theRegion"]
        ])

        expect:
        config.jobOpts.region == "theRegion"
    }

    void "should use namespace if provided"() {
        given:
        def config = new NomadConfig([
                jobs: [namespace: "namespace"]
        ])

        expect:
        config.jobOpts.namespace == "namespace"
    }
}
