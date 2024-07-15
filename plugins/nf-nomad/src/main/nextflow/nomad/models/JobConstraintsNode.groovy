package nextflow.nomad.models

class JobConstraintsNode {

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

    JobConstraintsNode setUnique(Map map){
        unique(map)
    }

    JobConstraintsNode unique(Map map){
        this.id = map.containsKey("id") ? map["id"].toString() : null
        this.name = map.containsKey("name") ? map["name"].toString() : null
        this
    }

    JobConstraintsNode setClientClass(Object map){
        clientClass(map)
    }

    JobConstraintsNode clientClass(Object clientClass){
        this.clientClass = clientClass.toString()
        this
    }

    JobConstraintsNode setPool(Object map){
        pool(map)
    }

    JobConstraintsNode pool(Object pool){
        this.pool = pool.toString()
        this
    }

    JobConstraintsNode setDataCenter(Object map){
        dataCenter(map)
    }

    JobConstraintsNode dataCenter(Object dataCenter){
        this.dataCenter = dataCenter.toString()
        this
    }

    JobConstraintsNode setRegion(Object map){
        region(map)
    }

    JobConstraintsNode region(Object region){
        this.region = region.toString()
        this
    }
}
