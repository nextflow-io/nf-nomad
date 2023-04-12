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

import spock.lang.Specification

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadClientOptsTest extends Specification {

    def 'should get server path'() {

        expect:
        new NomadClientOpts(serverBasePath: PATH, apiToken: TOKEN).serverBasePath == EXPECTED

        where:
        PATH               | TOKEN | EXPECTED
        null               | null  | NomadClientOpts.DEFAULT_SERVER_BASE_PATH
        "http://nomad.api" | null  | "http://nomad.api"

    }

}
