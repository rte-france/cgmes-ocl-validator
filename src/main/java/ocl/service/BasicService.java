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
 *       (c) RTE 2020
 *       Authors: Marco Chiaramello, Jerome Picault
 **/
package ocl.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class BasicService implements Runnable {

    static Logger logger = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        logger = Logger.getLogger(BasicService.class.getName());

    }

    protected static int MAX_POOL = Runtime.getRuntime().availableProcessors();
    protected static int CORE_POOL = MAX_POOL-1;
    protected static int QUEUE_SIZE = 100;

    // The executorService is shared between the different services as thu=is is not possible to share a thread pool
    // among distinct executor services
    protected ExecutorService executorService = null;


    public BasicService(){
        // initialization parallel service management
        initializePool();
    }

    private void initializePool(){
        if (executorService == null){
            executorService = new ThreadPoolExecutor(CORE_POOL, MAX_POOL,
                    0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue(QUEUE_SIZE));
        }
    }

    protected void printPoolSize(){
        logger.info("Pool size is now: " + ((ThreadPoolExecutor)executorService).getActiveCount());
    }

}
