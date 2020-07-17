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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DependencyHandler extends DefaultHandler
{
    public String getMyId() {
        return myId;
    }

    public void setMyId(String myId) {
        this.myId = myId;
    }

    public List<String> getMyDepOn() {
        return myDepOn;
    }

    public void setMyDepOn(List<String> myDepOn) {
        this.myDepOn = myDepOn;
    }

    public List<String> getModelProfile() {
        return modelProfile;
    }

    public void setModelProfile(List<String> modelProfile) {
        this.modelProfile = modelProfile;
    }

    public boolean isIsmodelProfile() {
        return ismodelProfile;
    }

    public void setIsmodelProfile(boolean ismodelProfile) {
        this.ismodelProfile = ismodelProfile;
    }

    String myId;
    List<String> myDepOn = new ArrayList<String>();
    List<String> modelProfile = new ArrayList<>();
    boolean ismodelProfile = false;

    @Override
    public void startElement(String namespaceURI, String localName, String qname, Attributes atts){
        if(qname.equalsIgnoreCase("md:FullModel")){
            myId =atts.getValue("rdf:about");
        }
        else if(qname.equalsIgnoreCase("md:Model.DependentOn")){
            myDepOn.add(atts.getValue("rdf:resource"));
        }
        else if(qname.equalsIgnoreCase("md:Model.profile")){
            ismodelProfile = true;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (ismodelProfile) {
            modelProfile.add(new String(ch, start, length));
            ismodelProfile=false;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equalsIgnoreCase("md:FullModel")) {
            throw new DoneParsingException();
        }
    }

    public class DoneParsingException extends SAXException{

    }

}