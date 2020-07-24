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

import ocl.service.TransformationService;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.util.HashMap;
import java.util.List;

import static ocl.service.util.TransformationUtils.getSimpleNameNoExt;

/**
 *
 */
public class XMITransformation extends TransformationService {

    /**
     *
     * @param IGM_CGM
     * @return
     * @throws SAXException
     * @throws ParserConfigurationException
     */
     HashMap<String, Document> convertData(HashMap<Profile,List<Profile>> IGM_CGM) {

        HashMap<String,Document> xmi_map = new HashMap<>();

        IGM_CGM.entrySet().parallelStream().forEach(entry->{

            Document resulting_xmi = singleTransformation(entry);
            if (resulting_xmi!=null){
                xmi_map.put(getSimpleNameNoExt(entry.getKey()),resulting_xmi);
            }

        });

        return xmi_map;

    }


}
