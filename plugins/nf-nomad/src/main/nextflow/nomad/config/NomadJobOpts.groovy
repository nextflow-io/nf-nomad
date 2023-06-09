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

/**
 * Model Nomad job settings defined in the nextflow.config file
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

class NomadJobOpts {

  //NOTE: Do not expose this to users, as this is the only job type supported
    static public final String DEFAULT_JOB_TYPE = "batch"

  //TODO: We should support CSI-volumes 


//Only nice to have
// group 

    NomadJobOpts() {
      volumes

    }

}
