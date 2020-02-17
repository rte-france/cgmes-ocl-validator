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
package ocl.util;

import ocl.OCLEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class XLSWriter {

    private static Logger LOGGER = null;
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tF %1$tT] [%4$-7s] %5$s %n");
        LOGGER=Logger.getLogger(OCLEvaluator.class.getName());
    }

    private XSSFCellStyle coloredCell(XSSFWorkbook wb, Color color){
        XSSFCellStyle style1 = wb.createCellStyle();
        style1.setFillForegroundColor(new XSSFColor(color));
        style1.setFillPattern(CellStyle.SOLID_FOREGROUND);
        return style1;
    }


    public void writeResults(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, File path){
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFCellStyle redStyle = coloredCell(workbook, Color.RED);
        XSSFCellStyle orangeStyle = coloredCell(workbook, Color.ORANGE);
        for (String k : synthesis.keySet()){
            XSSFSheet sheet = workbook.createSheet(k);
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
            for (EvaluationResult res : synthesis.get(k)){
                String infringedRule = res.getRule();
                row = sheet.createRow(rowNum++);
                colNum = 0;
                cell = row.createCell(colNum++);
                String severity = "UNKNOWN";
                if (rules.get(infringedRule)!=null)
                    severity = rules.get(infringedRule).getSeverity();
                cell.setCellValue(severity);
                if (severity.equalsIgnoreCase("ERROR"))
                    cell.setCellStyle(redStyle);
                else if (severity.equalsIgnoreCase("WARNING"))
                    cell.setCellStyle(orangeStyle);
                cell = row.createCell(colNum++);
                cell.setCellValue(infringedRule);
                cell = row.createCell(colNum++);
                if (res.getLevel()==null)
                    cell.setCellValue(0);
                else cell.setCellValue(res.getLevel());
                cell = row.createCell(colNum++);
                cell.setCellValue(res.getType());
                cell = row.createCell(colNum++);
                cell.setCellValue(res.getId());
                cell = row.createCell(colNum++);
                String name = res.getName();
                cell.setCellValue(name==null?"":name);
                cell = row.createCell(colNum++);
                String message = res.getName();
                if (rules.get(infringedRule)==null)
                    cell.setCellValue("");
                else
                    cell.setCellValue(rules.get(infringedRule).getMessage());

            }
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, colNum-1));
            for (int i = 1; i < colNum; i++)
                sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 1);
        }

        try {
            FileOutputStream outputStream = new FileOutputStream(path);
            workbook.write(outputStream);
            workbook.close();
            LOGGER.info("Excel created: " + path);
        } catch (Exception e) {
            LOGGER.severe("Excel creation failed");
            e.printStackTrace();
        }
    }

    public void writeUnknownRulesReport(Map<String, List<EvaluationResult>> synthesis, HashMap<String, RuleDescription> rules, File path){
        Set<String> unknownRulesSet = new HashSet<String>();
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
            LOGGER.info("Excel created: " + path);
        } catch (Exception e) {
            LOGGER.severe("Debug Excel creation failed");
            e.printStackTrace();
        }

    }

}
