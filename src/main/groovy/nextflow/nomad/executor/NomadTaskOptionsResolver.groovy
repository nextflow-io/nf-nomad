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
    public static final String PRIORITY = "priority"
    public static final String RESOURCES = "resources"
    public static final String NAMESPACE = "namespace"
    public static final String META = "meta"
    public static final String FAILURES = "failures"
    public static final String RESTART = "restart"
    public static final String RESCHEDULE = "reschedule"
    public static final String SHUTDOWN_DELAY = "shutdownDelay"

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

    static Object priority(TaskRun task) {
        return getOption(task, PRIORITY, TaskDirectives.PRIORITY)
    }
    static Object namespace(TaskRun task) {
        return getNomadOptions(task).get(NAMESPACE)
    }

    static Map meta(TaskRun task) {
        Map options = getNomadOptions(task)
        if( !options.containsKey(META) ) {
            return Collections.emptyMap()
        }

        def value = options.get(META)
        if( value == null ) {
            return Collections.emptyMap()
        }
        if( value instanceof Map ) {
            return ((Map)value).collectEntries { k, v -> [(k?.toString()): v?.toString()] } as Map<String, String>
        }

        log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${META}` because it is not a map -- value: ${value}")
        return Collections.emptyMap()
    }

    static Object shutdownDelay(TaskRun task) {
        return getNomadOptions(task).get(SHUTDOWN_DELAY)
    }

    static Map failures(TaskRun task) {
        Map options = getNomadOptions(task)
        if( !options.containsKey(FAILURES) ) {
            return Collections.emptyMap()
        }

        def value = options.get(FAILURES)
        if( value == null ) {
            return Collections.emptyMap()
        }
        if( value instanceof Map ) {
            return (Map)value
        }

        log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${FAILURES}` because it is not a map -- value: ${value}")
        return Collections.emptyMap()
    }

    static Map restart(TaskRun task) {
        Map failures = failures(task)
        if( !failures.containsKey(RESTART) ) {
            return Collections.emptyMap()
        }
        def value = failures.get(RESTART)
        if( value instanceof Map ) {
            return (Map)value
        }
        if( value != null ) {
            log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${FAILURES}.${RESTART}` because it is not a map -- value: ${value}")
        }
        return Collections.emptyMap()
    }

    static Map reschedule(TaskRun task) {
        Map failures = failures(task)
        if( !failures.containsKey(RESCHEDULE) ) {
            return Collections.emptyMap()
        }
        def value = failures.get(RESCHEDULE)
        if( value instanceof Map ) {
            return (Map)value
        }
        if( value != null ) {
            log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${FAILURES}.${RESCHEDULE}` because it is not a map -- value: ${value}")
        }
        return Collections.emptyMap()
    }

    static Map resources(TaskRun task) {
        Map options = getNomadOptions(task)
        if( !options.containsKey(RESOURCES) ) {
            return Collections.emptyMap()
        }

        def value = options.get(RESOURCES)
        if( value == null ) {
            return Collections.emptyMap()
        }
        if( value instanceof Map ) {
            return (Map)value
        }

        log.warn("Ignoring `${TaskDirectives.NOMAD_OPTIONS}.${RESOURCES}` because it is not a map -- value: ${value}")
        return Collections.emptyMap()
    }
}
