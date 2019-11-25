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
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class IGM_CGM_preparation {
    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER=Logger.getLogger(ocl.OCLEvaluator.class.getName());
    }

    public enum Type{
        EQ,TP, SSH, SV, other
    }

    public class Profile{
        public Type type;
        public String id;
        public List<String> depOn= new ArrayList<>();
        public File file;
        public String xml_name;
        public List<String> DepToBeReplaced= new ArrayList<>();
    }

    List<Profile> SVProfiles = new ArrayList<>();
    List<Profile> otherProfiles = new ArrayList<>();
    List<Profile> BDProfiles = new ArrayList<>();
    HashMap<Profile,List<Profile>> IGM_CGM= new HashMap<>();
    List<String> defaultBDIds = new ArrayList<>();


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
                profile.type=get_type(entry.getName());
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
        CheckConsitency();

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
                else{
                    Optional<Profile> matchingObject_bds = BDProfiles.stream().filter(p->p.id.equals(sv_dep)).findAny();
                    if(!matchingObject_bds.isPresent()){
                        my_sv_it.DepToBeReplaced.add(sv_dep);
                    }
                }
            }

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
                    else{
                        my_eq_it.DepToBeReplaced.add(eq_dep);
                    }
                }
            }

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

    public void CheckConsitency() throws ParserConfigurationException, SAXException, IOException {
        boolean BDParsed = false;
        List<Profile> defaultBDs = new ArrayList<>();
        for (Profile key : IGM_CGM.keySet()){
            int NumEqs = 0;
            int NumTPs = 0;
            int NumSSHs = 0;
            int NumBDs = 0;
            for(Profile value : IGM_CGM.get(key)){
                switch (value.type){
                    case EQ:
                        NumEqs+=1;
                        break;
                    case TP:
                        NumTPs+=1;
                        break;
                    case SSH:
                        NumSSHs+=1;
                        break;
                    case other:
                        NumBDs+=1;
                        break;
                }
            }
            if (!(NumEqs==NumTPs && NumTPs==NumSSHs)){
                LOGGER.severe("The following model is missing one instance: " + key.xml_name);
                IGM_CGM.remove(key);
            }
            else{
                if(NumBDs<2 ){
                    if (BDParsed == false){
                        defaultBDs=get_default_bds();
                        BDParsed = true;
                    }

                    IGM_CGM.get(key).addAll(defaultBDs);
                }
            }
        }

    }

    public List<Profile> get_default_bds() throws IOException, ParserConfigurationException, SAXException {
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG") + File.separator + "config.properties");
        Properties properties = new Properties();
        properties.load(config);
        List<Profile> defaultBDs = new ArrayList<>();
        if (properties.getProperty("default_bd") != null) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAXParser saxParser = factory.newSAXParser();
            File bds = new File(IOUtils.resolveEnvVars(properties.getProperty("default_bd")));
            FileFilter fileFilter = new WildcardFileFilter("*.zip", IOCase.INSENSITIVE);
            File[] listOfFiles = bds.listFiles(fileFilter);
            for (File file : listOfFiles) {
                ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    UserHandler handler = new UserHandler();
                    ZipEntry entry = entries.nextElement();
                    InputStream xmlStream = zip.getInputStream(entry);
                    saxParser.parse( xmlStream, handler );
                    if(get_type(entry.getName())==Type.other){
                        Profile profile = new Profile();
                        profile.type=get_type(entry.getName());
                        profile.depOn=handler.my_depOn;
                        profile.id=handler.my_id;
                        profile.file=file;
                        profile.xml_name=entry.getName();
                        defaultBDs.add(profile);
                        if(handler.my_depOn.size()!=0){
                            defaultBDIds.add(handler.my_depOn.get(0));
                            defaultBDIds.add(handler.my_id);

                        }
                    }
                    else{
                        LOGGER.severe("Impossible to add default boundaries!");
                        System.exit(0);
                    }
                }

            }
            if(defaultBDIds.size()<2){
                LOGGER.severe("One boundary instance is missing in "+properties.getProperty("default_bd")+": Validation stops!");
                System.exit(0);
            }
        }
        else {
            LOGGER.severe("Default boundary location not specified!");
            System.exit(0);
        }
        return defaultBDs;
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
