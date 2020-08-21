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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Profile{
    public enum Type{
        EQ, TP, SSH, SV, EQBD, TPBD, other
    }

    public Type type;
    public String id;
    public List<String> depOn= new ArrayList<>();
    public File file;
    public String xml_name;
    public List<String> DepToBeReplaced= new ArrayList<>();
    public List<String> modelProfile = new ArrayList<>();

    public Profile(Type type, String id, List<String> deps, File file, String xmlName, List<String> modelProfile){
        this.type = type;
        this.id = id;
        this.depOn = deps;
        this.file = file;
        this.xml_name = xmlName;
        this.modelProfile=modelProfile;
    }

    /**
     *
     * @param file_name
     * @return
     */
    public static Type getType(String file_name) {
        if (file_name.contains("_SV_")) return Type.SV;
        else if (file_name.contains("_SSH_")) return Type.SSH;
        else if (file_name.contains("_TP_")) return Type.TP;
        else if (file_name.contains("_EQ_")) return Type.EQ;
        else if (file_name.contains("_EQBD_")) return Type.EQBD;
        else if (file_name.contains("_TPBD")) return Type.TPBD;
        else return Type.other;
    }


}