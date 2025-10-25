#!/bin/bash

./wait-nomad.sh

./nomad system gc

NXF_ASSETS=$(pwd)/nomad_temp/scratchdir/assets \
  NXF_CACHE_DIR=$(pwd)/nomad_temp/scratchdir/cache \
    nextflow ${REMOTE_DEBUG} run -w $(pwd)/nomad_temp/scratchdir/ "$@"
