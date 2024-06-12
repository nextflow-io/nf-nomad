#!/bin/bash

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