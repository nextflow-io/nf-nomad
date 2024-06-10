package nextflow.nomad.config

import nextflow.nomad.NomadConfig

class VolumeSpec {

    final static public String VOLUME_DOCKER_TYPE = "docker"
    final static public String VOLUME_CSI_TYPE = "csi"
    final static public String VOLUME_HOST_TYPE = "host"

    final static protected String[] VOLUME_TYPES = [
            VOLUME_CSI_TYPE, VOLUME_DOCKER_TYPE, VOLUME_HOST_TYPE
    ]

    private String type
    private String name

    String getType() {
        return type
    }

    String getName() {
        return name
    }

    VolumeSpec type(String type){
        this.type = type
        this
    }

    VolumeSpec name(String name){
        this.name = name
        this
    }

    void validate(){
        if( !VOLUME_TYPES.contains(type) ) {
            throw new IllegalArgumentException("Volume type $type is not supported")
        }
        if( !this.name ){
            throw new IllegalArgumentException("Volume name is required")
        }
    }
}
