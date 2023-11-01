package nextflow.nomad.executor


import nextflow.executor.BashWrapperBuilder
import nextflow.executor.SimpleFileCopyStrategy
import nextflow.processor.TaskBean

/**
 * Custom bash wrapper builder for Nomad jobs/tasks
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadScriptLauncher extends BashWrapperBuilder {

    NomadScriptLauncher(TaskBean taskBean) {
        super(taskBean, new SimpleFileCopyStrategy(taskBean))
    }

}
