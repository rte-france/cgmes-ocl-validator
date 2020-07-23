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
import ocl.service.util.XGMPreparationUtils;
import ocl.util.DependencyHandler;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.xml.sax.SAXException;

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
        LOGGER=Logger.getLogger(IGM_CGM_preparation.class.getName());
    }

    private List<Profile> SVProfiles = new ArrayList<>();
    private List<Profile> otherProfiles = new ArrayList<>();
    private List<Profile> BDProfiles = new ArrayList<>();
    HashMap<Profile,List<Profile>> IGM_CGM= new HashMap<>();

    void readZip(File models) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();

        FileFilter fileFilter = new WildcardFileFilter("*.zip", IOCase.INSENSITIVE);
        File[] listOfFiles = models.listFiles(fileFilter);
        if (listOfFiles == null) return;
        for (File file: listOfFiles) {
            ZipFile zip = new ZipFile(new File(file.getAbsolutePath()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int numberEntry = 0;
            while (entries.hasMoreElements()) {
                numberEntry += 1;
                entries.nextElement();
            }
            entries = zip.entries();
            if (numberEntry == 1){
                while (entries.hasMoreElements()) {
                    DependencyHandler handler = new DependencyHandler();
                    ZipEntry entry = entries.nextElement();
                    InputStream xmlStream = zip.getInputStream(entry);
                    try{
                        saxParser.parse(xmlStream, handler);
                    }catch (DependencyHandler.DoneParsingException e ){

                    }catch (SAXException e ){
                        LOGGER.severe("Problem with header processing when reordering");
                        throw new RuntimeException(e);
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

        reorderModels();
        checkConsistency();

    }


    private void reorderModels(){
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
                } else{
                    Optional<Profile> matchingObject_bds = BDProfiles.stream().filter(p->p.id.equals(sv_dep)).findAny();
                    if(!matchingObject_bds.isPresent())
                        my_sv_it.DepToBeReplaced.add(sv_dep);
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
                            case EQBD:
                                EQBDs.add(matchingObject.get());
                                break;
                        }
                    } else my_eq_it.DepToBeReplaced.add(eq_dep);
                }
            }

            for (Profile my_eqbd_it : EQBDs){
                String eqbd_id_= my_eqbd_it.id;
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

            IGM_CGM.put(my_sv_it,EQs);
            IGM_CGM.get(my_sv_it).addAll(SSHs);
            IGM_CGM.get(my_sv_it).addAll(TPs);
            IGM_CGM.get(my_sv_it).addAll(EQBDs);
            IGM_CGM.get(my_sv_it).addAll(TPBDs);
        }
    }

    /**
     *
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    private void checkConsistency() throws ParserConfigurationException, SAXException, IOException {
        boolean BDParsed = false;
        List<Profile> defaultBDs = new ArrayList<>();
        Iterator<Map.Entry<Profile,List<Profile>>> it = IGM_CGM.entrySet().iterator();
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
                LOGGER.severe("The following model is missing one instance: " + entry.getKey().xml_name);
                it.remove();
            } else if(NumBDs<2){
                if (!BDParsed){
                    defaultBDs= XGMPreparationUtils.getDefaultBds();
                    BDParsed = true;
                }
                entry.getValue().addAll(defaultBDs);
            }
        }

    }

}
