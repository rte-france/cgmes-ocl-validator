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

import ocl.util.RuleDescription;
import ocl.util.RuleDescriptionParser;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.pivot.model.OCLstdlib;
import org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class ValidationUtils {

    static Logger logger = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        logger = Logger.getLogger(ValidationUtils.class.getName());

    }

    private static Resource ecoreResource;
    public static ResourceSet resourceSet;
    private static URI mmURI;
    public static List<EPackage> myPList = new ArrayList<>();

    public static HashMap<String, RuleDescription> rules;

    static{
        try {
            RuleDescriptionParser parser = new RuleDescriptionParser();
            rules = parser.parseRules("config/UMLRestrictionRules.xml");
            parser = null;
        } catch (ParserConfigurationException | IOException | SAXException e){
            e.printStackTrace();
        }

        resourceSet = new ResourceSetImpl();
        prepareValidator(Configuration.configs.get("ecore_model"));
    }


    public static void prepareValidator(String ecore_model){
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
}
