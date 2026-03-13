/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
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
import groovy.util.logging.Slf4j
import nextflow.util.Duration


/**
 * Nomad Config
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@Slf4j
@CompileStatic
class NomadClientOpts{

    final static protected API_VERSION = "v1"

    final String address
    final String token
    final int connectionTimeout

    final int readTimeout
    final int writeTimeout
    final Duration pollInterval
    final Duration submitThrottle

    final RetryConfig retryConfig

    NomadClientOpts(Map nomadClientOpts, Map<String,String> env=null){
        assert nomadClientOpts!=null

        def sysEnv = new HashMap<String,String>(System.getenv())
        if( env ) {
            sysEnv.putAll(env)
        }

        def address = (nomadClientOpts.address?.toString() ?: sysEnv.get('NOMAD_ADDR'))
        assert address != null, "Nomad Address is required"

        if( !address.endsWith("/"))
            address +="/"
        this.address = address + API_VERSION
        this.token = nomadClientOpts.token ?: sysEnv.get('NOMAD_TOKEN')
        this.connectionTimeout = (nomadClientOpts.connectionTimeout ?: 6000 ) as Integer
        this.readTimeout = (nomadClientOpts.readTimeout ?: 6000 ) as Integer
        this.writeTimeout = (nomadClientOpts.writeTimeout ?: 6000 ) as Integer
        def pollValue = nomadClientOpts.get('pollInterval') ?: sysEnv.get('NF_NOMAD_POLL_INTERVAL')
        if( pollValue ) {
            this.pollInterval = pollValue instanceof Duration
                    ? (pollValue as Duration)
                    : Duration.of(pollValue.toString())
        } else {
            this.pollInterval = Duration.of('1s')
        }

        def submitThrottleValue = nomadClientOpts.get('submitThrottle') ?: sysEnv.get('NF_NOMAD_SUBMIT_THROTTLE')
        if( submitThrottleValue ) {
            this.submitThrottle = submitThrottleValue instanceof Duration
                    ? (submitThrottleValue as Duration)
                    : Duration.of(submitThrottleValue.toString())
        } else {
            this.submitThrottle = Duration.of('0s')
        }

        this.retryConfig = new RetryConfig(nomadClientOpts.retryConfig as Map ?: Collections.emptyMap())

        //TODO: Add mTLS properties and env vars
        // https://developer.hashicorp.com/nomad/docs/commands#mtls-environment-variables
    }

    RetryConfig getRetryConfig() {
        return retryConfig
    }
}