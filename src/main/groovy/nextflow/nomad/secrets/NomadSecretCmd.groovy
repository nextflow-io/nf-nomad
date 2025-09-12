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
        return switch (action){
            case 'get' ->execGetSecretNames(args.removeAt(0).toString())
            case 'set' ->execSetSecretNames(args.removeAt(0).toString(),args.removeAt(0).toString())
            case 'list'->execListSecretsNames()
            case 'delete'->execDeleteSecretNames(args.removeAt(0).toString())
            default -> -1
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
