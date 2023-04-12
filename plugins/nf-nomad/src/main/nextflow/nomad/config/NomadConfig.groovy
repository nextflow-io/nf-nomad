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
import nextflow.Global
import nextflow.Session

/**
 * Model Nomad job settings defined in the nextflow.config file
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@CompileStatic
class NomadConfig {

    // private NomadJobOpts jobOpts
    private NomadClientOpts clientOpts

    // NomadJobOpts job() { jobOpts }

    NomadClientOpts client() { clientOpts }


    NomadConfig(Map nomad) {
        // this.jobOpts = new NomadJobOpts((Map) nomad.job ?: Collections.emptyMap())
        this.clientOpts = new NomadClientOpts((Map) nomad.client ?: Collections.emptyMap())
    }


    NomadConfig() {
//        this.jobOpts = new NomadJobOpts()
        this.clientOpts = new NomadClientOpts()
    }


    static NomadConfig getConfig(Session session) {
        if (!session)
            throw new IllegalStateException("Missing Nextflow session")

        new NomadConfig((Map) session.config.nomad ?: Collections.emptyMap())
    }

    static NomadConfig getConfig() {
        getConfig(Global.session as Session)
    }
}
