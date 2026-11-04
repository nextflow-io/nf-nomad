/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 * Copyright 2026-, Incremental Steps Software Solutions OÜ
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
package nextflow.nomad.builders


import io.nomadproject.client.model.Task
import nextflow.executor.Executor
import nextflow.executor.ExecutorConfig
import nextflow.nomad.config.NomadJobOpts
import nextflow.nomad.executor.NomadLifecycleTaskSpec
import nextflow.nomad.executor.TaskDirectives
import nextflow.processor.TaskConfig
import nextflow.script.ProcessConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.MemoryUnit
import spock.lang.Specification

/**
 * Unit test for Nomad JobBuilder
 *
 * @author : Abhinav Sharma <abhi18av@outlook.com>
 */


class JobBuilderSpec extends Specification {

    def "test JobBuilder withId method"() {
        given:
        def jobBuilder = new JobBuilder()

        when:
        def jb = jobBuilder
                .withId("test-id")
                .build()

        then:
        jb.ID == "test-id"
    }


    def "test createTask method"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> "docker"
        }
        def taskRun = Mock(TaskRun)
        def args = ["arg1", "arg2"]
        def env = ["key": "value"]

        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getConfig() >> [cpus: 2, memory: "1GB"]

        when:
        def task = JobBuilder.createTask(taskRun, args, env, jobOpts)

        then:
        task.name == "nf-task"
        task.driver == "docker"
        task.config.image == "test-container"
        task.config.command == "arg1"
        task.config.args == ["arg2"]
        task.env == env
        task.resources.cores == 2
        task.resources.memoryMB == 1024
    }


    def "test createTaskGroup method"() {
        given:
        def volumes = [{ type "csi" path "/container/path"}]

        def jobOpts = Mock(NomadJobOpts)

        def taskRun = Mock(TaskRun)
        def args = ["arg1", "arg2"]
        def env = ["key": "value"]

        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getConfig() >> [cpus: 2, memory: "1GB"]

        when:
        def taskGroup = JobBuilder.createTaskGroup(taskRun, args, env, jobOpts)

        then:
        taskGroup.name == "nf-taskgroup"
        taskGroup.tasks.size() == 1
        taskGroup.tasks[0].name == "nf-task"
        taskGroup.tasks[0].config.image == "test-container"
        taskGroup.tasks[0].config.command == "arg1"
        taskGroup.tasks[0].config.args == ["arg2"]
        taskGroup.tasks[0].env == env
    }

    def "test createTaskGroup with lifecycle sidecar tasks"() {
        given:
        def jobOpts = Mock(NomadJobOpts)
        def taskRun = Mock(TaskRun)
        def args = ["bash", "-lc", "echo main"]
        def env = ["MAIN": "1"]
        def lifecycleTasks = [
                new NomadLifecycleTaskSpec(
                        name: 'lifecycle-prestart',
                        hook: 'prestart',
                        sidecar: false,
                        driver: 'raw_exec',
                        user: 'nfx',
                        command: ['bash', '-lc', 'echo pre'],
                        env: [NXF_RCLONE_REMOTE_WORKDIR: 'minio:work/hash/']
                ),
                new NomadLifecycleTaskSpec(
                        name: 'lifecycle-poststop',
                        hook: 'poststop',
                        sidecar: false,
                        driver: 'raw_exec',
                        user: 'nfx',
                        command: ['bash', '-lc', 'echo post'],
                        env: [NXF_RCLONE_REMOTE_WORKDIR: 'minio:work/hash/']
                )
        ]

        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getConfig() >> [cpus: 2, memory: "1GB"]

        when:
        def taskGroup = JobBuilder.createTaskGroup(taskRun, args, env, jobOpts, lifecycleTasks)

        then:
        taskGroup.tasks.size() == 3
        taskGroup.tasks[0].name == 'nf-task'
        taskGroup.tasks[1].name == 'lifecycle-prestart'
        taskGroup.tasks[1].driver == 'raw_exec'
        taskGroup.tasks[1].user == 'nfx'
        taskGroup.tasks[1].lifecycle.hook == 'prestart'
        taskGroup.tasks[1].lifecycle.sidecar == false
        taskGroup.tasks[2].name == 'lifecycle-poststop'
        taskGroup.tasks[2].user == 'nfx'
        taskGroup.tasks[2].lifecycle.hook == 'poststop'
        taskGroup.tasks[2].lifecycle.sidecar == false
    }

    def "test createLifecycleTask applies config overrides"() {
        given:
        def spec = new NomadLifecycleTaskSpec(
                name: 'lifecycle-prestart',
                hook: 'prestart',
                driver: 'docker',
                command: ['bash', '-lc', 'echo pre'],
                config: [image: 'busybox:latest']
        )

        when:
        def task = JobBuilder.createLifecycleTask(spec)

        then:
        task.driver == 'docker'
        task.config.command == 'bash'
        task.config.args == ['-lc', 'echo pre']
        task.config.image == 'busybox:latest'
    }

    def "test createLifecycleTask embeds transferManifest in task meta"() {
        given:
        def manifestJson = '{"phase":"prestart","taskHash":"ab/hash123","transfers":[]}'
        def spec = new NomadLifecycleTaskSpec(
                name: 'lifecycle-prestart',
                hook: 'prestart',
                command: ['bash', '-lc', 'echo pre'],
                transferManifest: manifestJson,
                meta: ['nf.phase': 'prestart', 'nf.taskHash': 'ab/hash123']
        )

        when:
        def task = JobBuilder.createLifecycleTask(spec)

        then:
        task.meta != null
        task.meta['nf.transferManifest'] == manifestJson
        task.meta['nf.phase'] == 'prestart'
        task.meta['nf.taskHash'] == 'ab/hash123'
    }

    def "test createLifecycleTask without manifest has no meta"() {
        given:
        def spec = new NomadLifecycleTaskSpec(
                name: 'lifecycle-prestart',
                hook: 'prestart',
                command: ['bash', '-lc', 'echo pre']
        )

        when:
        def task = JobBuilder.createLifecycleTask(spec)

        then:
        // meta should be null or empty when no manifest or meta provided
        task.meta == null || task.meta.isEmpty()
    }


    def "test createTask with pbs driver produces hpc config"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> "pbs"
        }
        def mockTaskConfig = new TaskConfig([
                queue: 'gpu',
                time: '4h',
                cpus: 8,
                memory: '16 GB',
                clusterOptions: '-l ngpus=2',
        ])
        def mockExecConfig = Mock(ExecutorConfig) {
            getExecConfigProp('nomad', 'account', null) >> "myaccount"
        }
        def mockExecutor = Mock(Executor) {
            getConfig() >> mockExecConfig
        }
        def mockProcessor = Mock(TaskProcessor) {
            getExecutor() >> mockExecutor
        }
        def taskRun = Mock(TaskRun) {
            workDir >> new File("/scratch/work/ab/cd1234").toPath()
            getConfig() >> mockTaskConfig
            processor >> mockProcessor
        }
        def args = ["bash", ".command.run"]
        def env = ["NF_TASK_NAME": "hello"]

        when:
        def task = JobBuilder.createTask(taskRun, args, env, jobOpts)

        then:
        task.name == "nf-task"
        task.driver == "pbs"
        task.config.command == "bash"
        task.config.args == [".command.run"]
        task.config.work_dir == "/scratch/work/ab/cd1234"
        task.config.stdout_file == "/scratch/work/ab/cd1234/.command.log"
        task.config.stderr_file == "/scratch/work/ab/cd1234/.command.log"
        task.config.queue == "gpu"
        task.config.walltime == "04:00:00"
        task.config.cpus_per_task == 8
        task.config.memory == 16384
        task.config.account == "myaccount"
        task.config.extra_args == ["-l", "ngpus=2"]
        // Docker-specific fields should NOT be present
        task.config.image == null
        task.config.privileged == null
        task.env == env
    }

    def "test resolveDriver uses per-process nomadOptions driver over global"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> "docker"
        }
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key ->
            if (key == TaskDirectives.NOMAD_OPTIONS) return [driver: 'pbs']
            return null
        }
        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig
        def task = Mock(TaskRun) {
            getProcessor() >> processor
        }

        expect:
        JobBuilder.resolveDriver(task, jobOpts) == 'pbs'
    }

    def "test networkMode config"() {
        given:
        def jobOpts = Mock(NomadJobOpts){
            getNetworkMode() >> 'test'
        }
        def taskRun = Mock(TaskRun)
        def args = ["arg1", "arg2"]
        def env = ["key": "value"]

        taskRun.container >> "test-container"
        taskRun.workDir >> new File("/test/workdir").toPath()
        taskRun.getConfig() >> [cpus: 2, memory: "1GB"]

        when:
        def task = JobBuilder.createTask(taskRun, args, env, jobOpts)

        then:
        task.config.network_mode == "test"
    }
    def "test resolveDriver falls back to global jobOpts driver"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> "slurm"
        }
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> null
        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig
        def task = Mock(TaskRun) {
            getProcessor() >> processor
        }

        expect:
        JobBuilder.resolveDriver(task, jobOpts) == 'slurm'
    }

    def "test resolveDriver defaults to docker when no driver specified"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> null
        }
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> null
        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig
        def task = Mock(TaskRun) {
            getProcessor() >> processor
        }

        expect:
        JobBuilder.resolveDriver(task, jobOpts) == 'docker'
    }

    def "test createTask respects per-process driver override to pbs"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> "docker"
        }
        // Process config with nomadOptions.driver = 'pbs'
        def processConfig = Mock(ProcessConfig)
        processConfig.get(_ as String) >> { String key ->
            if (key == TaskDirectives.NOMAD_OPTIONS) return [driver: 'pbs']
            return null
        }
        def processor = Mock(TaskProcessor)
        processor.getConfig() >> processConfig
        def executor = Mock(Executor) {
            getConfig() >> Mock(ExecutorConfig)
        }
        processor.getExecutor() >> executor
        def mockTaskConfig = new TaskConfig([
                queue: 'gpu',
                time: '2h',
                cpus: 4,
                memory: '8 GB',
        ])
        def mockWorkDir = new File("/scratch/work/ab/cd1234").toPath()
        def task = Mock(TaskRun) {
            getProcessor() >> processor
            getName() >> "hpcProcess"
            getConfig() >> mockTaskConfig
            getWorkDir() >> mockWorkDir
            getContainer() >> null
        }
        def args = ["bash", ".command.run"]
        def env = [NXF_TASK_WORKDIR: "/scratch/work/ab/cd1234"]

        when:
        Task nomadTask = JobBuilder.createTask(task, args, env, jobOpts)

        then:
        nomadTask.driver == "pbs"
        nomadTask.config.work_dir == "/scratch/work/ab/cd1234"
        nomadTask.config.queue == "gpu"
        // Should NOT have docker-specific fields
        nomadTask.config.image == null
        nomadTask.config.privileged == null
    }

    def "test mixed-driver: same pipeline produces docker and hpc configs"() {
        given: 'global jobOpts with docker as default'
        def dockerJobOpts = Mock(NomadJobOpts) {
            driver >> "docker"
        }

        and: 'a cloud task using docker (container image set)'
        def cloudTaskRun = Mock(TaskRun) {
            container >> "ubuntu:22.04"
            workDir >> new File("/data/work/ab/cloud1").toPath()
            getConfig() >> [cpus: 2, memory: "4 GB"]
        }

        and: 'an HPC task overriding to pbs via per-process nomadOptions'
        def hpcJobOpts = Mock(NomadJobOpts) {
            driver >> "pbs"
        }
        def hpcTaskConfig = new TaskConfig([
                queue: 'batch',
                time: '8h',
                cpus: 16,
                memory: '64 GB',
        ])
        def hpcTaskRun = Mock(TaskRun) {
            workDir >> new File("/scratch/work/ab/hpc1").toPath()
            getConfig() >> hpcTaskConfig
            processor >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    getConfig() >> Mock(ExecutorConfig)
                }
            }
        }

        when: 'creating tasks for both'
        def cloudTask = JobBuilder.createTask(cloudTaskRun, ["bash", "/data/work/ab/cloud1/.command.run"], ["ENV": "1"], dockerJobOpts)
        def hpcTask = JobBuilder.createTask(hpcTaskRun, ["bash", ".command.run"], ["ENV": "1"], hpcJobOpts)

        then: 'cloud task uses docker driver with image'
        cloudTask.driver == "docker"
        cloudTask.config.image == "ubuntu:22.04"
        cloudTask.config.privileged == true
        cloudTask.config.work_dir == "/data/work/ab/cloud1"

        and: 'hpc task uses pbs driver with HPC config'
        hpcTask.driver == "pbs"
        hpcTask.config.work_dir == "/scratch/work/ab/hpc1"
        hpcTask.config.queue == "batch"
        hpcTask.config.walltime == "08:00:00"
        hpcTask.config.cpus_per_task == 16
        hpcTask.config.memory == 65536
        hpcTask.config.image == null
        hpcTask.config.privileged == null

        and: 'both have standard fields'
        cloudTask.config.command == "bash"
        hpcTask.config.command == "bash"
    }

    def "test mixed-driver: slurm task produces correct HPC config"() {
        given:
        def jobOpts = Mock(NomadJobOpts) {
            driver >> "slurm"
        }
        def taskConfig = new TaskConfig([
                queue: 'gpu-partition',
                time: '12h',
                cpus: 8,
                memory: '32 GB',
                clusterOptions: '--gres=gpu:2 --exclusive',
        ])
        def taskRun = Mock(TaskRun) {
            workDir >> new File("/scratch/work/cd/ef5678").toPath()
            getConfig() >> taskConfig
            processor >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    getConfig() >> Mock(ExecutorConfig) {
                        getExecConfigProp('nomad', 'account', null) >> "hpc-account"
                    }
                }
            }
        }

        when:
        def task = JobBuilder.createTask(taskRun, ["bash", ".command.run"], [:], jobOpts)

        then:
        task.driver == "slurm"
        task.config.queue == "gpu-partition"
        task.config.walltime == "12:00:00"
        task.config.cpus_per_task == 8
        task.config.memory == 32768
        task.config.account == "hpc-account"
        task.config.extra_args == ["--gres=gpu:2", "--exclusive"]
        task.config.image == null
    }
}
