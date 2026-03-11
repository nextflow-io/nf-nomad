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

    private TaskRun taskWithConfig(Map<String, Object> configValues) {
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key -> configValues.get(key) }

        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig

        return Mock(TaskRun) {
            getProcessor() >> processor
        }
    }
}
