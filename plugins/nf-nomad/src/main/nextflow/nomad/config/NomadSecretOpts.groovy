package nextflow.nomad.config

class NomadSecretOpts {

    final Boolean enable
    final String path

    NomadSecretOpts(Map map){
        this.enable = map.containsKey('enable') ? map.get('enable') as boolean : false
        this.path = map.path ?: "secrets/nf-nomad"
    }

}
