#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

BUILD=0
SKIP_HELLO=1
SKIP_DEMO=0
SKIP_RNASEQ=0

for arg in "$@"; do
  case "$arg" in
    --build) BUILD=1 ;;
    --with-hello) SKIP_HELLO=0 ;;
    --skip-hello) SKIP_HELLO=1 ;;
    --skip-demo) SKIP_DEMO=1 ;;
    --skip-rnaseq) SKIP_RNASEQ=1 ;;
    *)
      echo "Unknown argument: $arg"
      echo "Supported flags: --build --with-hello --skip-hello --skip-demo --skip-rnaseq"
      exit 1
      ;;
  esac
done

LOCAL_STACK_DIR="${NF_NOMAD_LOCAL_STACK_DIR:-$SCRIPT_DIR/../../../infrastructure/03_automation/035_terraform/local-nomad-minio}"
LOCAL_WORK_DIR="${NF_NOMAD_LOCAL_WORK_DIR:-$LOCAL_STACK_DIR/work}"
LOCAL_NOMAD_ADDR="${NF_NOMAD_LOCAL_NOMAD_ADDR:-${NOMAD_ADDR:-http://localhost:4646}}"
INTEGRATION_RUNS_DIR="${NF_NOMAD_INTEGRATION_RUNS_DIR:-$SCRIPT_DIR/local-nomadlab/runs/integration}"

mkdir -p "$LOCAL_WORK_DIR" "$INTEGRATION_RUNS_DIR"

export NOMAD_ADDR="$LOCAL_NOMAD_ADDR"
export NF_NOMAD_LOCAL_STACK_DIR="$LOCAL_STACK_DIR"
export NF_NOMAD_LOCAL_WORK_DIR="$LOCAL_WORK_DIR"

echo "Nomad address: $NOMAD_ADDR"
echo "Local stack dir: $NF_NOMAD_LOCAL_STACK_DIR"
echo "Local work dir: $NF_NOMAD_LOCAL_WORK_DIR"

if [[ "$BUILD" == 1 ]]; then
  pushd .. >/dev/null
  ./gradlew installPlugin -x test -P version=99.99.99
  popd >/dev/null
fi

if [[ "$SKIP_HELLO" == 0 ]]; then
  ./run-pipeline.sh \
    -c local-nomadlab/nextflow.local.config \
    basic/main.nf
fi

if [[ "$SKIP_DEMO" == 0 ]]; then
  ./run-pipeline.sh \
    -c local-nomadlab/nextflow.local.config \
    nf-core/demo \
    -r dev \
    -profile test,docker \
    --outdir "$INTEGRATION_RUNS_DIR/demo-out"
fi

if [[ "$SKIP_RNASEQ" == 0 ]]; then
  ./run-rnaseq-nf.sh
fi

echo "Local integration validation completed"
