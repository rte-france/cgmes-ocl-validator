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
package ocl.util;

import com.google.gson.Gson;
import ocl.Profile;

import ocl.service.util.Configuration;
import ocl.service.util.ValidationUtils;
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CheckXMLConsistency {

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
        String severity = ValidationUtils.rules.get(ruleName) == null ? "UNKOWN" : ValidationUtils.rules.get(ruleName).getSeverity();
        int level = ValidationUtils.rules.get(ruleName) == null ? 0 : ValidationUtils.rules.get(ruleName).getLevel();
        String specificMessage= ValidationUtils.rules.get(ruleName) == null? "mRID (rdf:ID or rdf:about) not unique within model":null;
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
        if(results.size()!=0)
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
        OutputStream zipout = Files.newOutputStream(Paths.get(Configuration.cacheDir.toAbsolutePath().toString()+File.separator+caseName+".json.zip"));
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
