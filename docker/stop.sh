#!/bin/bash

echo Stopping the nomad dev environment:
docker compose down -v
sudo rm -rf nomad-data
sudo rm -rf minio-data
sudo rm -rf /tmp/nomad/nomad_temp/scratchdir/
unset NOMAD_TOKEN
