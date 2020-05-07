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
 *       (c) RTE 2019
 *       Authors: Marco Chiaramello, Jerome Picault
 **/

package ocl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ocl.util.*;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public class OCLEvaluator {

    private static Boolean debugMode = false;
    private static File where = null;
    private static File cacheDir = null;
    private static int batchSize = 2;

    // ----- Static initializations
    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER = Logger.getLogger(OCLEvaluator.class.getName());

    }


    public OCLEvaluator(){

    }




    /**
     *
     * @param where
     * @return
     */
    private Map<String, List<EvaluationResult>> assessRules(File where) throws IOException {
        Map<String, List<EvaluationResult>> results = new HashMap<>();

        IGM_CGM_preparation my_prep = new IGM_CGM_preparation();
        XMITransformation my_transf = new XMITransformation();

        HashMap<String, Document> xmi_list = new HashMap<>();

        try {
            my_prep.readZip(where);
            LOGGER.info("Reordering done!");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        try {
            xmi_list= my_transf.convertData(my_prep.IGM_CGM, my_prep.defaultBDIds);
            LOGGER.info("XMI transformation done!");
        } catch (TransformerException | ParserConfigurationException | SAXException | URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        HashMap<String, Integer> ruleLevels = my_transf.getRuleLevels();

        my_prep = null; // save memory
        my_transf = null ; // save memory


        List<String> files = new ArrayList<>();
        cacheDir.mkdirs();

        LOGGER.info("Validator ready");

        files.addAll(write(xmi_list));

        xmi_list=null;
        


        int pseudoCPU = files.size()%batchSize!=0? files.size()/batchSize +1 : files.size()/batchSize;


        List<List<String>> partition = partition(files,pseudoCPU);



        partition.removeIf(item->item == null || item.size()==0);

        HashMap<String,String> fileCPUs = new HashMap<>();
        for(List<String> s:partition){
            String json = new Gson().toJson(s);
            UUID uuid = UUID.randomUUID();
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(cacheDir.getAbsolutePath()+File.separator+uuid.toString()+".json");
                fileWriter.write(json);
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(fileWriter!=null)
                fileCPUs.put(uuid.toString(),cacheDir.getAbsolutePath()+File.separator+uuid.toString()+".json");

        }


        LOGGER.info("Start Validation");

        submit(fileCPUs);

        LOGGER.info("End Validation");


        FileFilter fileFilter = new WildcardFileFilter("*.json.zip", IOCase.INSENSITIVE);
        File jsonresult = new File(cacheDir.getAbsolutePath()+File.separator);
        Type listType = new TypeToken<List<EvaluationResult>>() {}.getType();
        File[] jsonres = jsonresult.listFiles(fileFilter);
        for (File f: jsonres){

            ZipFile zip = new ZipFile(new File(f.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                InputStream jsonStream = zip.getInputStream(entry);
                Gson gson = new Gson();
                List<EvaluationResult> myList = gson.fromJson(org.apache.commons.io.IOUtils.toString(jsonStream, StandardCharsets.UTF_8.name()),listType);

                results.put(f.getName().replace(".json.zip",""),myList);
                jsonStream.close();
                gson=null;

            }
            zip.close();
            Files.delete(f.toPath());
        }

        return results;
    }


    private void cleanCache(){
        if (cacheDir==null) return;
        try {
            FileUtils.deleteDirectory(cacheDir);
            LOGGER.info("Cache cleaned");
        } catch (IOException e){
            LOGGER.severe("Cannot remove cache directory: " + cacheDir.getPath());
            e.printStackTrace();
        }
    }



    private synchronized void submit(HashMap<String,String> filesCPUS){
        int availableCPUs = (Runtime.getRuntime().availableProcessors()-1)<=0? 1 :  Runtime.getRuntime().availableProcessors()-1;
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(availableCPUs);

        for(String s: filesCPUS.keySet()){
            Runnable r = new Task(filesCPUS.get(s));
           pool.execute(r);
        }

        pool.shutdown();
        while (true) {
            try {
                if (!!pool.awaitTermination(5, TimeUnit.NANOSECONDS)) break;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };

    }

    private <T> List<List<T>> partition(Iterable<T> iterable, int partitions){
        List<List<T>> result = new ArrayList<>(partitions);
        for(int i = 0; i < partitions; i++)
            result.add(new ArrayList<>());

        Iterator<T> iterator = iterable.iterator();
        for(int i = 0; iterator.hasNext(); i++)
            result.get(i % partitions).add(iterator.next());

        return result;
    }


    private void create(String file){
        String javaHome = System.getProperty("java.home");

        String javaBin = javaHome +
                File.separator + "bin" +
                File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        List<String> command = new LinkedList<String>();
        command.add("java");
        command.add("-cp");
        command.add(classpath);
        command.add(Validation.class.getName());
        command.add(file);
        ProcessBuilder builder = new ProcessBuilder(command);
        try {
            Process process = builder.inheritIO().start();
            process.waitFor();
            process.destroy();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void printDocument(Document doc, String name) throws  TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        transformer.transform(new DOMSource(doc),new StreamResult(new File(cacheDir.getAbsolutePath()+File.separator+name)));
    }


    private static List<String> write(HashMap<String,Document> xmi_list){
        List<String> files = new ArrayList<>();
        xmi_list.entrySet().parallelStream().forEach(entry->{
            try{
                String key = entry.getKey();
                write(xmi_list.get(key),entry.getKey()+".xmi");
                files.add(cacheDir.getAbsolutePath()+File.separator+entry.getKey()+".xmi.zip");
                xmi_list.put(key,null); // to free memory
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return files;
    }

    private static void write( Document doc, String name) throws IOException {
        OutputStream zipout = Files.newOutputStream(Paths.get(cacheDir.getAbsolutePath() + File.separator + name+".zip"));
        try (ZipOutputStream zip = new ZipOutputStream(zipout)) {
            ZipEntry entry = new ZipEntry(name); // The name
            zip.putNextEntry(entry);
            write(doc, zip);
            zip.closeEntry();
        }

    }

    private static void write(Document doc, OutputStream out) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            transformer.transform(
                    new DOMSource(doc),
                    new StreamResult(new OutputStreamWriter(out, "UTF-8"))
            );

        } catch (final IllegalArgumentException
                | TransformerException
                | TransformerFactoryConfigurationError
                | UnsupportedEncodingException ex) {

            throw new RuntimeException(ex);
        }
    }

    /**
     * Inizializes configurations
     * @return
     * @throws IOException
     */
    static HashMap<String,String> getConfig() throws IOException, URISyntaxException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+File.separator+"config.properties");
        Properties properties = new Properties();
        properties.load(config);
        String basic_model = properties.getProperty("basic_model");
        String ecore_model = properties.getProperty("ecore_model");
        String bdExtensions = properties.getProperty("bdExtensions");
        String debug = properties.getProperty("debugMode");
        String cacheDir_= properties.getProperty("cacheDir");
        String batchSize_=properties.getProperty("batchSize");

        if (basic_model!=null && ecore_model!=null){
            configs.put("basic_model", IOUtils.resolveEnvVars(basic_model));
            configs.put("ecore_model", IOUtils.resolveEnvVars(ecore_model));
        }
        else{
            LOGGER.severe("Variable basic_model or ecore_model are missing from properties file");
            System.exit(0);
        }

        if(bdExtensions!=null){
            configs.put("bdExtensions",IOUtils.resolveEnvVars(bdExtensions));
        }
        else{
            LOGGER.severe("Boundary extensions file missing from properties file");
            System.exit(0);
        }

        if(debug!=null){
            if(debug.equalsIgnoreCase("TRUE")){
                debugMode = true;
            }
        }

        if(cacheDir_!=null ){
            try{
                cacheDir = new File(IOUtils.resolveEnvVars(cacheDir_)+File.separator+"cache");
            }catch (Exception e){

                cacheDir= new File(where.getAbsolutePath()+File.separator+"/cache");
            }

        }
        else{
            cacheDir= new File(where.getAbsolutePath()+File.separator+"/cache");
        }

        if(batchSize_!=null){
            batchSize = Integer.parseInt(batchSize_);
        }


        return configs;
    }

    private void writeExcelReport(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, File path){
        XLSWriter writer = new XLSWriter();
        //writer.writeResults(synthesis, rules, path);
        writer.writeResultsPerIGM(synthesis,rules,path);

    }

    private void writeDebugReports(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, File path){
        XLSWriter writer = new XLSWriter();
        if(debugMode)
            writer.writeUnknownRulesReport(synthesis,rules,path);

    }

    class Task implements Runnable
    {
        private String name;

        private Task(String s)
        {
            name = s;
        }

        public void run()
        {
           create(name);
        }
    }

    /**
     * Main
     * @param args - path to CGMES files to be validated
     */
    public static void main(String[] args) {
        Locale.setDefault(new Locale("en", "EN"));


        if (args.length<1){
            LOGGER.severe("You need to specify the folder containing the CGMES IGMs (one zip per instance)");
            System.exit(0);
        }
        else{
            where = new File(args[0]);
        }

        try {
            // Read rule details
            RuleDescriptionParser parser = new RuleDescriptionParser();
            HashMap<String, RuleDescription> rules = parser.parseRules("config/UMLRestrictionRules.xml");

            OCLEvaluator evaluator = new OCLEvaluator();
            if(debugMode)
                LOGGER.info("Validator running in debug mode");
            Map<String, List<EvaluationResult>> synthesis = evaluator.assessRules(where);

            // write report
            evaluator.writeExcelReport(synthesis, rules, new File(args[0]+"/QASv3_results.xlsx"));

            //write debug report
            evaluator.writeDebugReports(synthesis, rules, new File(args[0]+"/DebugReport.xlsx"));

            evaluator.cleanCache();

        } catch (ParserConfigurationException | IOException | SAXException e){
            e.printStackTrace();
            System.exit(-1);
        }

    }


}
