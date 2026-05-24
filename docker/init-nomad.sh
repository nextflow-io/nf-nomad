#!/bin/bash

sleep 30

OUT_DIR=/data
CLIENT_POLICY=/client-policy.hcl
export NOMAD_ADDR=http://127.0.0.1:4646

for i in {1..30}; do
  if nomad status &>/dev/null; then
    echo "Nomad is ready"
    break
  fi
  echo "Waiting for Nomad to be ready... ($i/30)"
  sleep 2
done

if [ ! -f $OUT_DIR/bootstrap-token.json ]; then
  echo "Bootstrapping ACL system..."
  nomad acl bootstrap -json > $OUT_DIR/bootstrap-token.json 2>&1
  if [ $? -eq 0 ]; then
    chmod 600 $OUT_DIR/bootstrap-token.json
    echo "Nomad ACL bootstrap completed successfully"
  else
    echo "Nomad ACL bootstrap failed or already done"
  fi
fi

if [ -f $OUT_DIR/bootstrap-token.json ]; then
  MANAGEMENT_TOKEN=$(jq -r '.SecretID' $OUT_DIR/bootstrap-token.json)
  export NOMAD_TOKEN=$MANAGEMENT_TOKEN
  echo "NOMAD_TOKEN=$MANAGEMENT_TOKEN" > $OUT_DIR/nomad-token.env
  echo "NOMAD_ADDR=$NOMAD_ADDR" >> $OUT_DIR/nomad-token.env
  chmod 600 $OUT_DIR/nomad-token.env
else
  echo "Error: Bootstrap token file not found. Cannot proceed."
  exit 1
fi

echo "Applying client policy..."
nomad acl policy apply -description "Node policy for clients" node-policy $CLIENT_POLICY

nomad namespace apply nf-nomad
echo "namespace nf-nomad created"

if [ ! -f $OUT_DIR/client-token.json ]; then
  echo "Creating new client token..."
  nomad acl token create -name="client-node-token" -policy=node-policy -type=client -json > $OUT_DIR/client-token.json 2>&1
  if [ $? -eq 0 ]; then
    CLIENT_TOKEN=$(jq -r '.SecretID' $OUT_DIR/client-token.json)
    echo "$CLIENT_TOKEN" > $OUT_DIR/client-token.txt
    chmod 600 $OUT_DIR/client-token.json $OUT_DIR/client-token.txt
    echo "Client token created successfully"
  else
    echo "Failed to create client token"
  fi
else
  echo "Client token already exists in $OUT_DIR/client-token.json"
  CLIENT_TOKEN=$(jq -r '.SecretID' $OUT_DIR/client-token.json)
fi

# Surface the bootstrap (management-tier) token as the primary credential
# for dev / real-world testing — the client-node-token has policy=node-policy
# only and isn't sufficient for namespace / job / ACL / cluster admin work.
# The client token stays available as a secondary example of policy scoping.
MGMT_ACCESSOR=$(jq -r '.AccessorID' $OUT_DIR/bootstrap-token.json)
MGMT_NAME=$(jq -r '.Name'        $OUT_DIR/bootstrap-token.json)

cat <<EOF

  Management token  (full permissions — use this):
    export NOMAD_ADDR=$NOMAD_ADDR
    export NOMAD_TOKEN=$MANAGEMENT_TOKEN

  Client token  (policy=node-policy — secondary, scoped example):
    export NOMAD_ADDR=$NOMAD_ADDR
    export NOMAD_TOKEN=$CLIENT_TOKEN
────────────────────────────────────────────────────────────────────────────
EOF

chmod -R ugoa+rw /tmp/nomad/nomad_temp/scratchdir/