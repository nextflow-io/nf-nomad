package nextflow.nomad.config

class NomadSecretOpts {

    final Boolean enabled
    final String path

    NomadSecretOpts(Map map){
        this.enabled = map.containsKey('enabled') ? map.get('enabled') as boolean : false
        this.path = map.path ?: "secrets/nf-nomad"
    }

}
