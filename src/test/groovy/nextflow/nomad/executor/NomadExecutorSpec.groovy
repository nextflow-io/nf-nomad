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

import nextflow.nomad.builders.JobBuilder
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.config.NomadJobOpts
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for NomadExecutor's per-task driver resolution and container-native behavior.
 *
 * Key concept: Nomad task drivers are split into two categories:
 *
 * 1. Container-native drivers (docker, podman) — Nomad manages the full container
 *    lifecycle (image pull, volume mounts, container creation). Nextflow delegates
 *    container management entirely to the driver.
 *
 * 2. Non-container-native drivers (pbs, slurm, raw_exec, exec) — Nomad does NOT
 *    manage containers. Nextflow generates .command.run with whatever container
 *    wrapping is configured (singularity exec, apptainer exec, or bare-metal).
 *
 * This distinction enables mixed-driver pipelines: cloud processes use docker (Nomad
 * manages containers), HPC processes use pbs/slurm (Nextflow wraps with singularity).
 */
class NomadExecutorSpec extends Specification {

    // --- Helpers ---

    private NomadExecutor createExecutor(String globalDriver) {
        def config = new NomadConfig([
                client: [address: 'http://localhost:4646'],
                jobs  : [driver: globalDriver]
        ] as Map)
        def executor = new NomadExecutor()
        def field = NomadExecutor.getDeclaredField('nomadConfig')
        field.setAccessible(true)
        field.set(executor, config)
        return executor
    }

    private TaskRun taskWithDriver(String perProcessDriver) {
        def nomadOptions = perProcessDriver != null ? [driver: perProcessDriver] : [:]
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key ->
            if (key == TaskDirectives.NOMAD_OPTIONS) return nomadOptions
            return null
        }
        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig
        return Mock(TaskRun) {
            getProcessor() >> processor
            getName() >> "testTask"
        }
    }

    // ========================================================================
    // isTaskDriverContainerNative — the central predicate
    // ========================================================================

    @Unroll
    def "isTaskDriverContainerNative: #driver is #expected"() {
        expect:
        NomadExecutor.isTaskDriverContainerNative(driver) == expected

        where:
        driver     | expected
        'docker'   | true
        'podman'   | true
        'pbs'      | false
        'slurm'    | false
        'raw_exec' | false
        'exec'     | false
        null       | false
    }

    def "CONTAINER_NATIVE_DRIVERS is immutable"() {
        when:
        NomadExecutor.CONTAINER_NATIVE_DRIVERS.add('fake')

        then:
        thrown(UnsupportedOperationException)
    }

    // ========================================================================
    // isContainerNative(TaskRun) — per-task resolution
    // ========================================================================

    @Unroll
    def "isContainerNative(task): global=#globalDriver, perProcess=#perProcess → #expected"() {
        given:
        def executor = createExecutor(globalDriver)
        def task = taskWithDriver(perProcess)

        expect:
        executor.isContainerNative(task) == expected

        where:
        globalDriver | perProcess | expected
        'docker'     | null       | true      // global docker, no override
        'podman'     | null       | true      // global podman, no override
        'pbs'        | null       | false     // global pbs
        'slurm'      | null       | false     // global slurm
        'raw_exec'   | null       | false     // global raw_exec
        'exec'       | null       | false     // global exec
        'docker'     | 'pbs'      | false     // per-process pbs overrides global docker
        'docker'     | 'slurm'    | false     // per-process slurm overrides global docker
        'docker'     | 'raw_exec' | false     // per-process raw_exec overrides global docker
        'docker'     | 'exec'     | false     // per-process exec overrides global docker
        'pbs'        | 'docker'   | true      // per-process docker overrides global pbs
        'slurm'      | 'docker'   | true      // per-process docker overrides global slurm
        'slurm'      | 'podman'   | true      // per-process podman overrides global slurm
        'docker'     | 'podman'   | true      // per-process podman overrides global docker
    }

    // ========================================================================
    // containerConfigEngine(TaskRun) — per-task resolution
    // ========================================================================

    @Unroll
    def "containerConfigEngine(task): global=#globalDriver, perProcess=#perProcess → #expected"() {
        given:
        def executor = createExecutor(globalDriver)
        def task = taskWithDriver(perProcess)

        expect:
        executor.containerConfigEngine(task) == expected

        where:
        globalDriver | perProcess | expected
        'docker'     | null       | 'docker'  // docker → Nextflow uses DockerConfig
        'podman'     | null       | 'podman'  // podman → Nextflow uses PodmanConfig
        'pbs'        | null       | null      // null → Nextflow uses enabled engine (singularity, apptainer, etc.)
        'slurm'      | null       | null
        'raw_exec'   | null       | null
        'exec'       | null       | null
        'docker'     | 'pbs'      | null      // per-process pbs → null
        'docker'     | 'slurm'    | null      // per-process slurm → null
        'pbs'        | 'docker'   | 'docker'  // per-process docker → 'docker'
        'slurm'      | 'podman'   | 'podman'  // per-process podman → 'podman'
    }

    // ========================================================================
    // Global isContainerNative() — backward compat
    // ========================================================================

    @Unroll
    def "global isContainerNative: #driver → #expected"() {
        expect:
        createExecutor(driver).isContainerNative() == expected

        where:
        driver     | expected
        'docker'   | true
        'podman'   | true
        'pbs'      | false
        'slurm'    | false
        'raw_exec' | false
        'exec'     | false
    }

    // ========================================================================
    // Mixed-driver pipeline scenarios
    // ========================================================================

    def "mixed pipeline: cloud (docker) + HPC (pbs) tasks get different behavior"() {
        given: 'executor with docker as global default'
        def executor = createExecutor('docker')
        def cloudTask = taskWithDriver(null)
        def hpcTask = taskWithDriver('pbs')

        expect: 'cloud task is container-native, HPC task is not'
        executor.isContainerNative(cloudTask)
        !executor.isContainerNative(hpcTask)

        and: 'cloud task uses docker engine, HPC task defers to nextflow config'
        executor.containerConfigEngine(cloudTask) == 'docker'
        executor.containerConfigEngine(hpcTask) == null
    }

    def "mixed pipeline: HPC-first (slurm global) + selective cloud (docker override)"() {
        given: 'executor with slurm as global default'
        def executor = createExecutor('slurm')
        def slurmTask = taskWithDriver(null)
        def dockerTask = taskWithDriver('docker')

        expect:
        !executor.isContainerNative(slurmTask)
        executor.isContainerNative(dockerTask)
        executor.containerConfigEngine(slurmTask) == null
        executor.containerConfigEngine(dockerTask) == 'docker'
    }

    def "mixed pipeline: three drivers — docker, pbs, raw_exec"() {
        given: 'executor with docker as global default'
        def executor = createExecutor('docker')
        def dockerTask = taskWithDriver(null)
        def pbsTask = taskWithDriver('pbs')
        def rawExecTask = taskWithDriver('raw_exec')

        expect: 'only docker is container-native'
        executor.isContainerNative(dockerTask)
        !executor.isContainerNative(pbsTask)
        !executor.isContainerNative(rawExecTask)

        and:
        executor.containerConfigEngine(dockerTask) == 'docker'
        executor.containerConfigEngine(pbsTask) == null
        executor.containerConfigEngine(rawExecTask) == null
    }

    // ========================================================================
    // resolveDriver — per-process override chain
    // ========================================================================

    def "resolveDriver: per-process nomadOptions.driver overrides global"() {
        given:
        def globalOpts = Mock(NomadJobOpts) { driver >> 'docker' }
        def task = taskWithDriver('pbs')

        expect:
        JobBuilder.resolveDriver(task, globalOpts) == 'pbs'
    }

    def "resolveDriver: falls back to global when no per-process override"() {
        given:
        def globalOpts = Mock(NomadJobOpts) { driver >> 'slurm' }
        def task = taskWithDriver(null)

        expect:
        JobBuilder.resolveDriver(task, globalOpts) == 'slurm'
    }

    def "resolveDriver: defaults to docker when nothing specified"() {
        given:
        def globalOpts = Mock(NomadJobOpts) { driver >> null }
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> null
        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig
        def task = Mock(TaskRun) { getProcessor() >> processor }

        expect:
        JobBuilder.resolveDriver(task, globalOpts) == 'docker'
    }
}
