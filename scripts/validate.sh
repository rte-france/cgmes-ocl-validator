#!/bin/bash
INPUTS=inputs
export VALIDATOR_HOME=.
export VALIDATOR_CONFIG=config
java -Xms1g -cp target/ocl_validator-1.2-jar-with-all-dependencies.jar ocl.OCLEvaluator $INPUTS

