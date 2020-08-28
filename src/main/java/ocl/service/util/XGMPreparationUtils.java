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
package ocl.service.util;

import ocl.Profile;
import ocl.util.DependencyHandler;
import ocl.util.IOUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class XGMPreparationUtils {

    static Logger logger = null;
    private static SAXParserFactory factory = SAXParserFactory.newInstance();
    private static SAXParser saxParser;

    private static List<Profile> DEFAULT_BDS;
    public static List<String> defaultBDIds = new ArrayList<>();


    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        logger = Logger.getLogger(XGMPreparationUtils.class.getName());
        factory.setNamespaceAware(true);
        try {
            saxParser = factory.newSAXParser();

            DEFAULT_BDS = getDefaultBds();
        } catch (ParserConfigurationException | SAXException | IOException e){
            e.printStackTrace();
        }

    }


    /**
     *
     * @return
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public static List<Profile> getDefaultBds() throws IOException {
        InputStream config = new FileInputStream(System.getenv("VALIDATOR_CONFIG") + File.separator + "config.properties");
        Properties properties = new Properties();
        properties.load(config);
        List<Profile> defaultBDs = new ArrayList<>();
        if (properties.getProperty("default_bd") != null) {
            File bds = new File(IOUtils.resolveEnvVars(properties.getProperty("default_bd")));
            FileFilter fileFilter = new WildcardFileFilter("*.zip", IOCase.INSENSITIVE);
            File[] listOfFiles = bds.listFiles(fileFilter);
            for (File file : listOfFiles) {
                ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    DependencyHandler handler = new DependencyHandler();
                    ZipEntry entry = entries.nextElement();
                    InputStream xmlStream = zip.getInputStream(entry);
                    try {
                        saxParser.parse(xmlStream, handler);
                    } catch (DependencyHandler.DoneParsingException e){

                    } catch (SAXException e){
                        logger.severe("Problem with header processing when reordering");
                        throw new IOException(e);
                    } finally{
                        xmlStream.close();
                    }
                    if ((Profile.getType(entry.getName()) == Profile.Type.EQBD) || (Profile.getType(entry.getName()) == Profile.Type.TPBD)){
                        Profile profile = new Profile(Profile.getType(entry.getName()), handler.getMyId(), handler.getMyDepOn(), file, entry.getName(),handler.getModelProfile());
                        defaultBDs.add(profile);
                        if(handler.getMyDepOn().size()!=0){
                            defaultBDIds.add(handler.getMyDepOn().get(0));
                            defaultBDIds.add(handler.getMyId());
                        }
                    } else{
                        logger.severe("Impossible to add default boundaries!");
                        System.exit(0);
                    }
                }

            }
            if(defaultBDIds.size()<2){
                logger.severe("One boundary instance is missing in "+properties.getProperty("default_bd")+": Validation stops!");
                System.exit(0);
            }
        } else {
            logger.severe("Default boundary location not specified!");
            System.exit(0);
        }
        config.close();
        return defaultBDs;
    }


    public static void readZips(File files, Set SVProfiles, Set otherProfiles, Set BDProfiles) throws IOException {
        FileFilter fileFilter = new WildcardFileFilter("*.zip", IOCase.INSENSITIVE);
        File[] listOfFiles = files.listFiles(fileFilter);
        if (listOfFiles == null) return;
        for (File f: listOfFiles) {
            readZip(f, SVProfiles, otherProfiles, BDProfiles);
        }
    }


    public static void readZip(File file, Set SVProfiles, Set otherProfiles, Set BDProfiles) throws IOException {

            ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int numberEntry = 0;
            while (entries.hasMoreElements()) {
                numberEntry += 1;
                entries.nextElement();
            }
            entries = zip.entries();
            if (numberEntry == 1) {
                while (entries.hasMoreElements()) {
                    DependencyHandler handler = new DependencyHandler();
                    ZipEntry entry = entries.nextElement();
                    InputStream xmlStream = zip.getInputStream(entry);
                    try {
                        saxParser.parse(xmlStream, handler);
                    } catch (DependencyHandler.DoneParsingException e) {

                    } catch (SAXException e) {
                        logger.severe("Problem with header parsing");
                        throw new IOException(e);
                    }
                    Profile profile = new Profile(Profile.getType(entry.getName()), handler.getMyId(), handler.getMyDepOn(), file, entry.getName(), handler.getModelProfile());
                    switch (profile.type) {
                        case SV:
                            SVProfiles.add(profile);
                            break;
                        case EQ:
                        case TP:
                        case SSH:
                            otherProfiles.add(profile);
                            break;
                        case EQBD:
                        case TPBD:
                            BDProfiles.add(profile);
                    }

                    xmlStream.close();
                }
            }
        }



    public static void reorderModels(Set<Profile> SVProfiles, Set<Profile> otherProfiles, Set<Profile> BDProfiles, HashMap<Profile,List<Profile>> xGM){
        for (Profile mySV : SVProfiles){
            logger.info("Reordering:\t" + mySV.xml_name);
            List<Profile> TPs= new ArrayList<>();
            List<Profile> SSHs= new ArrayList<>();
            List<Profile> EQs = new ArrayList<>();
            List<Profile> EQBDs = new ArrayList<>();
            List<Profile> TPBDs = new ArrayList<>();

            for (String SVdep : mySV.depOn){
                Optional<Profile> matchingObject = otherProfiles.stream().filter(p->p.id.equals(SVdep)).findAny();
                if(matchingObject.isPresent()){
                    switch (matchingObject.get().type){
                        case SSH:
                            SSHs.add(matchingObject.get());
                            break;
                        case TP:
                            TPs.add(matchingObject.get());
                            break;
                    }
                } else{
                    Optional<Profile> matchingObject_bds = BDProfiles.stream().filter(p->p.id.equals(SVdep)).findAny();
                    if(!matchingObject_bds.isPresent())
                        mySV.DepToBeReplaced.add(SVdep);
                }
            }

            for (Profile mySSH : SSHs){
                for(String SSHdep : mySSH.depOn){
                    Optional<Profile> matchingObject = otherProfiles.stream().filter(p->p.id.equals(SSHdep)).findAny();
                    if(matchingObject.isPresent()){
                        switch (matchingObject.get().type){
                            case EQ:
                                EQs.add(matchingObject.get());
                                break;
                        }
                    }
                }
            }

            for (Profile myEQ: EQs){
                for(String EQdep : myEQ.depOn){
                    Optional<Profile> matchingObject = BDProfiles.stream().filter(p->p.id.equals(EQdep)).findAny();
                    if(matchingObject.isPresent()){
                        switch (matchingObject.get().type){
                            case EQBD:
                                EQBDs.add(matchingObject.get());
                                break;
                        }
                    } else myEQ.DepToBeReplaced.add(EQdep);
                }
            }

            for (Profile myEQBD : EQBDs){
                String eqbd_id_= myEQBD.id;
                List<String> eqbd_id= new ArrayList<>();
                eqbd_id.add(eqbd_id_);
                Optional<Profile> matchingObject = BDProfiles.stream().filter(p->p.depOn.equals(eqbd_id)).findAny();
                if(matchingObject.isPresent()){
                    switch (matchingObject.get().type){
                        case TPBD:
                            TPBDs.add(matchingObject.get());
                            break;
                    }
                }
            }


            xGM.put(mySV,EQs);
            xGM.get(mySV).addAll(SSHs);
            xGM.get(mySV).addAll(TPs);
            xGM.get(mySV).addAll(EQBDs);
            xGM.get(mySV).addAll(TPBDs);

        }
    }


    /**
     *
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public static void checkConsistency(HashMap<Profile,List<Profile>>  xGM) throws ParserConfigurationException, SAXException, IOException {
        Iterator<Map.Entry<Profile,List<Profile>>> it = xGM.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry<Profile,List<Profile>> entry = it.next();
            int NumEqs = 0;
            int NumTPs = 0;
            int NumSSHs = 0;
            int NumBDs = 0;
            for (Profile value: entry.getValue()){
                switch (value.type){
                    case EQ:
                        NumEqs++;
                        break;
                    case TP:
                        NumTPs++;
                        break;
                    case SSH:
                        NumSSHs++;
                        break;
                    case EQBD:
                    case TPBD:
                        NumBDs++;
                        break;
                }
            }
            if (!(NumEqs==NumTPs && NumTPs==NumSSHs)){
                logger.severe("The following model is missing one instance: " + entry.getKey().xml_name);
                it.remove();
            } else {
                if(NumBDs<2){
                    entry.getValue().addAll(DEFAULT_BDS);
                }
                logger.info("xGM complete: " + entry.getKey().xml_name);
                if (Configuration.debugMode){
                    for (Profile p : entry.getValue()){
                        logger.info(" -- contains: "+p.xml_name);
                    }
                }
            }
        }

    }



}
