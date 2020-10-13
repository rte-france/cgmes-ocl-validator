#!/bin/bash
export VALIDATOR_INPUTS=inputs
export VALIDATOR_OUTPUTS=reports
export VALIDATOR_HOME=.
export VALIDATOR_CONFIG=config
java -Xms4g -XX:-UseGCOverheadLimit -cp target/ocl_validator-3.0-jar-with-all-dependencies.jar ocl.CGMESValidatorDaemon
