#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PIPELINE_NAME="${RNASEQ_PIPELINE_NAME:-nextflow-io/rnaseq-nf}"
# v2.3 tag currently resolves to 8253a586cc5a9679d37544ac54f72167cced324b
PIPELINE_REVISION="${RNASEQ_PIPELINE_REVISION:-8253a586cc5a9679d37544ac54f72167cced324b}"
PIPELINE_PROFILE="${RNASEQ_PIPELINE_PROFILE:-test,docker}"
RUN_LABEL="${RNASEQ_RUN_LABEL:-$(date +%Y%m%d-%H%M%S)}"
BASE_DIR="${RNASEQ_BASE_DIR:-$SCRIPT_DIR/nomad_temp/scratchdir/real-pipelines/rnaseq-nf}"
RESULTS_DIR="${RNASEQ_RESULTS_DIR:-$BASE_DIR/results-$RUN_LABEL}"
ARTIFACTS_DIR="${RNASEQ_ARTIFACTS_DIR:-$BASE_DIR/artifacts-$RUN_LABEL}"
TRACE_FILE="$ARTIFACTS_DIR/trace.txt"
REPORT_FILE="$ARTIFACTS_DIR/report.html"
TIMELINE_FILE="$ARTIFACTS_DIR/timeline.html"
NEXTFLOW_LOG="$ARTIFACTS_DIR/nextflow.log"

mkdir -p "$RESULTS_DIR" "$ARTIFACTS_DIR"

mkdir -p "$BASE_DIR"

assert_file() {
  local path="$1"
  local label="$2"
  if [[ ! -f "$path" ]]; then
    echo "Missing ${label}: $path"
    exit 1
  fi
}

assert_non_empty_file() {
  local path="$1"
  local label="$2"
  assert_file "$path" "$label"
  if [[ ! -s "$path" ]]; then
    echo "${label} exists but is empty: $path"
    exit 1
  fi
}

assert_trace_has_process() {
  local pattern="$1"
  if ! grep -q "$pattern" "$TRACE_FILE"; then
    echo "Trace is missing expected process pattern '$pattern'"
    exit 1
  fi
}

echo "Running $PIPELINE_NAME (revision $PIPELINE_REVISION) with profile $PIPELINE_PROFILE"

./run-pipeline.sh \
  -c rnaseq-nf/nextflow.config \
  "$PIPELINE_NAME" \
  -r "$PIPELINE_REVISION" \
  -profile "$PIPELINE_PROFILE" \
  --outdir "$RESULTS_DIR" \
  -with-trace "$TRACE_FILE" \
  -with-report "$REPORT_FILE" \
  -with-timeline "$TIMELINE_FILE" \
  "$@" 2>&1 | tee "$NEXTFLOW_LOG"

assert_non_empty_file "$TRACE_FILE" "trace file"
assert_non_empty_file "$REPORT_FILE" "report file"
assert_non_empty_file "$TIMELINE_FILE" "timeline file"
assert_non_empty_file "$NEXTFLOW_LOG" "nextflow log"
assert_file "$RESULTS_DIR/multiqc_report.html" "MultiQC output report"

if grep -Eq '\bFAILED\b' "$TRACE_FILE"; then
  echo "Trace contains FAILED tasks"
  exit 1
fi

assert_trace_has_process "RNASEQ:FASTQC"
assert_trace_has_process "RNASEQ:QUANT"
assert_trace_has_process "MULTIQC"

if ! grep -q "Execution complete -- Goodbye" "$NEXTFLOW_LOG"; then
  echo "Nextflow completion marker not found in log"
  exit 1
fi

echo "rnaseq-nf validation passed"
echo "results:   $RESULTS_DIR"
echo "artifacts: $ARTIFACTS_DIR"
