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

package nextflow.nomad

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cli.PluginAbstractExec
import nextflow.nomad.secrets.NomadSecretCmd
import nextflow.nomad.executor.TaskDirectives
import nextflow.plugin.BasePlugin
import nextflow.script.ProcessConfig
import nextflow.secret.SecretsLoader
import org.pf4j.PluginWrapper

/**
 * Nextflow plugin for Nomad executor
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 * @author Jorge Aguilera <jagedn@gmail.com>
 */
@CompileStatic
@Slf4j
class NomadPlugin extends BasePlugin implements PluginAbstractExec{

    NomadPlugin(PluginWrapper wrapper) {
        super(wrapper)
        addCustomDirectives()
        SecretsLoader.instance.reset()
    }

    private static void addCustomDirectives() {
        ProcessConfig.DIRECTIVES.addAll(TaskDirectives.ALL)
    }

    @Override
    List<String> getCommands() {
        return ['secrets']
    }

    @Override
    int exec(String cmd, List<String> args) {
        return switch (cmd){
            case 'secrets'-> secrets(args.first(), args.drop(1))
            default -> -1
        }
    }

    int secrets(String action, List<String>args){
        NomadSecretCmd nomadSecretCmd = new NomadSecretCmd()
        nomadSecretCmd.runCommand( session.config , action, args)
    }
}
