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

package nextflow.nomad

import nextflow.nomad.config.VolumeSpec
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

    void "should instantiate a volume spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [volume : { type "docker" name "test" }]
        ])

        then:
        config.jobOpts.volumeSpec
        config.jobOpts.volumeSpec.type == VolumeSpec.VOLUME_DOCKER_TYPE
        config.jobOpts.volumeSpec.name == "test"

        when:
        def config2 = new NomadConfig([
                jobs: [volume : { type "csi" name "test" }]
        ])

        then:
        config2.jobOpts.volumeSpec
        config2.jobOpts.volumeSpec.type == VolumeSpec.VOLUME_CSI_TYPE
        config2.jobOpts.volumeSpec.name == "test"

        when:
        def config3 = new NomadConfig([
                jobs: [volume  : { type "host" name "test" }]
        ])

        then:
        config3.jobOpts.volumeSpec
        config3.jobOpts.volumeSpec.type == VolumeSpec.VOLUME_HOST_TYPE
        config3.jobOpts.volumeSpec.name == "test"

        when:
        new NomadConfig([
                jobs: [volume : { type "not-supported" name "test" }]
        ])

        then:
        thrown(IllegalArgumentException)
    }

    void "should instantiate an affinity spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [affinity : {
                    attribute '${meta.my_custom_value}'
                    operator  ">"
                    value     "3"
                    weight    50
                }]
        ])

        then:
        config.jobOpts.affinitySpec
        config.jobOpts.affinitySpec.getAttribute() == '${meta.my_custom_value}'
        config.jobOpts.affinitySpec.getOperator() == '>'
        config.jobOpts.affinitySpec.getValue() == '3'
        config.jobOpts.affinitySpec.getWeight() == 50
    }

    void "should instantiate a constraint spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [constraint : {
                    attribute '${meta.my_custom_value}'
                    operator  ">"
                    value     "3"
                }]
        ])

        then:
        config.jobOpts.constraintSpec
        config.jobOpts.constraintSpec.getAttribute() == '${meta.my_custom_value}'
        config.jobOpts.constraintSpec.getOperator() == '>'
        config.jobOpts.constraintSpec.getValue() == '3'
    }
}
