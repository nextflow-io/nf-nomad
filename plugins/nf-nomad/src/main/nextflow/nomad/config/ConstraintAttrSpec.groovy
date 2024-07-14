package nextflow.nomad.config

class ConstraintAttrSpec {

    private String arch = null
    private Integer numcores= null
    private Integer reservablecores= null
    private Double totalcompute= null

    String getArch() {
        return arch
    }

    Integer getNumcores() {
        return numcores
    }

    Integer getReservablecores() {
        return reservablecores
    }

    Double getTotalcompute() {
        return totalcompute
    }

    ConstraintAttrSpec setCpu(Map map){
        cpu(map)
    }

    ConstraintAttrSpec cpu(Map map){
        this.arch = map.containsKey("arch") ? map["arch"].toString() : null
        this.numcores = map.containsKey("numcores") ? map["numcores"] as int : null
        this.reservablecores = map.containsKey("reservablecores") ? map["reservablecores"] as int : null
        this.totalcompute = map.containsKey("totalcompute") ? map["totalcompute"] as double : null
        this
    }

}
