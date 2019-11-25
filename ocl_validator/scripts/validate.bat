set INPUTS=inputs
set VALIDATOR_HOME=.
set VALIDATOR_CONFIG=config
java -Xms1g -Xmx4g -cp ocl_validator/target/ocl_validator-1.0-beta-jar-with-all-dependencies.jar ocl.OCLEvaluator %INPUTS%
PAUSE

