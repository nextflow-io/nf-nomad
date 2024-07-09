package nextflow.nomad.executor

class TaskDirectives {

    public static final String DATACENTERS = "datacenters"

    public static final String CONSTRAINTS = "constraints"

    public static final List<String> ALL = [
            DATACENTERS,
            CONSTRAINTS
    ]
}
