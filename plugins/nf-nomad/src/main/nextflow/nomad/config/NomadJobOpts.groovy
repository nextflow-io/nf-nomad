package nextflow.nomad.config

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * Models the Nomad job configuration settings
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@EqualsAndHashCode
@CompileStatic
class NomadJobOpts {

    private Map<String, String> sysEnv

    Boolean deleteJobsOnCompletion

//-------------------------------------------------------------------
//NOTE: Use the default for region and namespace
//-------------------------------------------------------------------

    static public final String DEFAULT_REGION = "global"
    String region

    static public final String DEFAULT_NAMESPACE = "default"
    String namespace

//-------------------------------------------------------------------
//NOTE: Hard-coded to job type and docker containers
//-------------------------------------------------------------------
    static public final String DEFAULT_DRIVER = "docker"
    String driver

    static public final String DEFAULT_JOB_TYPE = "batch"
    String jobType

//-------------------------------------------------------------------


    NomadJobOpts(Map jobConfig, Map<String, String> env = null) {
        assert jobConfig != null

        sysEnv = env == null ? new HashMap<String, String>(System.getenv()) : env

        this.deleteJobsOnCompletion = jobConfig.deleteTasksOnCompletion

    }

    String toString() {
        "${this.class.getSimpleName()}[ deleteJobsOnCompletion=$deleteJobsOnCompletion]"
    }

}

