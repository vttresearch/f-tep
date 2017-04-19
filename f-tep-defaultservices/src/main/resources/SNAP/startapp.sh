#!/bin/sh

WORKER_DIR="/home/worker"
IN_DIR="${WORKER_DIR}/workDir/inDir"
OUT_DIR="${WORKER_DIR}/workDir/outDir"

INPUT_FILES=$(ls -1 ${IN_DIR}/inputfile/*/*.xml | grep -v 'INSPIRE.xml')

/opt/snap/bin/snap --open ${INPUT_FILES}
