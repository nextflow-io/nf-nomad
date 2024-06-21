package nextflow.nomad.config

class VolumeSpec {

    final static public String VOLUME_DOCKER_TYPE = "docker"
    final static public String VOLUME_CSI_TYPE = "csi"
    final static public String VOLUME_HOST_TYPE = "host"

    final static protected String[] VOLUME_TYPES = [
            VOLUME_CSI_TYPE, VOLUME_DOCKER_TYPE, VOLUME_HOST_TYPE
    ]

    private String type
    private String name
    private String path
    private boolean workDir = false

    String getType() {
        return type
    }

    String getName() {
        return name
    }

    boolean getWorkDir() {
        return workDir
    }

    String getPath() {
        return path
    }

    VolumeSpec type(String type){
        this.type = type
        this
    }

    VolumeSpec name(String name){
        this.name = name
        this
    }

    VolumeSpec workDir(boolean b){
        this.workDir = b
        this
    }

    VolumeSpec path(String path){
        this.path = path
        this
    }

    void validate(){
        if( !VOLUME_TYPES.contains(type) ) {
            throw new IllegalArgumentException("Volume type $type is not supported")
        }
        if( !this.name ){
            throw new IllegalArgumentException("Volume name is required (type $type)")
        }
        if( !this.workDir && !this.path ){
            throw new IllegalArgumentException("Volume path is required in secondary volumes")
        }
    }
}
