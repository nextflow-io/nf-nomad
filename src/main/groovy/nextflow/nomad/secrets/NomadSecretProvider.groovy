package nextflow.nomad.secrets

import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.executor.NomadService
import nextflow.plugin.Priority
import nextflow.secret.LocalSecretsProvider
import nextflow.secret.Secret
import nextflow.secret.SecretImpl
import nextflow.secret.SecretsProvider

/**
 * Custom Nextflow CLI commands for nf-nomad
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */
@Slf4j
@Priority(-100) // high priority
class NomadSecretProvider extends LocalSecretsProvider implements SecretsProvider {

    NomadConfig config

    @Override
    LocalSecretsProvider load() {
        return super.load()
    }

    @Override
    protected List<Secret> loadSecrets() {
        Set<String> names = listSecretsNames()
        List<Secret> ret = names.collect{ name->
            String value = getSecret(name)
            new SecretImpl(name, value)
        }
        ret
    }

    protected boolean isEnabled(){
        if( !config ){
            config = new NomadConfig(Global.config?.nomad as Map ?: Map.of())
        }
        config?.jobOpts()?.secretOpts?.enabled
    }

    @Override
    Secret getSecret(String name) {
        if( !this.enabled ) {
            return super.getSecret(name)
        }
        NomadService service = new NomadService(config)
        String value = service.getVariableValue(name)
        return new SecretImpl(name: name, value: value)
    }

    @Override
    String getSecretsEnv(List<String> secretNames) {
        if( !this.enabled ) {
            return super.getSecretsEnv(secretNames)
        }
        null
    }

    @Override
    String getSecretsEnv() {
        if( !this.enabled ) {
            return super.getSecretsEnv()
        }
        null
    }

    @Override
    void putSecret(String name, String value) {
        if( !this.enabled ) {
            super.putSecret(name, value)
        }
    }

    @Override
    void removeSecret(String name) {
        if( !this.enabled ) {
            super.removeSecret(name)
        }
    }

    @Override
    Set<String> listSecretsNames() {
        if( !this.enabled ) {
            return super.listSecretsNames()
        }
        NomadService service = new NomadService(config)
        service.variablesList as Set<String>
    }

}
