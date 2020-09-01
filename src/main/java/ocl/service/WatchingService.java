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
package ocl.service;

import ocl.Profile;
import ocl.service.util.Priority;
import ocl.service.util.XGMPreparationUtils;
import ocl.util.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.Collections.min;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This service is in charge of:
 * - watching for new files in the configured input directory, polling to the input directory is done on a regular basis
 * - assembling together the profile instances that constitute an IGM/CGM (=xGM) among the provided input files
 * - passing the assembled data to the XMI transformation service
 */
public class WatchingService extends BasicService {

    private TransformationListener transformationListener = null;

    private Set<Profile> SVProfiles = Collections.synchronizedSet(new HashSet<>());
    private Set<Profile> otherProfiles = Collections.synchronizedSet(new HashSet<>());
    private Set<Profile> BDProfiles = Collections.synchronizedSet(new HashSet<>());
    private HashMap<Profile,List<Profile>> xGM= new HashMap<>();

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final Map<Path, Long> expirationTimes = new HashMap();
    private Long newFileWait = 5000L; // 5s

    /**
     * Creates a WatchService and registers the given directory
     */
    public WatchingService(Path dir) throws IOException {
        super();
        priority = Priority.LOW;

        watcher = FileSystems.getDefault().newWatchService();
        keys = new HashMap<>();

        walkAndRegisterDirectories(dir);
        logger.info("Watching service monitoring directory:"+dir.toString());
    }


    /**
     * Registers a transformation listener
     * @param tl
     */
    public void setListener(TransformationListener tl){
        transformationListener = tl;
    }


    /**
     *
     */
    public void triggerXgmTransformation(){
        transformationListener.enqueueForTransformation(xGM);
    }

    /**
     * Register the given directory with the WatchService; This function will be called by FileVisitor
     */
    private void registerDirectory(Path dir) throws IOException
    {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the WatchService.
     */
    private void walkAndRegisterDirectories(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     *
     * @param currentTime
     */
    private void handleExpiredWaitTimes(Long currentTime) {
        // Here we assume that after the time out the file is complete (because no more ENTRY_MODIFY occurred during some time)
        // Start import for files for which the expirationtime has passed
        int SVhash = SVProfiles.hashCode();

        Iterator<Map.Entry<Path,Long>> iter = expirationTimes.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<Path, Long> entry = iter.next();
            if(entry.getValue()<=currentTime) {
                iter.remove(); //thread-safe
                //TODO: do something with the file
                File f = entry.getKey().toFile();
                String ext = IOUtils.getExtensionByStringHandling(f.getName());
                if (!"zip".equalsIgnoreCase(ext)){
                    logger.warning("Not an archive: "+f);
                } else {
                    logger.info("New profile instance:\t "+entry.getKey().toFile().getAbsolutePath());
                    // process zip
                    try {
                        XGMPreparationUtils.readZip(f, SVProfiles, otherProfiles, BDProfiles);

                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
        }

        // trigger assembly
        XGMPreparationUtils.reorderModels(SVProfiles, otherProfiles, BDProfiles, xGM);
        try {
            // check if models are complete
            XGMPreparationUtils.checkConsistency(xGM);

            // XGM contains ready-to-use models
            triggerXgmTransformation();

            // clean cache info about ready-to-use models
            for (Map.Entry<Profile,List<Profile>> entry : xGM.entrySet()) {
                SVProfiles.remove(entry.getKey());
                otherProfiles.removeAll(entry.getValue());
                BDProfiles.removeAll(entry.getValue());
            }
            xGM.clear();

        } catch (ParserConfigurationException | SAXException | IOException e){
            e.printStackTrace();
        }


    }

    /**
     *
     * @param k
     */
    private void handleWatchEvents(WatchKey k) {
        List<WatchEvent<?>> events = k.pollEvents();
        for (WatchEvent<?> event : events) {
            handleWatchEvent(event, keys.get(k));
        }
        // reset watch key to allow the key to be reported again by the watch service
        k.reset();
    }

    /**
     *
     * @param event
     * @param dir
     */
    private void handleWatchEvent(WatchEvent<?> event, Path dir) {
        WatchEvent.Kind<?> kind = event.kind();

        WatchEvent<Path> ev = ((WatchEvent<Path>)event);

        Path name = ev.context();
        Path child = dir.resolve(name);
        logger.finest( event.kind().name() + ":" + child);

        if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
            try {
                // Update modified time
                FileTime lastModified = Files.getLastModifiedTime(child);
                expirationTimes.put(child, lastModified.toMillis() + newFileWait);

                // if directory is created, and watching recursively, then register it and its sub-directories
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child)) {
                            walkAndRegisterDirectories(child);
                        }
                    } catch (IOException x) {
                        // do something useful
                    }
                }
            } catch (IOException e){
                logger.severe("Cannot acces last modified time for :"+child);
            }
        }

        if (kind == ENTRY_DELETE) {
            expirationTimes.remove(child);
        }
    }

    /**
     *
     * @param set
     * @param prevHash
     * @return
     */
    private boolean hasChanged(Set set, int prevHash){
        int newHash = set.hashCode();
        return newHash != prevHash;
    }

    /**
     * Process all events for keys queued to the watcher
     */
    private void processEvents() throws InterruptedException{

        for(;;) {
            //Retrieves and removes next watch key, waiting if none are present.
            WatchKey key = watcher.take();
            Path dir = keys.get(key);

            if (dir == null) {
                logger.severe("WatchKey not recognized!!");
                continue;
            }

            for(;;) {
                long currentTime = Instant.now().toEpochMilli();

                if (key!=null)
                    handleWatchEvents(key);

                handleExpiredWaitTimes(currentTime);

                // If there are no files left stop polling and block on .take()
                if(expirationTimes.isEmpty())
                    break;

                long minExpiration = min(expirationTimes.values());
                long timeout = minExpiration-currentTime;
                logger.finest("Timeout: "+timeout);

                // workaround for Windows as poll does not respect the timeout
                if (SystemUtils.IS_OS_WINDOWS){
                    Thread.currentThread().sleep(timeout);
                }
                key = watcher.poll(timeout, TimeUnit.MILLISECONDS);

            }
        }
    }

    @Override
    public void run(){
        try {
            processEvents();
        } catch (InterruptedException e){
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }



}
