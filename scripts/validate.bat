set INPUTS=inputs
set VALIDATOR_HOME=.
set VALIDATOR_CONFIG=config
java -Xms1g -cp target/ocl_validator-1.1-jar-with-all-dependencies.jar ocl.OCLEvaluator %INPUTS%
PAUSE

