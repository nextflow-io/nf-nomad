#!/usr/bin/env nextflow

process sayHello {
    container   'ubuntu:20.04'

    input:
    val x
    output:
    stdout
    script:
    """
    sleep 10
    echo '$x world!'
    """
}

workflow {
    Channel.of('Bonjour', 'Ciao', 'Hello', 'Hola') | sayHello | view
}