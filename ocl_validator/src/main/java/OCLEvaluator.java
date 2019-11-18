package ocl;

import ocl.util.EvaluationResult;
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
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ocl.util.XLSWriter;

public class OCLEvaluator {

    private static Resource ecoreResource;
    private static ResourceSet resourceSet;

    private static URI mmURI;
    private static URI modelURI;
    private static Resource model;
    private static EPackage p;

    // ----- Static initializations
    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER = Logger.getLogger(OCLEvaluator.class.getName());
        try {
            resourceSet = new ResourceSetImpl();
            HashMap<String, String> configs = get_config();
            prepare_validator(configs.get("basic_model"), configs.get("ecore_model"));
        } catch (IOException io){
            io.printStackTrace();
        }
    }


    public OCLEvaluator(){

    }

    /**
     *
     * @return Diagnostic object
     */
    public Diagnostic evaluate(StreamResult xmi){
        InputStream inputStream = new ByteArrayInputStream(xmi.getOutputStream().toString().getBytes());

        HashMap<String, Boolean> options = new HashMap<>();
        options.put(XMLResource.OPTION_DEFER_IDREF_RESOLUTION,true);
        try {
            model.load(inputStream,options);
        } catch (IOException e) {
            e.printStackTrace();
        }

        EPackage ePackage = model.getContents().get(0).eClass().getEPackage();
        EPackage.Registry.INSTANCE.put(p.getNsURI(), ePackage);

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(
                Resource.Factory.Registry.DEFAULT_EXTENSION,
                new XMIResourceFactoryImpl());

        EObject rootObject = model.getContents().get(0);

        Diagnostician take = new Diagnostician();

        Diagnostic diagnostics;
        diagnostics = take.validate(rootObject);

        //printErrors(diagnostics);

        model.unload();

        return diagnostics;
    }

    /**
     * Load ecore resources
     * @param r
     * @return
     */
    public static List<EPackage> getPackages(Resource r){
        ArrayList<EPackage> pList = new ArrayList<EPackage>();
        if (r.getContents() != null)
            for (EObject obj : r.getContents())
                if (obj instanceof EPackage) {
                    pList.add((EPackage)obj);
                }
        return pList;
    }


    public Map<String, List<EvaluationResult>> assessRules(File where){
        Map<String, List<EvaluationResult>> results = new HashMap<>();

        ocl.IGM_CGM_preparation my_prep = new ocl.IGM_CGM_preparation();
        ocl.xmi_transform my_transf = new ocl.xmi_transform();

        HashMap<String, StreamResult> xmi_list = new HashMap<>();

        try {
            my_prep.read_zip(where);
            LOGGER.info("Reordering done!");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        try {
            xmi_list= my_transf.convert_data(my_prep.IGM_CGM, my_prep.defaultBDIds);
            LOGGER.info("XMI transformation done!");
        } catch (TransformerException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        OCLEvaluator evaluator = new OCLEvaluator();
        Diagnostic diagnostics;
        LOGGER.info("Validator ready");
        for(String key : xmi_list.keySet()){
            LOGGER.info("");
            LOGGER.info("************");
            LOGGER.info("Validating IGM "+key);
            LOGGER.info("************");

            diagnostics = evaluator.evaluate( xmi_list.get(key));

            List<EvaluationResult> res = getErrors(diagnostics);
            printError(res);
            results.put(key, res);

            if(!res.isEmpty())
                LOGGER.severe("ERROR: Invalid constraints for model: " + key);
            else
                LOGGER.info("All constraints are valid for model:" + key);
        }

        return results;
    }

    /**
     *
     * @param input
     * @return
     */
    static String resolveEnvVars(String input)
    {
        if (null == input) return null;

        // match ${ENV_VAR_NAME} or $ENV_VAR_NAME
        Pattern p = Pattern.compile("\\$\\{(\\w+)\\}|\\$(\\w+)");
        Matcher m = p.matcher(input); // get a matcher object
        StringBuffer sb = new StringBuffer();
        while(m.find()){
            String envVarName = null == m.group(1) ? m.group(2) : m.group(1);
            String envVarValue = System.getenv(envVarName);
            m.appendReplacement(sb, null == envVarValue ? "" : envVarValue);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Inizializes configurations
     * @return
     * @throws IOException
     */
    public static HashMap<String,String> get_config() throws IOException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+"/config.properties");
        Properties properties = new Properties();
        properties.load(config);
        String basic_model = properties.getProperty("basic_model");
        String ecore_model = properties.getProperty("ecore_model");

        if (basic_model!=null && ecore_model!=null){
            configs.put("basic_model", resolveEnvVars(basic_model));
            configs.put("ecore_model", resolveEnvVars(ecore_model));
        }
        else{
            LOGGER.severe("Variable basic_model or ecore_model are missing from properties file");
            System.exit(0);
        }

        return configs;
    }


    /**
     * Preparation of the validator
     * @param basic_model
     * @param ecore_model
     */
    public static void prepare_validator(String basic_model, String ecore_model){
        CompleteOCLStandaloneSetup.doSetup();

        OCLstdlib.install();

        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        mmURI = URI.createFileURI(new File(ecore_model).getAbsolutePath());
        File my_model = new File(basic_model);
        modelURI = URI.createFileURI(my_model.getAbsolutePath());

        ecoreResource = resourceSet.getResource(mmURI, true);

        List<EPackage> pList = getPackages(ecoreResource);
        p = pList.get(0);

        resourceSet.getPackageRegistry().put(p.getNsURI(), p);

        model = resourceSet.getResource(modelURI, true);
        model.unload();
    }


    public List<EvaluationResult> getErrors(Diagnostic diagnostics) {
        //FIXME: severity level is incorrect  (always error); missing rule level
        List<EvaluationResult> results = new ArrayList<>();
        {
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
                        String name = (object.eClass().getEStructuralFeature("name") != null) ? object.eGet(object.eClass().getEStructuralFeature("name")).toString() : null;
                        System.out.println();
                        results.add(new EvaluationResult(childDiagnostic.getSeverity(),
                                matcher.group(1),
                                object.eClass().getName(),
                                (object.eClass().getEStructuralFeature("mRID")!=null)?object.eGet(object.eClass().getEStructuralFeature("mRID")).toString():null,
                                name
                        ));
                    }
                } else {
                    msg = childDiagnostic.getMessage();
                    matcher = pattern.matcher(msg);
                    while (matcher.find()) {
                        results.add(new EvaluationResult(childDiagnostic.getSeverity(),
                                matcher.group(1),
                                object.eClass().getName(),
                                (object.eClass().getEStructuralFeature("mRID")!=null)?object.eGet(object.eClass().getEStructuralFeature("mRID")).toString():null,
                                null
                                ));
                    }
                }
            }
        }
        return results;
    }


    public void printError(List<EvaluationResult> res){
        for (EvaluationResult er: res) {
            if (er.getSeverity() == Diagnostic.ERROR)
                LOGGER.severe(er.toString());
            else if (er.getSeverity() == Diagnostic.WARNING)
                LOGGER.warning(er.toString());
        }
    }


    public void writeExcelReport(Map<String, List<EvaluationResult>> synthesis, File path){
        XLSWriter writer = new XLSWriter();
        writer.writeResults(synthesis, path);

    }

    /**
     * Not used anymore
     * @param diagnostics
     */
    /*
    public static void printErrors(Diagnostic diagnostics){

        if (diagnostics.getSeverity()==Diagnostic.ERROR)
        {
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
                        if (object.eClass().getEStructuralFeature("name")!=null){
                            LOGGER.severe("Rule "+matcher.group(1)+" is violated on "+ object.eClass().getName() +" "+object.eGet(object.eClass().getEStructuralFeature("mRID"))+" ("+object.eGet(object.eClass().getEStructuralFeature("name"))+")");
                        } else{
                            LOGGER.severe("Rule "+matcher.group(1)+" is violated on "+ object.eClass().getName() +" "+object.eGet(object.eClass().getEStructuralFeature("mRID")));
                        }
                    }
                } else {
                    msg = childDiagnostic.getMessage();
                    matcher = pattern.matcher(msg);
                    while (matcher.find()) {
                        LOGGER.severe("The required attribute "+matcher.group(1)+" must be set on  "+ object.eClass().getName() +" "+object.eGet(object.eClass().getEStructuralFeature("mRID")));
                    }
                }
            }
        }
    }*/

    /**
     * Main
     * @param args - path to CGMES files to be validated
     */
    public static void main(String[] args) {

        File where = null;

        if (args.length<1){
            LOGGER.severe("You need to specify the folder containing the CGMES IGMs (one zip per instance)");
            System.exit(0);
        }
        else{
            where = new File(args[0]);
        }

        OCLEvaluator evaluator = new OCLEvaluator();
        Map<String, List<EvaluationResult>> synthesis = evaluator.assessRules(where);
        evaluator.writeExcelReport(synthesis, new File(args[0]+"/QASv3_results.xlsx"));

    }


}
