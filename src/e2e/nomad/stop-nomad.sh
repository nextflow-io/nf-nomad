#!/bin/bash

TMP_NOMAD=/tmp/nomad
mkdir -p $TMP_NOMAD
cp install-nomad.sh $TMP_NOMAD
cd $TMP_NOMAD
./install-nomad.sh

./nomad node drain -enable $(./nomad node status -quiet)
./nomad system gc
kill $(ps aux | grep '../nomad agent' | awk '{print $2}')
sleep 1
df -h --output=target | grep nf-task | xargs sudo umount
sleep 1
cd $TMP_NOMAD
rm -rf *
