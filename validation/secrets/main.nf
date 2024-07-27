#!/usr/bin/env nextflow

process sayHello {
    container   'ubuntu:20.04'
    secret 'MY_ACCESS_KEY'
    secret 'MY_SECRET_KEY'

    input:
    val x
    output:
    stdout

    """
    echo $x world! the access \$MY_ACCESS_KEY and the secret \$MY_SECRET_KEY
    """
}

workflow {
    Channel.of('Bonjour', 'Ciao', 'Hello', 'Hola') | sayHello | view
}
workflow.onComplete {
    println("The secret is: ${secrets.MY_ACCESS_KEY}")
}