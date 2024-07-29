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

SECURE=0
[[ "$@" =~ '--secure' ]] && SECURE=1

if [ ! -f ./nomad ]; then
  curl -O "https://releases.hashicorp.com/nomad/${NOMAD_VERSION}/nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip"
  unzip nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip
  rm -f nomad_${NOMAD_VERSION}_${NOMAD_PLATFORM}.zip LICENSE.txt
  chmod +x ./nomad
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

if [ "$SECURE" == 1 ]; then
cat >>server-custom.conf <<EOL
acl {
  enabled = true
}
EOL
fi

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

if [ "$SECURE" == 0 ]; then
  # basic nomad cluter
  ../nomad agent -config server.conf -config client.conf -config server-custom.conf -config client-custom.conf
else
# secured nomad cluster
../nomad agent -config server.conf -config client.conf -config server-custom.conf -config client-custom.conf &
cd ..
#./nomad namespace apply -description "local-nomadlab" nf-nomad
./wait-nomad.sh
sleep 3
NOMAD_TOKEN=$(nomad acl bootstrap | awk '/^Secret ID/ {print $4}')
export NOMAD_TOKEN
echo New super token generated.
echo export NOMAD_TOKEN=$NOMAD_TOKEN
fi