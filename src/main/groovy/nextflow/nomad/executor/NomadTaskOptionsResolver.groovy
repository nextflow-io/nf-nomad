package nextflow.nomad.executor

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.processor.TaskRun

@CompileStatic
@Slf4j
class NomadTaskOptionsResolver {

    public static final String DATACENTERS = "datacenters"
    public static final String CONSTRAINTS = "constraints"
    public static final String SECRETS = "secrets"
    public static final String SPREAD = "spread"

    protected static Map getNomadOptions(TaskRun task) {
        def options = task?.processor?.config?.get(TaskDirectives.NOMAD_OPTIONS)
        if( options == null ) {
            return Collections.emptyMap()
        }
        if( options instanceof Map ) {
            return (Map)options
        }
        log.warn("Ignoring process directive `${TaskDirectives.NOMAD_OPTIONS}` because it is not a map -- value: ${options}")
        return Collections.emptyMap()
    }

    protected static Object getOption(TaskRun task, String key, String legacyDirective) {
        Map options = getNomadOptions(task)
        if( options.containsKey(key) ) {
            return options.get(key)
        }
        return task?.processor?.config?.get(legacyDirective)
    }

    static Object datacenters(TaskRun task) {
        return getOption(task, DATACENTERS, TaskDirectives.DATACENTERS)
    }

    static Closure constraints(TaskRun task) {
        def value = getOption(task, CONSTRAINTS, TaskDirectives.CONSTRAINTS)
        if( value == null ) {
            return null
        }
        if( value instanceof Closure ) {
            return (Closure)value
        }
        log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${CONSTRAINTS}` because it is not a closure -- value: ${value}")
        return null
    }

    static List<String> secrets(TaskRun task) {
        def value = getOption(task, SECRETS, TaskDirectives.SECRETS)
        if( value == null ) {
            return null
        }
        if( value instanceof Collection ) {
            return (value as Collection)
                    .collect { Object item -> item?.toString() }
                    .findAll { String item -> item } as List<String>
        }
        return [value.toString()]
    }

    static Map spread(TaskRun task) {
        def value = getOption(task, SPREAD, TaskDirectives.SPREAD)
        if( value == null ) {
            return null
        }
        if( value instanceof Map ) {
            return (Map)value
        }
        log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${SPREAD}` because it is not a map -- value: ${value}")
        return null
    }
}
