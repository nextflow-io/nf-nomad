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

package nextflow.nomad.executor


import nextflow.executor.BashWrapperBuilder
import nextflow.executor.SimpleFileCopyStrategy
import nextflow.processor.TaskBean
import nextflow.util.Escape

/**
 * Custom bash wrapper builder for Nomad jobs/tasks
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadScriptLauncher extends BashWrapperBuilder {

    NomadScriptLauncher(TaskBean taskBean) {
        super(taskBean)
    }

}
