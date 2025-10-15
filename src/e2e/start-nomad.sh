#!/bin/bash
set -ue

TMP_NOMAD=/tmp/nomad
mkdir -p $TMP_NOMAD
cp install-nomad.sh $TMP_NOMAD
cp stop-nomad.sh $TMP_NOMAD
cp server.conf $TMP_NOMAD
cp client.conf $TMP_NOMAD
cp wait-nomad.sh $TMP_NOMAD
cd $TMP_NOMAD
./install-nomad.sh

mkdir -p nomad_temp
cd nomad_temp

CURRENT_DIR=$(pwd)

mkdir -p client -p server -p scratchdir
chmod ugoa+rw -R scratchdir

rm -f server-custom.conf
cat >server-custom.conf <<EOL
data_dir  = "${CURRENT_DIR}/server"
EOL

cat >>server-custom.conf <<EOL
acl {
  enabled = true
}
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

# secured nomad cluster
../nomad agent -config server.conf -config client.conf -config server-custom.conf -config client-custom.conf &
cd ..
./wait-nomad.sh

NOMAD_TOKEN=$(./nomad acl bootstrap | awk '/^Secret ID/ {print $4}')
export NOMAD_TOKEN
echo New super token generated.
echo export NOMAD_TOKEN=$NOMAD_TOKEN

./nomad namespace apply -description "local-nomadlab" nf-nomad
./nomad var put -namespace=nf-nomad secrets/nf-nomad/MY_ACCESS_KEY MY_ACCESS_KEY=TheAccessKey
./nomad var put -namespace=nf-nomad secrets/nf-nomad/MY_SECRET_KEY MY_SECRET_KEY=TheSecretKey
