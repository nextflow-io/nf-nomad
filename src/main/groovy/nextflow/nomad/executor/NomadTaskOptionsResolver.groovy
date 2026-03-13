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
    public static final String SECRETS_PATH = "secretsPath"
    public static final String SPREAD = "spread"
    public static final String PRIORITY = "priority"
    public static final String RESOURCES = "resources"
    public static final String NAMESPACE = "namespace"
    public static final String META = "meta"
    public static final String FAILURES = "failures"
    public static final String RESTART = "restart"
    public static final String RESCHEDULE = "reschedule"
    public static final String SHUTDOWN_DELAY = "shutdownDelay"
    public static final String AFFINITY = "affinity"
    public static final String VOLUMES = "volumes"
    public static final String CPU = "cpu"
    public static final String CORES = "cores"
    private static final Set<String> SUPPORTED_RESOURCE_OPTIONS = ["memoryMax", "device", CPU, CORES] as Set<String>
    private static final Set<String> SUPPORTED_AFFINITY_OPTIONS = ["attribute", "operator", "value", "weight"] as Set<String>
    private static final Set<String> SUPPORTED_VOLUME_OPTIONS = ["type", "name", "path", "workDir", "readOnly"] as Set<String>

    private static final Set<String> SUPPORTED_OPTIONS = [
            DATACENTERS,
            CONSTRAINTS,
            SECRETS,
            SECRETS_PATH,
            SPREAD,
            PRIORITY,
            RESOURCES,
            NAMESPACE,
            META,
            FAILURES,
            SHUTDOWN_DELAY,
            AFFINITY,
            VOLUMES
    ] as Set<String>

    static void validate(TaskRun task) {
        Map options = getNomadOptions(task)
        options.keySet().each { key ->
            if( !SUPPORTED_OPTIONS.contains(key?.toString()) ) {
                invalidOption(task, TaskDirectives.NOMAD_OPTIONS, key, "contains unsupported key; supported keys: ${SUPPORTED_OPTIONS.sort().join(', ')}")
            }
        }

        datacenters(task)
        constraints(task)
        secrets(task)
        secretsPath(task)
        spread(task)
        priority(task)
        resources(task)
        namespace(task)
        meta(task)
        restart(task)
        reschedule(task)
        shutdownDelay(task)
        affinity(task)
        volumes(task)
    }

    protected static Map getNomadOptions(TaskRun task) {
        def options = task?.processor?.config?.get(TaskDirectives.NOMAD_OPTIONS)
        if( options == null ) {
            return Collections.emptyMap()
        }
        if( options instanceof Map ) {
            return (Map)options
        }
        invalidOption(task, TaskDirectives.NOMAD_OPTIONS, options, "must be a map")
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
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${CONSTRAINTS}", value, "must be a closure")
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

    static String secretsPath(TaskRun task) {
        Map options = getNomadOptions(task)
        if( !options.containsKey(SECRETS_PATH) ) {
            return null
        }
        def value = options.get(SECRETS_PATH)
        if( value == null ) {
            return null
        }
        if( value instanceof CharSequence ) {
            String path = value.toString().trim()
            if( path ) {
                return path
            }
        }
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${SECRETS_PATH}", value, "must be a non-empty string")
        return null
    }

    static Map spread(TaskRun task) {
        def value = getOption(task, SPREAD, TaskDirectives.SPREAD)
        if( value == null ) {
            return null
        }
        if( value instanceof Map ) {
            return (Map)value
        }
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${SPREAD}", value, "must be a map")
        return null
    }

    static Object priority(TaskRun task) {
        return getNomadOptions(task).get(PRIORITY)
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
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${META}", value, "must be a map")
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
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${FAILURES}", value, "must be a map")
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
            invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${FAILURES}.${RESTART}", value, "must be a map")
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
            invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${FAILURES}.${RESCHEDULE}", value, "must be a map")
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
            Map resources = (Map)value
            resources.keySet().each { key ->
                if( !SUPPORTED_RESOURCE_OPTIONS.contains(key?.toString()) ) {
                    invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${RESOURCES}", key,
                            "contains unsupported key; supported keys: ${SUPPORTED_RESOURCE_OPTIONS.sort().join(', ')}")
                }
            }
            if( resources.containsKey(CPU) && resources.containsKey(CORES)
                    && resources.get(CPU) != null && resources.get(CORES) != null ) {
                invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${RESOURCES}", resources,
                        "contains conflicting keys `${CPU}` and `${CORES}`; provide only one")
            }
            return resources
        }
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${RESOURCES}", value, "must be a map")
        return Collections.emptyMap()
    }

    static Map affinity(TaskRun task) {
        Map options = getNomadOptions(task)
        if( !options.containsKey(AFFINITY) ) {
            return Collections.emptyMap()
        }
        def value = options.get(AFFINITY)
        if( value == null ) {
            return Collections.emptyMap()
        }
        if( value instanceof Map ) {
            Map affinity = (Map)value
            affinity.keySet().each { key ->
                if( !SUPPORTED_AFFINITY_OPTIONS.contains(key?.toString()) ) {
                    invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${AFFINITY}", key,
                            "contains unsupported key; supported keys: ${SUPPORTED_AFFINITY_OPTIONS.sort().join(', ')}")
                }
            }
            return affinity
        }
        invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${AFFINITY}", value, "must be a map")
        return Collections.emptyMap()
    }

    static List<Map<String, Object>> volumes(TaskRun task) {
        Map options = getNomadOptions(task)
        if( !options.containsKey(VOLUMES) ) {
            return Collections.emptyList()
        }
        def value = options.get(VOLUMES)
        if( value == null ) {
            return Collections.emptyList()
        }
        if( !(value instanceof Collection) ) {
            invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${VOLUMES}", value, "must be a list of maps")
        }

        List<Map<String, Object>> result = []
        int idx = 0
        (value as Collection).each { item ->
            if( !(item instanceof Map) ) {
                invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${VOLUMES}[${idx}]", item, "must be a map")
            }
            Map map = (Map)item
            map.keySet().each { key ->
                if( !SUPPORTED_VOLUME_OPTIONS.contains(key?.toString()) ) {
                    invalidOption(task, "${TaskDirectives.NOMAD_OPTIONS}.${VOLUMES}[${idx}]", key,
                            "contains unsupported key; supported keys: ${SUPPORTED_VOLUME_OPTIONS.sort().join(', ')}")
                }
            }
            result.add(map.collectEntries { k, v -> [(k?.toString()): v] } as Map<String, Object>)
            idx++
        }
        return result
    }

    protected static void invalidOption(TaskRun task, String optionPath, Object value, String reason) {
        String process = processName(task)
        throw new IllegalArgumentException("Invalid Nomad option for process `${process}`: `${optionPath}` ${reason} -- value: ${value}")
    }

    protected static String processName(TaskRun task) {
        return task?.processor?.name?.toString() ?: task?.name?.toString() ?: "<unknown>"
    }
}
