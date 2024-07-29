#!/bin/bash

until curl --output /dev/null --silent --fail http://localhost:4646/v1/status/leader; do
    printf '.'
    sleep 5
done

./nomad namespace apply -description "local-nomadlab" nf-nomad
./nomad node drain -disable $(./nomad node status -quiet)
sleep 3
