package nextflow.nomad.executor

import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.ProcessConfig
import spock.lang.Specification

class NomadTaskOptionsResolverSpec extends Specification {

    void "should prefer nomadOptions values over legacy directives"() {
        given:
        Closure legacyConstraints = { node { unique = [name: 'legacy-node'] } }
        Closure nomadOptionsConstraints = { node { unique = [name: 'new-node'] } }
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        datacenters: ['dc-new'],
                        constraints: nomadOptionsConstraints,
                        secrets    : ['NEW_ONE', 'NEW_TWO'],
                        spread     : [name: 'node.class', weight: 10],
                        affinity   : [attribute: '${meta.workload}', operator: '=', value: 'batch', weight: 10],
                        resources  : [memoryMax: '4 GB'],
                        namespace  : 'process-ns',
                        meta       : [owner: 'team-b'],
                        failures   : [
                                restart   : [attempts: 2],
                                reschedule: [attempts: 3]
                        ],
                        shutdownDelay: '15s'
                ],
                (TaskDirectives.DATACENTERS)  : ['dc-legacy'],
                (TaskDirectives.CONSTRAINTS)  : legacyConstraints,
                (TaskDirectives.SECRETS)      : ['LEGACY_SECRET'],
                (TaskDirectives.SPREAD)       : [name: 'legacy.attr', weight: 50],
                (TaskDirectives.PRIORITY)     : 'low'
        ])

        expect:
        NomadTaskOptionsResolver.datacenters(task) == ['dc-new']
        NomadTaskOptionsResolver.constraints(task) == nomadOptionsConstraints
        NomadTaskOptionsResolver.secrets(task) == ['NEW_ONE', 'NEW_TWO']
        NomadTaskOptionsResolver.spread(task) == [name: 'node.class', weight: 10]
        NomadTaskOptionsResolver.affinity(task) == [attribute: '${meta.workload}', operator: '=', value: 'batch', weight: 10]
        NomadTaskOptionsResolver.priority(task) == 'low'
        NomadTaskOptionsResolver.resources(task) == [memoryMax: '4 GB']
        NomadTaskOptionsResolver.namespace(task) == 'process-ns'
        NomadTaskOptionsResolver.meta(task) == [owner: 'team-b']
        NomadTaskOptionsResolver.restart(task) == [attempts: 2]
        NomadTaskOptionsResolver.reschedule(task) == [attempts: 3]
        NomadTaskOptionsResolver.shutdownDelay(task) == '15s'
    }

    void "should prefer nomadOptions priority over legacy priority"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [priority: 'critical'],
                (TaskDirectives.PRIORITY)     : 'low'
        ])

        expect:
        NomadTaskOptionsResolver.priority(task) == 'critical'
    }

    void "should fallback to legacy directives when nomadOptions key is absent"() {
        given:
        Closure legacyConstraints = { node { unique = [name: 'legacy-node'] } }
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [datacenters: ['dc-new']],
                (TaskDirectives.CONSTRAINTS)  : legacyConstraints,
                (TaskDirectives.SECRETS)      : ['LEGACY_SECRET'],
                (TaskDirectives.SPREAD)       : [name: 'legacy.attr', weight: 50],
                (TaskDirectives.PRIORITY)     : 'high'
        ])

        expect:
        NomadTaskOptionsResolver.datacenters(task) == ['dc-new']
        NomadTaskOptionsResolver.constraints(task) == legacyConstraints
        NomadTaskOptionsResolver.secrets(task) == ['LEGACY_SECRET']
        NomadTaskOptionsResolver.spread(task) == [name: 'legacy.attr', weight: 50]
        NomadTaskOptionsResolver.priority(task) == 'high'
    }

    void "should fail on non-map nomadOptions"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): 'invalid',
                (TaskDirectives.DATACENTERS)  : ['dc-legacy']
        ])
        when:
        NomadTaskOptionsResolver.validate(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "should fail on non-map nomadOptions resources value"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [resources: 'invalid']
        ])
        when:
        NomadTaskOptionsResolver.resources(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "should fail on invalid map-based values for meta and failures"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        meta    : 'invalid',
                        failures: [restart: 'invalid', reschedule: 1]
                ]
        ])
        when:
        NomadTaskOptionsResolver.meta(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "should fail when both resources cpu and cores are provided"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        resources: [cpu: 2000, cores: 2]
                ]
        ])

        when:
        NomadTaskOptionsResolver.resources(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "should fail on unsupported nomadOptions resources keys"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        resources: [unsupported: true]
                ]
        ])

        when:
        NomadTaskOptionsResolver.resources(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "should fail on unsupported nomadOptions affinity keys"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        affinity: [attribute: '${meta.gpu}', value: 'true', unsupported: true]
                ]
        ])

        when:
        NomadTaskOptionsResolver.affinity(task)

        then:
        thrown(IllegalArgumentException)
    }

    void "should fail on unsupported nomadOptions keys"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): [
                        unsupportedKey: true
                ]
        ])

        when:
        NomadTaskOptionsResolver.validate(task)

        then:
        thrown(IllegalArgumentException)
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
