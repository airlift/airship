#!/bin/bash
DATA_DIR=$(cd $(dirname "$0"); pwd)
$DATA_DIR/galaxy-coordinator-${project.version}/bin/launcher --data $DATA_DIR start
$DATA_DIR/galaxy-agent-${project.version}/bin/launcher --data $DATA_DIR start
