package ocl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ocl.service.ValidationService;
import ocl.service.util.ValidationUtils;
import ocl.util.EvaluationResult;
import org.eclipse.emf.common.util.Diagnostic;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Validation {

    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER = Logger.getLogger(OCLEvaluator.class.getName());
    }


    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {

        Gson gson = new Gson();
        Type listType = new TypeToken<List<String>>() {}.getType();
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        List<String> myList = gson.fromJson(br,listType);

        ValidationService validationService = new ValidationService();
        for(String s:myList){
            File file = new File(s);
            ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                InputStream xmlStream = zip.getInputStream(entry);
                Diagnostic diagnostic = validationService.evaluate(xmlStream);
                LOGGER.info(entry.getName()+ " validated");
                List<EvaluationResult> res = validationService.getErrors(diagnostic, ValidationUtils.rules);
                OutputStream zipout = Files.newOutputStream(Paths.get(file.getParentFile().getAbsolutePath() + File.separator +entry.getName().replace("xmi","json") +".zip"));
                ZipOutputStream zipOutputStream = new ZipOutputStream(zipout);
                String json = new Gson().toJson(res);
                ZipEntry entry_ = new ZipEntry(entry.getName() + ".json"); // The name
                zipOutputStream.putNextEntry(entry_);
                zipOutputStream.write(json.getBytes());
                zipOutputStream.closeEntry();
                zipOutputStream.close();
                file.delete();
                res=null;
                xmlStream.close();

            }
        }
        File jsonCPU = new File(args[0]);
        jsonCPU.delete();
        validationService = null;


    }

}
