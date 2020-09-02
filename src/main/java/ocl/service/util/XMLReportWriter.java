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

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import ocl.Profile;
import ocl.service.reporting.xml.CGM;
import ocl.service.reporting.xml.CGMValidationParameters;
import ocl.service.reporting.xml.IGM;
import ocl.service.reporting.xml.IGMValidationParameters;
import ocl.service.reporting.xml.ObjectFactory;
import ocl.service.reporting.xml.QAReport;
import ocl.service.reporting.xml.QualityIndicator;
import ocl.service.reporting.xml.RuleViolation;
import ocl.service.reporting.xml.Severity;
import ocl.util.EvaluationResult;
import ocl.util.IOUtils;
import ocl.util.RuleDescription;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static ocl.service.reporting.xml.IGM.*;

public class XMLReportWriter implements ReportWriter {

    // NB. to add generated sources in Intellij: right click on project folder, select maven and generate sources and update folders

    private static String NAMESPACE_QAR = "http://entsoe.eu/checks";

    private JAXBContext context;
    private Marshaller marshaller;
    private ObjectFactory factory;
    private String validationType;

    public XMLReportWriter(String validationType) {
        try {
            this.validationType = validationType;
            context = JAXBContext.newInstance(QAReport.class);
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            factory = new ObjectFactory();
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void writeSingleReport(Profile p, List<EvaluationResult> results, HashMap<String, RuleDescription> rules, Path path) {
        if (TransformationUtils.IGM.equalsIgnoreCase(validationType)){
            writeSingleIGMReport(p, results, rules, path);
        } else
            writeSingleCGMReport(p, results, rules, path);
    }


    public void writeSingleIGMReport(Profile p, List<EvaluationResult> results, HashMap<String, RuleDescription> rules, Path path) {

        QAReport report = factory.createQAReport();
        try {

            // created
            Instant instant = Instant.now();
            XMLGregorianCalendar instant2 = DatatypeFactory.newInstance().newXMLGregorianCalendar(instant.toString());
            report.setCreated(instant2);
            // scheme version
            report.setSchemeVersion(new BigDecimal(3));
            // service provider
            report.setServiceProvider("QoCDCv3.2 prototype");

            // validation parameters
            IGMValidationParameters igmValidationParameters = factory.createIGMValidationParameters();

            // set report for IGM
            IGM igm = factory.createIGM();
            String[] meta = IOUtils.trimExtension(p.xml_name).split("_");             //20191009T0930Z_1D_EMS_SV_000.zip
            String i = meta[0].substring(0,4)+"-"+meta[0].substring(4,6)+"-"+meta[0].substring(6,11)+
                    ":" + meta[0].substring(11,13) + ":00.000" + meta[0].substring(13);//FIXME: not clean
            XMLGregorianCalendar scenarioTime = DatatypeFactory.newInstance().newXMLGregorianCalendar(i);
            igm.setCreated(scenarioTime); // TODO: shall be extracted from header
            igm.setScenarioTime(scenarioTime);
            igm.setProcessType(meta[1]);
            igm.setTso(meta[2]);
            igm.setVersion(new Integer(meta[4]));
            igm.setValidationParameters(igmValidationParameters);
            // resource
            igm.getResource().add(p.id);
            igm.getResource().addAll(p.depOn);
            List<RuleViolation> violatedRules = getViolatedRules(p, results, rules);
            igm.getRuleViolation().addAll(violatedRules);
            igm.setQualityIndicator(getQualityIndicator(results));
            report.getIGM().add(igm);

            // create report
            NamespacePrefixMapper mapper = new NamespacePrefixMapper() {
                public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                    if (NAMESPACE_QAR.equals(namespaceUri) && !requirePrefix)
                        return "";
                    return "ns";
                }
            };
            marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mapper);

            Path outputFile = path.resolve("QAR_"+p.xml_name);
            marshaller.marshal(new JAXBElement<QAReport>(new QName(NAMESPACE_QAR, "QAReport"), QAReport.class, report), outputFile.toFile());

            //FIXME: solve problems with namespace!
        } catch (Exception e) {
            logger.severe("Cannot create xml report for :" + p.xml_name);
            e.printStackTrace();
        }
    }

    public void writeSingleCGMReport(Profile p, List<EvaluationResult> results, HashMap<String, RuleDescription> rules, Path path) {

        QAReport report = factory.createQAReport();
        try {

            // created
            Instant instant = Instant.now();
            XMLGregorianCalendar instant2 = DatatypeFactory.newInstance().newXMLGregorianCalendar(instant.toString());
            report.setCreated(instant2);
            // scheme version
            report.setSchemeVersion(new BigDecimal(3));
            // service provider
            report.setServiceProvider("QoCDCv3.2 prototype");

            // validation parameters
            CGMValidationParameters cgmValidationParameters = factory.createCGMValidationParameters();

            // set report for CGM
            CGM cgm = factory.createCGM();
            String[] meta = IOUtils.trimExtension(p.xml_name).split("_");             //20191009T0930Z_1D_EMS_SV_000.zip
            String i = meta[0].substring(0,4)+"-"+meta[0].substring(4,6)+"-"+meta[0].substring(6,11)+
                    ":" + meta[0].substring(11,13) + ":00.000" + meta[0].substring(13);//FIXME: not clean
            XMLGregorianCalendar scenarioTime = DatatypeFactory.newInstance().newXMLGregorianCalendar(i);
            cgm.setCreated(scenarioTime); // TODO: shall be extracted from header
            cgm.setScenarioTime(scenarioTime);
            cgm.setProcessType(meta[1]);
            cgm.setTso(meta[2]);
            cgm.setVersion(new Integer(meta[4]));
            cgm.setValidationParameters(cgmValidationParameters);
            // resource
            cgm.setResource(p.id);
            // add IGMs info
            //FIXME: check content
            for (String s: p.depOn) {
                IGM igm = factory.createIGM();
                igm.getResource().add(s);
                cgm.getIGM().add(igm);
            }
            // add violated rules
            List<RuleViolation> violatedRules = getViolatedRules(p, results, rules);
            cgm.getRuleViolation().addAll(violatedRules);
            cgm.setQualityIndicator(getQualityIndicator(results));
            report.getCGM().add(cgm);

            // create report
            NamespacePrefixMapper mapper = new NamespacePrefixMapper() {
                public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                    if (NAMESPACE_QAR.equals(namespaceUri) && !requirePrefix)
                        return "";
                    return "ns";
                }
            };
            marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mapper);

            Path outputFile = path.resolve("QAR_"+p.xml_name);
            marshaller.marshal(new JAXBElement<QAReport>(new QName(NAMESPACE_QAR, "QAReport"), QAReport.class, report), outputFile.toFile());

            //FIXME: solve problems with namespace!
        } catch (Exception e) {
            logger.severe("Cannot create xml report for :" + p.xml_name);
            e.printStackTrace();
        }
    }

    private List<RuleViolation> getViolatedRules(Profile p,  List<EvaluationResult> results, HashMap<String, RuleDescription> rules){
        List<RuleViolation> violatedRules = new ArrayList<>();
        for (EvaluationResult res : results){
            String infringedRule = res.getRule();
            String severity = "WARNING";
            String message = "";
            if (rules.get(infringedRule) != null) {
                severity = rules.get(infringedRule).getSeverity();
                message = res.getSpecificMessage() != null ? rules.get(infringedRule).getMessage() + " " + res.getSpecificMessage() : rules.get(infringedRule).getMessage();
                if (message==null) message="";
            }
            int level = (res.getLevel() == null)?0:res.getLevel();
            String id = (res.getId()==null)?"":res.getId();
            String name = (res.getName()==null)?"":res.getName();
            RuleViolation ruleViolation = factory.createRuleViolation();
            ruleViolation.setValidationLevel(level);
            ruleViolation.setRuleId(infringedRule);
            ruleViolation.setSeverity(Severity.fromValue(severity));
            ruleViolation.setMessage(message);
            ruleViolation.setElementId(id);
            ruleViolation.setElementName(name);
            violatedRules.add(ruleViolation);
        }
        return violatedRules;
    }


    private QualityIndicator getQualityIndicator(List<EvaluationResult> results) {
        
        int nbWarnings = 0;
        int nbErrors = 0;

        for (EvaluationResult res : results) {
            switch (res.getSeverity()){
                case "WARNING":
                    nbWarnings ++;
                    break;
                case "ERROR":
                    nbErrors ++;
                    break;
            }
        }

        if (nbErrors>0)
            return QualityIndicator.REJECTED_OCL_RULE_VIOLATION_S;
        if (nbWarnings>0)
            return QualityIndicator.WARNING_NON_FATAL_INCONSISTENCIES;

        return QualityIndicator.VALID;
    }
}



