package nextflow.nomad.config

class AffinitySpec{

    private String attribute
    private String operator
    private String value
    private Integer weight

    String getOperator(){
        return operator
    }

    String getAttribute() {
        return attribute
    }

    String getValue() {
        return value
    }

    Integer getWeight() {
        return weight
    }

    AffinitySpec attribute(String attribute){
        this.attribute=attribute
        this
    }

    AffinitySpec operator(String operator){
        this.operator = operator
        this
    }

    AffinitySpec value(String value){
        this.value = value
        this
    }

    AffinitySpec weight(int weight){
        this.weight = weight
        this
    }

    void validate(){
    }
}