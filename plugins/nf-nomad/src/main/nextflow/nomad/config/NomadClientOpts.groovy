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

    static public final String DEFAULT_SERVER_BASE_PATH = "http://127.0.0.1:4646/v1"
    String serverBasePath

    static public final String DEFAULT_NAMESPACE = "default"
    String namespace

    static public final String DEFAULT_DATACENTER = "dc1"
    String dataCenter
  
    static public final String DEFAULT_REGION = "global"
    String region

    //FIXME Fail the config check if an ACL token isn't provided
    static public final String DEFAULT_API_TOKEN = "NONE"
    String apiToken

    //-------------------------------------------------------------------
    //NOTE: Hard-coded driver, refactor later to adapt different drivers
    //-------------------------------------------------------------------
    static public final String DEFAULT_DRIVER = "docker"
    String driver

    static public final String DEFAULT_JOB_TYPE = "batch"
    String jobType

    //-------------------------------------------------------------------


    // TODO (fix milestone): Implement the TLS certificate

    NomadClientOpts() {
        this.serverBasePath = DEFAULT_SERVER_BASE_PATH 
        this.namespace = DEFAULT_NAMESPACE 
        this.dataCenter = DEFAULT_DATACENTER 
        this.region = DEFAULT_REGION 
        this.apiToken = DEFAULT_API_TOKEN 
        this.driver = DEFAULT_DRIVER
        this.jobType = DEFAULT_JOB_TYPE
    }

    NomadClientOpts(Map config) {
        assert config != null
        this.serverBasePath = config.serverBasePath ?: DEFAULT_SERVER_BASE_PATH 
        this.namespace = config.namespace ?: DEFAULT_NAMESPACE 
        this.dataCenter = config.dataCenter ?: DEFAULT_DATACENTER
        this.region = config.region ?: DEFAULT_REGION 
        this.apiToken = config.apiToken ?: DEFAULT_API_TOKEN 
        this.driver = DEFAULT_DRIVER
        this.jobType = DEFAULT_JOB_TYPE
    }

}
