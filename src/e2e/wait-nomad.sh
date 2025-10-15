#!/bin/bash

TMP_NOMAD=/tmp/nomad
mkdir -p $TMP_NOMAD
cp install-nomad.sh $TMP_NOMAD
cd $TMP_NOMAD
./install-nomad.sh

until curl --output /dev/null --silent --fail http://localhost:4646/v1/status/leader; do
    printf '.'
    sleep 5
done

./nomad node drain -disable $(./nomad node status -quiet)
sleep 3
