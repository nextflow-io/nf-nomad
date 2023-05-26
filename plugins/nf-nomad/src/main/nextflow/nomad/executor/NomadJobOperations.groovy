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

package nextflow.nomad.executor

import groovy.transform.CompileStatic
import io.nomadproject.client.models.Job
import io.nomadproject.client.models.Task
import io.nomadproject.client.models.Resources
import io.nomadproject.client.models.TaskGroup
import io.nomadproject.client.models.Template
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskRun
import groovy.util.logging.Slf4j

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */


@Slf4j
@CompileStatic
class NomadJobOperations {

    static Job createJobDef(NomadConfig config, TaskRun task, String taskId ) {

        final container = task.getContainer()
        if (!container)
            throw new IllegalArgumentException("Missing container image for process: $task.name")

        log.trace "[NOMAD] Submitting task: $taskId, cpus=${task.config.getCpus()}, mem=${task.config.getMemory() ?: '-'}"

        def dataCenter = config.client().dataCenter
        def driver = config.client().driver
        def jobType = config.client().jobType

        def commandRunTmpl = new Template()
                .destPath("/local/.command.run")
                .embeddedTmpl(task.getScript())

        def commandShTmpl = new Template()
                .destPath("/local/.command.sh")


        def taskMemMB = task.config.getMemory().toMega().intValue()

        def taskResources = new Resources()
                .CPU(task.config.getCpus())
                .memoryMB(taskMemMB)

        def taskDef = new Task()
                .driver(driver)
                .name(taskId)
                .config(["image"  : task.container,
                         "command": task.config.getShell().first(),
                         "args"   : ["/local/.command.run"]])
                .templates([commandRunTmpl])
                .resources(taskResources)
                .killTimeout(task.config.getTime().toSeconds())

        def taskGroup = new TaskGroup()
                .addTasksItem(taskDef)
                .name(taskId)

        def jobDef = new Job()
                .taskGroups([taskGroup])
                .type(jobType)
                .datacenters([dataCenter])
                .name(taskId)
                .ID(taskId)


        return jobDef

    }

}
