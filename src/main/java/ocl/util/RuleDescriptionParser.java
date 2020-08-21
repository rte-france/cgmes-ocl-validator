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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class RuleDescriptionParser {

    private static String CGMBP = "http://entsoe.eu/CIM/Extensions/CGM-BP/2020#";
    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER=Logger.getLogger(RuleDescriptionParser.class.getName());
    }


    public HashMap<String, RuleDescription> parseRules(String inputFile) throws ParserConfigurationException, IOException, SAXException {

        HashMap<String, RuleDescription> rules = new HashMap<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        if(!new File(inputFile).exists()){
            LOGGER.severe("UMLRestrictionRules.xml missing in "+ System.getenv("VALIDATOR_CONFIG")+" !");
            System.exit(0);
        }
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();

        NodeList nList = doc.getElementsByTagName("cgmbp:UMLRestrictionRule");

        for (int temp = 0; temp < nList.getLength(); temp++) {
            RuleDescription rd = new RuleDescription();

            Node nNode = nList.item(temp);

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                rd.setName(eElement.getElementsByTagName("cgmbp:UMLRestrictionRule.name")
                        .item(0)
                        .getTextContent());
                rd.setSeverity(eElement.getElementsByTagName("cgmbp:UMLRestrictionRule.severity")
                        .item(0)
                        .getTextContent());
                rd.setLevel(new Integer(eElement.getElementsByTagName("cgmbp:UMLRestrictionRule.level")
                        .item(0)
                        .getTextContent()));
                rd.setDescription(eElement.getElementsByTagName("cgmbp:UMLRestrictionRule.description")
                        .item(0)
                        .getTextContent());
                Node nm = eElement.getElementsByTagName("cgmbp:UMLRestrictionRule.message")
                        .item(0);
                if (nm == null) rd.setMessage(""); else rd.setMessage(nm.getTextContent());
            }

            rules.put(rd.getName(), rd);

        }

        return rules;
    }


    public static void main(String[] args) {
        try {
            RuleDescriptionParser parser = new RuleDescriptionParser();
            HashMap<String, RuleDescription> rules = parser.parseRules("config/UMLRestrictionRules.xml");

            for (String k : rules.keySet()){
                System.out.println(rules.get(k));
            }

        } catch (ParserConfigurationException | IOException | SAXException e){
            e.printStackTrace();
        }

    }
}
