package ocl.service.util;

public enum Priority {

    HIGHEST(2),
    HIGH(1),
    MEDIUM(0),
    LOW(-1),
    LOWEST(-2);

    int value;

    Priority(int val) {
        this.value = val;
    }

    public int getValue(){
        return value;
    }


}