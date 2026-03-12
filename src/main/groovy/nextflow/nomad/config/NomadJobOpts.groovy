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
    Map<String, String> meta
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
    String cpuMode
    Boolean acceleratorAutoDevice
    String acceleratorDeviceName
    Boolean failOnPlacementFailure
    Duration placementFailureTimeout

    NomadSecretOpts secretOpts

    NomadJobOpts(Map nomadJobOpts, Map<String,String> env=null){
        assert nomadJobOpts!=null

        sysEnv = new HashMap<String,String>(System.getenv())
        if( env ) {
            sysEnv.putAll(env)
        }

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

        this.volumeSpec = parseVolumes(nomadJobOpts)
        this.affinitySpec = parseAffinity(nomadJobOpts)
        this.constraintSpec = parseConstraint(nomadJobOpts)
        this.constraintsSpec = parseConstraints(nomadJobOpts)
        this.secretOpts = parseSecrets(nomadJobOpts)
        this.spreadsSpec = parseSpreads(nomadJobOpts)
    }

    JobVolume[] parseVolumes(Map nomadJobOpts){
        List<JobVolume> ret = []
        if( nomadJobOpts.volume && nomadJobOpts.volume instanceof Closure){
            def volumeSpec = new JobVolume()
            def closure = (nomadJobOpts.volume as Closure)
            def clone = closure.rehydrate(volumeSpec, closure.owner, closure.thisObject)
            clone.resolveStrategy = Closure.DELEGATE_FIRST
            clone()
            volumeSpec.workDir(true)
            ret.add volumeSpec
        }

        if( nomadJobOpts.volumes && nomadJobOpts.volumes instanceof List){
            nomadJobOpts.volumes.each{ closure ->
                if( closure instanceof Closure){
                    def volumeSpec = new JobVolume()
                    def clone = closure.rehydrate(volumeSpec, closure.owner, closure.thisObject)
                    clone.resolveStrategy = Closure.DELEGATE_FIRST
                    clone()
                    ret.add volumeSpec
                }
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
        if (nomadJobOpts.constraints && nomadJobOpts.constraints instanceof Closure) {
            def constraintsSpec = new JobConstraints()
            def closure = (nomadJobOpts.constraints as Closure)
            def clone = closure.rehydrate(constraintsSpec, closure.owner, closure.thisObject)
            clone.resolveStrategy = Closure.DELEGATE_FIRST
            clone()
            constraintsSpec.validate()
            constraintsSpec
        }else{
            null
        }
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
