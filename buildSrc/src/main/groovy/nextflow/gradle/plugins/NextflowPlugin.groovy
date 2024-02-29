package nextflow.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

class NextflowPlugin implements Plugin<Project>{

    @Override
    void apply(Project target) {

        NextflowPluginExtension nextflowPluginExtension = target.extensions.create('nextflowPlugin',NextflowPluginExtension)

        target.subprojects.find{it.name=="plugins"}.subprojects.each{ project ->
            project.tasks.register('zipPlugin', ZipPluginTask,{

            })
            project.tasks.register('unzipPlugin', UnzipPluginTask,{

            })
            project.tasks.register('jsonPlugin', JsonPluginTask, {
                downloadUrl = nextflowPluginExtension.downloadUrl
            })
            project.tasks.register('generateIdx', GenerateIdxTask, {
                extensionPoints = nextflowPluginExtension.extensionPoints
            })
            project.tasks.findByName("processResources")?.dependsOn(project.tasks.findByName("generateIdx"))
        }
    }
}
