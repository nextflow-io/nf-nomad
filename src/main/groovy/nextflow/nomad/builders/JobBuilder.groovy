/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024-, Evaluacion y Desarrollo de Negocios, Spain
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

package nextflow.nomad.builders

import io.nomadproject.client.model.Affinity
import io.nomadproject.client.model.Constraint
import io.nomadproject.client.model.Job
import io.nomadproject.client.model.Spread
import io.nomadproject.client.model.TaskGroup
import io.nomadproject.client.model.Task
import io.nomadproject.client.model.ReschedulePolicy
import io.nomadproject.client.model.RestartPolicy
import io.nomadproject.client.model.Resources
import io.nomadproject.client.model.RequestedDevice
import io.nomadproject.client.model.Template
import io.nomadproject.client.model.VolumeMount
import io.nomadproject.client.model.VolumeRequest
import nextflow.nomad.config.NomadJobOpts
import nextflow.nomad.executor.NomadTaskOptionsResolver
import nextflow.nomad.executor.TaskDirectives
import nextflow.nomad.models.ConstraintsBuilder
import nextflow.nomad.models.JobConstraints
import nextflow.nomad.models.JobSpreads
import nextflow.nomad.models.JobVolume
import nextflow.nomad.models.SpreadsBuilder
import nextflow.executor.res.AcceleratorResource
import nextflow.processor.TaskConfig
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.MemoryUnit


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
*
* @author Abhinav Sharma <abhi18av@outlook.com>
*/

@Slf4j
@CompileStatic
class JobBuilder {
    private Job job = new Job()

    JobBuilder withId(String id) {
        job.ID = id
        return this
    }

    JobBuilder withName(String name) {
        job.name = name
        return this
    }

    JobBuilder withType(String type) {
        job.type = type
        return this
    }

    JobBuilder withDatacenters(List<String> datacenters) {
        job.datacenters = datacenters
        return this
    }

    static Job assignDatacenters(TaskRun task, Job job){
        final merged = [] as List<String>
        if( job.datacenters ) {
            merged.addAll(normalizeDatacenters(job.datacenters))
        }

        final processDatacenters = NomadTaskOptionsResolver.datacenters(task)
        if( processDatacenters != null ) {
            merged.addAll(normalizeDatacenters(processDatacenters))
        }

        if( merged ) {
            job.datacenters(new ArrayList<String>(new LinkedHashSet<String>(merged)))
        }
        return job
    }

    static protected List<String> normalizeDatacenters(Object value) {
        if( value == null ) {
            return Collections.emptyList()
        }
        if( value instanceof Closure ) {
            return normalizeDatacenters((value as Closure).call())
        }
        if( value instanceof Collection ) {
            return (value as Collection)
                    .collect { it?.toString()?.trim() }
                    .findAll { it } as List<String>
        }
        return value.toString()
                .split(',')
                .collect { it?.toString()?.trim() }
                .findAll { it } as List<String>
    }

    JobBuilder withNamespace(String namespace) {
        job.namespace = namespace
        return this
    }

    JobBuilder withTaskGroups(List<TaskGroup> taskGroups) {
        job.taskGroups = taskGroups
        return this
    }

    Job build() {
        return job
    }

    static protected Resources getResources(TaskRun task) {
        return getResources(task, null)
    }

    static protected Resources getResources(TaskRun task, NomadJobOpts jobOpts) {
        final DEFAULT_CPUS = 1
        final DEFAULT_MEMORY = "1.GB"

        final taskCfg = task.getConfig()
        final resourceOptions = NomadTaskOptionsResolver.resources(task)
        final taskCores = parseInteger(taskCfg.get("cpus"), DEFAULT_CPUS)
        final optionCpu = parseIntegerOption(task, "${TaskDirectives.NOMAD_OPTIONS}.resources.cpu", resourceOptions.get("cpu"))
        final optionCores = parseIntegerOption(task, "${TaskDirectives.NOMAD_OPTIONS}.resources.cores", resourceOptions.get("cores"))
        final taskMemory = new MemoryUnit( taskCfg.get("memory")?.toString() ?:  DEFAULT_MEMORY)
        final taskMemoryMb = taskMemory.toMega() as Integer

        final res = new Resources()
                .memoryMB(taskMemoryMb)
                .memoryMaxMB(resolveMemoryMaxMb(task, taskMemoryMb))
        if( optionCpu != null ) {
            res.CPU(optionCpu)
        }
        else if( optionCores != null ) {
            res.cores(optionCores)
        }
        else if( (jobOpts?.cpuMode ?: NomadJobOpts.CPU_MODE_CORES) == NomadJobOpts.CPU_MODE_CPU ) {
            res.CPU(taskCores * 1_000)
        }
        else {
            res.cores(taskCores)
        }
        final devices = resolveRequestedDevices(resourceOptions.get("device"))
        if( devices ) {
            res.devices(devices)
        }
        else if( jobOpts?.acceleratorAutoDevice ) {
            final accelerator = resolveAccelerator(taskCfg)
            final acceleratorDevice = resolveAcceleratorDevice(accelerator, jobOpts?.acceleratorDeviceName)
            if( acceleratorDevice ) {
                res.devices([acceleratorDevice])
            }
        }

        return res
    }

    static protected Integer resolveMemoryMaxMb(TaskRun task, Integer defaultMemoryMb) {
        Map resources = NomadTaskOptionsResolver.resources(task)
        if( !resources || !resources.containsKey("memoryMax") ) {
            return defaultMemoryMb
        }

        Integer parsed = parseMemoryToMega(resources.get("memoryMax"))
        if( parsed != null ) {
            return parsed
        }
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.resources.memoryMax", resources.get("memoryMax"),
                "must be a valid memory value")
        return null
    }

    static protected Integer parseMemoryToMega(Object value) {
        if( value == null ) {
            return null
        }
        try {
            if( value instanceof Number ) {
                return ((Number)value).intValue()
            }
            return new MemoryUnit(value.toString()).toMega() as Integer
        } catch(Exception ignored) {
            return null
        }
    }

    static TaskGroup createTaskGroup(TaskRun taskRun, List<String> args, Map<String, String>env, NomadJobOpts jobOpts){
        final ReschedulePolicy taskReschedulePolicy  = resolveReschedulePolicy(taskRun, jobOpts)
        final RestartPolicy taskRestartPolicy  = resolveRestartPolicy(taskRun, jobOpts)
        final List<JobVolume> volumeSpecs = resolveVolumeSpecs(taskRun, jobOpts)

        def task = createTask(taskRun, args, env, jobOpts, volumeSpecs)
        def taskGroup = new TaskGroup(
                name: "nf-taskgroup",
                tasks: [ task ],
                reschedulePolicy: taskReschedulePolicy,
                restartPolicy: taskRestartPolicy
        )

        if( volumeSpecs ) {
            taskGroup.volumes = [:]
            volumeSpecs.eachWithIndex { volumeSpec , idx->
                if (volumeSpec && volumeSpec.type == JobVolume.VOLUME_CSI_TYPE) {
                    taskGroup.volumes["vol_${idx}".toString()] = new VolumeRequest(
                            type: volumeSpec.type,
                            source: volumeSpec.name,
                            attachmentMode: volumeSpec.attachmentMode,
                            accessMode: volumeSpec.accessMode,
                            readOnly: volumeSpec.readOnly,
                    )
                }

                if (volumeSpec && volumeSpec.type == JobVolume.VOLUME_HOST_TYPE) {
                    taskGroup.volumes["vol_${idx}".toString()] = new VolumeRequest(
                            type: volumeSpec.type,
                            source: volumeSpec.name,
                            readOnly: volumeSpec.readOnly,
                    )
                }
            }
        }
        return taskGroup
    }

    static Task createTask(TaskRun task, List<String> args, Map<String, String>env, NomadJobOpts jobOpts) {
        return createTask(task, args, env, jobOpts, resolveVolumeSpecs(task, jobOpts))
    }

    static Task createTask(TaskRun task, List<String> args, Map<String, String>env, NomadJobOpts jobOpts, List<JobVolume> volumeSpecs) {
        final DRIVER = "docker"

        final imageName = task.container
        final workingDir = task.workDir.toAbsolutePath().toString()
        final taskResources = getResources(task, jobOpts)


        def taskDef = new Task(
                name: "nf-task",
                driver: DRIVER,
                resources: taskResources,
                config: [
                        image     : imageName,
                        privileged: (jobOpts?.privileged != null ? jobOpts.privileged : true),
                        work_dir  : workingDir,
                        command   : args.first(),
                        args      : args.tail(),
                ] as Map<String, Object>,
                env: env,
        )

        Long shutdownDelay = resolveShutdownDelayMillis(task, jobOpts)
        if( shutdownDelay != null ) {
            taskDef.shutdownDelay(shutdownDelay)
        }

        volumes(task, taskDef, workingDir, jobOpts, volumeSpecs)
        affinity(task, taskDef, jobOpts)
        constraint(task, taskDef, jobOpts)
        constraints(task, taskDef, jobOpts)
        secrets(task, taskDef, jobOpts)
        return taskDef
    }

    static protected Task volumes(TaskRun task, Task taskDef, String workingDir, NomadJobOpts jobOpts, List<JobVolume> volumeSpecs){
        if( jobOpts.dockerVolume){
            String destinationDir = workingDir.split(File.separator).dropRight(2).join(File.separator)
            taskDef.config.mount = [
                    type : "volume",
                    target : destinationDir,
                    source : jobOpts.dockerVolume,
                    readonly : false
            ]
        }

        if( volumeSpecs ){
            taskDef.volumeMounts = []
            volumeSpecs.eachWithIndex { volumeSpec, idx ->
                String destinationDir = volumeSpec.workDir && !volumeSpec.path ?
                        workingDir.split(File.separator).dropRight(2).join(File.separator) : volumeSpec.path
                taskDef.volumeMounts.add new VolumeMount(
                        destination: destinationDir,
                        volume: "vol_${idx}".toString(),
                        readOnly: volumeSpec.readOnly
                )
            }
        }

        taskDef
    }

    static protected List<JobVolume> resolveVolumeSpecs(TaskRun task, NomadJobOpts jobOpts) {
        List<JobVolume> result = []
        if( jobOpts?.volumeSpec ) {
            result.addAll(jobOpts.volumeSpec as List<JobVolume>)
        }
        List<Map<String, Object>> processVolumes = NomadTaskOptionsResolver.volumes(task)
        processVolumes.eachWithIndex { Map<String, Object> spec, int idx ->
            result.add(parseProcessVolumeSpec(task, spec, idx))
        }
        validateMergedVolumeSpecs(task, result)
        return result
    }

    static protected void validateMergedVolumeSpecs(TaskRun task, List<JobVolume> volumeSpecs) {
        if( !volumeSpecs ) {
            return
        }
        int workDirCount = 0
        volumeSpecs.each { JobVolume volume ->
            if( volume?.workDir ) {
                workDirCount++
            }
        }
        if( workDirCount > 1 ) {
            invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.volumes", volumeSpecs,
                    "defines multiple `workDir` volumes across global and process scopes; only one is allowed")
        }
    }

    static protected JobVolume parseProcessVolumeSpec(TaskRun task, Map<String, Object> spec, int idx) {
        JobVolume volume = new JobVolume()
        if( spec.containsKey('type') ) {
            volume.type(spec.get('type')?.toString())
        }
        if( spec.containsKey('name') ) {
            volume.name(spec.get('name')?.toString())
        }
        if( spec.containsKey('path') ) {
            volume.path(spec.get('path')?.toString())
        }
        if( spec.containsKey('workDir') ) {
            Boolean workDir = parseBoolean(spec.get('workDir'))
            volume.workDir(workDir ?: false)
        }
        if( spec.containsKey('readOnly') ) {
            Boolean readOnly = parseBoolean(spec.get('readOnly'))
            volume.readOnly(readOnly ?: false)
        }

        try {
            volume.validate()
        }
        catch (IllegalArgumentException e) {
            invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.volumes[${idx}]", spec, e.message)
        }
        return volume
    }

    static protected Task affinity(TaskRun task, Task taskDef, NomadJobOpts jobOpts) {
        final affinities = [] as List<Affinity>
        if (jobOpts.affinitySpec) {
            def affinity = new Affinity()
            if (jobOpts.affinitySpec.attribute) {
                affinity.ltarget(jobOpts.affinitySpec.attribute)
            }

            affinity.operand(jobOpts.affinitySpec.operator ?: "=")

            if (jobOpts.affinitySpec.value) {
                affinity.rtarget(jobOpts.affinitySpec.value)
            }
            if (jobOpts.affinitySpec.weight != null) {
                affinity.weight(jobOpts.affinitySpec.weight)
            }
            affinities.add(affinity)
        }

        Map affinityOption = NomadTaskOptionsResolver.affinity(task)
        if( affinityOption ) {
            String attribute = affinityOption.get("attribute")?.toString()
            if( !attribute ) {
                invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.affinity.attribute", affinityOption.get("attribute"),
                        "must be a non-empty string")
            }

            String value = affinityOption.get("value")?.toString()
            if( !value ) {
                invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.affinity.value", affinityOption.get("value"),
                        "must be a non-empty string")
            }

            String operator = affinityOption.get("operator")?.toString() ?: "="
            Integer weight = parseIntegerOption(task, "${TaskDirectives.NOMAD_OPTIONS}.affinity.weight", affinityOption.get("weight"))

            def affinity = new Affinity()
                    .ltarget(attribute)
                    .operand(operator)
                    .rtarget(value)

            if( weight != null ) {
                affinity.weight(weight)
            }
            affinities.add(affinity)
        }

        if( affinities ) {
            taskDef.affinities(affinities)
        }
        taskDef
    }

    protected static Task constraint(TaskRun task, Task taskDef, NomadJobOpts jobOpts){
        if( jobOpts.constraintSpec ){
            def constraint = new Constraint()
            if(jobOpts.constraintSpec.attribute){
                constraint.ltarget(jobOpts.constraintSpec.attribute)
            }

            constraint.operand(jobOpts.constraintSpec.operator ?: "=")

            if(jobOpts.constraintSpec.value){
                constraint.rtarget(jobOpts.constraintSpec.value)
            }
            taskDef.constraints([constraint])
        }

        taskDef
    }

    protected static Task constraints(TaskRun task, Task taskDef, NomadJobOpts jobOpts){
        def constraints = [] as List<Constraint>

        if( jobOpts.constraintsSpec ){
            def list = ConstraintsBuilder.constraintsSpecToList(jobOpts.constraintsSpec)
            constraints.addAll(list)
        }

        Closure closure = NomadTaskOptionsResolver.constraints(task)
        if( closure ) {
            JobConstraints constraintsSpec = JobConstraints.parse(closure)
            def list = ConstraintsBuilder.constraintsSpecToList(constraintsSpec)
            constraints.addAll(list)
        }

        if( constraints.size()) {
            taskDef.constraints(constraints)
        }
        taskDef
    }

    protected static Task secrets(TaskRun task, Task taskDef, NomadJobOpts jobOpts){
        if( jobOpts?.secretOpts?.enabled) {
            def secrets = NomadTaskOptionsResolver.secrets(task)
            if (secrets) {
                Template template = new Template(envvars: true, destPath: "/secrets/nf-nomad")
                String secretPath = NomadTaskOptionsResolver.secretsPath(task) ?: jobOpts?.secretOpts?.path
                String tmpl = secrets.collect { String name ->
                    "${name}={{ with nomadVar \"$secretPath/${name}\" }}{{ .${name} }}{{ end }}"
                }.join('\n').stripIndent()
                template.embeddedTmpl(tmpl)
                taskDef.addTemplatesItem(template)
            }
        }
        taskDef
    }

    static Job spreads(TaskRun task, Job jobDef, NomadJobOpts jobOpts){
        def spreads = [] as List<Spread>
        if( jobOpts.spreadsSpec ){
            def list = SpreadsBuilder.spreadsSpecToList(jobOpts.spreadsSpec)
            spreads.addAll(list)
        }
        Map map = NomadTaskOptionsResolver.spread(task)
        if( map ) {
            JobSpreads spreadSpec = new JobSpreads()
            spreadSpec.spread(map)
            def list = SpreadsBuilder.spreadsSpecToList(spreadSpec)
            spreads.addAll(list)
        }

        spreads.each{
            jobDef.addSpreadsItem(it)
        }
        jobDef
    }

    /**
     * Resolves priority string to Nomad priority integer
     * Nomad supports priorities from 0-100, default is 50
     *
     * Supports:
     * - Numeric strings (0-100)
     * - Predefined aliases: critical, high, normal, low, min
     *
     * @param priorityValue string value (numeric or alias) or null
     * @return Integer priority value or null if invalid
     */
    static Integer resolvePriority(String priorityValue) {
        if (!priorityValue) {
            return null
        }

        try {
            // Try parsing as integer first
            Integer intValue = priorityValue as Integer
            if (intValue >= 0 && intValue <= 100) {
                return intValue
            }
            log.warn("Priority value ${intValue} is outside valid range 0-100, ignoring")
            return null
        } catch (NumberFormatException ignored) {
            // Handle string aliases for priority levels
            switch (priorityValue.toLowerCase()) {
                case 'critical':
                    return 100
                case 'high':
                    return 80
                case 'normal':
                    return 50
                case 'low':
                    return 30
                case 'min':
                    return 10
                default:
                    log.warn("Unknown priority value: ${priorityValue}, ignoring")
                    return null
            }
        }
    }

    static protected List<RequestedDevice> resolveRequestedDevices(Object value) {
        if( value == null ) {
            return Collections.emptyList()
        }
        if( !(value instanceof Collection) ) {
            log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.resources.device` because it is not a list -- value: ${value}")
            return Collections.emptyList()
        }

        List<RequestedDevice> devices = []
        (value as Collection).each { item ->
            if( item instanceof Map ) {
                String name = (item as Map).get("name")?.toString()
                Integer count = parseInteger((item as Map).get("count")) ?: 1
                if( name ) {
                    devices.add(new RequestedDevice().name(name).count(count))
                } else {
                    log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.resources.device` entry without `name` -- value: ${item}")
                }
            } else {
                log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.resources.device` entry because it is not a map -- value: ${item}")
            }
        }
        return devices
    }

    static protected AcceleratorResource resolveAccelerator(Object taskConfig) {
        if( taskConfig instanceof TaskConfig ) {
            return (taskConfig as TaskConfig).getAccelerator()
        }
        return null
    }

    static protected RequestedDevice resolveAcceleratorDevice(AcceleratorResource accelerator, String defaultDeviceName) {
        if( accelerator == null ) {
            return null
        }

        Integer count = accelerator.request ?: accelerator.limit ?: 1
        if( count <= 0 ) {
            return null
        }

        String deviceName = defaultDeviceName ?: 'nvidia/gpu'
        return new RequestedDevice()
                .name(deviceName)
                .count(count)
    }

    static protected RestartPolicy resolveRestartPolicy(TaskRun task, NomadJobOpts jobOpts) {
        Map<String, Object> effective = [:]
        if( jobOpts?.restartPolicy ) {
            effective.putAll(jobOpts.restartPolicy)
        }
        Map<String, Object> override = NomadTaskOptionsResolver.restart(task) as Map<String, Object>
        if( override ) {
            effective.putAll(override)
        }

        RestartPolicy policy = new RestartPolicy().attempts(parseInteger(effective.get("attempts"), jobOpts?.restartAttempts ?: 1))
        Long delay = parseDurationToMillis(effective.get("delay"))
        if( delay != null ) {
            policy.delay(delay)
        }
        Long interval = parseDurationToMillis(effective.get("interval"))
        if( interval != null ) {
            policy.interval(interval)
        }
        String mode = effective.get("mode")?.toString()
        if( mode ) {
            policy.mode(mode)
        }
        Boolean renderTemplates = parseBoolean(effective.get("renderTemplates"))
        if( renderTemplates != null ) {
            policy.renderTemplates(renderTemplates)
        }
        return policy
    }

    static protected ReschedulePolicy resolveReschedulePolicy(TaskRun task, NomadJobOpts jobOpts) {
        Map<String, Object> effective = [:]
        if( jobOpts?.reschedulePolicy ) {
            effective.putAll(jobOpts.reschedulePolicy)
        }
        Map<String, Object> override = NomadTaskOptionsResolver.reschedule(task) as Map<String, Object>
        if( override ) {
            effective.putAll(override)
        }

        ReschedulePolicy policy = new ReschedulePolicy().attempts(parseInteger(effective.get("attempts"), jobOpts?.rescheduleAttempts ?: 1))
        Long delay = parseDurationToMillis(effective.get("delay"))
        if( delay != null ) {
            policy.delay(delay)
        }
        String delayFunction = effective.get("delayFunction")?.toString()
        if( delayFunction ) {
            policy.delayFunction(delayFunction)
        }
        Long interval = parseDurationToMillis(effective.get("interval"))
        if( interval != null ) {
            policy.interval(interval)
        }
        Long maxDelay = parseDurationToMillis(effective.get("maxDelay"))
        if( maxDelay != null ) {
            policy.maxDelay(maxDelay)
        }
        Boolean unlimited = parseBoolean(effective.get("unlimited"))
        if( unlimited != null ) {
            policy.unlimited(unlimited)
        }
        return policy
    }

    static protected Long resolveShutdownDelayMillis(TaskRun task, NomadJobOpts jobOpts) {
        Object processValue = NomadTaskOptionsResolver.shutdownDelay(task)
        Long processDelay = parseDurationToMillis(processValue)
        if( processValue != null && processDelay == null ) {
            invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.shutdownDelay", processValue,
                    "must be a valid duration")
        }
        if( processDelay != null ) {
            return processDelay
        }
        return jobOpts?.shutdownDelay?.millis as Long
    }
    static protected Integer parseIntegerOption(TaskRun task, String optionPath, Object value) {
        if( value == null ) {
            return null
        }
        Integer parsed = parseInteger(value)
        if( parsed != null ) {
            return parsed
        }
        invalidOption(task, optionPath, value, "must be an integer")
        return null
    }

    static protected Integer parseInteger(Object value, Integer defaultValue = null) {
        if( value == null ) {
            return defaultValue
        }
        try {
            if( value instanceof Number ) {
                return ((Number)value).intValue()
            }
            return value.toString() as Integer
        } catch(Exception ignored) {
            return defaultValue
        }
    }

    static protected Boolean parseBoolean(Object value) {
        if( value == null ) {
            return null
        }
        return Boolean.valueOf(value.toString())
    }

    static protected Long parseDurationToMillis(Object value) {
        if( value == null ) {
            return null
        }
        try {
            if( value instanceof Number ) {
                return ((Number)value).longValue()
            }
            if( value instanceof Duration ) {
                return ((Duration)value).millis
            }
            return Duration.of(value.toString()).millis
        } catch(Exception ignored) {
            return null
        }
    }

    static protected void invalidOption(TaskRun task, String optionPath, Object value, String reason) {
        String process = task?.processor?.name?.toString() ?: task?.name?.toString() ?: "<unknown>"
        throw new IllegalArgumentException("Invalid Nomad option for process `${process}`: `${optionPath}` ${reason} -- value: ${value}")
    }
}
