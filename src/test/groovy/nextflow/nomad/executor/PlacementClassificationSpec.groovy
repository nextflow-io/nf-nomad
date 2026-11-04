/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 * Copyright 2026-, Incremental Steps Software Solutions OÜ
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

import io.nomadproject.client.model.AllocationMetric
import io.nomadproject.client.model.Resources
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A task waiting its turn on a busy cluster and a task that can never be placed look identical
 * from the allocation list -- no node, status `pending`. Failing the first aborts healthy
 * pipelines; waiting on the second hangs the run. These cover the discriminator between them.
 */
class PlacementClassificationSpec extends Specification {

    @Unroll
    def "capacity in use is queueing, not failure: #scenario"() {
        given:
        def metrics = new AllocationMetric()
        applyTo(metrics)

        expect: 'eligible nodes exist and are full -- the task will place once capacity frees'
        NomadService.classifyPlacement(metrics) == NomadService.PlacementState.QUEUED

        where:
        scenario                | applyTo
        'a resource dimension'  | { AllocationMetric m -> m.dimensionExhausted = ['cpu': 1] }
        'concrete resources'    | { AllocationMetric m -> m.resourcesExhausted = ['nf-task': new Resources()] }
        'a namespace quota'     | { AllocationMetric m -> m.quotaExhausted = ['cpu'] }
    }

    @Unroll
    def "nodes rejected on their properties can never be satisfied: #scenario"() {
        given:
        def metrics = new AllocationMetric()
        applyTo(metrics)

        expect: 'no amount of waiting changes a node property, so fail rather than hang'
        NomadService.classifyPlacement(metrics) == NomadService.PlacementState.UNPLACEABLE

        where:
        scenario                       | applyTo
        'an unsatisfiable constraint'  | { AllocationMetric m -> m.constraintFiltered = ['${node.class} = gpu': 3] }
        'a node class filter'          | { AllocationMetric m -> m.classFiltered = ['batch': 2] }
        'every evaluated node filtered'| { AllocationMetric m -> m.nodesEvaluated = 3; m.nodesFiltered = 3 }
    }

    def "exhaustion wins over filtering when both are reported"() {
        given: 'some nodes were filtered out, but others matched and are merely busy'
        def metrics = new AllocationMetric(
                constraintFiltered: ['${node.class} = gpu': 1],
                dimensionExhausted: ['cpu': 1],
                nodesEvaluated: 2)

        expect: 'a node did match, so the task is queued behind it rather than unplaceable'
        NomadService.classifyPlacement(metrics) == NomadService.PlacementState.QUEUED
    }

    def "absent metrics classify as OK rather than failing closed"() {
        expect: 'no evidence of trouble must never be read as evidence of failure'
        NomadService.classifyPlacement(null) == NomadService.PlacementState.OK
        NomadService.classifyPlacement(new AllocationMetric()) == NomadService.PlacementState.OK
    }

    def "partially filtered nodes are not unplaceable"() {
        given: 'two of three nodes were filtered -- one remains eligible'
        def metrics = new AllocationMetric(nodesEvaluated: 3, nodesFiltered: 2)

        expect:
        NomadService.classifyPlacement(metrics) == NomadService.PlacementState.OK
    }
}
