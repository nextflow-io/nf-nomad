package nextflow.nomad.config

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import nextflow.util.Duration

import java.nio.file.Paths

/**
 * Models the Nomad client configuration settings
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@EqualsAndHashCode
@CompileStatic
class NomadClientOpts {

    private Map<String, String> sysEnv

    String server
    String dataCenter
    String token
    Duration httpReadTimeout
    Duration httpConnectTimeout

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


    NomadClientOpts(Map clientConfig, Map<String, String> env = null) {
        assert clientConfig != null

        sysEnv = env == null ? new HashMap<String, String>(System.getenv()) : env

        this.server = clientConfig.server ?: "${sysEnv.get('NOMAD_ADDR')}/v1"
        this.token = clientConfig.token ?: sysEnv.get("NOMAD_TOKEN")
        this.dataCenter = clientConfig.dataCenter ?: sysEnv.get("NOMAD_DC")

        this.region = clientConfig.region ?: DEFAULT_REGION
        this.namespace = clientConfig.namespace ?: DEFAULT_NAMESPACE

        this.driver = DEFAULT_DRIVER
        this.jobType = DEFAULT_JOB_TYPE


    }

    private String cut(String str) {
        if (!str) return '-'
        return str.size() < 10 ? str : str[0..5].toString() + '..'
    }

    String toString() {
        "${this.class.getSimpleName()}[ server=$server, namespace=$namespace, token=${cut(token)}, httpReadTimeout=$httpReadTimeout, httpConnectTimeout=$httpConnectTimeout ]"
    }

}

