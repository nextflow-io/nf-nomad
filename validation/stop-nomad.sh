#!/bin/bash
./nomad node drain -enable $(./nomad node status -quiet)
./nomad system gc
sleep 1
df -h --output=target | grep nf-task | xargs sudo umount
kill $(ps aux | grep '../nomad agent' | awk '{print $2}')
sleep 1
rm -rf nomad_temp
