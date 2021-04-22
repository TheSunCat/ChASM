package chasm;

import chasm.instructions.Instruction;
import java.util.LinkedHashMap;
import javax.swing.*;

public class ChASM {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                
                new CasmEditorForm().setVisible(true);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
    
    public static final LinkedHashMap<Integer, Instruction> INSTRUCTIONS_MAP = new LinkedHashMap()
    {{
        put(0x00, new Instruction(0x00, "NOP"                                      ));
        put(0x01, new Instruction(0x01, "ADD", "r1"   , "r2", "rOut"               ));
        put(0x02, new Instruction(0x02, "SUB", "r1"   , "r2", "rOut"               ));
        put(0x03, new Instruction(0x03, "MUL", "r1"   , "r2", "rOut"               ));
        put(0x04, new Instruction(0x04, "RGT", "r1"   , "r2", "rOut"               ));
        put(0x05, new Instruction(0x05, "LFT", "r1"   , "r2", "rOut"               ));
        put(0x06, new Instruction(0x06, "LBL", "lId"  , "sAddress"                 ));
        put(0x07, new Instruction(0x07, "JMP", "lId"                               ));
        put(0x08, new Instruction(0x08, "JIF", "lId"  , "#CompType"                ));
        put(0x09, new Instruction(0x09, "MOV", "rTo"  , "#Val"                     ));
        put(0x0A, new Instruction(0x0A, "PSH", "rFrom"                             ));
        put(0x0B, new Instruction(0x0B, "RTR", "rFrom", "rTo"                      ));
        put(0x0C, new Instruction(0x0C, "MTR", "sFrom", "rTo"                      ));
        put(0x0D, new Instruction(0x0D, "RTM", "rFrom", "sTo"                      ));
        put(0x0E, new Instruction(0x0E, "MTM", "sFrom", "sTo"                      ));
        put(0x0F, new Instruction(0x0F, "VTR", "vFrom", "rTo"                      ));
        put(0x10, new Instruction(0x10, "RTV", "rFrom", "vTo"                      ));
        put(0x11, new Instruction(0x11, "FTR", "rTo"                               ));
        put(0x12, new Instruction(0x12, "CMP", "r1"   , "r2"                       ));
        put(0x13, new Instruction(0x13, "AND", "r1"   , "r2", "rOut"               ));
        put(0x14, new Instruction(0x14, "NOT", "rIn"  , "rOut"                     ));
        put(0x15, new Instruction(0x15, "OR" , "r1"   , "r2", "rOut"               ));
        put(0x16, new Instruction(0x16, "XOR", "r1"   , "r2", "rOut"               ));
        put(0x50, new Instruction(0x50, "PXL", "rX"   , "rY", "rCol"               ));
        put(0x51, new Instruction(0x51, "LIN", "rX"   , "rY", "rX1", "rY1", "rCol" ));
        put(0x52, new Instruction(0x52, "PRT", "rX"   , "rY", "rChar"              ));
        put(0x53, new Instruction(0x53, "GMT"                                      ));
        
        // fake shortcut instruction
        put(0xFF, new Instruction(0xFF, "LBL", "lId"                               ));
    }};
}