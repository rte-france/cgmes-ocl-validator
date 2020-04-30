package ocl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ocl.util.EvaluationResult;
import ocl.util.IOUtils;
import ocl.util.RuleDescription;
import ocl.util.RuleDescriptionParser;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.pivot.model.OCLstdlib;
import org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Validation {
    private static Resource ecoreResource;
    private static ResourceSet resourceSet;

    private static URI mmURI;
    private static URI modelURI;
    private static Resource model;
    private static EPackage p;

    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER = Logger.getLogger(OCLEvaluator.class.getName());
        try {
            resourceSet = new ResourceSetImpl();
            HashMap<String, String> configs = getConfig();
            prepareValidator(configs.get("basic_model"), configs.get("ecore_model"));
        } catch (IOException | URISyntaxException io){
            io.printStackTrace();
        }
    }

    public Validation(){

    }


    private  static HashMap<String,String> getConfig() throws IOException, URISyntaxException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+ File.separator+"config.properties");
        Properties properties = new Properties();
        properties.load(config);
        String basic_model = properties.getProperty("basic_model");
        String ecore_model = properties.getProperty("ecore_model");
        String debug = properties.getProperty("debugMode");

        if (basic_model!=null && ecore_model!=null){
            configs.put("basic_model", IOUtils.resolveEnvVars(basic_model));
            configs.put("ecore_model", IOUtils.resolveEnvVars(ecore_model));
        }
        else{
            LOGGER.severe("Variable basic_model or ecore_model are missing from properties file");
            System.exit(0);
        }


        return configs;
    }

    private static void prepareValidator(String basic_model, String ecore_model){
        CompleteOCLStandaloneSetup.doSetup();

        OCLstdlib.install();

        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        mmURI = URI.createFileURI(new File(ecore_model).getAbsolutePath());
        File my_model = new File(basic_model);
        modelURI = URI.createFileURI(my_model.getAbsolutePath());
        try {
            ecoreResource = resourceSet.getResource(mmURI, true);
        }
        catch (Exception e ){
            LOGGER.severe("Ecore file missing in "+ System.getenv("VALIDATOR_CONFIG")+" !");
            System.exit(0);
        }


        List<EPackage> pList = getPackages(ecoreResource);
        p = pList.get(0);

        resourceSet.getPackageRegistry().put(p.getNsURI(), p);

        model = resourceSet.getResource(modelURI, true);
        model.unload();
    }


    private static List<EPackage> getPackages(Resource r){
        ArrayList<EPackage> pList = new ArrayList<EPackage>();
        if (r.getContents() != null)
            for (EObject obj : r.getContents())
                if (obj instanceof EPackage) {
                    pList.add((EPackage)obj);
                }
        return pList;
    }


    private Diagnostic evaluate(InputStream inputStream, String name){
        HashMap<String, Boolean> options = new HashMap<>();
        options.put(XMLResource.OPTION_DEFER_IDREF_RESOLUTION,true);
        try {
            model.load(inputStream,options);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        EObject rootObject = model.getContents().get(0);

        Diagnostician take = new Diagnostician();

        Diagnostic diagnostics;

        diagnostics = take.validate(rootObject);
        LOGGER.info(name + " validated");


        model.unload();
        rootObject = null;
        take = null;
        inputStream = null;

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
        RuleDescriptionParser parser = new RuleDescriptionParser();
        HashMap<String, RuleDescription> rules = parser.parseRules("config/UMLRestrictionRules.xml");
        parser = null;
        for(String s:myList){
            File file = new File(s);
            ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                InputStream xmlStream = zip.getInputStream(entry);
                List<EvaluationResult> res = validation.getErrors(validation.evaluate(xmlStream,entry.getName()), rules);
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
