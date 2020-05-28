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

import ocl.util.CheckXMLConsistency;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.*;

import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


import javax.xml.transform.*;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 */
class XMITransformation {

    private static Logger LOGGER = null;

    private static TransformerFactory tfactory = TransformerFactory.newInstance();

    private static String ECORE_FILE = "cgmes61970oclModel.ecore";

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER=Logger.getLogger(ocl.OCLEvaluator.class.getName());
    }

    private class BDObject{
        Node TPn;
        Node EQn;
        Node CNn;
        Node BVn;
    }

    private class BDExtensions{
        HashMap<String,Node> CimProfiles = new HashMap<>();
        HashMap<String,Node> ProcessType = new HashMap<>();
        HashMap<String,Node> ToBeAdded = new HashMap<>();
        HashMap<String,Node> ModelingAuthority = new HashMap<>();
        HashMap<String,Node> GeographicalRegionIds = new HashMap<>();
        HashMap<String,Node> GeographicalRegionEIC = new HashMap<>();
    }

    private Set<String> classes = new HashSet<>();
    private HashMap<String, Integer> ruleLevels= new HashMap<>();
    private HashMap<String,BDObject> BDObjects = new HashMap<>();
    private HashMap<String,Node> BVmap = new HashMap<>();
    private HashMap<String,String> authExt = new HashMap<>();

    private HashMap<String,String> xmiXmlns = new HashMap<>();

    private boolean isNb = false;
    private boolean isShortCircuit = false;
    private BDExtensions bdExtensions = new BDExtensions();
    private HashMap<String,String> brlndType = new HashMap<>();

    HashMap<String, Integer> getRuleLevels(){
        return ruleLevels;
    }


    /**
     *
     * @param IGM_CGM
     * @param defaultBDIds
     * @return
     * @throws TransformerException
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
     HashMap<String, Document> convertData(HashMap<Profile,List<Profile>>  IGM_CGM, List<String> defaultBDIds)
            throws TransformerException, IOException, SAXException, ParserConfigurationException, URISyntaxException {

        setAuthExt();
        parseEcore();
        HashMap<String,Document> xmi_map = new HashMap<>();
        parseBdExtensions();
        brlndType.put("EQ","EqModel");
        brlndType.put("TP","TpModel");
        brlndType.put("SSH","SshModel");
        brlndType.put("SV","SvModel");


        IGM_CGM.entrySet().parallelStream().forEach(entry->{
            Profile key = entry.getKey();
            try {
                    Document resulting_xmi ;

                    Profile EQBD = null;
                    Profile TPBD = null;
                    List<String> sv_sn = new ArrayList<>();


                    Profile EQ = null;
                    Profile SSH = null;
                    Profile TP = null;

                    sv_sn.add(getSimpleNameNoExt(key));

                    for(Profile value : IGM_CGM.get(key)){
                        switch (value.type){
                            case EQ:
                                EQ = value;
                                break;
                            case TP:
                                TP = value;
                                break;
                            case SSH:
                                SSH = value;
                                break;
                            case other:
                                if(value.file.getName().contains("_EQBD_")){
                                    EQBD=value;
                                } else{
                                    TPBD=value;
                                }
                                break;
                        }
                    }
                    CheckXMLConsistency xmlConsistency = new CheckXMLConsistency(EQ,TP,SSH,key, sv_sn.get(0));

                    if(!xmlConsistency.isExcluded()){
                        Document merged_xml = createMerge(EQBD,TPBD, getBusinessProcess(key.xml_name), key, EQ, SSH, TP,defaultBDIds);
                        resulting_xmi = createXmi(merged_xml);
                        LOGGER.info("Transformed:"+key.xml_name);

                        xmi_map.put(sv_sn.get(0),resulting_xmi);
                    }
                    else{
                        LOGGER.info("Xmi not created for "+key.xml_name+". Probably basic xml/rdf consistency is broken.");
                    }




            } catch (Exception e){
                    LOGGER.severe("Error in processing: "+ key.xml_name);
                    throw new RuntimeException(e);
            }
        });



        return xmi_map;

    }

    /**
     *
     * @param name
     * @return
     */
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

    private NodeList getNodeList(File file) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(file);
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        return nodeList;
    }



    /**
     *
     * @param eqbd
     * @param tpbd
     * @param business
     * @param SV
     * @param EQ
     * @param SSH
     * @param TP
     * @param defaultBDIds
     * @return
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws TransformerException
     */
    private synchronized Document createMerge(Profile eqbd, Profile tpbd, String business, Profile SV, Profile EQ, Profile SSH, Profile TP, List<String> defaultBDIds) throws ParserConfigurationException, IOException, SAXException, TransformerException {




        NodeList nodeListeq = correctDeps(getNodeList(EQ), EQ.DepToBeReplaced,defaultBDIds.get(0));

        NodeList nodeListssh = getNodeList(SSH);
        NodeList nodeListtp = getNodeList(TP);
        NodeList nodeListsv = correctDeps(getNodeList(SV), SV.DepToBeReplaced,defaultBDIds.get(1));
        NodeList nodeListEqBd = getNodeList(eqbd);
        NodeList nodeListTpBd = getNodeList(tpbd);
        isNb = isNb(nodeListeq);
        Document target = nodeListeq.item(0).getOwnerDocument();
        target.getDocumentElement().setAttributeNS("http://www.w3.org/2000/xmlns/","xmlns:brlnd","http://brolunda.com/ecore-converter#" );
        target.getDocumentElement().setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:cgmbp","http://entsoe.eu/CIM/Extensions/CGM-BP/2020#");

        addFullModelInfo(target,"EQ",business);
        addFullModelInfo(nodeListtp.item(0).getOwnerDocument(),"TP",business);
        addFullModelInfo(nodeListssh.item(0).getOwnerDocument(),"SSH",business);
        addFullModelInfo(nodeListsv.item(0).getOwnerDocument(),"SV",business);
        addFullModelInfo(nodeListEqBd.item(0).getOwnerDocument(),"EQBD",null);
        addFullModelInfo(nodeListTpBd.item(0).getOwnerDocument(),"TPBD",null);
        
        mergeBoundaries(nodeListTpBd,nodeListEqBd);

        HashMap<String,Node> eq_ = new HashMap<>();
        boolean isusingCN = false;
        isusingCN = (convertToArray(target.getElementsByTagNameNS(authExt.get("cim"),"Terminal.ConnectivityNode")).length!=0);

        Node[] EQnodes = convertToArray(nodeListeq);
        for (Node eQnode : EQnodes) {
            if(!StringUtils.isEmpty(eQnode.getLocalName())){
                if(!StringUtils.contains(eQnode.getLocalName(),"FullModel")){
                    addModelBrlndDependency(eQnode,EQ.type,EQ.id,target);
                }
                eq_.put(eQnode.getAttributes().item(0).getNodeValue(),eQnode);
            }
        }


        addObject(target,nodeListssh,"md:FullModel",true);
        addObject(target,nodeListtp,"md:FullModel",true);
        addObject(target,nodeListsv,"md:FullModel",true);
        addObject(target,nodeListEqBd,"md:FullModel",true);
        addObject(target,nodeListTpBd,"md:FullModel",true);
        eq_.putAll(addObject(target,nodeListEqBd,"cim:GeographicalRegion",false));
        eq_.putAll(addObject(target,nodeListEqBd,"cim:SubGeographicalRegion",false));
        addModelBrlndDependency(new HashSet<Node>(addObject(target,nodeListEqBd,"entsoe:EnergySchedulingType",false).values()),EQ.type,eqbd.id,target);


        HashMap<String,Node> declaredBV = new HashMap<>();
        Node[] baseVoltage_ = convertToArray(target.getElementsByTagName("cim:BaseVoltage"));
        for (Node node : baseVoltage_) {
            if(!StringUtils.isEmpty(node.getLocalName())){
                String id = node.getAttributes().item(0).getNodeValue();
                declaredBV.put(id,node);
            }
        }

        Node[] transf_ = convertToArray(target.getElementsByTagNameNS("http://iec.ch/TC57/2013/CIM-schema-cim16#","TransformerEnd.BaseVoltage"));

        for (Node node : transf_) {
            if(!StringUtils.isEmpty(node.getLocalName())){
                String id = node.getAttributes().item(0).getNodeValue().replace("#","");
                if(!declaredBV.containsKey(id)){
                    if(BVmap.containsKey(id)){
                        Node node1 = target.importNode(BVmap.get(id),true);
                        addModelBrlndDependency(target.getDocumentElement().appendChild(node1),EQ.type,eqbd.id,target);
                        declaredBV.put(id,node1);
                    }
                }
            }
        }


        Node[] voltageLevels_ = convertToArray(target.getElementsByTagName("cim:VoltageLevel"));
        for (Node node : voltageLevels_) {
            if(!StringUtils.isEmpty(node.getLocalName())&& node.hasChildNodes()){
                for (int c=0;c<node.getChildNodes().getLength();c++){
                    if (StringUtils.contains(node.getChildNodes().item(c).getLocalName(),"VoltageLevel.BaseVoltage")){
                        String id = node.getChildNodes().item(c).getAttributes().item(0).getNodeValue().replace("#","");
                        if (!declaredBV.containsKey(id) && BVmap.containsKey(id)){
                            Node node1 = target.importNode(BVmap.get(id),true);
                            addModelBrlndDependency(target.getDocumentElement().appendChild(node1),EQ.type,eqbd.id,target);
                            declaredBV.put(id,node1);
                        }
                    }
                }
            }
        }

        Set<String> controlAreas = new HashSet<>();

        Node[] controlArea = convertToArray(target.getElementsByTagName("cim:ControlArea"));
        for (Node node : controlArea) {
            if(!StringUtils.isEmpty(node.getLocalName()) && node.hasChildNodes()){
                Node[] childs = convertToArray(node.getChildNodes());
                for (Node child : childs) {
                    if(StringUtils.contains(child.getLocalName(),"IdentifiedObject.energyIdentCodeEic")){
                        controlAreas.add(child.getTextContent());
                    }
                }
            }
        }

        Node[] SSHnodes = convertToArray(nodeListssh);
        for (Node node : SSHnodes) {
            if(!StringUtils.isEmpty(node.getLocalName())){
                String id = node.getAttributes().item(0).getNodeValue().replaceAll("#","");
                if(eq_.containsKey(id) && !node.getLocalName().contains("FullModel")){
                    if(node.hasChildNodes()){
                        for(int c=0; c<node.getChildNodes().getLength();c++){
                            if(!StringUtils.isEmpty(node.getChildNodes().item(c).getLocalName()) && !StringUtils.contains(node.getChildNodes().item(c).getLocalName(),"name")){
                                Node node1 = target.importNode(node.getChildNodes().item(c),true);
                                eq_.get(id).appendChild(node1);

                            }

                        }
                        addModelBrlndDependency(eq_.get(id),SSH.type,SSH.id,target);
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
                            if(nodeListtp.item(i).getChildNodes().item(c).getLocalName()!=null && !StringUtils.contains(nodeListtp.item(i).getChildNodes().item(c).getLocalName(),"name")) {
                                Node node = eq_.get(id).getOwnerDocument().importNode(nodeListtp.item(i).getChildNodes().item(c),true);
                                eq_.get(id).appendChild(node);
                                addModelBrlndDependency(eq_.get(id), TP.type,TP.id,target);
                                if(nodeListtp.item(i).getChildNodes().item(c).getLocalName().contains("TopologicalNode")){
                                    String tpid = nodeListtp.item(i).getChildNodes().item(c).getAttributes().item(0).getNodeValue().replace("#","");
                                    if(BDObjects.containsKey(tpid)){
                                        TPs2add.put(tpid,BDObjects.get(tpid).TPn);
                                    }
                                }
                            }
                        }
                    }
                } else if(nodeListtp.item(i).getLocalName().contains("TopologicalNode"))
                        TPs2add.put(id,nodeListtp.item(i));
            }

        }


        

        Node[] SVnodes = convertToArray(nodeListsv);
        for (Node sVnode : SVnodes) {
            if(!StringUtils.isEmpty(sVnode.getLocalName())){
                if(!StringUtils.contains(sVnode.getLocalName(),"FullModel")){
                    String id= sVnode.getAttributes().item(0).getNodeValue().replace("#","");
                    if(eq_.containsKey(id)){
                        if(sVnode.hasChildNodes()){
                            NodeList childs = sVnode.getChildNodes();
                            for(int c=0; c<childs.getLength();c++){
                                if(childs.item(c).getLocalName()!=null && !childs.item(c).getLocalName().contains("name")){
                                    Node node = target.importNode(childs.item(c),true);
                                    eq_.get(id).appendChild(node);
                                }
                            }
                        }
                    }
                    else{
                        if(sVnode.hasChildNodes() && !sVnode.getLocalName().contains("TopologicalIsland")){
                            Node[] childs = convertToArray(sVnode.getChildNodes());
                            for (Node child : childs) {
                                if (child.getLocalName() != null && child.getLocalName().contains("name")) {
                                    if(child.getParentNode()!=null)
                                        child.getParentNode().removeChild(child);
                                }
                                if(!StringUtils.isEmpty(child.getLocalName()) && StringUtils.contains(child.getLocalName(),"TopologicalNode")){
                                    String ReferredTp=child.getAttributes().item(0).getNodeValue().replace("#","");
                                    if(!TPs2add.containsKey(ReferredTp)){
                                        TPs2add.put(ReferredTp,null);
                                    }
                                }
                            }
                        }
                        Node my_node = addNode(target, sVnode);
                        addModelBrlndDependency(my_node,SV.type,SV.id,target);

                    }
                }
            }
        }

        for(String t: TPs2add.keySet()){
            String bv;
            if(BDObjects.containsKey(t)){
                bv = BDObjects.get(t).BVn.getAttributes().item(0).getNodeValue();
                if(!declaredBV.containsKey(bv)){
                    addModelBrlndDependency(addNode(target,BDObjects.get(t).BVn),EQ.type,eqbd.id,target);
                    declaredBV.put(bv,BDObjects.get(t).BVn);
                }

                addModelBrlndDependency(addNode(target,BDObjects.get(t).TPn),TP.type,tpbd.id,target);
                addModelBrlndDependency(addNode(target,BDObjects.get(t).EQn), EQ.type,eqbd.id,target);
                if(isNb || isusingCN) {
                    Node CnBdAdd = addNode(target,BDObjects.get(t).CNn);
                    addModelBrlndDependency(CnBdAdd,TP.type,tpbd.id,target);
                    addModelBrlndDependency(CnBdAdd,EQ.type,eqbd.id,target);
                }

            }
            else{
                if(TPs2add.get(t)!=null){
                    addModelBrlndDependency(addNode(target,TPs2add.get(t)), TP.type,TP.id,target);
                    if(TPs2add.get(t).hasChildNodes()){
                        for (Node child : convertToArray(TPs2add.get(t).getChildNodes())) {
                            if(!StringUtils.isEmpty(child.getLocalName()) && StringUtils.contains(child.getLocalName(),"BaseVoltage")){
                                bv = (child.getAttributes().item(0).getNodeValue().replace("#",""));
                                if(!declaredBV.containsKey(bv)){
                                    Node node1 = target.importNode(BVmap.get(bv),true);
                                    addModelBrlndDependency(target.getDocumentElement().appendChild(node1),EQ.type,eqbd.id,target);
                                    declaredBV.put(bv,node1);
                                }
                            }
                        }
                    }
                }
            }
        }

        
        Set<Node> addExtensions = new HashSet<>();
        addExtensions.addAll(addCimProfileExtensions(EQ,TP,SSH,SV,eqbd,tpbd,target));
        addExtensions.addAll(addProcessTypeExtension(business,target));
        addExtensions.addAll(addGeographicalRegionExtension(controlAreas,target,eq_));
        addExtensions.addAll(addGenericExtensions(target,eq_));
        addModelBrlndDependency(addExtensions,EQ.type,eqbd.id,target);


        cleanXml(target);
        nodeListeq = null;
        nodeListtp = null;
        nodeListssh = null;
        nodeListsv = null;
        EQnodes = null;
        SVnodes = null;
        TPs2add = null;
        SSHnodes = null;
        voltageLevels_=null;
        transf_=null;
       return  target;

    }


    /**
     * @param nodes
     * @param type
     * @param id
     * @param target
     */
    private void addModelBrlndDependency(Set<Node> nodes, Profile.Type type, String id, Document target){
        for (Node node : nodes) {
            Element extEq = target.createElement("brlnd:ModelObject."+brlndType.get(type.toString()));
            extEq.setAttribute("rdf:resource", id);
            node.appendChild(extEq);
        }
    }

    /**
     * @param node
     * @param type
     * @param id
     * @param target
     */
    private void addModelBrlndDependency(Node node, Profile.Type type, String id, Document target){
            Element extEq = target.createElement("brlnd:ModelObject."+brlndType.get(type.toString()));
            extEq.setAttribute("rdf:resource", id);
            node.appendChild(extEq);
    }

    /**
     * @param EQ
     * @param TP
     * @param SSH
     * @param SV
     * @param EQBD
     * @param TPBD
     * @param target
     * @return
     */
    private Set<Node> addCimProfileExtensions(Profile EQ, Profile TP, Profile SSH, Profile SV, Profile EQBD, Profile TPBD, Document target){
        Set<String> cimprofilesuris = new HashSet<>();
        cimprofilesuris.addAll(EQ.modelProfile);
        cimprofilesuris.addAll(TP.modelProfile);
        cimprofilesuris.addAll(SSH.modelProfile);
        cimprofilesuris.addAll(SV.modelProfile);
        cimprofilesuris.addAll(EQBD.modelProfile);
        cimprofilesuris.addAll(TPBD.modelProfile);
        Set<Node> nodes = new HashSet<>();
        for (String s : cimprofilesuris) {
            if(bdExtensions.CimProfiles.containsKey(s)){
                nodes.add(addNode(target, bdExtensions.CimProfiles.get(s)));
            }
            else if (StringUtils.contains(s,"/EquipmentBoundary/")){
                nodes.add(addNode(target, bdExtensions.CimProfiles.get("http://iec.ch/TC57/2013/61970-452/EquipmentBoundary/3")));
            }
        }
        return nodes;
    }

    /**
     * @param business
     * @param target
     * @return
     */
    private Set<Node> addProcessTypeExtension(String business, Document target){
        Set<Node> nodes = new HashSet<>();
        if(bdExtensions.ProcessType.containsKey(business)){
            nodes.add(addNode(target,bdExtensions.ProcessType.get(business)));
        }
        else{
            for(String s : bdExtensions.ProcessType.keySet()){
                nodes.add(addNode(target,bdExtensions.ProcessType.get(s)));
            }
        }
        return nodes;
    }

    /**
     * @param controlAreas
     * @param target
     * @param eqids
     * @return
     */
    private Set<Node> addGeographicalRegionExtension(Set<String> controlAreas, Document target, HashMap<String,Node> eqids){
        Set<Node> nodes = new HashSet<>();
        for (String controlArea : controlAreas) {
            if(bdExtensions.GeographicalRegionEIC.containsKey(controlArea)){
                nodes.add(addNode(target,bdExtensions.GeographicalRegionEIC.get(controlArea)));
                Node myGeo = bdExtensions.GeographicalRegionEIC.get(controlArea);
                String modelingAuthority = null;
                if(myGeo.hasChildNodes()){
                    for (Node node : convertToArray(myGeo.getChildNodes())) {
                        if(StringUtils.contains(node.getLocalName(),"MARegion.ModelingAuthority")){
                            modelingAuthority = node.getAttributes().item(0).getNodeValue().replace("#","");
                        }
                    }
                }

                if(bdExtensions.ModelingAuthority.containsKey(modelingAuthority)){
                    nodes.add(addNode(target,bdExtensions.ModelingAuthority.get(modelingAuthority)));
                }

            }
        }


        for (String s : bdExtensions.GeographicalRegionIds.keySet()) {
            if(eqids.containsKey(s)){
                String modelingAuthority = null;
                if(bdExtensions.GeographicalRegionIds.get(s).hasChildNodes()){
                    for (Node node : convertToArray(bdExtensions.GeographicalRegionIds.get(s).getChildNodes())) {
                        eqids.get(s).appendChild(target.importNode(node,true));
                        if(StringUtils.contains(node.getLocalName(),"MARegion.ModelingAuthority")){
                            modelingAuthority = node.getAttributes().item(0).getNodeValue().replace("#","");
                        }
                    }
                    nodes.add(eqids.get(s));
                    if(bdExtensions.ModelingAuthority.containsKey(modelingAuthority)){
                        nodes.add(addNode(target,bdExtensions.ModelingAuthority.get(modelingAuthority)));
                    }
                }
            }
        }

        return nodes;

    }


    /**
     * @param target
     * @param eqids
     * @return
     */
    private Set<Node> addGenericExtensions(Document target, HashMap<String,Node> eqids){
        Set<Node> nodes = new HashSet<>();
        for(String s: bdExtensions.ToBeAdded.keySet()){
            if(eqids.containsKey(s)){
                if(bdExtensions.ToBeAdded.get(s).hasChildNodes()){
                    for (Node node : convertToArray(bdExtensions.ToBeAdded.get(s).getChildNodes())) {
                        eqids.get(s).appendChild(target.importNode(node,true));
                    }
                    nodes.add(eqids.get(s));
                }
            }
            else {
                nodes.add(addNode(target,bdExtensions.ToBeAdded.get(s)));
            }
        }
        return nodes;
    }


    /**
     * @throws IOException
     * @throws URISyntaxException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    private void parseBdExtensions() throws IOException, URISyntaxException, ParserConfigurationException, SAXException {
        Node[] bdExts = convertToArray(getNodeList(new File(OCLEvaluator.getConfig().get("bdExtensions"))));

        for (Node bdExt : bdExts) {
            if(!StringUtils.isEmpty(bdExt.getLocalName())){
                if(StringUtils.contains(bdExt.getLocalName(),"CimProfile")){
                    if(bdExt.hasChildNodes()){
                        for (Node node : convertToArray(bdExt.getChildNodes())) {
                            if(!StringUtils.isEmpty(node.getLocalName()) && StringUtils.contains(node.getLocalName(),"CimProfile.uri")){
                                bdExtensions.CimProfiles.put(node.getTextContent(),bdExt);
                            }
                        }
                    }
                }
                else if(StringUtils.contains(bdExt.getLocalName(),"ProcessType")){
                    if(bdExt.hasChildNodes()){
                        for (Node node : convertToArray(bdExt.getChildNodes())) {
                            if(!StringUtils.isEmpty(node.getLocalName()) && StringUtils.contains(node.getLocalName(),"IdentifiedObject.name")){
                                bdExtensions.ProcessType.put(node.getTextContent(),bdExt);
                            }
                        }
                    }
                }

                else if(StringUtils.contains(bdExt.getLocalName(),"ModelingAuthority")){
                    bdExtensions.ModelingAuthority.put(bdExt.getAttributes().item(0).getNodeValue(),bdExt);
                }

                else if(StringUtils.contains(bdExt.getLocalName(),"GeographicalRegion")){
                    bdExtensions.GeographicalRegionIds.put(bdExt.getAttributes().item(0).getNodeValue(),bdExt);
                    if(bdExt.hasChildNodes()){
                        for (Node node : convertToArray(bdExt.getChildNodes())) {
                            if(!StringUtils.isEmpty(node.getLocalName()) && StringUtils.contains(node.getLocalName(),"IdentifiedObject.energyIdentCodeEic")){
                                bdExtensions.GeographicalRegionEIC.put(node.getTextContent(),bdExt);
                            }
                        }
                    }
                }

                else{
                    bdExtensions.ToBeAdded.put(bdExt.getAttributes().item(0).getNodeValue(), bdExt);
                }

            }
        }
    }


    /**
     * @param target
     * @return
     * @throws URISyntaxException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws TransformerException
     */
    private  Document createXmi(Document target) throws URISyntaxException, ParserConfigurationException, SAXException, IOException, TransformerException {
        xmiXmlns = null;
        xmiXmlns = new HashMap<>();
        HashMap<String,String> sub = parseEcoreXmi();
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document xmi = builder.newDocument();


        HashMap<String,Integer> numbering = new HashMap<>();
        Node[] objects = convertToArray(target.getDocumentElement().getChildNodes());
        int count = 0;
        for (Node object : objects) {
            if(!StringUtils.isEmpty(object.getLocalName())){
                if(object.hasAttributes()){
                    numbering.put(object.getAttributes().item(0).getNodeValue(),count);
                    count+=1;
                }
            }
        }
        Node[] lines = convertToArray(target.getDocumentElement().getChildNodes());
        Node[] basics = convertToArray(getNodeList(new File(OCLEvaluator.getConfig().get("basic_model"))));



        xmi.appendChild(xmi.createElementNS("http://Model/1.0/CGMES/IEC61970/Base/MetaData", "MetaData:DataSet"));
        for(String s : authExt.keySet()){
            xmi.getDocumentElement().setAttributeNS(authExt.get("xmlns"),"xmlns:"+s,authExt.get(s) );
        }


        for(String s: xmiXmlns.keySet()){
            if(s.contains("schemaLocation"))
                xmi.getDocumentElement().setAttributeNS(xmiXmlns.get("xsi"),"xsi:"+s,xmiXmlns.get(s) );
            else{
                xmi.getDocumentElement().setAttributeNS(authExt.get("xmlns"),"xmlns:"+s ,xmiXmlns.get(s) );
            }

        }

        xmi.getDocumentElement().setAttribute("type","igm");
        xmi.getDocumentElement().setAttribute("validationScope", "QOCDCV3_1");
        xmi.getDocumentElement().setAttribute("excludeProvedRules", "false");
        xmi.getDocumentElement().setAttribute("local_level_validation", "true");
        xmi.getDocumentElement().setAttribute("global_level_validation", "true");
        xmi.getDocumentElement().setAttribute("emf_level_validation", "true");
        xmi.getDocumentElement().setAttribute("isEQoperation", Boolean.toString(isNb));
        xmi.getDocumentElement().setAttribute("isEQshortCircuit", Boolean.toString(isShortCircuit));



        for (Node basic : basics) {
            if(!StringUtils.isEmpty(basic.getLocalName())){
                xmi.getDocumentElement().appendChild(xmi.importNode(basic,true));

            }
        }


        for (Node line : lines) {
            if(!StringUtils.isEmpty(line.getLocalName())){
                Element datasetmember = xmi.createElement("DataSetMember");
                datasetmember.setAttribute("xsi:type",sub.get(line.getLocalName())+":"+line.getLocalName());
                datasetmember.setAttribute("mRID",line.getAttributes().item(0).getNodeValue());

                Node[] childs = convertToArray(line.getChildNodes());
                for (Node child : childs) {
                    if(!StringUtils.contains(child.getNodeName(),"#")){
                        if(child.hasAttributes()){
                            String refId = child.getAttributes().getNamedItem("rdf:resource").getNodeValue().replaceAll("#","");
                            if(numbering.containsKey(refId)){
                                String already = "";
                                if(datasetmember.getAttributes().getNamedItem(child.getNodeName().split("\\.")[1])!=null){
                                    already = datasetmember.getAttributes().getNamedItem(child.getNodeName().split("\\.")[1]).getNodeValue()+" ";
                                }
                                datasetmember.setAttribute(child.getNodeName().split("\\.")[1], already+"//@DataSetMember."+String.valueOf(numbering.get(refId)));
                            }
                            else{
                                String literal = null;
                                for (String s : authExt.keySet()) {
                                    if(StringUtils.contains(refId,authExt.get(s).replace("#",""))){
                                        literal = refId.replaceAll(authExt.get(s).replace("#",""), "");
                                        literal = literal.substring(literal.lastIndexOf(".") + 1);
                                    }
                                }
                                if(StringUtils.isEmpty(literal))
                                    literal="";
                                String already = "";
                                if(datasetmember.getAttributes().getNamedItem(child.getNodeName().split("\\.")[1])!=null){
                                    already = datasetmember.getAttributes().getNamedItem(child.getNodeName().split("\\.")[1]).getNodeValue()+" ";
                                }

                                datasetmember.setAttribute(child.getNodeName().split("\\.")[1], already+literal);

                            }

                        }
                        else{

                            datasetmember.setAttribute(child.getNodeName().split("\\.")[1],child.getTextContent());
                        }
                    }
                }
                datasetmember.setTextContent(String.valueOf(numbering.get(line.getAttributes().item(0).getNodeValue())));
                xmi.getDocumentElement().appendChild(datasetmember);
            }
        }

        lines = null;
        return xmi;
    }

    /**
     * @return
     * @throws IOException
     * @throws URISyntaxException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    private HashMap<String, String> parseEcoreXmi() throws IOException, URISyntaxException, ParserConfigurationException, SAXException {
        HashMap<String,String> sub = new HashMap<>();
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(OCLEvaluator.getConfig().get("ecore_model"));
        Node[] subpackages = convertToArray(doc.getElementsByTagName("eSubpackages"));


        for (Node subpackage : subpackages) {
            String name = subpackage.getAttributes().getNamedItem("name").getNodeValue();
            String nsUri = subpackage.getAttributes().getNamedItem("nsURI").getNodeValue();
            xmiXmlns.put(name,nsUri);
            NodeList childs = subpackage.getChildNodes();
            for(int c=0; c<childs.getLength();c++){
                if(StringUtils.contains(childs.item(c).getLocalName(),"eClassifiers")){
                    sub.put(childs.item(c).getAttributes().getNamedItem("name").getNodeValue(),name);
                }
            }

        }



        String schema = " ";
        for(String s: xmiXmlns.keySet()){
            schema+= xmiXmlns.get(s);
            schema+= " "+xmiXmlns.get(s).replace("http://Model/1.0","cgmes61970oclModel.ecore#/")+" ";
        }
        xmiXmlns.put("xmi","http://www.omg.org/spec/XMI/20131001");
        xmiXmlns.put("xsi","http://www.w3.org/2001/XMLSchema-instance");
        xmiXmlns.put("ecore","http://www.eclipse.org/emf/2002/Ecore");
        xmiXmlns.put("uml","http://www.omg.org/spec/UML/20131001");

        xmiXmlns.put("schemaLocation", schema);

        return sub;

    }

    /**
     *
     */
    private void setAuthExt(){
        authExt.put("rdf","http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        authExt.put("cim", "http://iec.ch/TC57/2013/CIM-schema-cim16#");
        authExt.put("entsoe", "http://entsoe.eu/CIM/SchemaExtension/3/1#");
        authExt.put("md","http://iec.ch/TC57/61970-552/ModelDescription/1#");
        authExt.put("xmlns","http://www.w3.org/2000/xmlns/");
        authExt.put("brlnd","http://brolunda.com/ecore-converter#");
        authExt.put("cgmbp","http://entsoe.eu/CIM/Extensions/CGM-BP/2020#");
    }

    /**
     *
     * @param document
     */
    private void cleanXml(Document document){

        Element root = document.getDocumentElement();

        Node[] allNodes = convertToArray(root.getElementsByTagName("*"));
        for (Node node : allNodes) {
            if(node.getNamespaceURI()!=null &&(!authExt.values().contains(node.getNamespaceURI())||( node.getNamespaceURI().contains("cim")&&!StringUtils.isEmpty(node.getLocalName())))){
                String name = node.getLocalName();
                if(!classes.contains(name)){
                    if(node.getParentNode()!=null)
                        node.getParentNode().removeChild(node);

                }
            }
        }

        for(int i=root.getAttributes().getLength()-1;i>=0;i--){
            if(root.getAttributes().item(i).getNodeType()== Node.ATTRIBUTE_NODE) {
                if(!authExt.keySet().contains(root.getAttributes().item(i).getLocalName())){
                    root.getAttributes().removeNamedItemNS(root.getAttributes().item(i).getNamespaceURI(),root.getAttributes().item(i).getLocalName());
                }
            }
        }


        NodeList season = document.getElementsByTagNameNS("http://iec.ch/TC57/2013/CIM-schema-cim16#","Season.endDate");
        Node[] seNodes = convertToArray(season);
        for (Node seNode : seNodes) {
            if(!StringUtils.isEmpty(seNode.getLocalName())){
                if(Character.toString(seNode.getTextContent().charAt(0)).contains("-")){

                    String s = seNode.getTextContent().replaceFirst("-","2019");
                    seNode.setTextContent(s);
                }
            }
        }

        season = document.getElementsByTagNameNS("http://iec.ch/TC57/2013/CIM-schema-cim16#","Season.startDate");
        seNodes = convertToArray(season);
        for (Node seNode : seNodes) {
            if(!StringUtils.isEmpty(seNode.getLocalName())){
                if(Character.toString(seNode.getTextContent().charAt(0)).contains("-")){
                    String s = seNode.getTextContent().replaceFirst("-","2019");
                    seNode.setTextContent(s);
                }
            }
        }


        root.setAttributeNS("http://www.w3.org/2000/xmlns/","xmlns:brlnd","http://brolunda.com/ecore-converter#" );
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:cgmbp","http://entsoe.eu/CIM/Extensions/CGM-BP/2020#");
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

    /**
     * @param doc
     * @param name
     * @throws TransformerException
     */
    private static void printDocument(Document doc, String name) throws  TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");



        transformer.transform(new DOMSource(doc),new StreamResult(new File("/home/chiaramellomar/EMF_meetings/ocl_validator/models/"+name)));
    }


    /**
     *
     * @throws IOException
     * @throws URISyntaxException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    private void parseEcore() throws IOException, URISyntaxException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(OCLEvaluator.getConfig().get("ecore_model"));
        NodeList nodeList = doc.getElementsByTagName("eClassifiers");
        for(int i=0; i<nodeList.getLength();i++){
            String className= nodeList.item(i).getAttributes().getNamedItem("name").getNodeValue();
            classes.add(className);
            if(nodeList.item(i).hasChildNodes()){
                NodeList childs = nodeList.item(i).getChildNodes();
                for(int c = 0; c<childs.getLength();c++){
                    if(childs.item(c).getLocalName()!=null) {
                        if (childs.item(c).getLocalName().contains("eStructuralFeatures")) {
                            String derived = childs.item(c).getAttributes().getNamedItem("name").getNodeValue();
                            if(childs.item(c).getAttributes().getNamedItem("lowerBound")!=null){
                                classes.add(className+"."+derived);
                            }
                            else if (childs.item(c).getAttributes().getNamedItem("upperBound")!=null){
                                classes.add(derived+"."+className);
                            }
                            else{
                                classes.add(className+"."+derived);
                                classes.add(derived+"."+className);
                            }
                        }
                    }
                }
            }
        }

        NodeList nl = doc.getElementsByTagName("details");
        for (int i=0; i< nl.getLength(); i++){
            String key = nl.item(i).getAttributes().getNamedItem("key").getNodeValue();
            if (key!=null) {
                String value = nl.item(i).getAttributes().getNamedItem("value").getNodeValue();
                if (value != null) {
                    Pattern pattern = Pattern.compile("QoCDCv3\\s*Level=(\\d)");
                    Matcher matcher = pattern.matcher(value);
                    if (matcher.find()) {
                        ruleLevels.put(key, new Integer(matcher.group(1)));
                    }
                }
            }
        }
    }


    /**
     *
     * @param nodeList
     * @return
     */
    private boolean isNb(NodeList nodeList){
        boolean nb = false;
        NodeList fullmodel = nodeList.item(0).getOwnerDocument().getElementsByTagName("md:FullModel");for(int i=0; i<fullmodel.getLength();i++){
            if(fullmodel.item(i).getLocalName()!=null){
                if(fullmodel.item(i).hasChildNodes()){
                    NodeList childs = fullmodel.item(i).getChildNodes();
                    for(int c=0;c<childs.getLength();c++){
                        if(childs.item(c).getLocalName()!=null){
                            String localName= childs.item(c).getLocalName();
                            if(localName.contains("Model.profile")){
                                if (childs.item(c).getTextContent().contains("EquipmentOperation"))
                                    nb=true;
                                if (childs.item(c).getTextContent().contains("EquipmentShortCircuit"))
                                    isShortCircuit = true;
                            }

                        }
                    }
                }
            }
        }
        return nb;
    }

    /**
     *
     * @param doc
     * @param nodeList
     * @param s
     * @param begin
     * @throws IOException
     * @throws TransformerException
     */
    private HashMap<String,Node> addObject(Document doc, NodeList nodeList, String s, boolean begin) throws IOException, TransformerException {
        NodeList nodes = nodeList.item(0).getOwnerDocument().getElementsByTagName(s);
        HashMap<String,Node> addedNodes = new HashMap<>();
        for(int i=0; i<nodes.getLength();i++){
            if(nodes.item(i).getLocalName()!=null){
                Node node;

                if(begin) {
                   node= doc.getDocumentElement().insertBefore(doc.importNode(nodes.item(i), true), doc.getDocumentElement().getChildNodes().item(0));
                }
                else{
                    node= doc.getDocumentElement().appendChild(doc.importNode(nodes.item(i),true));
                }

                addedNodes.put(node.getAttributes().item(0).getNodeValue(), node);
            }

        }
        return addedNodes;
    }


    /**
     *
     * @param doc
     * @param modelPart_
     * @param business
     * @throws IOException
     * @throws TransformerException
     */
    private void addFullModelInfo(Document doc, String modelPart_, String business) throws IOException, TransformerException {

        NodeList fullmodel = doc.getElementsByTagName("md:FullModel");
        String effectiveDate = "";
        String sourcingTSO_ = "";
        String fileVersion_ = "";
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
                                if(StringUtils.isEmpty(sourcingTSO_)){
                                    sourcingTSO_= childs.item(c).getTextContent();
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

    }

    /**
     *
     * @param doc
     * @param node
     */
    private synchronized Node addNode(Document doc, Node node){
        Node nd = doc.getDocumentElement().appendChild(doc.importNode(node,true));
        return nd;
    }

    /**
     *
     * @param nodeListTpBd
     * @param nodeListEqBd
     */
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
                BVmap.put(id,BVlist.item(i));
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
                                    BDObjects.get(TPid).BVn=BVmap.get(bv);
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
    

    /**
     *
     * @return
     */
    private InputStream getCommander(){
       InputStream commander = this.getClass().getClassLoader().getResourceAsStream("cim16_analyse_igm.xml");
       return  commander;
    }


    /**
     *
     * @param object
     * @return
     */
    private String getSimpleNameNoExt(Profile object){
        int pos = object.file.getName().lastIndexOf(".");
        String name= pos>0 ? object.file.getName().substring(0,pos) : object.file.getName();
        return name;
    }

    /**
     *
     * @param nodeList
     * @param ToBeReplaced
     * @param defaultBDId
     * @return
     */
    private NodeList correctDeps(NodeList nodeList, List<String> ToBeReplaced, String defaultBDId){
        NodeList dep = nodeList.item(0).getOwnerDocument().getElementsByTagName("md:Model.DependentOn");
        for (int i =0; i<dep.getLength();i++){
            Node attribute = dep.item(i).getAttributes().item(0);
            if(ToBeReplaced.stream().anyMatch(p->p.trim().equals(attribute.getNodeValue()))){
                attribute.setNodeValue(defaultBDId);
            }
        }
        return nodeList;
    }


}
