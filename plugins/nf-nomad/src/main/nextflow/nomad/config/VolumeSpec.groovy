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

package nextflow.nomad.config
/**
 * Nomad Volume Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */
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
    private boolean readOnly = false

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

    boolean getReadOnly(){
        return readOnly
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

    VolumeSpec readOnly(boolean readOnly){
        this.readOnly = readOnly
        this
    }

    String getAccessMode(){
        return switch (this.type){
            case VOLUME_CSI_TYPE->
                readOnly ?
                        "multi-node-reader-only"
                        :
                        "multi-node-multi-writer";
            default -> ""
        }
    }

    String getAttachmentMode(){
        return switch (this.type){
            case VOLUME_CSI_TYPE->"file-system";
            default -> ""
        }
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
        if( this.workDir && this.readOnly ){
            throw new IllegalArgumentException("WorkingDir Volume can't be readOnly")
        }
    }
}
