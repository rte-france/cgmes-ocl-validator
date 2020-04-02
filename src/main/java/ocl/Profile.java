package ocl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class Profile{
    enum Type{
        EQ, TP, SSH, SV, other
    }

    Type type;
    String id;
    List<String> depOn= new ArrayList<>();
    File file;
    String xml_name;
    List<String> DepToBeReplaced= new ArrayList<>();

    Profile(Type type, String id, List<String> deps, File file, String xmlName){
        this.type = type;
        this.id = id;
        this.depOn = deps;
        this.file = file;
        this.xml_name = xmlName;
    }

    /**
     *
     * @param file_name
     * @return
     */
    static Type getType(String file_name) {
        if (file_name.contains("_SV_")) return Type.valueOf("SV");
        else if (file_name.contains("_SSH_")) return Type.valueOf("SSH");
        else if (file_name.contains("_TP_")) return Type.valueOf("TP");
        else if (file_name.contains("_EQ_")) return Type.valueOf("EQ");
        else if (file_name.contains("BD")) return Type.valueOf("other");
        else return Type.valueOf("other");
    }


}