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

import ocl.service.util.Priority;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
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

    protected static final int MAX_POOL = Runtime.getRuntime().availableProcessors();
    protected static final int CORE_POOL = MAX_POOL-1;

    protected Priority priority = Priority.MEDIUM;

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
                    new PriorityBlockingQueue<>()) {

                @Override
                protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
                    return new ComparableFutureTask<T>(runnable, value);
                }

                @Override
                protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                    return new ComparableFutureTask<T>(callable);
                };
            };
        }
    }

    public class ComparableFutureTask<T> extends FutureTask<T>
            implements Comparable<Object> {

        private final Comparable<Object> comparableJob;

        @SuppressWarnings("unchecked")
        public ComparableFutureTask(Runnable runnable, T value) {
            super(runnable, value);
            this.comparableJob = (Comparable<Object>) runnable;
        }

        @SuppressWarnings("unchecked")
        public ComparableFutureTask(Callable<T> callable) {
            super(callable);
            this.comparableJob = (Comparable<Object>) callable;
        }

        @Override
        public int compareTo(Object o) {
            return this.comparableJob
                    .compareTo(((ComparableFutureTask<?>)o).comparableJob);
        }
    }


    public abstract class PriorityCallable implements Callable, Comparable<PriorityCallable>{
        private Priority priority;

        public PriorityCallable(Priority priority){
            this.priority = priority;
        }

        @Override
        public int compareTo(PriorityCallable other) {
            // we want higher priority to go first
            return other.priority.getValue() - this.priority.getValue();
        }

    }

    public abstract class PriorityRunnable implements Runnable, Comparable<PriorityRunnable>{
        private Priority priority;

        public PriorityRunnable(Priority priority){
            this.priority = priority;
        }

        @Override
        public int compareTo(PriorityRunnable other) {
            // we want higher priority to go first
            return other.priority.getValue() - this.priority.getValue();
        }

    }

    protected void printPoolSize(){
        logger.info("Pool size is now: " + ((ThreadPoolExecutor)executorService).getActiveCount());
    }

}
