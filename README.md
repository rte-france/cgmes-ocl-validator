# CGMES OCL rules V3 Validator Prototype

This project is a framework to automatize the validation of CGMES files using OCL rules. It uses the Eclipse EMF framework.

## Purpose

The goal of this tool is to provide some feedback to TSOs and RSCs about the quality
of data they generate or use.

## Compilation and packaging

### Requirements

- Java 64 bits >= 1.8. It can be downloaded from https://www.java.com/fr/download/.
- Maven >= 3.5. it can be downloaded from https://maven.apache.org/download.cgi

Be sure that Java and maven are properly configured.

For Maven, if you are behind a proxy, be sure to set the configuration properly or use a maven repository managed by your organization.

### Compilation

Run the command `mvn clean package` to generate the jar file of the validation library. The generated jar is stored in the `target` folder: `target/ocl_validator-*.jar`


### Distributable library with dependencies

Run the command `mvn clean install` to create a redistributable package containing the validator library with required dependencies and scripts to easily launch the validator. The fully packaged validator is stored in the `target` folder:
`target/ocl_validator-3.0-bin.tar.gz` or `target/ocl_validator-3.0-bin.zip`

## How to run the validator

### Requirements

This tool requires Java >= 1.8.
It can be downloaded from https://www.java.com/fr/download/.

The tool has been tested under Windows and Linux.

### Use the installation package
Unzip the installation package wherever you want.
The installation package contains has the following structure:

----- config/

----- inputs/

----- ocl_validator/

----- validate.bat

----- validate.sh

----- startValidationDeamon.bat

----- startValidationDeamon.sh

## Configuration

- copy the input IGMS to be validated into the directory `inputs`. The format has to 
be the following: each xml profile instance file has to be in a separate zip file 
- copy in the directory `config` a CGMES boundary set (same format: each 
instance as a separate zip). This boundary set will be substituted to the one defined
in the IGM if this is not the same. This process is similar to what OPDE does.
- **important**: required validation rules are specified in a separare configuration files, it has to be stored into the `config` directory.
These configuration files can be obtained from ENTSOe CGM BP group:

https://entsoe.sharefile.com/home/shared/foc0a777-e28e-45a2-a775-a517e1f8580a 



## Usage

### Stand-alone mode

####  Windows users
Run the following script
`validate.bat
`
#### Linux users
Run the following script
`validate.sh
`
During the execution, logs will be displayed on the screen.
The output of CGMES file analysis is stored in an **Excel sheet** under the directory `reports`. This xls file contains one sheet per IGM. 

For each IGM are reported: 
- the name of the violated rule, 
- the object class it applies to and 
- the rdf:id (and when possible the name) of the object instances it refers to.

### Daemon mode

We provide another mode of generation of reports, which aims at dealing with flows of data rather than static content of a directory

####  Windows users
Run the following script
`startValidationDaemon.bat
`
#### Linux users
Run the following script
`startValidationDaemon.sh
`

The program is going to monitor the `inputs` directory.

Each time there is a new profile instance, it will analyse it, wait for new inputs until there is a full IGM, then for this IGM the several steps of validation are triggered and two reports are created - the XLS one as for the stand-alone mode, and an XML report, similar to the one that the ENTSO-E quality portal displays. It is possible to activate/deactivate one or the other of the report types in the configuration file `config/config.properties`.

The program works as a daemon (service), which means it stops only when the script is interrupted.

Remark. This solution can for example be plugged on the file system interface of OPDE to automatically check incoming data.



## Disclaimer

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Copyright
&copy; RTE 2019-2020
Contributors: Marco Chiaramello, Jérôme Picault, Maria Hergueta Ortega, Thibaut Vermeulen, Lars-Ola Gottfried Österlund

## License
Mozilla Public License 2.0

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

