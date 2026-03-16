package nextflow.nomad.builders

import io.nomadproject.client.model.Job
import io.nomadproject.client.model.Task
import nextflow.executor.res.AcceleratorResource
import nextflow.nomad.config.NomadJobOpts
import nextflow.nomad.executor.TaskDirectives
import nextflow.nomad.models.JobVolume
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import spock.lang.Specification

import java.nio.file.Path

class JobBuilderNomadOptionsSpec extends Specification {

    void "assignDatacenters should merge global and process datacenters deterministically"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [datacenters: ['dc-new']],
                (TaskDirectives.DATACENTERS)  : ['dc-legacy']
        ])
        def job = new Job().datacenters(['dc-global'])

        when:
        JobBuilder.assignDatacenters(task, job)

        then:
        job.datacenters == ['dc-global', 'dc-new']
    }

    void "assignDatacenters should keep legacy behavior when nomadOptions is absent"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.DATACENTERS): ['dc-legacy']
        ])
        def job = new Job()

        when:
        JobBuilder.assignDatacenters(task, job)

        then:
        job.datacenters == ['dc-legacy']
    }

    void "constraints should prefer nomadOptions constraints closure"() {
        given:
        Closure legacyConstraints = { node { unique = [name: 'legacy-node'] } }
        Closure nomadOptionsConstraints = { attr { kernel = [name: 'linux'] } }
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [constraints: nomadOptionsConstraints],
                (TaskDirectives.CONSTRAINTS)  : legacyConstraints
        ])
        def taskDef = new Task()

        when:
        JobBuilder.constraints(task, taskDef, Stub(NomadJobOpts))

        then:
        taskDef.constraints.size() == 1
        taskDef.constraints[0].getLtarget() == '${attr.kernel.name}'
        taskDef.constraints[0].getRtarget() == 'linux'
    }

    void "secrets should prefer nomadOptions secrets list"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [secrets: ['NEW_ACCESS', 'NEW_SECRET']],
                (TaskDirectives.SECRETS)      : ['OLD_SECRET']
        ])
        def taskDef = new Task()
        def jobOpts = Stub(NomadJobOpts) {
            getSecretOpts() >> [enabled: true, path: 'secret/path']
        }

        when:
        JobBuilder.secrets(task, taskDef, jobOpts)

        then:
        taskDef.templates.size() == 1
        taskDef.templates[0].embeddedTmpl.contains('NEW_ACCESS={{ with nomadVar "secret/path/NEW_ACCESS" }}{{ .NEW_ACCESS }}{{ end }}')
        taskDef.templates[0].embeddedTmpl.contains('NEW_SECRET={{ with nomadVar "secret/path/NEW_SECRET" }}{{ .NEW_SECRET }}{{ end }}')
        !taskDef.templates[0].embeddedTmpl.contains('OLD_SECRET')
    }

    void "secrets should use nomadOptions secretsPath override when provided"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        secrets: ['NEW_ACCESS'],
                        secretsPath: 'secret/process'
                ]
        ])
        def taskDef = new Task()
        def jobOpts = Stub(NomadJobOpts) {
            getSecretOpts() >> [enabled: true, path: 'secret/global']
        }

        when:
        JobBuilder.secrets(task, taskDef, jobOpts)

        then:
        taskDef.templates.size() == 1
        taskDef.templates[0].embeddedTmpl.contains('NEW_ACCESS={{ with nomadVar \"secret/process/NEW_ACCESS\" }}{{ .NEW_ACCESS }}{{ end }}')
        !taskDef.templates[0].embeddedTmpl.contains('secret/global/NEW_ACCESS')
    }

    void "spreads should prefer nomadOptions spread map"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [spread: [name: 'node.class', weight: 10, targets: ['gpu': 100]]],
                (TaskDirectives.SPREAD)       : [name: 'legacy.class', weight: 50, targets: ['cpu': 100]]
        ])
        def jobDef = new Job()

        when:
        JobBuilder.spreads(task, jobDef, Stub(NomadJobOpts))

        then:
        jobDef.spreads.size() == 1
        jobDef.spreads[0].attribute == 'node.class'
        jobDef.spreads[0].weight == 10
        jobDef.spreads[0].spreadTarget.first().value == 'gpu'
        jobDef.spreads[0].spreadTarget.first().percent == 100
    }
    void "getResources should default memoryMax to task memory"() {
        given:
        def task = taskWithConfig([:], [cpus: 2, memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getMemoryMB() == 2048
        resources.getMemoryMaxMB() == 2048
    }

    void "affinity should include global and process nomadOptions affinity entries"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [affinity: [attribute: '${meta.workload}', operator: '=', value: 'batch', weight: 20]]
        ])
        def taskDef = new Task()
        def jobOpts = Stub(NomadJobOpts) {
            getAffinitySpec() >> new nextflow.nomad.models.JobAffinity()
                    .attribute('${meta.global}')
                    .operator('=')
                    .value('true')
                    .weight(10)
        }

        when:
        JobBuilder.affinity(task, taskDef, jobOpts)

        then:
        taskDef.affinities.size() == 2
        taskDef.affinities[0].getLtarget() == '${meta.global}'
        taskDef.affinities[1].getLtarget() == '${meta.workload}'
    }

    void "affinity should fail when process affinity is missing required fields"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [affinity: [operator: '=']]
        ])
        def taskDef = new Task()

        when:
        JobBuilder.affinity(task, taskDef, Stub(NomadJobOpts))

        then:
        thrown(IllegalArgumentException)
    }

    void "getResources should apply nomadOptions resources cores override"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [cores: 6]]
        ], [cpus: 2, memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getCores() == 6
    }

    void "getResources should apply nomadOptions resources cpu override"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [cpu: 1500]]
        ], [cpus: 2, memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getCPU() == 1500
        resources.getCores() == null
    }

    void "getResources should map task cpus to Nomad CPU when global cpuMode is cpu"() {
        given:
        def taskConfig = Mock(TaskConfig)
        taskConfig.get("cpus") >> 3
        taskConfig.get("memory") >> "2 GB"
        taskConfig.getAccelerator() >> null
        def task = taskWithConfig([:], taskConfig)
        def jobOpts = Stub(NomadJobOpts) {
            getCpuMode() >> NomadJobOpts.CPU_MODE_CPU
            getAcceleratorAutoDevice() >> false
        }

        when:
        def resources = JobBuilder.getResources(task, jobOpts)

        then:
        resources.getCPU() == 3000
        resources.getCores() == null
    }

    void "getResources should fail when both nomadOptions resources cpu and cores are set"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [cpu: 1500, cores: 3]]
        ], [cpus: 2, memory: '2 GB'])

        when:
        JobBuilder.getResources(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "createTaskGroup should merge global and process volume specs"() {
        given:
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key ->
            key == TaskDirectives.NOMAD_OPTIONS
                    ? [volumes: [[type: 'host', name: 'process-vol', path: '/data', readOnly: true]]]
                    : null
        }
        def processor = Mock(TaskProcessor) {
            getConfig() >> processConfig
        }
        def task = Mock(TaskRun) {
            getProcessor() >> processor
            getConfig() >> [memory: '1 GB', cpus: 1]
            getContainer() >> 'ubuntu:22.04'
            getWorkDir() >> Path.of('/tmp/nf/work')
        }
        def globalVolume = new JobVolume().type('host').name('global-workdir').workDir(true)
        def jobOpts = Stub(NomadJobOpts) {
            getVolumeSpec() >> ([globalVolume] as JobVolume[])
            getDockerVolume() >> null
            getPrivileged() >> true
            getRestartAttempts() >> 1
            getRescheduleAttempts() >> 1
        }

        when:
        def group = JobBuilder.createTaskGroup(task, ['bash', '-c', 'echo test'], [:], jobOpts)

        then:
        group.volumes.keySet().toList() == ['vol_0', 'vol_1']
        group.tasks[0].volumeMounts*.destination.contains('/tmp')
        group.tasks[0].volumeMounts*.destination.contains('/data')
        group.tasks[0].volumeMounts.find { it.destination == '/data' }?.readOnly == true
    }

    void "createTaskGroup should fail for invalid process volume entries"() {
        given:
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key ->
            key == TaskDirectives.NOMAD_OPTIONS
                    ? [volumes: [[type: 'host', name: 'broken-volume']]]
                    : null
        }
        def processor = Mock(TaskProcessor) {
            getConfig() >> processConfig
        }
        def task = Mock(TaskRun) {
            getProcessor() >> processor
            getConfig() >> [memory: '1 GB', cpus: 1]
            getContainer() >> 'ubuntu:22.04'
            getWorkDir() >> Path.of('/tmp/nf/work')
        }
        def jobOpts = Stub(NomadJobOpts) {
            getVolumeSpec() >> null
            getDockerVolume() >> null
            getPrivileged() >> true
            getRestartAttempts() >> 1
            getRescheduleAttempts() >> 1
        }

        when:
        JobBuilder.createTaskGroup(task, ['bash', '-c', 'echo test'], [:], jobOpts)

        then:
        thrown(IllegalArgumentException)
    }

    void "createTaskGroup should fail when merged global and process volumes define multiple workDir mounts"() {
        given:
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key ->
            key == TaskDirectives.NOMAD_OPTIONS
                    ? [volumes: [[type: 'host', name: 'process-work', workDir: true]]]
                    : null
        }
        def processor = Mock(TaskProcessor) {
            getConfig() >> processConfig
        }
        def task = Mock(TaskRun) {
            getProcessor() >> processor
            getConfig() >> [memory: '1 GB', cpus: 1]
            getContainer() >> 'ubuntu:22.04'
            getWorkDir() >> Path.of('/tmp/nf/work')
        }
        def globalVolume = new JobVolume().type('host').name('global-work').workDir(true)
        def jobOpts = Stub(NomadJobOpts) {
            getVolumeSpec() >> ([globalVolume] as JobVolume[])
            getDockerVolume() >> null
            getPrivileged() >> true
            getRestartAttempts() >> 1
            getRescheduleAttempts() >> 1
        }

        when:
        JobBuilder.createTaskGroup(task, ['bash', '-c', 'echo test'], [:], jobOpts)

        then:
        thrown(IllegalArgumentException)
    }

    void "getResources should apply nomadOptions resources device list"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [device: [[name: 'nvidia/gpu', count: 2]]]]
        ], [memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getDevices().size() == 1
        resources.getDevices().first().getName() == 'nvidia/gpu'
        resources.getDevices().first().getCount() == 2
    }

    void "getResources should infer device request from accelerator when enabled"() {
        given:
        def taskConfig = Mock(TaskConfig)
        taskConfig.get("cpus") >> 2
        taskConfig.get("memory") >> "2 GB"
        taskConfig.getAccelerator() >> new AcceleratorResource([request: 2, type: 'nvidia'])
        def task = taskWithConfig([:], taskConfig)
        def jobOpts = Stub(NomadJobOpts) {
            getCpuMode() >> NomadJobOpts.CPU_MODE_CORES
            getAcceleratorAutoDevice() >> true
            getAcceleratorDeviceName() >> 'nvidia/gpu'
        }

        when:
        def resources = JobBuilder.getResources(task, jobOpts)

        then:
        resources.getDevices().size() == 1
        resources.getDevices().first().getName() == 'nvidia/gpu'
        resources.getDevices().first().getCount() == 2
    }

    void "getResources should prefer explicit device request over inferred accelerator mapping"() {
        given:
        def taskConfig = Mock(TaskConfig)
        taskConfig.get("cpus") >> 2
        taskConfig.get("memory") >> "2 GB"
        taskConfig.getAccelerator() >> new AcceleratorResource([request: 3, type: 'nvidia'])
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [device: [[name: 'custom/gpu', count: 1]]]]
        ], taskConfig)
        def jobOpts = Stub(NomadJobOpts) {
            getCpuMode() >> NomadJobOpts.CPU_MODE_CORES
            getAcceleratorAutoDevice() >> true
            getAcceleratorDeviceName() >> 'nvidia/gpu'
        }

        when:
        def resources = JobBuilder.getResources(task, jobOpts)

        then:
        resources.getDevices().size() == 1
        resources.getDevices().first().getName() == 'custom/gpu'
        resources.getDevices().first().getCount() == 1
    }

    void "resolveRestartPolicy should merge global and process options with process precedence"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [failures: [restart: [attempts: 1, delay: '5s', mode: 'fail']]]
        ])
        def jobOpts = Stub(NomadJobOpts) {
            getRestartAttempts() >> 3
            getRestartPolicy() >> [attempts: 3, interval: '1m']
        }

        when:
        def policy = JobBuilder.resolveRestartPolicy(task, jobOpts)

        then:
        policy.getAttempts() == 1
        policy.getDelay() == 5000
        policy.getInterval() == 60000
        policy.getMode() == 'fail'
    }

    void "resolveReschedulePolicy should merge global and process options with process precedence"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [failures: [reschedule: [attempts: 2, delay: '20s', unlimited: true]]]
        ])
        def jobOpts = Stub(NomadJobOpts) {
            getRescheduleAttempts() >> 4
            getReschedulePolicy() >> [attempts: 4, interval: '1m', maxDelay: '2m']
        }

        when:
        def policy = JobBuilder.resolveReschedulePolicy(task, jobOpts)

        then:
        policy.getAttempts() == 2
        policy.getDelay() == 20000
        policy.getInterval() == 60000
        policy.getMaxDelay() == 120000
        policy.getUnlimited() == true
    }

    void "resolveShutdownDelayMillis should prefer process nomadOptions over global setting"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [shutdownDelay: '15s']
        ])
        def jobOpts = Stub(NomadJobOpts) {
            getShutdownDelay() >> nextflow.util.Duration.of('30s')
        }

        expect:
        JobBuilder.resolveShutdownDelayMillis(task, jobOpts) == 15000
    }

    void "getResources should use nomadOptions resources memoryMax when provided"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [memoryMax: '3 GB']]
        ], [cpus: 2, memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getMemoryMB() == 2048
        resources.getMemoryMaxMB() == 3072
    }

    void "getResources should fail when nomadOptions resources memoryMax is invalid"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [memoryMax: 'invalid']]
        ], [cpus: 2, memory: '2 GB'])

        when:
        JobBuilder.getResources(task)

        then:
        thrown(IllegalArgumentException)
    }

    private TaskRun taskWithConfig(Map<String, Object> configValues, Object taskConfigValues = [:]) {
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key -> configValues.get(key) }

        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig

        return Mock(TaskRun) {
            getProcessor() >> processor
            getConfig() >> taskConfigValues
        }
    }
}
