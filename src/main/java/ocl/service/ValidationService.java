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
import ocl.service.util.Configuration;
import ocl.service.util.Priority;
import ocl.service.util.ValidationUtils;
import ocl.util.EvaluationResult;
import ocl.util.RuleDescription;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.IllegalValueException;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ocl.service.util.TransformationUtils.getValidationType;
import static ocl.service.util.TransformationUtils.toInputStream;

public class ValidationService extends BasicService implements ValidationListener{

    private ReportingListener reportingListener = null;

    public ValidationService(){
        super();
        priority = Priority.HIGHEST;

    }

    /**
     * Registers a validation listener
     * @param rl
     */
    public void setListener(ReportingListener rl){
        reportingListener = rl;
    }


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


    public Diagnostic evaluate(InputStream inputStream){

        ResourceSet resourceSet = ValidationUtils.createResourceSet();

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
                String name = (object.eClass().getEStructuralFeature("name") != null) ? (" "+object.eGet(object.eClass().getEStructuralFeature("name"))+" ") : "";
                String id =  (object.eClass().getEStructuralFeature("mRID") != null) ? String.valueOf(object.eGet(object.eClass().getEStructuralFeature("mRID"))) : null;
                logger.severe("Problem with: " + id + name + " (value:" + ive.getValue().toString() + ")");
            }
            if (Configuration.debugMode) exc.printStackTrace();
            else logger.severe(exc.getMessage());
            return null;
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

    @Override
    public void enqueueForValidation(Profile p, Document xmi) {
        String key = p.xml_name;
        logger.info("Validating:\t"+key);

        String type = getValidationType(xmi);


        Future<List<EvaluationResult>> results = executorService.submit(new ValidationTask(xmi));

        // debug: display pool size
        if (Configuration.debugMode)
            printPoolSize();

        try {
            List<EvaluationResult> res = results.get();
            if (res == null)
                logger.severe("Cannot validate:\t" + key);
            else {
                logger.info("Validated:\t" + key);

                reportingListener.enqueueForReporting(p, res, type);
            }

        } catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
        }
    }


    private class ValidationTask extends PriorityCallable{

        private Document xmi;

        private ValidationTask(Document xmi){
            super(priority);
            this.xmi = xmi;
        }


        @Override
        public List<EvaluationResult> call() {

            try {
                InputStream xmlStream = toInputStream(xmi);

                Diagnostic diagnostic = evaluate(xmlStream);

                if (diagnostic == null)
                    return null;
                List<EvaluationResult> res = getErrors(diagnostic, ValidationUtils.rules);
                xmlStream.close();
                xmlStream = null; // to free memory

                return res;
            } catch (IOException | TransformerException e){
                e.printStackTrace();
                //FIXME: ???
            }
            return new ArrayList<>();
        }
    }
}
