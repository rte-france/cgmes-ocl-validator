package ocl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ocl.service.util.ValidationUtils;
import ocl.util.EvaluationResult;
import ocl.util.RuleDescription;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.IllegalValueException;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static ocl.service.util.ValidationUtils.createResourceSet;

public class Validation {

    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER = Logger.getLogger(OCLEvaluator.class.getName());
    }

    public Validation(){
        super();
    }


    private Diagnostic evaluate(InputStream inputStream, String name){

        ResourceSet resourceSet = createResourceSet();

        HashMap<String, Boolean> options = new HashMap<>();
        options.put(XMLResource.OPTION_DEFER_IDREF_RESOLUTION,true);
        UUID uuid = UUID.randomUUID();
        Resource model = resourceSet.createResource(URI.createURI(uuid.toString()));
        try {
            model.load(inputStream,options);
        } catch (Resource.IOWrappedException we){
            Exception exc = we.getWrappedException();
            if (exc instanceof IllegalValueException){
                IllegalValueException ive = (IllegalValueException) exc;
                EObject object = ive.getObject();
                String n = (object.eClass().getEStructuralFeature("name") != null) ? (" "+object.eGet(object.eClass().getEStructuralFeature("name"))+" ") : "";
                String id =  (object.eClass().getEStructuralFeature("mRID") != null) ? String.valueOf(object.eGet(object.eClass().getEStructuralFeature("mRID"))) : null;
                LOGGER.severe("Problem with: " + id + n + " (value:" + ive.getValue().toString() + ")");
            }
            LOGGER.severe(exc.getMessage());
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        EObject rootObject = model.getContents().get(0);

        Diagnostician take = new Diagnostician();

        Diagnostic diagnostics;

        diagnostics = take.validate(rootObject);
        LOGGER.info(name + " validated");

        rootObject = null;
        take = null;
        inputStream = null;
        resourceSet = null;

        return diagnostics;
    }

    private boolean excludeRuleName(String ruleName){
        // MessageType introduced in ecore file but not managed on Java side
        return "MessageType".equalsIgnoreCase(ruleName);
    }


    private List<EvaluationResult> getErrors(Diagnostic diagnostics, HashMap<String, RuleDescription> rules) {

        List<EvaluationResult> results = new ArrayList<>();
        if (diagnostics==null) return results;
        for (Diagnostic childDiagnostic: diagnostics.getChildren()){
            List<?> data = childDiagnostic.getData();
            EObject object = (EObject) data.get(0);
            String msg;
            Matcher matcher;
            Pattern pattern = Pattern.compile(".*'(\\w*)'.*");
            if(data.size()==1){
                msg = childDiagnostic.getMessage();
                matcher = pattern.matcher(msg);
                while (matcher.find()) {
                    String name = (object.eClass().getEStructuralFeature("name") != null) ? String.valueOf(object.eGet(object.eClass().getEStructuralFeature("name"))) : null;
                    String ruleName = matcher.group(1);
                    if (!excludeRuleName(ruleName)){
                        String severity = rules.get(ruleName) == null ? "UNKOWN" : rules.get(ruleName).getSeverity();
                        int level = rules.get(ruleName) == null ? 0 : rules.get(ruleName).getLevel();
                        results.add(new EvaluationResult(severity,
                                ruleName,
                                level,
                                object.eClass().getName(),
                                (object.eClass().getEStructuralFeature("mRID") != null) ? String.valueOf(object.eGet(object.eClass().getEStructuralFeature("mRID"))) : null,
                                name, null
                        ));
                    }
                }
            } else {
                // it is for sure a problem of cardinality, we set it by default to IncorrectAttributeOrRoleCard,
                // later on we check anyway if it exists in UMLRestrictionRules file
                msg = childDiagnostic.getMessage();
                matcher = pattern.matcher(msg);
                while (matcher.find()) {
                    String ruleName = matcher.group(1);
                    if (!excludeRuleName(ruleName)) {
                        if(rules.get(ruleName)==null){
                            ruleName = "IncorrectAttributeOrRoleCard";
                        }
                        String severity = rules.get(ruleName) == null ? "UNKOWN" : rules.get(ruleName).getSeverity();
                        int level = rules.get(ruleName) == null ? 0 : rules.get(ruleName).getLevel();
                        EvaluationResult evaluationResult = new EvaluationResult(severity,
                                ruleName,
                                level,
                                object.eClass().getName(),
                                (object.eClass().getEStructuralFeature("mRID") != null) ? String.valueOf(object.eGet(object.eClass().getEStructuralFeature("mRID"))) : null,
                                null, null
                        );
                        if(rules.get(ruleName)!=null){
                            evaluationResult.setSpecificMessage(matcher.group(1)+" of "+object.eClass().getName()+" is required.");
                        }
                        results.add(evaluationResult);

                    }
                }
            }
        }
        diagnostics = null;
        return results;
    }


    public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {

        Gson gson = new Gson();
        Type listType = new TypeToken<List<String>>() {}.getType();
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        List<String> myList = gson.fromJson(br,listType);

        Validation validation = new Validation();
        for(String s:myList){
            File file = new File(s);
            ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                InputStream xmlStream = zip.getInputStream(entry);
                List<EvaluationResult> res = validation.getErrors(validation.evaluate(xmlStream,entry.getName()), ValidationUtils.rules);
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
        validation = null;


    }

}
