package ocl.util;

public class EvaluationResult{

    private int severity;
    private String rule;

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String type;
    private String id;
    private String name;

    public EvaluationResult(int severity, String rule, String type, String id, String name) {
        this.severity = severity;
        this.rule = rule;
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public String toString(){
        String s = String.format("%-35s: %-25s %-37s %-30s", this.rule, this.type, this.id, (name!=null)?name:"");
        return s;
    }

}

