package nextflow.nomad.config

class ConstraintSpec {

    private String attribute
    private String operator
    private String value

    String getOperator(){
        return operator
    }

    String getAttribute() {
        return attribute
    }

    String getValue() {
        return value
    }

    ConstraintSpec attribute(String attribute){
        this.attribute=attribute
        this
    }

    ConstraintSpec operator(String operator){
        this.operator = operator
        this
    }

    ConstraintSpec value(String value){
        this.value = value
        this
    }

    void validate(){
    }
}