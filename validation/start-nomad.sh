#!/bin/bash
set -uex

export NOMAD_VERSION="1.8.1"
export NOMAD_PLATFORM="linux_amd64"

if [ ! -f ./nomad ]; then
  curl -O "https://releases.hashicorp.com/nomad/${NOMAD_VERSION}/nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip"
  unzip nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip
  rm -f nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip
fi

mkdir -p nomad_temp
cd nomad_temp

CURRENT_DIR=$(pwd)

mkdir -p client -p server -p scratchdir
chmod ugoa+rw -R scratchdir

rm -f server-custom.conf
cat >server-custom.conf <<EOL
data_dir  = "${CURRENT_DIR}/server"
EOL

rm -f client-custom.conf
cat >client-custom.conf <<EOL
data_dir  = "${CURRENT_DIR}/client"

client{
    host_volume "scratchdir" {
      path      = "${CURRENT_DIR}/scratchdir"
      read_only = false
    }
}
EOL

cp ../server.conf .
cp ../client.conf .
../nomad agent -config server.conf -config client.conf -config server-custom.conf -config client-custom.conf