#!/usr/bin/env nextflow
nextflow.enable.dsl=2 

process sayHello {
    container   'ubuntu:20.04'

    input: 
        val x
    output:
        stdout
    script:
        """
        echo '$x world!'
        """
}

workflow {
    Channel.of('Bonjour', 'Ciao', 'Hello', 'Hola') | sayHello | view
}