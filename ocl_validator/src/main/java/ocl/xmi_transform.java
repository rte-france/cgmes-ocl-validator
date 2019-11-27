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

import ocl.util.IOUtils;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class xmi_transform {

    private static Logger LOGGER = null;

    private static TransformerFactory tfactory = TransformerFactory.newInstance();

    private static String ECORE_FILE = "cgmes61970oclModel.ecore";

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER=Logger.getLogger(ocl.OCLEvaluator.class.getName());
    }

    class BDObject{
        Node TPn;
        Node EQn;
        Node CNn;
        Node BVn;
    }
    class object{
        Node node;
    }

    HashMap<String,BDObject> BDObjects = new HashMap<>();
    HashMap<String,object> BVmap = new HashMap<>();

    public HashMap<String, StreamResult> convertData(HashMap<ocl.IGM_CGM_preparation.Profile,List<ocl.IGM_CGM_preparation.Profile>>  IGM_CGM, List<String> defaultBDIds)
            throws TransformerException, IOException, SAXException, ParserConfigurationException {

        HashMap<String,StreamResult> xmi_map = new HashMap<>();
        for(ocl.IGM_CGM_preparation.Profile key : IGM_CGM.keySet()){
            StreamResult resulting_xmi ;
            StreamResult cleaned_sv = cleanProfile(getNameForXslt(key));
            if(key.DepToBeReplaced.size()!=0){
                cleaned_sv = correctDeps(cleaned_sv,key.DepToBeReplaced, defaultBDIds.get(1));
            }
            List<StreamResult> cleaned_eqs = new ArrayList<>();
            List<StreamResult> cleaned_tps = new ArrayList<>();
            List<StreamResult> cleaned_sshs = new ArrayList<>();
            ocl.IGM_CGM_preparation.Profile EQBD = null;
            ocl.IGM_CGM_preparation.Profile TPBD = null;
            List<String> sv_sn = new ArrayList<>();
            List<String> eq_sn = new ArrayList<>();
            List<String> tp_sn = new ArrayList<>();
            List<String> ssh_sn = new ArrayList<>();
            List<String> eqbd_sn = new ArrayList<>();
            List<String> tpbd_sn = new ArrayList<>();

            sv_sn.add(getSimpleNameNoExt(key));

            for(ocl.IGM_CGM_preparation.Profile value : IGM_CGM.get(key)){
                switch (value.type){
                    case EQ:
                        StreamResult cleaned_EQ;
                        cleaned_EQ = cleanProfile(getNameForXslt(value));
                        if(key.DepToBeReplaced.size()!=0){
                            cleaned_EQ = correctDeps(cleaned_EQ,value.DepToBeReplaced,defaultBDIds.get(0));
                        }
                        cleaned_eqs.add(cleaned_EQ);
                        eq_sn.add(getSimpleNameNoExt(value));
                        break;
                    case TP:
                        cleaned_tps.add(cleanProfile(getNameForXslt(value)));
                        tp_sn.add(getSimpleNameNoExt(value));
                        break;
                    case SSH:
                        cleaned_sshs.add(cleanProfile(getNameForXslt(value)));
                        ssh_sn.add(getSimpleNameNoExt(value));
                        break;
                    case other:
                        if(value.file.getName().contains("_EQBD_")){
                            EQBD=value;
                            eqbd_sn.add(getSimpleNameNoExt(value));
                        }
                        else{
                            TPBD=value;
                            tpbd_sn.add(getSimpleNameNoExt(value));
                        }
                        break;
                }
            }

            LOGGER.info("Cleaned:"+key.xml_name);
            Document merged_xml = createMerge(cleaned_sv,cleaned_eqs.get(0),cleaned_sshs.get(0),cleaned_tps.get(0),EQBD,TPBD, getBusinessProcess(key.xml_name));

            LOGGER.info("Merged:"+key.xml_name);
            resulting_xmi = transformToXmi(merged_xml);


            LOGGER.info("Tranformed:"+key.xml_name);

            xmi_map.put(sv_sn.get(0),resulting_xmi);

        }
        return xmi_map;

    }

    private String getBusinessProcess(String name){
        String business = null;
        Pattern pattern = Pattern.compile("\\_(.*?)\\_.*");
        Matcher matcher = pattern.matcher(name);
        while (matcher.find()) {
            for (int l = 1; l <= matcher.groupCount(); l++) {
                business=matcher.group(l);
            }
        }
        return business;
    }

    private Document createMerge(StreamResult sv, StreamResult eq, StreamResult ssh, StreamResult tp, ocl.IGM_CGM_preparation.Profile eqbd, ocl.IGM_CGM_preparation.Profile tpbd, String business) throws ParserConfigurationException, IOException, SAXException, TransformerException {

        NodeList nodeListeq = getNodeList(eq);
        NodeList nodeListssh = getNodeList(ssh);
        NodeList nodeListtp = getNodeList(tp);
        NodeList nodeListsv = getNodeList(sv);
        NodeList nodeListEqBd = getNodeListBD(eqbd.file);
        NodeList nodeListTpBd = getNodeListBD(tpbd.file);
        Document target = nodeListeq.item(0).getOwnerDocument();
        addFullModelInfo(nodeListeq,"EQ",business);
        addFullModelInfo(nodeListtp,"TP",business);
        addFullModelInfo(nodeListssh,"SSH",business);
        addFullModelInfo(nodeListsv,"SV",business);
        addFullModelInfo(nodeListEqBd,"EQBD",null);
        addFullModelInfo(nodeListTpBd,"TPBD",null);
        addObject(target,nodeListssh,"md:FullModel",true);
        addObject(target,nodeListtp,"md:FullModel",true);
        addObject(target,nodeListsv,"md:FullModel",true);
        addObject(target,nodeListEqBd,"md:FullModel",true);
        addObject(target,nodeListTpBd,"md:FullModel",true);
        addObject(target,nodeListEqBd,"cim:GeographicalRegion",false);
        addObject(target,nodeListEqBd,"cim:SubGeographicalRegion",false);
        addObject(target,nodeListEqBd,"entsoe:EnergySchedulingType",false);

        mergeBoundaries(nodeListTpBd,nodeListEqBd);

        HashMap<String,object> eq_ = new HashMap<>();
        for(int i=0; i<nodeListeq.getLength();i++){
            if(nodeListeq.item(i).getLocalName()!=null){
                object my_eq = new object();
                my_eq.node=nodeListeq.item(i);
                eq_.put(nodeListeq.item(i).getAttributes().item(0).getNodeValue(),my_eq);
            }
        }

        HashMap<String,object> declaredBV = new HashMap<>();
        NodeList baseVoltage = nodeListeq.item(0).getOwnerDocument().getElementsByTagName("cim:BaseVoltage");
        for(int i=0; i<baseVoltage.getLength();i++){
            if(baseVoltage.item(i).getLocalName()!=null){
                String id = baseVoltage.item(i).getAttributes().item(0).getNodeValue();
                object object = new object();
                object.node=baseVoltage.item(i);
                declaredBV.put(id,object);
            }
        }

        NodeList transf = nodeListeq.item(0).getOwnerDocument().getElementsByTagNameNS("http://iec.ch/TC57/2013/CIM-schema-cim16#","TransformerEnd.BaseVoltage");

        for(int i=0;i<transf.getLength();i++){
            if(transf.item(i).getLocalName()!=null){
                String id = transf.item(i).getAttributes().item(0).getNodeValue().replace("#","");
                if(!declaredBV.containsKey(id)){
                    if(BVmap.containsKey(id)){
                        Node node = target.importNode(BVmap.get(id).node,true);
                        target.getDocumentElement().appendChild(node);
                        object object = new object();
                        object.node=node;
                        declaredBV.put(id,object);
                    }
                }
            }
        }


        NodeList voltageLevels = nodeListeq.item(0).getOwnerDocument().getElementsByTagName("cim:VoltageLevel");
        for(int i=0;i<voltageLevels.getLength();i++){
            if(voltageLevels.item(i).getLocalName()!=null){
                if(voltageLevels.item(i).hasChildNodes()){
                    for(int c=0;c<voltageLevels.item(i).getChildNodes().getLength();c++){
                        if(voltageLevels.item(i).getChildNodes().item(c).getLocalName()!=null){
                            if(voltageLevels.item(i).getChildNodes().item(c).getLocalName().contains("VoltageLevel.BaseVoltage")){
                                String id  = voltageLevels.item(i).getChildNodes().item(c).getAttributes().item(0).getNodeValue().replace("#","");
                                if(!declaredBV.containsKey(id)){
                                    if(BVmap.containsKey(id)){
                                        Node node = target.importNode(BVmap.get(id).node,true);
                                        target.getDocumentElement().appendChild(node);
                                        object object = new object();
                                        object.node=node;
                                        declaredBV.put(id,object);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        for(int i=0; i<nodeListssh.getLength();i++){
            if(nodeListssh.item(i).getLocalName()!=null){
                String id = nodeListssh.item(i).getAttributes().item(0).getNodeValue().replaceAll("#","");
                if(eq_.containsKey(id) && !nodeListssh.item(i).getLocalName().contains("FullModel")){
                    if(nodeListssh.item(i).hasChildNodes()){
                        for(int c=0; c<nodeListssh.item(i).getChildNodes().getLength();c++){
                            if(nodeListssh.item(i).getChildNodes().item(c).getLocalName()!=null) {
                                Node node = eq_.get(id).node.getOwnerDocument().importNode(nodeListssh.item(i).getChildNodes().item(c),true);
                                eq_.get(id).node.appendChild(node);
                            }
                        }
                    }
                }
            }
        }



        HashMap<String,Node> TPs2add = new HashMap<>();


        for(int i=0;i<nodeListtp.getLength();i++){
            if(nodeListtp.item(i).getLocalName()!=null){
                String id = nodeListtp.item(i).getAttributes().item(0).getNodeValue().replaceAll("#","");
                if(eq_.containsKey(id) && !nodeListtp.item(i).getLocalName().contains("FullModel")){
                    if(nodeListtp.item(i).hasChildNodes()){
                        for(int c=0; c<nodeListtp.item(i).getChildNodes().getLength();c++){
                            if(nodeListtp.item(i).getChildNodes().item(c).getLocalName()!=null) {
                                Node node = eq_.get(id).node.getOwnerDocument().importNode(nodeListtp.item(i).getChildNodes().item(c),true);
                                eq_.get(id).node.appendChild(node);
                                if(nodeListtp.item(i).getChildNodes().item(c).getLocalName().contains("TopologicalNode")){

                                    String tpid = nodeListtp.item(i).getChildNodes().item(c).getAttributes().item(0).getNodeValue().replace("#","");
                                    if(BDObjects.containsKey(tpid)){
                                        TPs2add.put(tpid,BDObjects.get(tpid).TPn);
                                    }
                                }
                            }
                        }
                    }
                }
                else{
                    if(nodeListtp.item(i).getLocalName().contains("TopologicalNode")){
                        TPs2add.put(id,nodeListtp.item(i));
                    }
                   /* else{
                        System.out.println("here");
                        System.out.println(nodeListtp.item(i).getLocalName());
                    }*/
                }

            }
        }


        for(String t: TPs2add.keySet()){
            String bv;
            if(BDObjects.containsKey(t)){
                bv = BDObjects.get(t).BVn.getAttributes().item(0).getNodeValue();
                if(!declaredBV.containsKey(bv)){
                    object object = new object();
                    object.node=BDObjects.get(t).BVn;
                    addNode(target,BDObjects.get(t).BVn);
                    declaredBV.put(bv,object);
                }
                addNode(target,BDObjects.get(t).TPn);
                addNode(target,BDObjects.get(t).EQn);
                addNode(target,BDObjects.get(t).CNn);
            }
            else{
                addNode(target,TPs2add.get(t));

            }
        }

        for(int i=0; i<nodeListsv.getLength();i++){
            if(nodeListsv.item(i).getLocalName()!=null){
                if(!nodeListsv.item(i).getLocalName().contains("FullModel")) {
                    String id = nodeListsv.item(i).getAttributes().item(0).getNodeValue().replace("#","");
                    if(eq_.containsKey(id)){
                        if(nodeListsv.item(i).hasChildNodes()){
                            NodeList childs = nodeListsv.item(i).getChildNodes();
                            for(int c=0; c<childs.getLength();c++){
                                if(childs.item(c).getLocalName()!=null){
                                    Node node = eq_.get(id).node.getOwnerDocument().importNode(childs.item(c),true);
                                    eq_.get(id).node.appendChild(node);
                                }
                            }
                        }
                    }
                    else{
                        addNode(target, nodeListsv.item(i));
                    }
                }
            }
        }


        return target;

    }


    private void addObject(Document doc, NodeList nodeList, String s, boolean begin) throws IOException, TransformerException {
        NodeList nodes = nodeList.item(0).getOwnerDocument().getElementsByTagName(s);
        for(int i=0; i<nodes.getLength();i++){
            if(nodes.item(i).getLocalName()!=null){

                if(begin) {
                    doc.getDocumentElement().insertBefore(doc.importNode(nodes.item(i), true), doc.getDocumentElement().getChildNodes().item(0));
                }
                else{
                    doc.getDocumentElement().appendChild(doc.importNode(nodes.item(i),true));
                }
            }
        }

    }

    private void addFullModelInfo(NodeList nodeList, String modelPart_, String business) throws IOException, TransformerException {
        Document doc = nodeList.item(0).getOwnerDocument();
        NodeList fullmodel = nodeList.item(0).getOwnerDocument().getElementsByTagName("md:FullModel");
        String effectiveDate = new String();
        String sourcingTSO_ = new String();
        String fileVersion_ = new String();
        for(int i=0; i<fullmodel.getLength();i++){
            if(fullmodel.item(i).getLocalName()!=null){
                if(fullmodel.item(i).hasChildNodes()){
                    NodeList childs = fullmodel.item(i).getChildNodes();
                    for(int c=0;c<childs.getLength();c++){
                        if(childs.item(c).getLocalName()!=null){
                            String localName= childs.item(c).getLocalName();
                            if(localName.contains("Model.scenarioTime")){
                                effectiveDate = childs.item(c).getTextContent().replaceAll("[:.-]","");
                            }
                            if(localName.contains("Model.modelingAuthoritySet")){
                                Pattern pattern = Pattern.compile("\\:\\/\\/(.*)\\..*\\/");
                                Matcher matcher = pattern.matcher(childs.item(c).getTextContent());
                                while (matcher.find()) {
                                    for (int l = 1; l <= matcher.groupCount(); l++) {
                                        sourcingTSO_=matcher.group(l);
                                    }
                                }
                            }
                            if(localName.contains("Model.version")){
                                fileVersion_ = childs.item(c).getTextContent();
                            }
                        }
                    }
                }
            }
        }

        Node region = doc.createElement("brlnd:Model.region");
        Node bp = doc.createElement("brlnd:Model.bp");
        Node tool = doc.createElement("brlnd:Model.tool");
        Node rsc = doc.createElement("brlnd:Model.rsc");
        Node effectiveDateTime = doc.createElement("brlnd:Model.effectiveDateTime");
        Node businessProcess = doc.createElement("brlnd:Model.businessProcess");
        Node sourcingTSO = doc.createElement("brlnd:Model.sourcingTSO");
        Node modelPart = doc.createElement("brlnd:Model.modelPart");
        Node fileVersion = doc.createElement("brlnd:Model.fileVersion");
        Node sourcingRSC = doc.createElement("brlnd:Model.sourcingRSC");
        Node syncArea = doc.createElement("brlnd:Model.synchronousArea");

        fullmodel.item(0).appendChild(region);
        fullmodel.item(0).appendChild(bp);
        fullmodel.item(0).appendChild(tool);
        fullmodel.item(0).appendChild(rsc);

        effectiveDateTime.setTextContent(effectiveDate);
        fullmodel.item(0).appendChild(effectiveDateTime);

        if(business!=null) businessProcess.setTextContent(business);
        fullmodel.item(0).appendChild(businessProcess);

        if(sourcingTSO_!=null){
            sourcingTSO.setTextContent(sourcingTSO_);
            if(!modelPart_.contains("BD")){
                sourcingTSO.setTextContent(sourcingTSO_);
            }
            else{
                sourcingTSO.setTextContent("ENTSOE");
            }
        }
        fullmodel.item(0).appendChild(sourcingTSO);

        if(modelPart_!=null) modelPart.setTextContent(modelPart_);
        fullmodel.item(0).appendChild(modelPart);

        fileVersion.setTextContent(fileVersion_);
        fullmodel.item(0).appendChild(fileVersion);

        fullmodel.item(0).appendChild(sourcingRSC);
        fullmodel.item(0).appendChild(syncArea);


    }
    /*private static void printDocument(Document doc, OutputStream out) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");


        transformer.transform(new DOMSource(doc),new StreamResult(new File("/home/chiaramellomar/EMF_meetings/ocl_validator/models/example_marco.xml")));
    }
*/
    private void addNode(Document doc, Node node){
        doc.getDocumentElement().appendChild(doc.importNode(node,true));
    }

    private void mergeBoundaries(NodeList nodeListTpBd, NodeList nodeListEqBd){
        HashMap<String, BDObject> TPObj = new HashMap<>();
        NodeList TpinBD = nodeListTpBd.item(0).getOwnerDocument().getElementsByTagName("cim:TopologicalNode");
        for(int i=0;i<TpinBD.getLength();i++){
            if(TpinBD.item(i).hasChildNodes()){
                for(int c=0; c<TpinBD.item(i).getChildNodes().getLength();c++){
                    if(TpinBD.item(i).getChildNodes().item(c).getLocalName()!=null){
                        if(TpinBD.item(i).getChildNodes().item(c).getLocalName().contains("TopologicalNode.ConnectivityNodeContainer")){
                            String id = TpinBD.item(i).getChildNodes().item(c).getAttributes().item(0).getNodeValue().replace("#","");
                            BDObject bdObject = new BDObject();
                            bdObject.TPn=TpinBD.item(i);
                            TPObj.put(id,bdObject);
                        }
                    }
                }
            }
        }



        NodeList BVlist = nodeListEqBd.item(0).getOwnerDocument().getElementsByTagName("cim:BaseVoltage");
        for(int i=0; i<BVlist.getLength();i++){
            if(BVlist.item(i).getLocalName()!=null){
                String id = BVlist.item(i).getAttributes().item(0).getNodeValue();
                object bv = new object();
                bv.node=BVlist.item(i);
                BVmap.put(id,bv);
            }
        }


        NodeList BDLines = nodeListEqBd.item(0).getOwnerDocument().getElementsByTagName("cim:Line");
        for(int i=0; i<BDLines.getLength();i++){
            if(BDLines.item(i).getLocalName()!=null){
                String id = BDLines.item(i).getAttributes().item(0).getNodeValue();
                if(TPObj.containsKey(id)){
                    String TPid = TPObj.get(id).TPn.getAttributes().item(0).getNodeValue();
                    TPObj.get(id).EQn=BDLines.item(i);
                    BDObjects.put(TPid,TPObj.get(id));
                    NodeList TPChilds = TPObj.get(id).TPn.getChildNodes();
                    for(int c = 0; c<TPChilds.getLength();c++){
                        if(TPChilds.item(c).getLocalName()!=null){
                            if(TPChilds.item(c).getLocalName().contains("TopologicalNode.BaseVoltage")){
                                String bv =TPChilds.item(c).getAttributes().item(0).getNodeValue().replace("#","");
                                if(BVmap.containsKey(bv)){
                                    BDObjects.get(TPid).BVn=BVmap.get(bv).node;
                                }
                            }
                        }
                    }
                }
            }
        }

        NodeList BDCn = nodeListEqBd.item(0).getOwnerDocument().getElementsByTagName("cim:ConnectivityNode");
        for(int i=0; i<BDCn.getLength();i++){
            if(BDCn.item(i).getLocalName()!=null){
                if(BDCn.item(i).hasChildNodes()){
                    for(int c=0; c<BDCn.item(i).getChildNodes().getLength();c++){
                        if(BDCn.item(i).getChildNodes().item(c).getLocalName()!=null){
                            if(BDCn.item(i).getChildNodes().item(c).getLocalName().contains("ConnectivityNode.ConnectivityNodeContainer")){
                                String ideq = BDCn.item(i).getChildNodes().item(c).getAttributes().item(0).getNodeValue().replace("#","");
                                if(TPObj.containsKey(ideq)){
                                    BDObjects.get(TPObj.get(ideq).TPn.getAttributes().item(0).getNodeValue()).CNn=BDCn.item(i);
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private NodeList getNodeList(StreamResult profile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new InputSource(new StringReader(profile.getOutputStream().toString())));
        Element root = document.getDocumentElement();
        NodeList nodeList = root.getChildNodes();
        return  nodeList;
    }
    private NodeList getNodeListBD(File BD) throws ParserConfigurationException, IOException, SAXException {
        ZipFile zip = new ZipFile(new File(BD.getAbsolutePath()));
        Enumeration<? extends ZipEntry> entries = zip.entries();
        NodeList nodeList=null;
        while (entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            InputStream xmlStream = zip.getInputStream(entry);
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(xmlStream);
            Element root = document.getDocumentElement();
            nodeList = root.getChildNodes();
        }
        return nodeList;
    }

    private InputStream getCommander(){
       InputStream commander = this.getClass().getClassLoader().getResourceAsStream("cim16_analyse_igm.xml");
        return  commander;
    }


    private InputStream getXslt(String name){
        InputStream xslt = this.getClass().getClassLoader().getResourceAsStream(name);
        return xslt;
    }

    private String getSimpleNameNoExt(ocl.IGM_CGM_preparation.Profile object){
        int pos = object.file.getName().lastIndexOf(".");
        String name= pos>0 ? object.file.getName().substring(0,pos) : object.file.getName();
        return name;
    }

    private String getNameForXslt(ocl.IGM_CGM_preparation.Profile object){
        String fURI = object.file.toURI().toASCIIString();
        String name = "jar:"+fURI+"!/"+object.xml_name;
        return name;
    }

    private StreamResult correctDeps(StreamResult profile, List<String> ToBeReplaced, String defaultBDId) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(new InputSource(new StringReader(profile.getOutputStream().toString())));
        NodeList nodeList = document.getElementsByTagName("md:Model.DependentOn");
        for (int i =0; i<nodeList.getLength();i++){
            Node attribute = nodeList.item(i).getAttributes().item(0);
           if(ToBeReplaced.stream().anyMatch(p->p.trim().equals(attribute.getNodeValue()))){
                attribute.setNodeValue(defaultBDId);
           }
        }
        DOMSource domSource = new DOMSource(document);
        StreamResult result = new StreamResult(new ByteArrayOutputStream());
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(domSource,result);
        return result;
    }

    private StreamResult cleanProfile(String file_name) throws TransformerException {

        InputStream xslt = getXslt("cim16_clean_data_not_in_profile.xslt");

        Transformer transformer = tfactory.newTransformer(new StreamSource(xslt));

        transformer.setParameter("file",file_name);
        StreamResult result = new StreamResult(new ByteArrayOutputStream());

        transformer.transform(new StreamSource(getCommander()), result);

        return result;

    }



    public StreamResult transformToXmi(Document merged_xml) throws TransformerException {
        //TransformerFactory tfactory = TransformerFactory.newInstance();
        InputStream xslt = getXslt("cim16_create_xmi_from_cimxml.xslt");

        Transformer transformer = tfactory.newTransformer(new StreamSource(xslt));
        transformer.setParameter("merged_xml",merged_xml);

        try {
            transformer.setParameter("ecore",
                    IOUtils.readFile(ocl.OCLEvaluator.getConfig().get("ecore_model"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        transformer.setParameter("ecore_name", ECORE_FILE);

        transformer.setParameter("type", "igm");
        StreamResult result = new StreamResult(new ByteArrayOutputStream());
        transformer.transform(new StreamSource(getCommander()), result);
        return result;
    }

}
