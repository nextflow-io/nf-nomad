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

package nextflow.nomad.batch

import groovy.transform.CompileStatic
import io.nomadproject.client.Configuration
import nextflow.nomad.config.NomadClientOpts

/**
 * Nomad API client
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@CompileStatic
class NomadClient {
    NomadClient() {
        Configuration.getDefaultApiClient()
                .setBasePath(NomadClientOpts.DEFAULT_BASE_PATH)
                .getAuthentication(NomadClientOpts.DEFAULT_AUTH_NAME)
    }

    NomadClient(NomadClientOpts clientOpts) {
        Configuration.getDefaultApiClient()
                .setBasePath(clientOpts.basePath)
                .getAuthentication(clientOpts.authName)
    }
}
