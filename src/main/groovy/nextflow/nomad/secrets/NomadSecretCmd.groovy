package nextflow.nomad.secrets

import groovy.util.logging.Slf4j
import nextflow.exception.AbortOperationException
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.executor.NomadService

@Slf4j
class NomadSecretCmd {

    protected NomadService service
    protected NomadConfig nomadConfig

    int runCommand(Map config, String action, List<String> args){
        nomadConfig = new NomadConfig((config.nomad ?: Collections.emptyMap()) as Map)
        service = new NomadService(nomadConfig)
        def actionArgs = args ?: []
        return switch (action){
            case 'get' -> {
                requireArgs(action, actionArgs, 1)
                execGetSecretNames(actionArgs.removeAt(0).toString())
            }
            case 'set' -> {
                requireArgs(action, actionArgs, 2)
                execSetSecretNames(actionArgs.removeAt(0).toString(), actionArgs.removeAt(0).toString())
            }
            case 'list'->execListSecretsNames()
            case 'delete'-> {
                requireArgs(action, actionArgs, 1)
                execDeleteSecretNames(actionArgs.removeAt(0).toString())
            }
            default -> {
                throw new AbortOperationException("Unknown secrets action: ${action}")
            }
        }
    }

    protected void requireArgs(String action, List<String> args, int expected) {
        if( !args || args.size() < expected ) {
            throw new AbortOperationException("Wrong number of arguments for `${action}`")
        }
    }

    int execListSecretsNames(){
        def list = listSecretsNames()
        println list.join('\n')
        return 0
    }

    int execGetSecretNames(String name){
        if(!name){
            throw new AbortOperationException("Wrong number of arguments")
        }
        def secret = getSecret(name)
        println secret
        return 0
    }

    int execSetSecretNames(String name, String value){
        if(!name){
            throw new AbortOperationException("Wrong number of arguments")
        }
        setSecret(name, value)
        return 0
    }

    int execDeleteSecretNames(String name){
        if(!name){
            throw new AbortOperationException("Wrong number of arguments")
        }
        deleteSecret(name)
        return 0
    }

    String getSecret(String name) {
        String value = service.getVariableValue(name)
        if( !value )
            throw new AbortOperationException("Missing secret name")
        value
    }

    Set<String> listSecretsNames() {
        service.variablesList
    }

    void setSecret(String name, String value) {
        service.setVariableValue(name, value)
    }

    void deleteSecret(String name){
        service.deleteVariable(name)
    }
}
