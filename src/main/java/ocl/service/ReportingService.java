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
import ocl.service.util.Configuration;
import ocl.service.util.Priority;
import ocl.service.util.ReportWriter;
import ocl.service.util.ValidationUtils;
import ocl.service.util.XMLReportWriter;
import ocl.util.EvaluationResult;
import ocl.service.util.XLSReportWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ReportingService extends BasicService implements ReportingListener{

    private Path xlsReportsPath = Configuration.reportsDir.resolve("excelReports");
    private Path xmlReportsPath = Configuration.reportsDir.resolve("xmlReports");

    public ReportingService(){

        super();
        priority = Priority.MEDIUM;

        try {
            if (Files.notExists(xlsReportsPath)){
                Files.createDirectories(xlsReportsPath);
            }
            if (Files.notExists(xmlReportsPath)){
                Files.createDirectories(xmlReportsPath);
            }
        } catch (IOException e){
            e.printStackTrace();
        }

        logger.info("Reporting Service reports directory:" + xlsReportsPath.getParent());

    }

    @Override
    public void run() {

    }

    @Override
    public void enqueueForReporting(Profile p, List<EvaluationResult> errors, String validationType) {

        if (Configuration.generateXLSreports) executorService.submit(new ExcelReportingTask(p, errors, validationType));
        if (Configuration.generateXMLreports) executorService.submit(new XmlReportingTask(p, errors, validationType));

        // debug: display pool size
        if (Configuration.debugMode)
            printPoolSize();

    }

    private class ReportingTask extends PriorityRunnable{

        protected Profile svProfile;
        protected List<EvaluationResult> validationResults;
        protected String validationType;
        protected ReportWriter reportWriter;
        protected Path path;

        ReportingTask(Profile p, List<EvaluationResult> results, String validationType){
            super(priority);
            this.svProfile = p;
            this.validationResults = results;
            this.validationType = validationType;
        }

        public void run()  {
            reportWriter.writeSingleReport(svProfile, validationResults, ValidationUtils.rules, path);
            if (this instanceof ExcelReportingTask)
                logger.info("Wrote XLS report for:\t" + svProfile.xml_name);
            else if (this instanceof XmlReportingTask)
                logger.info("Wrote XML report for:\t" + svProfile.xml_name);
        }
    }

    private class ExcelReportingTask extends ReportingTask{

        ExcelReportingTask(Profile p, List<EvaluationResult> results, String validationType){
            super(p, results, validationType);
            reportWriter = new XLSReportWriter();
            path = xlsReportsPath;
        }

    }


    private class XmlReportingTask extends ReportingTask{

        XmlReportingTask(Profile p, List<EvaluationResult> results, String validationType){
            super(p, results, validationType);
            reportWriter = new XMLReportWriter(validationType);
            path = xmlReportsPath;
        }

    }




}
