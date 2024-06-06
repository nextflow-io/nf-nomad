#!/bin/bash

./wait-nomad.sh

NXF_ASSETS=$(pwd)/nomad_temp/scratchdir/assets \
  NXF_CACHE_DIR=$(pwd)/nomad_temp/scratchdir/cache \
    nextflow run -w $(pwd)/nomad_temp/scratchdir/ "$@"
