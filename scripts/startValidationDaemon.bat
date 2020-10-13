set VALIDATOR_INPUTS=inputs
set VALIDATOR_OUTPUTS=reports
set VALIDATOR_HOME=.
set VALIDATOR_CONFIG=config
chcp 65001
java -Xms4g -XX:-UseGCOverheadLimit -cp target/ocl_validator-3.0-jar-with-all-dependencies.jar ocl.CGMESValidatorDaemon
PAUSE
