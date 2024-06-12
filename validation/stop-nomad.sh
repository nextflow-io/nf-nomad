#!/bin/bash
./nomad node drain -enable $(./nomad node status -quiet)
./nomad system gc
sleep 1
df -h --output=target | grep nf-task | xargs sudo umount
pkill -9 nomad