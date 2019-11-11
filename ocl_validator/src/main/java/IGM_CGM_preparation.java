package ocl;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class IGM_CGM_preparation {


    public enum Type{
        EQ,TP, SSH, SV, other
    }

    public class Profile{
        public Type type;
        public String id;
        public List<String> depOn= new ArrayList<>();
        public File file;
        public String xml_name;
    }

    List<Profile> SVProfiles = new ArrayList<>();
    List<Profile> otherProfiles = new ArrayList<>();
    List<Profile> BDProfiles = new ArrayList<>();
    HashMap<Profile,List<Profile>> IGM_CGM= new HashMap<>();


    public  void read_zip(File models) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();

        FileFilter fileFilter = new WildcardFileFilter("*.zip", IOCase.INSENSITIVE);
        File[] listOfFiles = models.listFiles(fileFilter);
        for (File file: listOfFiles){
            ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()){
                UserHandler handler = new UserHandler();
                ZipEntry entry = entries.nextElement();
                InputStream xmlStream = zip.getInputStream(entry);
                saxParser.parse( xmlStream, handler );
                Profile profile = new Profile();
                profile.type=get_type(file.getName());
                profile.depOn=handler.my_depOn;
                profile.id=handler.my_id;
                profile.file=file;
                profile.xml_name=entry.getName();

                switch (profile.type){
                    case SV:
                        SVProfiles.add(profile);
                        break;
                    case EQ:
                    case TP:
                    case SSH:

                        otherProfiles.add(profile);
                        break;
                    case other:
                        BDProfiles.add(profile);
                }

                xmlStream.close();
            }
        }

        reorder_models();
    }

    public void reorder_models(){
        for (Profile my_sv_it : SVProfiles){
            List<Profile> TPs= new ArrayList<>();
            List<Profile> SSHs= new ArrayList<>();
            List<Profile> EQs = new ArrayList<>();
            List<Profile> EQBDs = new ArrayList<>();
            List<Profile> TPBDs = new ArrayList<>();

            for (String sv_dep : my_sv_it.depOn){
                Optional<Profile> matchingObject = otherProfiles.stream().filter(p->p.id.equals(sv_dep)).findAny();
                if(matchingObject.isPresent()){
                    switch (matchingObject.get().type){
                        case SSH:
                            SSHs.add(matchingObject.get());
                            break;
                        case TP:
                            TPs.add(matchingObject.get());
                            break;
                    }
                }
            }

            // System.out.println(SSHs);
            for (Profile my_ssh_it : SSHs){
                for(String ssh_dep : my_ssh_it.depOn){
                    Optional<Profile> matchingObject = otherProfiles.stream().filter(p->p.id.equals(ssh_dep)).findAny();
                    if(matchingObject.isPresent()){
                        switch (matchingObject.get().type){
                            case EQ:
                                EQs.add(matchingObject.get());
                                break;
                        }
                    }
                }
            }

            // System.out.println(EQs);
            for (Profile my_eq_it: EQs){
                for(String eq_dep : my_eq_it.depOn){
                    Optional<Profile> matchingObject = BDProfiles.stream().filter(p->p.id.equals(eq_dep)).findAny();
                    if(matchingObject.isPresent()){
                        switch (matchingObject.get().type){
                            case other:
                                EQBDs.add(matchingObject.get());
                                break;
                        }
                    }
                }
            }

            //System.out.println(EQBDs);
            for (Profile my_eqbd_it : EQBDs){
                String eqbd_id_= my_eqbd_it.id;
                List<String> eqbd_id= new ArrayList<>();
                eqbd_id.add(eqbd_id_);
                Optional<Profile> matchingObject = BDProfiles.stream().filter(p->p.depOn.equals(eqbd_id)).findAny();
                if(matchingObject.isPresent()){
                    switch (matchingObject.get().type){
                        case other:
                            TPBDs.add(matchingObject.get());
                            break;
                    }
                }

            }

            IGM_CGM.put(my_sv_it,EQs);
            IGM_CGM.get(my_sv_it).addAll(SSHs);
            IGM_CGM.get(my_sv_it).addAll(TPs);
            IGM_CGM.get(my_sv_it).addAll(EQBDs);
            IGM_CGM.get(my_sv_it).addAll(TPBDs);

        }
    }


    public Type get_type(String file_name) {

        if (file_name.contains("_SV_")) {
            return Type.valueOf("SV");
        } else if (file_name.contains("_SSH_")) {
            return Type.valueOf("SSH");
        } else if (file_name.contains("_TP_")) {
            return Type.valueOf("TP");
        } else if (file_name.contains("_EQ_")){
            return Type.valueOf("EQ");
        } else if (file_name.contains("BD")) {
            return Type.valueOf("other");
        }
        else{
            return Type.valueOf("other");
        }
    }

    static class UserHandler extends DefaultHandler
    {
        String my_id;
        List<String> my_depOn = new ArrayList<String>();

        @Override
        public void startElement(String namespaceURI, String localName, String qname, Attributes atts) throws SAXException
        {

            if(qname.equalsIgnoreCase("md:FullModel")){
                /*System.out.println(qname);
                System.out.println(atts.getValue("rdf:about"));*/
                my_id=atts.getValue("rdf:about");

            }
            if(qname.equalsIgnoreCase("md:Model.DependentOn")){
                //System.out.println(atts.getValue("rdf:resource"));
                my_depOn.add(atts.getValue("rdf:resource"));

            }

        }

    }

}
