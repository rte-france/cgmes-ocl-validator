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
        EQ,TP, SSH,SV, other;
    }

    public class Profile{
        public Type type;
        public String id;
        public List<String> depOn= new ArrayList<String>();
        public File file;
        public String xml_name;


    }

    List<Profile> SVProfiles = new ArrayList<Profile>();
    List<Profile> otherProfiles = new ArrayList<Profile>();
    List<Profile> BDProfiles = new ArrayList<Profile>();
    HashMap<Profile,List<Profile>> IGM_CGM= new HashMap<>();





    public  void read_zip(File models) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();

        List<String> found = new ArrayList<String>();
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
        Iterator<Profile> sv_ = SVProfiles.iterator();
        while (sv_.hasNext()){
            final Object my_sv_it=sv_.next();
            List<Profile> TPs= new ArrayList<>();
            List<Profile> SSHs= new ArrayList<>();
            List<Profile> EQs = new ArrayList<>();
            List<Profile> EQBDs = new ArrayList<>();
            List<Profile> TPBDs = new ArrayList<>();

            for (String sv_dep : ((Profile) my_sv_it).depOn){

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
            Iterator<Profile> sshs_=SSHs.iterator();
            while (sshs_.hasNext()){
                final Object my_ssh_it = sshs_.next();
                for(String ssh_dep : ((Profile) my_ssh_it).depOn){
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
            Iterator<Profile> eqs_= EQs.iterator();
            while (eqs_.hasNext()){
                final Object my_eq_it=eqs_.next();
                for(String eq_dep : ((Profile) my_eq_it).depOn){
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
            Iterator<Profile> eqbds_ =  EQBDs.iterator();
            while (eqbds_.hasNext()){
                final Object my_eqbd_it=eqbds_.next();
                String eqbd_id_= ((Profile) my_eqbd_it).id;
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

            IGM_CGM.put((Profile) my_sv_it,EQs);
            IGM_CGM.get((Profile)my_sv_it).addAll(SSHs);
            IGM_CGM.get((Profile)my_sv_it).addAll(TPs);
            IGM_CGM.get((Profile)my_sv_it).addAll(EQBDs);
            IGM_CGM.get((Profile)my_sv_it).addAll(TPBDs);


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
