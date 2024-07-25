package nextflow.nomad.secrets

import groovy.util.logging.Slf4j
import nextflow.plugin.Priority
import nextflow.secret.Secret
import nextflow.secret.SecretsProvider

@Slf4j
@Priority(-100) // high priority
class NomadSecretProvider implements SecretsProvider, Closeable{

    @Override
    void close() throws IOException {
    }

    @Override
    boolean activable() {
        return true
    }

    @Override
    SecretsProvider load() {
        this
    }

    @Override
    Secret getSecret(String name) {
        log.debug("NomadSecretProvider can't get secret, use nomad cli or nextflow plugin nf-nomad:secrets")
        null
    }

    @Override
    String getSecretsEnv(List<String> secretNames) {
        log.debug("NomadSecretProvider can't get secret, use nomad cli or nextflow plugin nf-nomad:secrets")
        null
    }

    @Override
    String getSecretsEnv() {
        log.debug("NomadSecretProvider can't get secret, use nomad cli or nextflow plugin nf-nomad:secrets")
        null
    }

    @Override
    void putSecret(String name, String value) {
        throw new UnsupportedOperationException("NomadSecretProvider can't put secret, use nomad cli or nextflow plugin nf-nomad:secrets")
    }

    @Override
    void removeSecret(String name) {
        throw new UnsupportedOperationException("NomadSecretProvider can't remove secret, use nomad cli or nextflow plugin nf-nomad:secrets")
    }

    @Override
    Set<String> listSecretsNames() {
        log.debug("NomadSecretProvider can't get secret, use nomad cli or nextflow plugin nf-nomad:secrets")
        null
    }

}
