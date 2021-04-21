package chasm.compiler;

import chasm.ChASM;
import chasm.instructions.*;
import java.util.*;

public class Assembler {
    
    private static byte[] bytes = null;
    
    private static Thread t;
    private static ArrayList<Instruction> instructions = new ArrayList();
    private static String text;
    private static char c;
    private static int index = -1;
    
    public static void assemble(String source) {
        bytes = null;
        
        if(t != null && t.isAlive())
            t.stop();
        
        t = new Thread(() -> {
            bytes = toByteCode(source);
        }, "Assembler-Work Thread");
        
        t.start();
    }
    
    public static boolean assembling() {
        return t == null ? false : t.isAlive();
    }
    
    public static byte[] getByteCode() {
        if(t == null || t.isAlive()) {
            System.err.println("Did not wait for assembler...");
            return null;
        }
        
        return bytes;
    }
    
    private static byte[] toByteCode(String casm) {
        text = prepare(casm);
        instructions.clear();
        c = ' ';
        index = -1;
        getChar();
        
        
        /***************************************************
         * First Pass - Move all LBLs to the beginning.
         **************************************************/
        
        String topLbls = "";
        ArrayList<Instruction> pastInsts = new ArrayList();
        
        while(index + 1  < text.length()) {
            int startIndex = index;
            Instruction curInst = instruction();
            int instSize = index - startIndex;
            
            pastInsts.add(curInst);
            
            if(curInst.id == (byte) 0xFF) {
                topLbls += "LBL(L" + curInst.args[0].getValue() + ",#" + sizeOf(pastInsts) + ")";
                
                // cut out the special lbl instruction
                index -= instSize;
                String text1 = text.substring(0, index);
                String text2 = text.substring(index + instSize, text.length());
                
                text = text1 + text2;
            }
        }
        
        text = topLbls + text;
        
        System.out.println("\n\n" + text);
        
        instructions.clear();
        c = ' ';
        index = -1;
        getChar();
        
        /*******************************************
         * Second Pass - Parse all instructions.
         ******************************************/
        
        // parse the instruction from the current line
        while(c != '.' && index < text.length()) {
            instructions.add(instruction());
        }

        /**********************
         * Generate bytecode.
         *********************/

        // Dabbed AF (header)
        ArrayList<Byte> byteCode = new ArrayList(Arrays.asList(
                (byte) 0xDA, (byte) 0xBB, (byte) 0xED, (byte) 0xAF));

        // padding
        for(int i = 0; i < 0xC; i++)
            byteCode.add((byte) 0xAB);

        // turn instructions into bytecode
        for(Instruction curInstruction : instructions)
            byteCode.addAll(curInstruction.toByteCode());

        // convert array for returning
        byte[] byteCodeArray = new byte[byteCode.size()];
        for(int i = 0; i < byteCode.size(); i++) {
            byte b = byteCode.get(i);
            byteCodeArray[i] = b;
        }
        
        return byteCodeArray;
    }
    
    private static int sizeOf(ArrayList<Instruction> insts) {
        int size = 0;
        
        for(Instruction inst : insts) {
            if(inst.id == (byte) 0xFF)
                size += 4; // special lbl instruction
            else
                size += inst.toByteCode().size();
        }
        
        return size;
    }
    
    private static void encountered(Instruction inst, String expectedArg, String foundType) {
        error("Expected argument of type " + expectedArg + " but found a " + foundType + " argument."
                        + "\nIn instruction " + inst.fullName() + "!");
    }
    
    public static void error(String text) {
        System.err.println(text);
        t.stop();
    }
    
    private static String getName() {
        String name = "";
        
        while(c >= 'A' && c <= 'Z') {
            name += Character.toUpperCase(c);
            getChar();
        }
        
        return name;
    }
    
    private static void getChar() {
        if(++index >= text.length())
            error("Reached end of code unexpectedly!");
        
        c = text.charAt(index);
    }
    
    private static void match(char m) {
        if(c != m)
            error("Expected '" + m + "'");
        
        getChar();
    }
    
    private static byte getByte() {
        String num = "";
        while(c >= '0' && c <= '9') {
            num += c;
            getChar();
        }
        
        if(num.isEmpty())
            error("Expected byte.");
        
        int i = Integer.parseInt(num);
        
        if(i < 0 || i > 255)
            error("Expected byte value within 0 and 255.");
        
        return (byte) i;
    }
    
    private static int getShort() {
        String num = "";
        while(c >= '0' && c <= '9') {
            num += c;
            getChar();
        }
        
        if(num.isEmpty())
            error("Expected short.");
        
        int i = Integer.parseInt(num);
        
        if(i < 0 || i > 65535)
            error("Expected short value within 0 and 65,535.");
        
        return i;
    }
    
    /** Parse an instruction from a line of text. **/
    private static Instruction instruction() {
        ArrayList<Argument> arguments = new ArrayList();
        
        // get instruction name
        String instructionName = getName();
        
        if(instructionName.isEmpty())
            error("Expected instruction name.");
        
        Instruction identifier = getInstructionKey(instructionName);
        
        if(identifier == null)
            error("Unknown instruction: " + instructionName + ".");
        
        match('(');
        
        int argsFound = 0;
        int curUsedReg = 1; // used when a number arg is entered where a register is expected
                            // a MOV to a temp register is inserted and then that register is used
        
        while (c != ')') {
            String curArgName = identifier.args[argsFound].getName();
            
            if(curArgName.equals("#CompType") && c != '#') {
                String compName = "";
                while((c >= 'A' && c <= 'Z') || c == '_') {
                    compName += c;
                    getChar();
                }
                
                Relation rel = Relation.fromString(compName);
                
                if(rel == null)
                    error("Unknown relation name " + compName + ".");
                
                arguments.add(new Argument(curArgName, rel.getId()));
            } else {
                switch (c) {
                    case 'C': // color arg
                        match('C');

                        if(!curArgName.equals("rCol"))
                            encountered(identifier, curArgName, "color");

                        match('(');

                        byte r = (byte) (getByte() / 36.428f);
                        match(',');

                        byte g = (byte) (getByte() / 36.428f);
                        match(',');

                        byte b = (byte) (getByte() / 64     );
                        match(')');

                        byte rgb = (byte) ((r << 5) | (g << 2) | b); // RRR GGG BB

                        instructions.add(new Instruction(ChASM.INSTRUCTIONS_MAP.get(0x09), new Argument[] {
                            new Argument("rTo", curUsedReg),
                            new Argument("#Val", rgb)
                        })); // MOV the color to an unused reg

                        arguments.add(new Argument(curArgName, curUsedReg++));

                        break;
                    case '#': // number arg
                        match('#');

                        if(!curArgName.startsWith("#") && !curArgName.startsWith("s")) {
                            instructions.add(new Instruction(ChASM.INSTRUCTIONS_MAP.get(0x09), new Argument[] {
                                        new Argument("rTo", curUsedReg),
                                        new Argument("#Val", getByte())
                                    })); // MOV the value to an unused reg

                            arguments.add(new Argument(curArgName, curUsedReg++));
                        } else {
                            arguments.add(new Argument(curArgName, (curArgName.startsWith("s") ? getShort() : getByte())));
                        }

                        break;
                    case 'S': // pop stack
                        if(!(curArgName.startsWith("r")))
                            encountered(identifier, curArgName, "pop stack");

                        match('S');
                        match('P');
                        match('+');

                        arguments.add(new Argument(curArgName, 10));

                        break;
                    case 'R':  // register argument
                        match('R');

                        if(!curArgName.startsWith("r"))
                            encountered(identifier, curArgName, "register");

                        if(!(c >= '0' && c <= '7'))
                            error("Invalid register ID " + c + ".");

                        arguments.add(new Argument(curArgName, Integer.parseInt("" + c)));

                        getChar();

                        break;
                    case 'L': // label
                        match('L');

                        if(!curArgName.startsWith("l"))
                            encountered(identifier, curArgName, "label");

                        arguments.add(new Argument(curArgName, getByte()));
                        break;
                    case 'V': // variable ID
                        match('V');

                        if(!curArgName.startsWith("v"))
                            encountered(identifier, curArgName, "variable");

                        arguments.add(new Argument(curArgName, getByte()));
                        break;
                    default:
                        error("Encountered unknown argument type " + c + " while parsing " + identifier + ".");
                }
            }
            
            argsFound++;
            
            if(argsFound > identifier.nargs)
                error("Too many arguments found for instruction " + identifier + ".");
            
            if(c == ')')
                break;
            
            match(',');
        }
        
        match(')');
        
        Instruction ret;
        
        if(instructionName.equals("LBL") && arguments.size() < 2) {
            // shortcut lbl, needs to be moved to beginning w/ address arg
            ret = new Instruction(0xFF, instructionName, arguments.toArray(new Argument[arguments.size()]));
            
        } else {
            if(argsFound < identifier.nargs)
            {
                int needed = identifier.nargs - argsFound;
                error("Instruction " + identifier + " is missing " + needed + " argument" + (needed != 1 ? "s." : "."));
            }
            
            ret = new Instruction(identifier, arguments.toArray(new Argument[arguments.size()]));
        }
        
        return ret;
    }
    
    /** Remove spaces and comments for parsing instructions. **/
    private static String prepare(String text) {
        text = text.replace(" " , ""); // remove all spaces
        
        text = text.replace("\n", "\n ");
        String[] lines = text.split("\n");
        
        text = "";
        // remove comment if there is one
        for(String line : lines) {
            if(line.length() > 1) {
                for(int i = 0; i < line.length() - 1; i++) {
                    if(line.charAt(i) == '/'
                            && line.charAt(i + 1) == '/') {
                        line = line.substring(0, i);
                        break;
                    }
                }
            }
            
            text += line.trim();
        }
        
        return text.toUpperCase() + '.'; // add termination char
    }
    
    /** Helper method for parsing instructions.
     * @param instructionName the name of the instruction
     * @return an instruction from the documented list based on the name**/
    public static Instruction getInstructionKey(String instructionName) {
        Instruction ret = null;
        
        for(Instruction i : ChASM.INSTRUCTIONS_MAP.values()) {
            if(i.name.toUpperCase().equals(instructionName)) {
                ret = i;
                break;
            }
        }
        
        return ret;
    }
    
    private enum Relation {
        Equal, Greater, GreaterEqual, Less, LessEqual, NotEqual;
        
        public Relation getOpposite() {
            Relation ret;
            
            switch(this) {
                case Equal:
                    ret = NotEqual;
                    break;
                case Greater:
                    ret = LessEqual;
                    break;
                case GreaterEqual:
                    ret = Less;
                    break;
                case Less:
                    ret = GreaterEqual;
                    break;
                case LessEqual:
                    ret = Greater;
                    break;
                default:
                    ret = Equal;
            }
            
            return ret;
        }
        
        public int getId() {
            int ret;
            
            switch(this) {
                case Equal:
                    ret = 0;
                    break;
                case Greater:
                    ret = 1;
                    break;
                case GreaterEqual:
                    ret = 2;
                    break;
                case Less:
                    ret = 3;
                    break;
                case LessEqual:
                    ret = 4;
                    break;
                default:
                    ret = 5;
            }
            
            return ret;
        }
        
        public String getOp() {
            String ret;
            
            switch(this) {
                case Equal:
                    ret = "==";
                    break;
                case Greater:
                    ret = ">";
                    break;
                case GreaterEqual:
                    ret = ">=";
                    break;
                case Less:
                    ret = "<";
                    break;
                case LessEqual:
                    ret = "<=";
                    break;
                default:
                    ret = "!=";
            }
            
            return ret;
        }
        
        public static Relation fromString(String s) {
            Relation ret = null;
            
            switch(s) {
                case "EQUAL":
                    ret = Equal;
                    break;
                case "GREATER":
                    ret = Greater;
                    break;
                case "GREATER_EQUAL":
                    ret = GreaterEqual;
                    break;
                case "LESS":
                    ret = Less;
                    break;
                case "LESS_EQUAL":
                    ret = LessEqual;
                    break;
                case "NOT_EQUAL":
                    ret = NotEqual;
            }
            
            return ret;
        }
        
        @Override
        public String toString() {
            String ret;
            switch(this) {
                case Equal:
                    ret = "EQUAL";
                    break;
                case Greater:
                    ret = "GREATER";
                    break;
                case GreaterEqual:
                    ret = "GREATER_EQUAL";
                    break;
                case Less:
                    ret = "LESS";
                    break;
                case LessEqual:
                    ret = "LESS_EQUAL";
                    break;
                default:
                    ret = "NOT_EQUAL";
            }
            
            return ret;
        }
    }
}
