#!/bin/bash
#export NXF_SYNTAX_PARSER=v1
sudo rm -rf /tmp/nomad/nomad_temp/scratchdir/tests/
NXF_ASSETS=/tmp/nomad/nomad_temp/scratchdir/assets ./nf-test test $@