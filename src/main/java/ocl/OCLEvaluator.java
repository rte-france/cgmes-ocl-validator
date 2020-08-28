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
import ocl.service.util.Configuration;
import ocl.service.util.TransformationUtils;
import ocl.service.util.ValidationUtils;
import ocl.service.util.XGMPreparationUtils;
import ocl.service.util.XLSReportWriter;
import ocl.util.EvaluationResult;
import ocl.util.RuleDescription;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public class OCLEvaluator {

    // ----- Static initializations
    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER = Logger.getLogger(OCLEvaluator.class.getName());

    }


    private Set<Profile> SVProfiles = Collections.synchronizedSet(new HashSet<>());
    private Set<Profile> otherProfiles = Collections.synchronizedSet(new HashSet<>());
    private Set<Profile> BDProfiles = Collections.synchronizedSet(new HashSet<>());
    private HashMap<Profile,List<Profile>> IGM_CGM = new HashMap<>();

    /**
     *
     * @param where
     * @return
     */
    private Map<String, List<EvaluationResult>> assessRules(Path where) throws IOException {
        Map<String, List<EvaluationResult>> results = new HashMap<>();

        XMITransformation my_transf = new XMITransformation();

        HashMap<String, Document> xmi_list = new HashMap<>();
        try {
            XGMPreparationUtils.readZips(where.toFile(), SVProfiles, otherProfiles, BDProfiles);
            // trigger assembly
            XGMPreparationUtils.reorderModels(SVProfiles, otherProfiles, BDProfiles, IGM_CGM);
            // check if models are complete
            XGMPreparationUtils.checkConsistency(IGM_CGM);
            LOGGER.info("Reordering done!");
        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }

        xmi_list= my_transf.convertData(IGM_CGM);

        LOGGER.info("XMI transformation done!");

        HashMap<String, Integer> ruleLevels = my_transf.getRuleLevels();

        my_transf = null ; // save memory

        List<String> files = new ArrayList<>();

        LOGGER.info("Validator ready");

        files.addAll(write(xmi_list));

        xmi_list=null;

        int pseudoCPU = files.size()% Configuration.batchSize!=0? files.size()/ Configuration.batchSize +1 : files.size()/ Configuration.batchSize;

        List<List<String>> partition = partition(files,pseudoCPU);

        partition.removeIf(item->item == null || item.size()==0);

        HashMap<String,String> fileCPUs = new HashMap<>();
        for(List<String> s:partition){
            String json = new Gson().toJson(s);
            UUID uuid = UUID.randomUUID();
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(Configuration.cacheDir.resolve(uuid.toString()+".json").toFile());
                fileWriter.write(json);
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(fileWriter!=null)
                fileCPUs.put(uuid.toString(), Configuration.cacheDir.resolve(uuid.toString()+".json").toString());

        }


        LOGGER.info("Start Validation");

        submit(fileCPUs);

        LOGGER.info("End Validation");


        FileFilter fileFilter = new WildcardFileFilter("*.json.zip", IOCase.INSENSITIVE);
        File jsonresult = new File(Configuration.cacheDir.toAbsolutePath().toString()+File.separator);
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
        if (Configuration.cacheDir==null) return;
        try {
            FileUtils.deleteDirectory(Configuration.cacheDir.toFile());
            LOGGER.info("Cache cleaned");
        } catch (IOException e){
            LOGGER.severe("Cannot remove cache directory: " + Configuration.cacheDir.toString());
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
        command.add("-Dfile.encoding=UTF-8");
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


    private static List<String> write(HashMap<String,Document> xmi_list){
        List<String> files = new ArrayList<>();
        xmi_list.entrySet().parallelStream().forEach(entry->{
            try{
                String key = entry.getKey();
                write(xmi_list.get(key),entry.getKey()+".xmi");
                files.add(Configuration.cacheDir.toAbsolutePath().toString()+File.separator+entry.getKey()+".xmi.zip");
                xmi_list.put(key,null); // to free memory
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return files;
    }

    private static void write(Document doc, String name) throws IOException {
        OutputStream zipout = Files.newOutputStream(Paths.get(Configuration.cacheDir.toAbsolutePath().toString() + File.separator + name+".zip"));
        try (ZipOutputStream zip = new ZipOutputStream(zipout)) {
            ZipEntry entry = new ZipEntry(name); // The name
            zip.putNextEntry(entry);
            TransformationUtils.writeDocument(doc, zip);
            zip.closeEntry();
        }

    }

    private void writeExcelReport(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, Path path){
        XLSReportWriter writer = new XLSReportWriter();
        writer.writeResultsPerIGM(synthesis,rules,path);

    }

    private void writeDebugReports(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, Path path){
        XLSReportWriter writer = new XLSReportWriter();
        writer.writeUnknownRulesReport(synthesis,rules,path.toFile());
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

        try {
            OCLEvaluator evaluator = new OCLEvaluator();

            if(Configuration.debugMode)
                LOGGER.info("Validator running in debug mode");
            Map<String, List<EvaluationResult>> synthesis = evaluator.assessRules(Configuration.inputDir);

            // writeDocument report
            evaluator.writeExcelReport(synthesis, ValidationUtils.rules, Configuration.reportsDir.resolve("excelReports"));

            //writeDocument debug report
            if(Configuration.debugMode)
                evaluator.writeDebugReports(synthesis, ValidationUtils.rules, Configuration.reportsDir.resolve("DebugReport.xlsx"));
            evaluator.cleanCache();

        } catch (IOException e){
            e.printStackTrace();
            System.exit(-1);
        }

    }


}
