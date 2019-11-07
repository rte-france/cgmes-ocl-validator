package ocl;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class OCLEvaluator {

    private static Resource ecoreResource;
    private static ResourceSet resourceSet = null;


    private static URI mmURI;
    private static URI modelURI;
    private static Resource model;
    private static EPackage p;


    private static boolean valid;

    public OCLEvaluator(){

        ecoreResource=null;
        mmURI=null;
        modelURI=null;
        model=null;
        resourceSet = new ResourceSetImpl();
        p=null;
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

        printErrors(diagnostics);

        model.unload();



        return diagnostics;
    }

    /*
     * load ecore resource
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



    public static void main(String[] args) throws IOException {


        File where = null;
        HashMap<String,String> configs = get_config();


        if (args.length<1){
            System.out.println("You need to specify the folder containing the CGMES IGMs (one zip per instance)");
            System.exit(0);
        }
        else{
            where = new File(args[0]);


        }



        ocl.IGM_CGM_preparation my_prep = new ocl.IGM_CGM_preparation();
        ocl.xmi_transform my_transf = new ocl.xmi_transform();

        HashMap<String, StreamResult> xmi_list = new HashMap<>();

        try {
            my_prep.read_zip(where);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("reordering done!");
        try {

            xmi_list= my_transf.convert_data(my_prep.IGM_CGM);

        } catch (TransformerException e) {
            e.printStackTrace();
        }
        System.out.println("xmi transf done!");

        OCLEvaluator evaluator = new OCLEvaluator();
        prepare_validator(configs.get("basic_model"),configs.get("ecore_model"));
        Diagnostic diagnostics;
        System.out.println("validator ready");
        for(String key : xmi_list.keySet()){
            System.out.println("************");
            System.out.println("Validating IGM "+key);
            System.out.println("************");




            diagnostics = evaluator.evaluate( xmi_list.get(key));
            valid = true;

            if(!diagnostics.getChildren().isEmpty()){
                valid = false;
            }

            if(valid){
                System.out.println("All constraints for model are valid!");
            }
            else{
                System.out.println("ERROR: Constraints are invalid!");
            }
        }




    }

    public static HashMap<String,String> get_config() throws IOException {
        HashMap<String,String> configs =  new HashMap<>();
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG")+"/config.properties");
        Properties properties = new Properties();
        properties.load(config);
        if (properties.getProperty("basic_model")!=null && properties.getProperty("ecore_model")!=null){
            configs.put("basic_model", properties.getProperty("basic_model"));
            configs.put("ecore_model", properties.getProperty("ecore_model"));
        }
        else{

            System.out.println("Variable basic_model or ecore_model are missing from properties file");
            System.exit(0);
        }

        return configs;
    }


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


    public static void printErrors(Diagnostic diagnostics){
        if (diagnostics.getSeverity()==Diagnostic.ERROR){
            for (Iterator<Diagnostic> i = diagnostics.getChildren().iterator(); i.hasNext();){
                Diagnostic childDiagnostic = i.next();
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
                            System.out.println("Rule "+matcher.group(1)+" is violated on "+ object.eClass().getName() +" "+object.eGet(object.eClass().getEStructuralFeature("mRID"))+" ("+object.eGet(object.eClass().getEStructuralFeature("name"))+")");
                        }
                        else{
                            System.out.println("Rule "+matcher.group(1)+" is violated on "+ object.eClass().getName() +" "+object.eGet(object.eClass().getEStructuralFeature("mRID")));
                        }



                    }


                }

                else {

                    msg = childDiagnostic.getMessage();
                    matcher = pattern.matcher(msg);
                    while (matcher.find()) {
                        System.out.println("The required attribute "+matcher.group(1)+" must be set on  "+ object.eClass().getName() +" "+object.eGet(object.eClass().getEStructuralFeature("mRID")));
                    }

                }
            }
        }
    }



}
