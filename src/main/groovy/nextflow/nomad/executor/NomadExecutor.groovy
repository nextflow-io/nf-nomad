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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.Executor
import nextflow.fusion.FusionHelper
import nextflow.nomad.builders.JobBuilder
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.ServiceName
import org.pf4j.ExtensionPoint

/**
 * Implement the Nomad executor
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
@ServiceName('nomad')
class NomadExecutor extends Executor implements ExtensionPoint {

    private NomadService service
    private NomadConfig nomadConfig

    /**
     * Nomad task drivers that natively manage the container lifecycle.
     * When a driver is in this set, Nomad pulls the image, creates the container,
     * mounts volumes, and manages the container process directly.
     * Nextflow does NOT wrap .command.run with container commands (e.g. docker run, singularity exec).
     *
     * Drivers NOT in this set (pbs, slurm, raw_exec, exec) rely on Nextflow's
     * native .command.run script generation for container wrapping — enabling
     * singularity, apptainer, or bare-metal execution.
     */
    static final Set<String> CONTAINER_NATIVE_DRIVERS = Collections.unmodifiableSet(
            ['docker', 'podman'] as Set<String>
    )

    @Override
    protected void register() {
        super.register()
        initNomadService()
    }

    protected void initNomadService(){
        this.nomadConfig = new NomadConfig((session.config.nomad ?: Collections.emptyMap()) as Map)
        if( this.nomadConfig.enabled ) {
            this.service = new NomadService(this.nomadConfig)
        }
    }

    @Override
    protected TaskMonitor createTaskMonitor() {
        TaskPollingMonitor.create(session, config, name, Duration.of('5 sec'))
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        assert task
        assert task.workDir
        log.trace "[NOMAD] launching process > ${task.name} -- work folder: ${task.workDirStr}"
        return new NomadTaskHandler(
                task,
                this.nomadConfig,
                service,
                (session.config ?: Collections.emptyMap()) as Map,
                session.workDir
        )
    }

    @Override
    boolean isFusionEnabled() {
        return FusionHelper.isFusionEnabled(session)
    }

    /**
     * Checks whether a given Nomad task driver natively manages the container lifecycle.
     * Container-native drivers (docker, podman) handle image pulling, volume mounts,
     * and container creation — Nextflow delegates container management entirely.
     * Non-container-native drivers (pbs, slurm, raw_exec, exec) require Nextflow
     * to generate container-wrapped .command.run scripts itself.
     *
     * @param driver The Nomad task driver name
     * @return {@code true} if the driver natively manages containers
     */
    static boolean isTaskDriverContainerNative(String driver) {
        return CONTAINER_NATIVE_DRIVERS.contains(driver)
    }

    /**
     * Global default: returns true when the global nomad.jobs.driver is container-native.
     * Overrides Nextflow's {@link Executor#isContainerNative()}.
     */
    @Override
    boolean isContainerNative() {
        return isTaskDriverContainerNative(nomadConfig.jobOpts().driver)
    }

    /**
     * Task-aware container-native check.
     * Resolves per-process driver override (nomadOptions.driver) and returns true
     * only when the resolved Nomad task driver is container-native (docker, podman).
     * For HPC drivers (pbs, slurm) and process drivers (raw_exec, exec), returns false
     * so Nextflow generates container-wrapped .command.run scripts
     * (e.g. singularity exec, apptainer exec).
     *
     * Note: not annotated {@code @Override} — Nextflow's {@link Executor} only
     * exposes the no-arg {@code isContainerNative()}. This is an additional
     * task-aware variant called by the JobBuilder when it has a TaskRun in
     * hand; it would become a true override when/if Nextflow upstream adds
     * the matching signature.
     */
    boolean isContainerNative(TaskRun task) {
        final driver = JobBuilder.resolveDriver(task, nomadConfig.jobOpts())
        return isTaskDriverContainerNative(driver)
    }

    /**
     * Task-aware container config engine.
     * When the resolved Nomad task driver is container-native, returns the driver name
     * so Nextflow uses the matching ContainerConfig (e.g. DockerConfig for docker).
     * For non-container-native drivers, returns null — letting Nextflow use whichever
     * container engine is enabled in config (singularity, apptainer, etc.).
     *
     * Note: not annotated {@code @Override} — see the rationale on
     * {@link #isContainerNative(TaskRun)} above.
     */
    String containerConfigEngine(TaskRun task) {
        final driver = JobBuilder.resolveDriver(task, nomadConfig.jobOpts())
        return isTaskDriverContainerNative(driver) ? driver : null
    }
}
