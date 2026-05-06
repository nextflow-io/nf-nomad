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

package nextflow.nomad.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.nomad.models.JobAffinity
import nextflow.nomad.models.JobConstraint
import nextflow.nomad.models.JobConstraints
import nextflow.nomad.models.JobSpreads
import nextflow.nomad.models.JobVolume
import nextflow.util.Duration


/**
 * Nomad JobOpts
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
class NomadJobOpts{
    static final String CPU_MODE_CORES = 'cores'
    static final String CPU_MODE_CPU = 'cpu'
    static final String CLEANUP_ALWAYS = 'always'
    static final String CLEANUP_NEVER = 'never'
    static final String CLEANUP_ON_SUCCESS = 'onSuccess'
    private Map<String,String> sysEnv

    boolean deleteOnCompletion
    String cleanup
    List<String> datacenters
    String region
    String namespace
    String dockerVolume
    /**
     * Nomad task driver used for the per-process child task.
     * Defaults to 'docker' (preserves existing behaviour). Set to
     * 'containerd-driver' (or any other registered driver) to run on
     * nodes that don't have docker — e.g. aither, which only has
     * nomad-driver-containerd registered. Reads from
     * `nomad.jobs.taskDriver` in nextflow.config or NF_NOMAD_TASK_DRIVER env.
     */
    String taskDriver
    Map<String, String> meta
    /**
     * Names of head-task environment variables whose values are mirrored
     * onto every spawned child task — both as Job.Meta entries (lowercased
     * key) and as task env. Lets an enclosing harness inject correlation
     * fields (user / workspace / pipeline) without nf-nomad having to know
     * which fields exist. Defaults to empty (legacy behaviour).
     *
     * Configure as: {@code nomad.jobs.identityEnvPassthrough = ['MY_VAR_A', 'MY_VAR_B']}
     * or via NF_NOMAD_IDENTITY_ENV_PASSTHROUGH=COMMA,SEPARATED.
     */
    List<String> identityEnvPassthrough
    Duration shutdownDelay
    Map<String, Object> restartPolicy
    Map<String, Object> reschedulePolicy
    JobVolume[] volumeSpec
    JobAffinity affinitySpec
    JobConstraint constraintSpec
    JobConstraints constraintsSpec
    JobSpreads spreadsSpec

    Integer rescheduleAttempts
    Integer restartAttempts
    Boolean privileged
    String networkMode
    String cpuMode
    Boolean acceleratorAutoDevice
    String acceleratorDeviceName
    Boolean failOnPlacementFailure
    Duration placementFailureTimeout

    NomadSecretOpts secretOpts

    String driver

    NomadJobOpts(Map nomadJobOpts, Map<String,String> env=null){
        assert nomadJobOpts!=null

        sysEnv = new HashMap<String,String>(System.getenv())
        if( env ) {
            sysEnv.putAll(env)
        }

        driver = nomadJobOpts.driver?.toString() ?: "docker"

        deleteOnCompletion = nomadJobOpts.containsKey("deleteOnCompletion") ?
                nomadJobOpts.deleteOnCompletion : true
        cleanup = parseCleanup(nomadJobOpts.get('cleanup') ?: sysEnv.get('NF_NOMAD_CLEANUP'), deleteOnCompletion)
        if( nomadJobOpts.containsKey("datacenters") ) {
            datacenters = ((nomadJobOpts.datacenters instanceof List<String> ?
                    nomadJobOpts.datacenters : nomadJobOpts.datacenters.toString().split(","))
                    as List<String>).collect { it?.toString()?.trim() }.findAll { it?.size() }.unique()
        }else{
            def envDatacenters = sysEnv.get('NOMAD_DC')
            if( envDatacenters?.toString()?.trim() ) {
                datacenters = (envDatacenters.toString().split(",") as List<String>)
                        .collect { it?.toString()?.trim() }
                        .findAll { it?.size() }
                        .unique()
            }
        }

        region = sanitizeOptionalString(nomadJobOpts.region) ?: sanitizeOptionalString(sysEnv.get('NOMAD_REGION'))
        namespace = sanitizeOptionalString(nomadJobOpts.namespace) ?: sanitizeOptionalString(sysEnv.get('NOMAD_NAMESPACE'))
        meta = parseStringMap(nomadJobOpts.meta)
        identityEnvPassthrough = parseStringList(
                nomadJobOpts.get('identityEnvPassthrough'),
                sysEnv.get('NF_NOMAD_IDENTITY_ENV_PASSTHROUGH'))
        shutdownDelay = parseOptionalDuration(nomadJobOpts.shutdownDelay)

        Map<String, Object> failures = nomadJobOpts.failures instanceof Map ? (nomadJobOpts.failures as Map<String, Object>) : Collections.emptyMap()
        restartPolicy = failures.restart instanceof Map ? (failures.restart as Map<String, Object>) : Collections.emptyMap()
        reschedulePolicy = failures.reschedule instanceof Map ? (failures.reschedule as Map<String, Object>) : Collections.emptyMap()

        //NOTE: Default to a single attempt per nomad job definition
        rescheduleAttempts = nomadJobOpts.rescheduleAttempts as Integer ?: 1
        restartAttempts = nomadJobOpts.restartAttempts as Integer ?: 1
        privileged = nomadJobOpts.containsKey("privileged")
                ? Boolean.valueOf(nomadJobOpts.privileged.toString())
                : true
        networkMode = nomadJobOpts.containsKey("networkMode") ?: "bridge"
        cpuMode = parseCpuMode(nomadJobOpts.get('cpuMode') ?: sysEnv.get('NF_NOMAD_CPU_MODE'))
        acceleratorAutoDevice = nomadJobOpts.containsKey('acceleratorAutoDevice')
                ? Boolean.valueOf(nomadJobOpts.get('acceleratorAutoDevice')?.toString())
                : Boolean.valueOf(sysEnv.get('NF_NOMAD_ACCELERATOR_AUTO_DEVICE') ?: 'true')
        acceleratorDeviceName = sanitizeOptionalString(nomadJobOpts.get('acceleratorDeviceName'))
                ?: sanitizeOptionalString(sysEnv.get('NF_NOMAD_ACCELERATOR_DEVICE_NAME'))
                ?: 'nvidia/gpu'

        // Placement failure handling
        failOnPlacementFailure = nomadJobOpts.containsKey("failOnPlacementFailure") ?
                Boolean.valueOf(nomadJobOpts.failOnPlacementFailure.toString()) :
                Boolean.valueOf(sysEnv.get('NOMAD_FAIL_ON_PLACEMENT_FAILURE') ?: 'false')

        // Placement failure timeout (default: 60 seconds)
        // Supports Nextflow Duration format: "20s", "2m", "1h", "2d"
        // Or long milliseconds: 60000
        def timeoutValue = nomadJobOpts.get('placementFailureTimeout') ?: sysEnv.get('NF_NOMAD_PLACEMENT_FAILURE_TIMEOUT')
        if (timeoutValue) {
            placementFailureTimeout = timeoutValue instanceof Duration ?
                (timeoutValue as Duration) :
                Duration.of(timeoutValue.toString())
        } else {
            placementFailureTimeout = Duration.of('60s')
        }


        dockerVolume = nomadJobOpts.dockerVolume ?: null
        if( dockerVolume ){
            log.info "dockerVolume config will be deprecated, use volume type:'docker' name:'name' instead"
        }

        taskDriver = sanitizeOptionalString(nomadJobOpts.get('taskDriver'))
                ?: sanitizeOptionalString(sysEnv.get('NF_NOMAD_TASK_DRIVER'))
                ?: 'docker'

        this.volumeSpec = parseVolumes(nomadJobOpts)
        this.affinitySpec = parseAffinity(nomadJobOpts)
        this.constraintSpec = parseConstraint(nomadJobOpts)
        this.constraintsSpec = parseConstraints(nomadJobOpts)
        this.secretOpts = parseSecrets(nomadJobOpts)
        this.spreadsSpec = parseSpreads(nomadJobOpts)
    }

    JobVolume[] parseVolumes(Map nomadJobOpts){
        List<JobVolume> ret = []
        if( nomadJobOpts.volume ){
            def volumeSpec = new JobVolume()
            if( nomadJobOpts.volume instanceof Closure) {
                def closure = (nomadJobOpts.volume as Closure)
                def clone = closure.rehydrate(volumeSpec, closure.owner, closure.thisObject)
                clone.resolveStrategy = Closure.DELEGATE_FIRST
                clone()
                volumeSpec.workDir(true)
            }
            if( nomadJobOpts.volume instanceof Map) {
                volumeSpec = JobVolume.fromMap(nomadJobOpts.volume as Map)
            }
            ret.add volumeSpec
        }

        if( nomadJobOpts.volumes && nomadJobOpts.volumes instanceof List){
            nomadJobOpts.volumes.each{ spec ->
                def volumeSpec = new JobVolume()
                if( spec instanceof Closure){
                    def closure = spec as Closure
                    def clone = closure.rehydrate(volumeSpec, closure.owner, closure.thisObject)
                    clone.resolveStrategy = Closure.DELEGATE_FIRST
                    clone()
                }
                if( spec instanceof Map){
                    volumeSpec = JobVolume.fromMap(spec as Map)
                }
                ret.add volumeSpec
            }
        }

        if( ret.size() && !ret.find{ it.workDir } ){
            ret.first().workDir(true)
        }

        ret*.validate()

        if( ret.findAll{ it.workDir}.size() > 1 ){
            throw new IllegalArgumentException("No more than a workdir volume allowed")
        }

        return ret as JobVolume[]
    }

    JobAffinity parseAffinity(Map nomadJobOpts) {
        if (nomadJobOpts.affinity && nomadJobOpts.affinity instanceof Closure) {
            log.info "affinity config will be deprecated, use affinities closure instead"
            def affinitySpec = new JobAffinity()
            def closure = (nomadJobOpts.affinity as Closure)
            def clone = closure.rehydrate(affinitySpec, closure.owner, closure.thisObject)
            clone.resolveStrategy = Closure.DELEGATE_FIRST
            clone()
            affinitySpec.validate()
            affinitySpec
        } else {
            null
        }
    }

    JobConstraint parseConstraint(Map nomadJobOpts){
        if (nomadJobOpts.constraint && nomadJobOpts.constraint instanceof Closure) {
            log.info "constraint config will be deprecated, use constraints closure instead"
            def constraintSpec = new JobConstraint()
            def closure = (nomadJobOpts.constraint as Closure)
            def clone = closure.rehydrate(constraintSpec, closure.owner, closure.thisObject)
            clone.resolveStrategy = Closure.DELEGATE_FIRST
            clone()
            constraintSpec.validate()
            constraintSpec
        } else {
            null
        }
    }

    JobConstraints parseConstraints(Map nomadJobOpts){
        def value = nomadJobOpts.constraints
        if( !value ) return null
        // Closure shape: written as `constraints { node { ... } }` AND assigned
        // via property-assignment (`= { ... }`) — Nextflow preserves it as a Closure.
        if( value instanceof Closure ) {
            def constraintsSpec = new JobConstraints()
            def closure = (value as Closure)
            def clone = closure.rehydrate(constraintsSpec, closure.owner, closure.thisObject)
            clone.resolveStrategy = Closure.DELEGATE_FIRST
            clone()
            constraintsSpec.validate()
            return constraintsSpec
        }
        // Map shape: produced by Nextflow's config-file parser when the user writes
        // `constraints { node { unique = [name: 'host'] } }` as a block (not `=` form).
        // Without this branch the constraint is silently dropped.
        if( value instanceof Map ) {
            return JobConstraints.fromMap(value as Map)
        }
        log.warn "Ignoring nomad.jobs.constraints: expected a closure or map, got ${value.getClass().name}"
        return null
    }

    NomadSecretOpts parseSecrets(Map nomadJobOpts){
        if (nomadJobOpts.secrets && nomadJobOpts.secrets instanceof Map) {
            def secretOpts = new NomadSecretOpts(nomadJobOpts.secrets as Map)
            secretOpts
        }else{
            null
        }
    }

    JobSpreads parseSpreads(Map nomadJobOpts){
        if( nomadJobOpts.spreads && nomadJobOpts.spreads instanceof Closure){
            def spec = new JobSpreads()
            def closure = (nomadJobOpts.spreads as Closure)
            def clone = closure.rehydrate(spec, closure.owner, closure.thisObject)
            clone.resolveStrategy = Closure.DELEGATE_FIRST
            clone()
            spec.validate()
            spec
        }
    }

    private static String sanitizeOptionalString(Object value) {
        final str = value?.toString()?.trim()
        str ? str : null
    }

    /**
     * Parse a list-of-strings config value. Accepts either a {@link List}
     * (config form) or a comma-separated {@link String} (env-var form).
     * Whitespace trimmed; empty entries dropped; duplicates preserved.
     */
    private static List<String> parseStringList(Object configValue, Object envValue) {
        Object raw = configValue ?: envValue
        if( raw == null ) return Collections.<String>emptyList()
        List<String> items
        if( raw instanceof List ) {
            items = ((List)raw).collect { it?.toString()?.trim() }
        } else {
            items = raw.toString().split(',').collect { it?.trim() }
        }
        return items.findAll { it } as List<String>
    }

    private static Map<String, String> parseStringMap(Object value) {
        if( !(value instanceof Map) ) {
            return Collections.emptyMap()
        }
        return ((Map)value).collectEntries { k, v -> [(k?.toString()): v?.toString()] } as Map<String, String>
    }

    private static Duration parseOptionalDuration(Object value) {
        if( value == null ) {
            return null
        }
        if( value instanceof Duration ) {
            return (Duration)value
        }
        return Duration.of(value.toString())
    }

    private static String parseCpuMode(Object value) {
        String mode = sanitizeOptionalString(value)?.toLowerCase()
        if( !mode ) {
            return CPU_MODE_CORES
        }
        if( mode == CPU_MODE_CORES || mode == CPU_MODE_CPU ) {
            return mode
        }
        throw new IllegalArgumentException("Invalid nomad.jobs.cpuMode value `${value}` -- expected `${CPU_MODE_CORES}` or `${CPU_MODE_CPU}`")
    }

    private static String parseCleanup(Object value, boolean deleteOnCompletion) {
        String mode = sanitizeOptionalString(value)
        if( !mode ) {
            return deleteOnCompletion ? CLEANUP_ALWAYS : CLEANUP_NEVER
        }

        String normalized = mode.equalsIgnoreCase('onSuccess') ? CLEANUP_ON_SUCCESS : mode.toLowerCase()
        if( normalized == CLEANUP_ALWAYS || normalized == CLEANUP_NEVER || normalized == CLEANUP_ON_SUCCESS ) {
            return normalized
        }
        throw new IllegalArgumentException("Invalid nomad.jobs.cleanup value `${value}` -- expected `${CLEANUP_ALWAYS}`, `${CLEANUP_NEVER}` or `${CLEANUP_ON_SUCCESS}`")
    }
}
