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

package nextflow.nomad.config

import spock.lang.Specification

/**
 * Unit test for NomadJobOpts
 *
 * @author : Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadJobOptsSpec extends Specification {


    def "test default values"() {
        given:
        def nomadJobOpts = new NomadJobOpts([:])

        expect:
        nomadJobOpts.rescheduleAttempts == 1
        nomadJobOpts.restartAttempts == 1
        nomadJobOpts.dockerVolume == null
    }

    def "test rescheduleAttempts and restartAttempts"() {
        given:
        def nomadJobOpts = new NomadJobOpts([rescheduleAttempts: 3, restartAttempts: 2])

        expect:
        nomadJobOpts.rescheduleAttempts == 3
        nomadJobOpts.restartAttempts == 2
    }

    def "test dockerVolume"() {
        given:
        def nomadJobOpts = new NomadJobOpts([dockerVolume: "test-volume"])

        expect:
        nomadJobOpts.dockerVolume == "test-volume"
    }

    def "test parseVolumes with single volume"() {
        given:
        def volumeClosure = { type "csi" name "test" }
        def nomadJobOpts = new NomadJobOpts([volume: volumeClosure])

        when:
        def volumes = nomadJobOpts.parseVolumes([volume: volumeClosure])

        then:
        volumes.size() == 1
        volumes[0].workDir == true
    }

    def "test parseVolumes with multiple volumes"() {
        given:
        def volumeClosure1 = { type "host" name "test" path "/volume/csi"  }
        def volumeClosure2 = { type "csi" name "test" path "/volume/host"}
        def nomadJobOpts = new NomadJobOpts([volumes: [volumeClosure1, volumeClosure2]])

        when:
        def volumes = nomadJobOpts.parseVolumes([volumes: [volumeClosure1, volumeClosure2]])

        then:
        volumes.size() == 2
        volumes[0].path == "/volume/csi"
        volumes[1].path == "/volume/host"
    }

    def "test parseAffinity"() {
        given:
        def affinityClosure = { -> }
        def nomadJobOpts = new NomadJobOpts([affinity: affinityClosure])

        when:
        def affinity = nomadJobOpts.parseAffinity([affinity: affinityClosure])

        then:
        affinity != null
    }

    def "test parseConstraint"() {
        given:
        def constraintClosure = { -> }
        def nomadJobOpts = new NomadJobOpts([constraint: constraintClosure])

        when:
        def constraint = nomadJobOpts.parseConstraint([constraint: constraintClosure])

        then:
        constraint != null
    }

    def "test parseConstraints"() {
        given:
        def constraintsClosure = { -> }
        def nomadJobOpts = new NomadJobOpts([constraints: constraintsClosure])

        when:
        def constraints = nomadJobOpts.parseConstraints([constraints: constraintsClosure])

        then:
        constraints != null
    }


    def "test parseSpreads"() {
        given:
        def spreadsClosure = { -> }
        def nomadJobOpts = new NomadJobOpts([spreads: spreadsClosure])

        when:
        def spreads = nomadJobOpts.parseSpreads([spreads: spreadsClosure])

        then:
        spreads != null
    }
}