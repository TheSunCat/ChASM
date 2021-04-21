package chasm.instructions;

public class Argument {
    public Argument(String name, Object value) {
        argName = name;
        argVal = value;
    }
    
    public Argument(String name) {
        argName = name;
    }
    
    public String getName() {
        return argName;
    }
    
    public char getPrefix() {
        return argName.charAt(0);
    }
    
    public int getValue() {
        if(argVal instanceof Byte)
            return Byte.toUnsignedInt((byte) argVal);
        else if(argVal instanceof Character)
            return (int) ((Character) argVal);
        else
            return (int) argVal;
    }
    
    @Override
    public String toString() {
        if(argVal == null)
            return argName;
        else
            return argName.charAt(0) + ("" + (argVal instanceof Character ? (int) (Character) argVal: argVal));
    }
    
    private final String argName;
    private Object argVal;
}
