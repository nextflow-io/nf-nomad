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
                        spread     : [name: 'node.class', weight: 10]
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
        NomadTaskOptionsResolver.priority(task) == 'low'
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

    void "should ignore non-map nomadOptions and keep using legacy directives"() {
        given:
        def task = taskWithConfig([
                (TaskDirectives.NOMAD_OPTIONS): 'invalid',
                (TaskDirectives.DATACENTERS)  : ['dc-legacy']
        ])

        expect:
        NomadTaskOptionsResolver.datacenters(task) == ['dc-legacy']
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
