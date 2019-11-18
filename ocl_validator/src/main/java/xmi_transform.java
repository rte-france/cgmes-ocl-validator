package ocl;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class xmi_transform {

    private static Logger LOGGER = null;

    private static TransformerFactory tfactory = TransformerFactory.newInstance();

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER=Logger.getLogger(ocl.OCLEvaluator.class.getName());
    }


    public HashMap<String, StreamResult> convert_data(HashMap<ocl.IGM_CGM_preparation.Profile,List<ocl.IGM_CGM_preparation.Profile>>  IGM_CGM, List<String> defaultBDIds)
            throws TransformerException, IOException, SAXException, ParserConfigurationException {

        HashMap<String,StreamResult> xmi_map = new HashMap<>();
        for(ocl.IGM_CGM_preparation.Profile key : IGM_CGM.keySet()){
            StreamResult resulting_xmi ;
            StreamResult cleaned_sv = clean_profile(get_name_for_xslt(key));
            if(key.DepToBeReplaced.size()!=0){
                cleaned_sv = CorrectDeps(cleaned_sv,key.DepToBeReplaced, defaultBDIds.get(1));
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

            sv_sn.add(get_simple_name_no_ext(key));

            for(ocl.IGM_CGM_preparation.Profile value : IGM_CGM.get(key)){
                switch (value.type){
                    case EQ:
                        StreamResult cleaned_EQ = new StreamResult();
                        cleaned_EQ = clean_profile(get_name_for_xslt(value));
                        if(key.DepToBeReplaced.size()!=0){
                            cleaned_EQ = CorrectDeps(cleaned_EQ,value.DepToBeReplaced,defaultBDIds.get(0));
                        }
                        cleaned_eqs.add(cleaned_EQ);
                        eq_sn.add(get_simple_name_no_ext(value));
                        break;
                    case TP:
                        cleaned_tps.add(clean_profile(get_name_for_xslt(value)));
                        tp_sn.add(get_simple_name_no_ext(value));
                        break;
                    case SSH:
                        cleaned_sshs.add(clean_profile(get_name_for_xslt(value)));
                        ssh_sn.add(get_simple_name_no_ext(value));
                        break;
                    case other:
                        if(value.file.getName().contains("_EQBD_")){
                            EQBD=value;
                            eqbd_sn.add(get_simple_name_no_ext(value));
                        }
                        else{
                            TPBD=value;
                            tpbd_sn.add(get_simple_name_no_ext(value));
                        }
                        break;
                }
            }

            LOGGER.info("Cleaned:"+key);
            StreamResult merged_xml = merge_profiles(cleaned_sv,cleaned_eqs.get(0),cleaned_tps.get(0),cleaned_sshs.get(0),EQBD,TPBD,sv_sn.get(0),eq_sn.get(0),tp_sn.get(0),ssh_sn.get(0),eqbd_sn.get(0),tpbd_sn.get(0));
            LOGGER.info("Merged:"+key);
            resulting_xmi = transform_to_xmi(merged_xml);
            LOGGER.info("Tranformed:"+key);

            xmi_map.put(sv_sn.get(0),resulting_xmi);

        }
        return xmi_map;

    }

    private InputStream get_commander(){
        //ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        //File commander = new File(classLoader.getResource("cim16_analyse_igm.xml").getFile());
       // File commander = new File(this.getClass().getClassLoader().getResource("cim16_analyse_igm.xml").getFile());
        InputStream commander = this.getClass().getClassLoader().getResourceAsStream("cim16_analyse_igm.xml");

        return  commander;
    }


    private InputStream get_xslt( String name){
        //ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        //File xslt_ =  new File(classLoader.getResource(name).getFile());
        //File xslt =  new File(this.getClass().getClassLoader().getResource(name).getFile());
        InputStream xslt = this.getClass().getClassLoader().getResourceAsStream(name);
        return xslt;
    }

    private InputStream get_ecore(){
        //ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        //File ecore_ = new File(classLoader.getResource("cgmes61970oclModel.ecore").getFile());
        //File ecore = new File(this.getClass().getClassLoader().getResource("cgmes61970oclModel.ecore").getFile());
        InputStream ecore = this.getClass().getClassLoader().getResourceAsStream("cgmes61970oclModel.ecore");
        return ecore;
    }

    private String get_simple_name_no_ext(ocl.IGM_CGM_preparation.Profile object){
        int pos = object.file.getName().lastIndexOf(".");
        String name= pos>0 ? object.file.getName().substring(0,pos) : object.file.getName();
        return name;
    }

    private String get_name_for_xslt(ocl.IGM_CGM_preparation.Profile object){
        String name = "jar:file:"+object.file.getAbsolutePath()+"!/"+object.xml_name;
        return name;
    }

    private StreamResult CorrectDeps (StreamResult profile, List<String> ToBeReplaced, String defaultBDId) throws ParserConfigurationException, IOException, SAXException, TransformerException {
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

    private StreamResult clean_profile(String file_name) throws TransformerException {

        InputStream xslt = get_xslt("cim16_clean_data_not_in_profile.xslt");

        Transformer transformer = tfactory.newTransformer(new StreamSource(xslt));

        transformer.setParameter("file",file_name);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(new ByteArrayOutputStream());

        transformer.transform(new StreamSource(get_commander()), result);

        return result;

    }

    private StreamResult merge_profiles(StreamResult sv, StreamResult eq, StreamResult tp, StreamResult ssh, ocl.IGM_CGM_preparation.Profile EQBD, ocl.IGM_CGM_preparation.Profile TPBD,
                                       String sv_sn,
                                       String eq_sn,
                                       String tp_sn,
                                       String ssh_sn,
                                       String eqbd_sn,
                                       String tpbd_sn) throws TransformerException {
        InputStream xslt = get_xslt("cim16_merge_igm.xslt");

        Transformer transformer = tfactory.newTransformer(new StreamSource(xslt));
        transformer.setParameter("SV",sv.getOutputStream().toString());
        transformer.setParameter("EQ",eq.getOutputStream().toString());
        transformer.setParameter("TP",tp.getOutputStream().toString());
        transformer.setParameter("SSH",ssh.getOutputStream().toString());
        transformer.setParameter("EQBD",get_name_for_xslt(EQBD));
        transformer.setParameter("TPBD",get_name_for_xslt(TPBD));
        transformer.setParameter("sv_sn",sv_sn);
        transformer.setParameter("eq_sn",eq_sn);
        transformer.setParameter("tp_sn",tp_sn);
        transformer.setParameter("ssh_sn",ssh_sn);
        transformer.setParameter("eqbd_sn",eqbd_sn);
        transformer.setParameter("tpbd_sn",tpbd_sn);
        StreamResult result = new StreamResult(new ByteArrayOutputStream());
        transformer.transform(new StreamSource(get_commander()), result);
        return result;
    }

    public StreamResult transform_to_xmi(StreamResult merged_xml) throws TransformerException {
        //TransformerFactory tfactory = TransformerFactory.newInstance();
        InputStream xslt = get_xslt("cim16_create_xmi_from_cimxml.xslt");

        Transformer transformer = tfactory.newTransformer(new StreamSource(xslt));
        transformer.setParameter("merged_xml",merged_xml.getOutputStream().toString());

        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(get_ecore(),writer);
            transformer.setParameter("ecore", writer.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        transformer.setParameter("ecore_name","cgmes61970oclModel.ecore");

        transformer.setParameter("type", "igm");
        StreamResult result = new StreamResult(new ByteArrayOutputStream());
        transformer.transform(new StreamSource(get_commander()), result);
        return result;
    }

}


 /*TransformerFactory tfactory = TransformerFactory.newInstance();
        String sourceID = "/home/chiaramellomar/EMF_meetings/ocl_validator/xslt_tests/test_4/cim16_analyse_igm.xml";
        String xslID = "/home/chiaramellomar/EMF_meetings/ocl_validator/xslt_tests/test_4/converter.xslt";
        Transformer transformer = tfactory.newTransformer(new StreamSource(xslID));
        //transformer.setParameter("render_id","1234");
        //transformer.transform(new StreamSource(sourceID), new StreamResult(new File("/home/chiaramellomar/EMF_meetings/ocl_validator/xslt_tests/test_4/Simple2.out")));
        for(Object key : IGM_CGM.keySet()){
            int pos = ((ocl.IGM_CGM_preparation.Profile)key).file.getName().lastIndexOf(".");
            String file_name= pos>0 ? ((ocl.IGM_CGM_preparation.Profile)key).file.getName().substring(0,pos) : ((ocl.IGM_CGM_preparation.Profile)key).file.getName();
            String sv_name = "jar:file:"+((ocl.IGM_CGM_preparation.Profile)key).file.getAbsolutePath()+"!/"+file_name+".xml";
            transformer.setParameter("sv_file",sv_name);
            Result result = new StreamResult(new ByteArrayOutputStream());

            transformer.transform(new StreamSource(sourceID), result);
           *//* System.out.println(((StreamResult) result).getOutputStream().toString());*//*
        }*/