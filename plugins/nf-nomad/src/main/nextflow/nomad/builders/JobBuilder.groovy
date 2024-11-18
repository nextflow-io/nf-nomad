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
import io.nomadproject.client.model.Template
import io.nomadproject.client.model.VolumeMount
import io.nomadproject.client.model.VolumeRequest
import nextflow.nomad.config.NomadJobOpts
import nextflow.nomad.executor.TaskDirectives
import nextflow.nomad.models.ConstraintsBuilder
import nextflow.nomad.models.JobConstraints
import nextflow.nomad.models.JobSpreads
import nextflow.nomad.models.JobVolume
import nextflow.nomad.models.SpreadsBuilder
import nextflow.processor.TaskRun
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

    JobBuilder withDatacenters(TaskRun task){
        def datacenters = task.processor?.config?.get(TaskDirectives.DATACENTERS)
        if( datacenters ){
            if( datacenters instanceof List<String>) {
                job.datacenters( datacenters as List<String>)
                return this;
            }
            if( datacenters instanceof Closure) {
                String str = datacenters.call().toString()
                job.datacenters( [str])
                return this;
            }
            job.datacenters( [datacenters.toString()] as List<String>)
            return this
        }
        this
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
        final DEFAULT_CPUS = 1
        final DEFAULT_MEMORY = "500.MB"

        final taskCfg = task.getConfig()
        final taskCores =  !taskCfg.get("cpus") ? DEFAULT_CPUS :  taskCfg.get("cpus") as Integer
        final taskMemory = taskCfg.get("memory") ? new MemoryUnit( taskCfg.get("memory") as String ) : new MemoryUnit(DEFAULT_MEMORY)

        final res = new Resources()
                .cores(taskCores)
                .memoryMB(taskMemory.toMega() as Integer)

        return res
    }

    static TaskGroup createTaskGroup(TaskRun taskRun, List<String> args, Map<String, String>env, NomadJobOpts jobOpts){
        final ReschedulePolicy taskReschedulePolicy  = new ReschedulePolicy().attempts(jobOpts.rescheduleAttempts)
        final RestartPolicy taskRestartPolicy  = new RestartPolicy().attempts(jobOpts.restartAttempts)

        def task = createTask(taskRun, args, env, jobOpts)
        def taskGroup = new TaskGroup(
                name: "group",
                tasks: [ task ],
                reschedulePolicy: taskReschedulePolicy,
                restartPolicy: taskRestartPolicy
        )


        if( jobOpts.volumeSpec ) {
            taskGroup.volumes = [:]
            jobOpts.volumeSpec.eachWithIndex { volumeSpec , idx->
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
        final DRIVER = "docker"
        final DRIVER_PRIVILEGED = true

        final imageName = task.container
        final workingDir = task.workDir.toAbsolutePath().toString()
        final taskResources = getResources(task)


        def taskDef = new Task(
                name: "nf-task",
                driver: DRIVER,
                resources: taskResources,
                config: [
                        image     : imageName,
                        privileged: DRIVER_PRIVILEGED,
                        work_dir  : workingDir,
                        command   : args.first(),
                        args      : args.tail(),
                ] as Map<String, Object>,
                env: env,
        )

        volumes(task, taskDef, workingDir, jobOpts)
        affinity(task, taskDef, jobOpts)
        constraint(task, taskDef, jobOpts)
        constraints(task, taskDef, jobOpts)
        secrets(task, taskDef, jobOpts)
        return taskDef
    }

    static protected Task volumes(TaskRun task, Task taskDef, String workingDir, NomadJobOpts jobOpts){
        if( jobOpts.dockerVolume){
            String destinationDir = workingDir.split(File.separator).dropRight(2).join(File.separator)
            taskDef.config.mount = [
                    type : "volume",
                    target : destinationDir,
                    source : jobOpts.dockerVolume,
                    readonly : false
            ]
        }

        if( jobOpts.volumeSpec){
            taskDef.volumeMounts = []
            jobOpts.volumeSpec.eachWithIndex { volumeSpec, idx ->
                String destinationDir = volumeSpec.workDir ?
                        workingDir.split(File.separator).dropRight(2).join(File.separator) : volumeSpec.path
                taskDef.volumeMounts.add new VolumeMount(
                        destination: destinationDir,
                        volume: "vol_${idx}".toString()
                )
            }
        }

        taskDef
    }

    static protected Task affinity(TaskRun task, Task taskDef, NomadJobOpts jobOpts) {
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
            taskDef.affinities([affinity])
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

        if( task.processor?.config?.get(TaskDirectives.CONSTRAINTS) &&
                task.processor?.config?.get(TaskDirectives.CONSTRAINTS) instanceof Closure) {
            Closure closure = task.processor?.config?.get(TaskDirectives.CONSTRAINTS) as Closure
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
            def secrets = task.processor?.config?.get(TaskDirectives.SECRETS)
            if (secrets) {
                Template template = new Template(envvars: true, destPath: "/secrets/nf-nomad")
                String secretPath = jobOpts?.secretOpts?.path
                String tmpl = secrets.collect { String name ->
                    "${name}={{ with nomadVar \"$secretPath/${name}\" }}{{ .${name} }}{{ end }}"
                }.join('\n').stripIndent()
                template.embeddedTmpl(tmpl)
                taskDef.addTemplatesItem(template)
            }
        }
        taskDef
    }

    JobBuilder withSpreads(TaskRun task, NomadJobOpts jobOpts){
        def spreads = [] as List<Spread>
        if( jobOpts.spreadsSpec ){
            def list = SpreadsBuilder.spreadsSpecToList(jobOpts.spreadsSpec)
            spreads.addAll(list)
        }
        if( task.processor?.config?.get(TaskDirectives.SPREAD) &&
                task.processor?.config?.get(TaskDirectives.SPREAD) instanceof Map) {
            Map map = task.processor?.config?.get(TaskDirectives.SPREAD) as Map
            JobSpreads spreadSpec = new JobSpreads()
            spreadSpec.spread(map)
            def list = SpreadsBuilder.spreadsSpecToList(spreadSpec)
            spreads.addAll(list)
        }

        spreads.each{
            job.addSpreadsItem(it)
        }
        this
    }

    JobBuilder withPriority(int priority){
        job.priority = priority
        this
    }

    JobBuilder withPriority(TaskRun task){
        if( task.processor?.config?.containsKey(TaskDirectives.PRIORITY) ){
            withPriority( task.processor?.config?.get(TaskDirectives.PRIORITY) as int)
        }
        this
    }
}