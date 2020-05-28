package ocl.util;

import com.google.gson.Gson;
import ocl.OCLEvaluator;
import ocl.Profile;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CheckXMLConsistency {

    RuleDescriptionParser parser = new RuleDescriptionParser();
    HashMap<String, RuleDescription> rules = parser.parseRules("config/UMLRestrictionRules.xml");

    private boolean isExcluded = false;
    private String caseName;

    public boolean isExcluded() {
        return isExcluded;
    }

    class IDUniqueness{
        Profile.Type type ;
        String Object;
        String id;
        IDUniqueness(Profile.Type type, String Object, String id){
            this.type = type;
            this.Object = Object;
            this.id=id;
        }
    }

    List<IDUniqueness> idUniquenessList = new ArrayList<>();

    /**
     * @param EQ
     * @param TP
     * @param SSH
     * @param SV
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public CheckXMLConsistency(Profile EQ, Profile TP, Profile SSH, Profile SV, String caseName) throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        this.caseName = caseName;
        IDUniqueness(EQ,TP, SSH, SV);
    }

    /**
     * @param EQ
     * @param TP
     * @param SSH
     * @param SV
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public void IDUniqueness(Profile EQ, Profile TP, Profile SSH, Profile SV) throws IOException, SAXException, ParserConfigurationException, URISyntaxException {
        checkIDUniqueness(EQ);
        checkIDUniqueness(TP);
        checkIDUniqueness(SSH);
        checkIDUniqueness(SV);
        List<EvaluationResult> results = new ArrayList<>();
        String ruleName = "IDuniqueness";
        String severity = rules.get(ruleName) == null ? "UNKOWN" : rules.get(ruleName).getSeverity();
        int level = rules.get(ruleName) == null ? 0 : rules.get(ruleName).getLevel();
        String specificMessage= rules.get(ruleName) == null? "mRID (rdf:ID or rdf:about) not unique within model":null;
        for(IDUniqueness idUniqueness:idUniquenessList){
            EvaluationResult evaluationResult = new EvaluationResult(severity,
                    ruleName,
                    level,
                    idUniqueness.id,
                    idUniqueness.type.toString()+"."+idUniqueness.Object,
                    null, specificMessage
            );
            results.add(evaluationResult);
        }
        writeJsonResults(results);
    }

    /**
     * @param profile
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public void checkIDUniqueness(Profile profile) throws ParserConfigurationException, SAXException, IOException {
        Set ids = new HashSet();
        for (Node node : convertToArray(getNodeList(profile))) {
            if(!StringUtils.isEmpty(node.getLocalName())){
                String id = node.getAttributes().item(0).getNodeValue().replace("#","");
                if (ids.contains(id)){
                    idUniquenessList.add(new IDUniqueness(profile.type,node.getNodeName(), id ));
                    isExcluded = true;
                }
                ids.add(id);
            }
        }
        ids = null;
    }


    public void writeJsonResults(List<EvaluationResult> results) throws IOException, URISyntaxException {

        File cachedir = new File(OCLEvaluator.getConfig().get("cacheDir"));
        OutputStream zipout = Files.newOutputStream(Paths.get(cachedir.getAbsolutePath()+File.separator+caseName+".json.zip"));
        ZipOutputStream zipOutputStream = new ZipOutputStream(zipout);
        String json = new Gson().toJson(results);
        ZipEntry entry_ = new ZipEntry(caseName + ".xmi.json"); // The name
        zipOutputStream.putNextEntry(entry_);
        zipOutputStream.write(json.getBytes());
        zipOutputStream.closeEntry();
        zipOutputStream.close();
    }

    /**
     *
     * @param profile
     * @return
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    private NodeList getNodeList(Profile profile) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document document = null;
        ZipFile zip = new ZipFile(new File(profile.file.getAbsolutePath()));
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            InputStream xmlStream = zip.getInputStream(entry);
            document = documentBuilder.parse(xmlStream);
            xmlStream.close();
        }
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        return nodeList;

    }

    /**
     *
     * @param list
     * @return
     */
    private Node[] convertToArray(NodeList list)
    {
        int length = list.getLength();
        Node[] copy = new Node[length];

        for (int n = 0; n < length; ++n)
            copy[n] = list.item(n);

        return copy;
    }

}
