package chasm.instructions;

import static chasm.MathUtils.*;
import java.util.ArrayList;

public class Instruction {
    /**
     * Instruction constructor
     * @param reference null arg instruction to copy name and bytecode from
     * @param _args arguments to store
     */
    public Instruction(Instruction reference, Argument... _args) {
        id = reference.id;
        name = reference.name;
        
        for(int i = 0; i < reference.args.length; i++) {
            if(!reference.args[i].getName().equals(_args[i].getName()))
                throw new IllegalArgumentException("Argument " + _args[i].getName()
                        + " does not match source instruction's matching argument's name ("
                        + reference.args[i].getName() + ").");
        }
        
        nargs = _args.length;
        args = _args.clone();
    }
    
    /**
     * Instruction constructor
     * @param _id instruction ID
     * @param _name instruction name
     * @param _args arguments to store
     */
    public Instruction(int _id, String _name, Argument... _args) {
        id = (byte) _id;
        name = _name;
        nargs = _args.length;
        
        args = _args.clone();
    }
    
    /** "Reference" Instruction constructor */
    public Instruction(int _id, String _name, String... argNames) {
        id = (byte) _id;
        name = _name;
        nargs = argNames.length;
        
        args = new Argument[argNames.length];
        for(int i = 0; i < argNames.length; i++) {
            String curName = argNames[i];
            args[i] = new Argument(curName);
        }
    }
    
    /** "Reference" Instruction constructor with no arguments */
    public Instruction(int _id, String _name) {
        id = (byte) _id;
        name = _name;
        nargs = 0;
        args = new Argument[0];
    }
    
    public String toCode() {
        String ret = name + "(";
        
        for(Argument arg : args)
            ret += arg.toString() + ", ";
        
        if(args.length > 0)
            ret = ret.substring(0, ret.length() - 2); // cut off the extra ", "
        ret += ")";
        
        return ret;
    }
    
    @Override
    public String toString() {
        return fullName();
    }
    
    public String fullName() {
        String ret = name + "(";
        for(Argument arg : args)
            ret += arg.getName() + ", ";
        
        if(args.length > 0)
            ret = ret.substring(0, ret.length() - 2); // cut off the extra ", " at the end
        ret += ")";
        
        return ret;
    }
    
    public ArrayList<Byte> toByteCode() {
        ArrayList<Byte> ret = new ArrayList();
        ret.add(id);
        
        for (Argument arg : args) {
            if(arg.getName().startsWith("s")) {
                ret.add(getLeftByte ((char) arg.getValue()));
                ret.add(getRightByte((char) arg.getValue()));
            } else {
                ret.add((byte) arg.getValue());
            }
        }
        
        return ret;
    }
    
    public byte id;
    public String name;
    public Argument[] args;
    public int nargs;
}
