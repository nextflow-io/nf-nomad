package nextflow.nomad.builders

import io.nomadproject.client.model.Job
import io.nomadproject.client.model.Task
import nextflow.nomad.config.NomadJobOpts
import nextflow.nomad.executor.TaskDirectives
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import spock.lang.Specification

class JobBuilderNomadOptionsSpec extends Specification {

    void "assignDatacenters should prefer nomadOptions over legacy datacenters"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [datacenters: ['dc-new']],
                (TaskDirectives.DATACENTERS)  : ['dc-legacy']
        ])
        def job = new Job().datacenters(['dc-global'])

        when:
        JobBuilder.assignDatacenters(task, job)

        then:
        job.datacenters == ['dc-new']
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

    void "getResources should ignore nomadOptions resources cores and keep task cpus"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [cores: 6]]
        ], [cpus: 2, memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getCores() == 2
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

    void "getResources should fallback to task memory when nomadOptions resources memoryMax is invalid"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: [memoryMax: 'invalid']]
        ], [cpus: 2, memory: '2 GB'])

        when:
        def resources = JobBuilder.getResources(task)

        then:
        resources.getMemoryMB() == 2048
        resources.getMemoryMaxMB() == 2048
    }

    private TaskRun taskWithConfig(Map<String, Object> configValues, Map<String, Object> taskConfigValues = [:]) {
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
