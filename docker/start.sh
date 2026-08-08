#!/bin/bash

# The Nomad client runs inside a container but launches task containers on the HOST
# docker daemon (via the mounted docker.sock), so any shared path must resolve to the
# same location inside and outside this container -- hence the identity bind mount in
# docker-compose.yml.
#
# On macOS /tmp is a symlink to /private/tmp. A work dir under /tmp therefore gets
# canonicalised to /private/tmp on the host, while the Linux task container only knows
# /tmp -- staged inputs become dangling symlinks and tasks fail with "does not exist".
# Default to a non-symlinked path there. Override with NOMAD_SHARED_DIR.
if [ -z "${NOMAD_SHARED_DIR:-}" ]; then
  case "$(uname -s)" in
    Darwin) NOMAD_SHARED_DIR="$HOME/.nf-nomad-dev/scratchdir" ;;
    *)      NOMAD_SHARED_DIR="/tmp/nomad/nomad_temp/scratchdir" ;;
  esac
fi
export NOMAD_SHARED_DIR
mkdir -p "$NOMAD_SHARED_DIR"

# server.conf is baked into the image at build time, so render it before building.
sed "s#@NOMAD_SHARED_DIR@#${NOMAD_SHARED_DIR}#g" server.conf.in > server.conf

echo "Shared work-dir volume (host == container): $NOMAD_SHARED_DIR"

echo Creating a nomad dev environment:

docker compose build
docker compose up -d
docker compose exec minio sh /usr/local/bin/init-minio.sh
docker compose exec nomad sh /usr/local/bin/init-nomad.sh

echo Grab the NOMAD_TOKEN environment
