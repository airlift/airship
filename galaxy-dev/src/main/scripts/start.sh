#!/bin/bash
DATA_DIR=$(cd $(dirname "$0"); pwd)
$DATA_DIR/galaxy-coordinator-${project.version}/bin/launcher start
$DATA_DIR/galaxy-agent-${project.version}/bin/launcher start
