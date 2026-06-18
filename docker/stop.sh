#!/bin/bash

echo Stopping the nomad dev environment:
docker compose down -v
sudo rm -rf minio-data
sudo rm -rf /tmp/nomad/
unset NOMAD_TOKEN
