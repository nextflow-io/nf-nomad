package nextflow.gradle.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


abstract class GenerateIdxTask extends DefaultTask{

    @Internal
    abstract ListProperty<String> extensionPoints

    @OutputFile
    final abstract RegularFileProperty outputFile =
            project.objects.fileProperty().convention(project.layout.buildDirectory.file(
                    "resources/main/META-INF/extensions.idx"))

    GenerateIdxTask() {
        setGroup('nextflow')
        dependsOn(project.tasks.findByName('build'))
    }

    @TaskAction
    def runTask() {
        def output = outputFile.get().asFile

        if( extensionPoints.getOrElse([]).size() ){
            output.text = extensionPoints.getOrElse([]).join('\n')
            return
        }

        def matcher = new SourcesMatcher(project)
        def extensionsClassName = matcher.pluginExtensions
        extensionsClassName += matcher.providers
        def traceClassName = matcher.traceObservers
        def all = extensionsClassName+traceClassName
        output.text = all.join('\n')
    }

}
