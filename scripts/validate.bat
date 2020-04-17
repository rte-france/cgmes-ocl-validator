set INPUTS=inputs
set VALIDATOR_HOME=.
set VALIDATOR_CONFIG=config
chcp 65001
java -Xms1g -Dfile.encoding=UTF-8 -cp target/ocl_validator-2.0-jar-with-all-dependencies.jar ocl.OCLEvaluator %INPUTS%
PAUSE

