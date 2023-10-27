//package nextflow.nomad.model
//
//import groovy.transform.CompileStatic
//import groovy.transform.EqualsAndHashCode
//import groovy.transform.ToString
//
//@CompileStatic
//@ToString(includeNames = true)
//@EqualsAndHashCode(includeFields = true)
//class NomadJobConstraints {
//
//    private Map spec = [:]
//
//    PodNodeSelector(selector) {
//        if( selector instanceof CharSequence )
//            createWithString(selector.toString())
//
//        else if( selector instanceof Map )
//            createWithMap(selector)
//
//        else if( selector != null )
//            throw new IllegalArgumentException("K8s invalid pod nodeSelector value: $selector [${selector.getClass().getName()}]")
//    }
//
//    private createWithMap(Map selection ) {
//        if(selection) {
//            for( Map.Entry entry : selection.entrySet() ) {
//                spec.put(entry.key.toString(), entry.value?.toString())
//            }
//        }
//    }
//
//    /**
//     * @param selector
//     *      A string representing a comma separated list of pairs
//     *      e.g. foo=1,bar=2
//     *
//     */
//    private createWithString( String selector ) {
//        if(!selector) return
//        def entries = selector.tokenize(',')
//        for( String item : entries ) {
//            def pair = item.tokenize('=')
//            spec.put( trim(pair[0]), trim(pair[1]) ?: 'true' )
//        }
//    }
//
//    private String trim(String v) {
//        v?.trim()
//    }
//
//    Map<String,String> toSpec() { spec }
//
//    String toString() {
//        "PodNodeSelector[ ${spec?.toString()} ]"
//    }
//}
//
//
//class NomadJobMeta {
//}
