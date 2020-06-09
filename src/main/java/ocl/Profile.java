package ocl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Profile{
    public enum Type{
        EQ, TP, SSH, SV, other
    }

    public Type type;
    public String id;
    public List<String> depOn= new ArrayList<>();
    public File file;
    public String xml_name;
    public List<String> DepToBeReplaced= new ArrayList<>();
    public List<String> modelProfile = new ArrayList<>();

    Profile(Type type, String id, List<String> deps, File file, String xmlName, List<String> modelProfile){
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
    static Type getType(String file_name) {
        if (file_name.contains("_SV_")) return Type.valueOf("SV");
        else if (file_name.contains("_SSH_")) return Type.valueOf("SSH");
        else if (file_name.contains("_TP_")) return Type.valueOf("TP");
        else if (file_name.contains("_EQ_")) return Type.valueOf("EQ");
        else if (file_name.contains("BD")) return Type.valueOf("other");
        else return Type.valueOf("other");
    }


}