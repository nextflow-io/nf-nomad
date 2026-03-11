package nextflow.nomad.executor

class TaskDirectives {

    public static final String DATACENTERS = "datacenters"

    public static final String CONSTRAINTS = "constraints"

    public static final String SECRETS = "secret"

    public static final String SPREAD = "spread"

    public static final String PRIORITY = "priority"

    public static final List<String> ALL = [
            DATACENTERS,
            CONSTRAINTS,
            SECRETS,
            SPREAD,
            PRIORITY
    ]
}
