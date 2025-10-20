#!/bin/bash
set -ue

NOMAD_VERSION="1.8.1"
NOMAD_PLATFORM=${NOMAD_PLATFORM:-linux_amd64}

## Available platforms
#- "linux_amd64"
#- "linux_arm64"
#- "darwin_amd64"
#- "darwin_arm64"
#- "windows_amd64"


if [ ! -f ./nomad ]; then
  curl -O "https://releases.hashicorp.com/nomad/${NOMAD_VERSION}/nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip"
  unzip nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip
  rm -f nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip LICENSE.txt
  chmod +x ./nomad
fi
