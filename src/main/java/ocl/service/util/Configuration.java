/**
 *       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 *       EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *       OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 *       SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *       INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 *       TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *       CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *       ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 *       DAMAGE.
 *       (c) RTE 2020
 *       Authors: Marco Chiaramello, Jerome Picault
 **/
package ocl.service.util;

import ocl.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

public class Configuration {

    public static Path inputDir = null;
    public static Path reportsDir = null;
    public static Path cacheDir = null;
    public static Boolean debugMode = false;
    public static int batchSize = 2;

    public static Boolean generateXMLreports = true;
    public static Boolean generateXLSreports = true;

    public static HashMap<String,String> configs = null;

    static Logger logger = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        logger = Logger.getLogger(Configuration.class.getName());

        try {
            configs = getConfig();
        } catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * Inizializes configurations
     * @return
     * @throws IOException
     */
    public static HashMap<String,String> getConfig() throws IOException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+File.separator+"config.properties");
        Properties properties = new Properties();
        properties.load(config);

        String inputDirStr = properties.getProperty("inputDir");
        if (inputDirStr == null){
            logger.severe("You need to specify the folder containing the CGMES IGMs (one zip per instance)");
            System.exit(0);
        }
        inputDir = Paths.get(IOUtils.resolveEnvVars(inputDirStr));

        String reportsDirStr = properties.getProperty("reportsDir");
        if (reportsDirStr == null){
            reportsDir = inputDir.resolve("reports");
        }
        reportsDir = Paths.get(IOUtils.resolveEnvVars(reportsDirStr));

        String basic_model = properties.getProperty("basic_model");
        String ecore_model = properties.getProperty("ecore_model");
        String bdExtensions = properties.getProperty("bdExtensions");
        String debug = properties.getProperty("debugMode");

        String cacheDir_= properties.getProperty("cacheDir");
        String batchSize_=properties.getProperty("batchSize");

        String xmlreport = properties.getProperty("generateXMLreports");
        String xlsreport = properties.getProperty("generateXLSreports");

        if (basic_model!=null && ecore_model!=null){
            configs.put("basic_model", IOUtils.resolveEnvVars(basic_model));
            configs.put("ecore_model", IOUtils.resolveEnvVars(ecore_model));
        }
        else{
            logger.severe("Variable basic_model or ecore_model are missing from properties file");
            System.exit(0);
        }

        if(bdExtensions!=null){
            configs.put("bdExtensions",IOUtils.resolveEnvVars(bdExtensions));
        }
        else{
            logger.severe("Boundary extensions file missing from properties file");
            System.exit(0);
        }

        if (debug!=null) debugMode = debug.equalsIgnoreCase("TRUE");
        if (xlsreport != null) generateXLSreports = xlsreport.equalsIgnoreCase("TRUE");
        if (xmlreport != null) generateXMLreports = xmlreport.equalsIgnoreCase("TRUE");


        if(cacheDir_!=null ){
            try{
                String cacheDirStr = IOUtils.resolveEnvVars(cacheDir_)+File.separator+"cache";
                configs.put("cacheDir", cacheDirStr);
                cacheDir = Paths.get(cacheDirStr);
            }catch (Exception e){
                String cacheDirStr = inputDir.toAbsolutePath().toString()+File.separator+"/cache";
                configs.put("cacheDir", cacheDirStr);
                cacheDir= Paths.get(cacheDirStr);
            }

        }
        else{
            cacheDir= Paths.get(inputDir.toAbsolutePath().toString()+File.separator+"/cache");
        }
        cacheDir.toFile().mkdirs();


        if(batchSize_!=null){
            batchSize = Integer.parseInt(batchSize_);
        }


        return configs;
    }
}
