#!/bin/bash

until curl --output /dev/null --silent --head --fail http://localhost:4646; do
    printf '.'
    sleep 5
done

./nomad node drain -disable $(./nomad node status -quiet)
sleep 3
