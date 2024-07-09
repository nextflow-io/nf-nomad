package nextflow.nomad.config

class ConstraintNodeSpec {

    private String id = null
    private String name = null
    private String clientClass = null
    private String pool = null
    private String dataCenter = null
    private String region = null

    String getId() {
        return id
    }

    String getName() {
        return name
    }

    String getClientClass() {
        return clientClass
    }

    String getPool() {
        return pool
    }

    String getDataCenter() {
        return dataCenter
    }

    String getRegion() {
        return region
    }

    ConstraintNodeSpec setUnique(Map map){
        unique(map)
    }

    ConstraintNodeSpec unique(Map map){
        this.id = map.containsKey("id") ? map["id"].toString() : null
        this.name = map.containsKey("name") ? map["name"].toString() : null
        this
    }

    ConstraintNodeSpec setClientClass(Object map){
        clientClass(map)
    }

    ConstraintNodeSpec clientClass(Object clientClass){
        this.clientClass = clientClass.toString()
        this
    }

    ConstraintNodeSpec setPool(Object map){
        pool(map)
    }

    ConstraintNodeSpec pool(Object pool){
        this.pool = pool.toString()
        this
    }

    ConstraintNodeSpec setDataCenter(Object map){
        dataCenter(map)
    }

    ConstraintNodeSpec dataCenter(Object dataCenter){
        this.dataCenter = dataCenter.toString()
        this
    }

    ConstraintNodeSpec setRegion(Object map){
        region(map)
    }

    ConstraintNodeSpec region(Object region){
        this.region = region.toString()
        this
    }
}
