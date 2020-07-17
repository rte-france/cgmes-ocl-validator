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
package ocl.service;

import ocl.Profile;
import ocl.util.EvaluationResult;
import ocl.util.IOUtils;
import ocl.util.RuleDescription;
import ocl.util.RuleDescriptionParser;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.TreeIterator;
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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidationService extends BasicService implements ValidationListener{

    private ReportingListener reportingListener = null;

    private static Resource ecoreResource;
    private static ResourceSet resourceSet;

    private static URI mmURI;

    private static List<EPackage> myPList = new ArrayList<>();

    static{
        try {
            resourceSet = new ResourceSetImpl();
            HashMap<String, String> configs = getConfig();
            prepareValidator(configs.get("ecore_model"));
        } catch (IOException | URISyntaxException io){
            io.printStackTrace();
        }
    }


    public static HashMap<String, RuleDescription> rules;

    public ValidationService(){
        super();

        try {
            RuleDescriptionParser parser = new RuleDescriptionParser();
            rules = parser.parseRules("config/UMLRestrictionRules.xml");
            parser = null;
        } catch (ParserConfigurationException | IOException | SAXException e){
            e.printStackTrace();
        }
    }

    /**
     * Registers a validation listener
     * @param rl
     */
    public void setListener(ReportingListener rl){
        reportingListener = rl;
    }

    private  static HashMap<String,String> getConfig() throws IOException, URISyntaxException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+ File.separator+"config.properties");
        Properties properties = new Properties();
        properties.load(config);
        String basic_model = properties.getProperty("basic_model");
        String ecore_model = properties.getProperty("ecore_model");

        if (basic_model!=null && ecore_model!=null){
            configs.put("basic_model", IOUtils.resolveEnvVars(basic_model));
            configs.put("ecore_model", IOUtils.resolveEnvVars(ecore_model));
        }
        else{
            logger.severe("Variable basic_model or ecore_model are missing from properties file");
            System.exit(0);
        }

        return configs;
    }

    private static void prepareValidator(String ecore_model){
        CompleteOCLStandaloneSetup.doSetup();

        OCLstdlib.install();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        mmURI = URI.createFileURI(new File(ecore_model).getAbsolutePath());


        try {
            ecoreResource = resourceSet.getResource(mmURI, true);
        }
        catch (Exception e ){
            logger.severe("Ecore file missing in "+ System.getenv("VALIDATOR_CONFIG")+" !");
            System.exit(0);
        }

        myPList = getPackages(ecoreResource);

    }


    private static List<EPackage> getPackages(Resource r){
        ArrayList<EPackage> pList = new ArrayList<EPackage>();
        if (r.getContents() != null)
            for (EObject obj : r.getContents())
                if (obj instanceof EPackage) {
                    pList.add((EPackage)obj);
                }
        TreeIterator<EObject> test = r.getAllContents();
        while(test.hasNext()){
            EObject t = test.next();
            if(t instanceof EPackage)
                pList.add((EPackage)t);
        }
        return pList;
    }

    public static ResourceSet createResourceSet(){

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());

        for(EPackage ePackage : myPList){
            resourceSet.getPackageRegistry().put(ePackage.getNsURI(),ePackage);
        }

        return resourceSet;
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

    private boolean excludeRuleName(String ruleName){
        // MessageType introduced in ecore file but not managed on Java side
        return "MessageType".equalsIgnoreCase(ruleName);
    }


    private Diagnostic evaluate(InputStream inputStream){

        ResourceSet resourceSet = createResourceSet();

        HashMap<String, Boolean> options = new HashMap<>();
        options.put(XMLResource.OPTION_DEFER_IDREF_RESOLUTION,true);
        UUID uuid = UUID.randomUUID();
        Resource model = resourceSet.createResource(URI.createURI(uuid.toString()));
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

        rootObject = null;
        take = null;
        inputStream = null;
        resourceSet = null;

        return diagnostics;
    }


    @Override
    public void run() {

    }

    /***************************************************************************************************************/

    private InputStream toInputStream(Document doc) throws TransformerException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Source xmlSource = new DOMSource(doc);
        Result outputTarget = new StreamResult(outputStream);
        TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
        InputStream is = new ByteArrayInputStream(outputStream.toByteArray());
        return is;
    }


    @Override
    public void enqueueForValidation(Profile p, Document xmi) {
        String key = p.xml_name;
        logger.info("Validating:\t"+key);

        Future<List<EvaluationResult>> results = executorService.submit(new ValidationTask(key, xmi));

        //FIXME
        printPoolSize();

        try {
            List<EvaluationResult> res = results.get();
            logger.info("Validated:\t" + key + res.size());

            reportingListener.enqueueForReporting(p, results.get());

        } catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
    }


    private class ValidationTask implements Callable{

        private String key;
        private Document xmi;

        private ValidationTask(String key, Document xmi){
            this.key = key;
            this.xmi = xmi;
        }


        @Override
        public List<EvaluationResult> call() {

            try {
                InputStream xmlStream = toInputStream(xmi);

                Diagnostic diagnostic = evaluate(xmlStream);

                List<EvaluationResult> res = getErrors(diagnostic, rules);

                return res;
            } catch (TransformerException e){
                //FIXME: ???
            }
            return new ArrayList<>();
        }
    }
}
