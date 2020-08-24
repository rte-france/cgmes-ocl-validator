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
import ocl.util.EvaluationResult;
import ocl.util.RuleDescription;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static ocl.util.IOUtils.trimExtension;

public class XLSReportWriter implements ReportWriter {

    /**
     *
     * @param wb
     * @param color
     * @return
     */
    private XSSFCellStyle coloredCell(XSSFWorkbook wb, short color){
        XSSFCellStyle style1 = wb.createCellStyle();
        style1.setFillForegroundColor(color);
        style1.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style1;
    }


    /**
     *
     * @param p
     * @param results
     * @param rules
     * @param path
     */
    @Override
    public void writeSingleReport(Profile p, List<EvaluationResult> results, HashMap<String, RuleDescription> rules, Path path) {
        String key = trimExtension(p.xml_name);
        writeSingleReport(key, results, rules, path);
    }

    public void writeSingleReport(String key, List<EvaluationResult> results, HashMap<String, RuleDescription> rules, Path path) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFCellStyle redStyle = coloredCell(workbook, IndexedColors.RED.getIndex());
            XSSFCellStyle orangeStyle = coloredCell(workbook, IndexedColors.LIGHT_ORANGE.getIndex());
            XSSFSheet sheet = workbook.createSheet(key);
            int rowNum = 0;
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            Cell cell = row.createCell(colNum++);
            cell.setCellValue("SEVERITY");
            cell = row.createCell(colNum++);
            cell.setCellValue("RULE");
            cell = row.createCell(colNum++);
            cell.setCellValue("LEVEL");
            cell = row.createCell(colNum++);
            cell.setCellValue("OBJECT");
            cell = row.createCell(colNum++);
            cell.setCellValue("ID");
            cell = row.createCell(colNum++);
            cell.setCellValue("NAME");
            cell = row.createCell(colNum++);
            cell.setCellValue("MESSAGE");
            for (EvaluationResult res : results) {
                String infringedRule = res.getRule();
                row = sheet.createRow(rowNum++);
                colNum = 0;
                cell = row.createCell(colNum++);
                String severity = "UNKNOWN";
                if (rules.get(infringedRule) != null)
                    severity = rules.get(infringedRule).getSeverity();
                cell.setCellValue(severity);
                if (severity.equalsIgnoreCase("ERROR"))
                    cell.setCellStyle(redStyle);
                else if (severity.equalsIgnoreCase("WARNING"))
                    cell.setCellStyle(orangeStyle);
                cell = row.createCell(colNum++);
                cell.setCellValue(infringedRule);
                cell = row.createCell(colNum++);
                if (res.getLevel() == null)
                    cell.setCellValue(0);
                else cell.setCellValue(res.getLevel());
                cell = row.createCell(colNum++);
                cell.setCellValue(res.getType());
                cell = row.createCell(colNum++);
                cell.setCellValue(res.getId());
                cell = row.createCell(colNum++);
                String name = res.getName();
                cell.setCellValue(name == null ? "" : name);
                cell = row.createCell(colNum++);

                if (rules.get(infringedRule) == null)
                    cell.setCellValue("");
                else {
                    String message = res.getSpecificMessage() != null ? rules.get(infringedRule).getMessage() + " " + res.getSpecificMessage() : rules.get(infringedRule).getMessage();
                    cell.setCellValue(message);
                }

            }
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, colNum - 1));
            IntStream.range(0, colNum).forEach((int i) -> sheet.autoSizeColumn(i));
            sheet.createFreezePane(0, 1);


            try {
                FileOutputStream outputStream = new FileOutputStream(path + File.separator + key + ".xlsx");
                workbook.write(outputStream);
                workbook.close();
                outputStream.close();
            } catch (Exception e) {
                logger.severe("Excel creation failed for " + key);
                e.printStackTrace();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     *
     * @param synthesis
     * @param rules
     * @param path
     */
    public void writeResultsPerIGM(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, Path path){
        logger.info("Creating reports...");

        if (Files.notExists(path)){
            try {
                Files.createDirectories(path);
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        synthesis.entrySet().parallelStream().forEach(e->{

            writeSingleReport(e.getKey(), e.getValue(), rules, path);

        });

        logger.info("All excel reports created");
    }


    /**
     *
     * @param synthesis
     * @param rules
     * @param path
     */
    public void writeUnknownRulesReport(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, File path){
        Set<String> unknownRulesSet = new HashSet<>();
        for (String k : synthesis.keySet()){
            for (EvaluationResult res : synthesis.get(k)) {
                String infringedRule = res.getRule();
                if (rules.get(infringedRule)!=null) {
                    String severity = rules.get(infringedRule).getSeverity();
                    if (severity.equalsIgnoreCase("UNKNOWN"))
                        unknownRulesSet.add(infringedRule);
                }
                else{
                    unknownRulesSet.add(infringedRule);
                }

            }

        }

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("UnknownRules");
        int rowNum = 0;
        Row row = sheet.createRow(rowNum++);
        int colNum = 0;
        Cell cell = row.createCell(colNum++);
        cell.setCellValue("SEVERITY");
        cell = row.createCell(colNum++);
        cell.setCellValue("RULE");
        for(String unknown : unknownRulesSet){
            row = sheet.createRow(rowNum++);
            colNum = 0;
            cell = row.createCell(colNum++);
            cell.setCellValue("UNKNOWN");
            cell = row.createCell(colNum++);
            cell.setCellValue(unknown);
        }
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, colNum-1));
        for (int i = 1; i < colNum; i++)
            sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 1);

        try {
            FileOutputStream outputStream = new FileOutputStream(path);
            workbook.write(outputStream);
            workbook.close();
            outputStream.close();
            logger.info("Excel created: " + path);
        } catch (Exception e) {
            logger.severe("Debug Excel creation failed");
            e.printStackTrace();
        }

    }

}
