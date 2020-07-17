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

import ocl.Profile;
import ocl.util.EvaluationResult;
import ocl.util.RuleDescription;
import ocl.util.XLSWriter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

import static ocl.service.util.ServiceUtils.trimExtension;

public class ReportingService extends BasicService implements ReportingListener{

    public ReportingService(){
        super();
    }

    @Override
    public void run() {

    }

    @Override
    public void enqueueForReporting(Profile p, List<EvaluationResult> errors) {

        executorService.submit(new ExcelReportingTask(p, errors));
        executorService.submit(new XmlReportingTask(p, errors));


        //FIXME
        printPoolSize();

    }

    private abstract class ReportingTask implements Runnable{

        protected Profile svProfile;
        protected List<EvaluationResult> validationResults;

        ReportingTask(Profile p, List<EvaluationResult> results){
            this.svProfile = p;
            this.validationResults = results;
        }

    }

    private class ExcelReportingTask extends ReportingTask{

        ExcelReportingTask(Profile p, List<EvaluationResult> results){
            super(p, results);
        }

        @Override
        public void run()  {

            XLSWriter writer = new XLSWriter();
            //TODO
            //FIXME - path
            String key = trimExtension(svProfile.xml_name);
            writer.writeSingleReport(key, validationResults, ValidationService.rules, new File("."));
            logger.info("Wrote report:\t" + key);

        }
    }


    private class XmlReportingTask extends ReportingTask{

        XmlReportingTask(Profile p, List<EvaluationResult> results){
            super(p, results);
        }

        @Override
        public void run() {
            //FIXME
            //TODO - export compliant with QAS portal
        }
    }




}
