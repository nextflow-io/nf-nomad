/*
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

    static public final String DEFAULT_BASE_PATH = "http://127.0.0.1:4646/v1"
    static public final String DEFAULT_API_KEY = "NONE"
    static public final String DEFAULT_NAMESPACE = "NONE"
    static public final String DEFAULT_REGION = "NONE"

    String basePath
    String apiKey
    String namespace
    String region

    NomadClientOpts() {
        this.basePath = DEFAULT_BASE_PATH
        this.apiKey = DEFAULT_API_KEY
        this.namespace = DEFAULT_NAMESPACE
        this.region = DEFAULT_REGION
    }

    NomadClientOpts(Map config) {
        assert config != null
        this.basePath = config.basePath ?: DEFAULT_BASE_PATH
        this.apiKey = config.apiKey ?: DEFAULT_API_KEY
        this.namespace = config.namespace ?: DEFAULT_NAMESPACE
        this.region = config.region ?: DEFAULT_REGION
    }


}
