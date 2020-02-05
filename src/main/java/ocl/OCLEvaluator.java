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

import ocl.util.*;
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
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            HashMap<String, String> configs = getConfig();
            prepareValidator(configs.get("basic_model"), configs.get("ecore_model"));
        } catch (IOException io){
            io.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }


    public OCLEvaluator(){

    }

    /**
     *
     * @return Diagnostic object
     */
    public synchronized Diagnostic evaluate(StreamResult xmi){
        InputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(xmi.getOutputStream().toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        HashMap<String, Boolean> options = new HashMap<>();
        options.put(XMLResource.OPTION_DEFER_IDREF_RESOLUTION,true);
        try {
            model.load(inputStream,options);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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


    /**
     *
     * @param where
     * @return
     */
    public Map<String, List<EvaluationResult>> assessRules(File where, HashMap<String, RuleDescription> rules){
        Map<String, List<EvaluationResult>> results = new HashMap<>();

        IGM_CGM_preparation my_prep = new IGM_CGM_preparation();
        xmi_transform my_transf = new xmi_transform();

        HashMap<String, StreamResult> xmi_list = new HashMap<>();

        try {
            my_prep.readZip(where);
            LOGGER.info("Reordering done!");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        try {
            xmi_list= my_transf.convertData(my_prep.IGM_CGM, my_prep.defaultBDIds);
            LOGGER.info("XMI transformation done!");
        } catch (TransformerException | ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException | URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        HashMap<String, Integer> ruleLevels = my_transf.getRuleLevels();

        my_prep = null; // save memory
        my_transf = null ; // save memory
        

        OCLEvaluator evaluator = new OCLEvaluator();
        Diagnostic diagnostics;
        LOGGER.info("Validator ready");
        for(String key : xmi_list.keySet()){
            LOGGER.info("");
            LOGGER.info("************");
            LOGGER.info("Validating IGM "+key);
            LOGGER.info("************");

            diagnostics = evaluator.evaluate( xmi_list.get(key));
            if (diagnostics==null) {
                LOGGER.severe("Problem with input xmi for model: " + key);
            } else {
                List<EvaluationResult> res = getErrors(diagnostics, rules);
                printError(res);
                results.put(key, res);

                if (!res.isEmpty())
                    LOGGER.severe("ERROR: Invalid constraints for model: " + key);
                else
                    LOGGER.info("All constraints are valid for model:" + key);
            }
            xmi_list.put(key,null); // to free memory
        }

        return results;
    }


    /**
     * Inizializes configurations
     * @return
     * @throws IOException
     */
    public static HashMap<String,String> getConfig() throws IOException, URISyntaxException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+File.separator+"config.properties");
        Properties properties = new Properties();
        properties.load(config);
        String basic_model = properties.getProperty("basic_model");
        String ecore_model = properties.getProperty("ecore_model");

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


    /**
     * Preparation of the validator
     * @param basic_model
     * @param ecore_model
     */
    public static void prepareValidator(String basic_model, String ecore_model){
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

    /**
     *
     * @param diagnostics
     * @return
     */
    public List<EvaluationResult> getErrors(Diagnostic diagnostics, HashMap<String, RuleDescription> rules) {

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
                    String severity = rules.get(ruleName)==null?"UNKOWN":rules.get(ruleName).getSeverity();
                    int level = rules.get(ruleName)==null?0:rules.get(ruleName).getLevel();
                    results.add(new EvaluationResult(severity,
                            ruleName,
                            level,
                            object.eClass().getName(),
                            (object.eClass().getEStructuralFeature("mRID")!=null)?String.valueOf(object.eGet(object.eClass().getEStructuralFeature("mRID"))) : null,
                            name
                    ));
                }
            } else {
                msg = childDiagnostic.getMessage();
                matcher = pattern.matcher(msg);
                while (matcher.find()) {
                    String ruleName = matcher.group(1);
                    String severity = rules.get(ruleName)==null?"UNKOWN":rules.get(ruleName).getSeverity();
                    int level = rules.get(ruleName)==null?0:rules.get(ruleName).getLevel();
                    results.add(new EvaluationResult(severity,
                            ruleName,
                            level,
                            object.eClass().getName(),
                            (object.eClass().getEStructuralFeature("mRID")!=null)?String.valueOf(object.eGet(object.eClass().getEStructuralFeature("mRID"))) : null,
                            null
                    ));
                }
            }
        }
        return results;
    }


    private void printError(List<EvaluationResult> res){
        for (EvaluationResult er: res) {
            if (er.getSeverity().equalsIgnoreCase("ERROR"))
                LOGGER.severe(er.toString());
            else if (er.getSeverity().equalsIgnoreCase("WARNING"))
                LOGGER.warning(er.toString());
            else LOGGER.info(er.toString());
        }
    }


    private void writeExcelReport(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, File path){
        XLSWriter writer = new XLSWriter();
        writer.writeResults(synthesis, rules, path);

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
        Locale.setDefault(new Locale("en", "EN"));
        File where = null;

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
            Map<String, List<EvaluationResult>> synthesis = evaluator.assessRules(where, rules);

            // write report
            evaluator.writeExcelReport(synthesis, rules, new File(args[0]+"/QASv3_results.xlsx"));


        } catch (ParserConfigurationException | IOException | SAXException e){
            e.printStackTrace();
            System.exit(-1);
        }

    }


}
