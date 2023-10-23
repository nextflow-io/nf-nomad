/*
 * Copyright 2023, Stellenbosch University, South Africa
 * Copyright 2022, Center for Medical Genetics, Ghent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.nomad.config

import groovy.transform.CompileStatic

/**
 * Model Nomad job settings defined in the nextflow.config file
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@CompileStatic
class NomadClientOpts {

    private Map<String,String> sysEnv

    String address
    String dataCenter
    String token

    // TODO (fix milestone): Implement the TLS certificate

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



    NomadClientOpts(Map config, Map<String,String> env = null) {
        assert config != null
        sysEnv = env==null ? new HashMap<String,String>(System.getenv()) : env


        //Source these from the environment
        def addr = config.address ?: sysEnv.get("NOMAD_ADDR")
        this.address = "${addr}/v1"
        this.token = config.token ?: sysEnv.get("NOMAD_TOKEN")
        this.dataCenter = config.dataCenter ?: sysEnv.get("NOMAD_DC")

        //Source these from the config file
        this.region = config.region ?: DEFAULT_REGION
        this.namespace = config.namespace ?: DEFAULT_NAMESPACE


        //These are hard-coded temporarily
        this.driver = DEFAULT_DRIVER
        this.jobType = DEFAULT_JOB_TYPE
    }

}
