#!/usr/bin/env nextflow

process LoremIpsumDolorSitAmetConsecteturAdipiscingElitLoremIpsumDolorSitAmetConsecteturAdipiscingElit {
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

workflow MAIN {
    main:
    Channel.of('Bonjour', 'Ciao', 'Hello', 'Hola') | LoremIpsumDolorSitAmetConsecteturAdipiscingElitLoremIpsumDolorSitAmetConsecteturAdipiscingElit

    emit:
    LoremIpsumDolorSitAmetConsecteturAdipiscingElitLoremIpsumDolorSitAmetConsecteturAdipiscingElit.out
}
