package nextflow.nomad.executor

import groovy.transform.CompileStatic

@CompileStatic
class NomadLifecycleTaskSpec {
    String name
    String hook
    boolean sidecar = false
    String driver = 'raw_exec'
    String user
    List<String> command = Collections.emptyList()
    Map<String, Object> config = Collections.emptyMap()
    Map<String, String> env = Collections.emptyMap()
    Integer cpu = 200
    Integer memoryMb = 128

    /**
     * Structured transfer manifest JSON string describing the data movements
     * this lifecycle task will perform.  Embedded in Nomad task Meta so that
     * an external control plane can inspect and gate the job before submission.
     */
    String transferManifest

    /**
     * Additional metadata to attach to this Nomad task's Meta field.
     * Keys are merged with any auto-generated meta (e.g. from transferManifest).
     */
    Map<String, String> meta = Collections.emptyMap()
}
