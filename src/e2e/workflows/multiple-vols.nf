#!/usr/bin/env nextflow

process sayHello {
    container   'ubuntu:20.04'

    input:
    val x
    output:
    stdout
    script:
    """
    echo '$x world!' > /var/data1/hello
    cp /var/data1/hello /var/data2/hello2
    echo '$x'
    """
}

workflow MAIN {
    main:
    Channel.of('Bonjour', 'Ciao', 'Hello', 'Hola') | sayHello

    emit:
    sayHello.out
}
