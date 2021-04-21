package chasm;

import java.io.*;
import java.nio.file.Files;
import java.util.logging.*;

public class Settings {
    public static boolean autoSave = true;
    public static boolean syntaxHighlight = true;
    
    /**
     * Load settings from config file in %AppData%/ChASM/config
     */
    public static void init() {
        if(!configFile.exists())
                return;
        
        try {
            byte[] config = Files.readAllBytes(configFile.toPath());
            
            if(config.length != 1)
                System.err.println("Config file is corrupted!");
            
            // load booleans from bits
            autoSave        = ((config[0]     ) & 0x1) == 1;
            syntaxHighlight = ((config[0] >> 1) & 0x1) == 1;
            
        } catch (IOException ex) {
            Logger.getLogger(Settings.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void save() {
        try {
            FileOutputStream fos = new FileOutputStream(configFile);
            
            byte autoSaveBit        = (byte) (autoSave        ? 0x1 : 0x0);
            byte syntaxHighlightBit = (byte) (syntaxHighlight ? 0x1 : 0x0);
            
            // compose bit out of options
            byte config = 0;
            config |= autoSaveBit;
            config |= syntaxHighlightBit << 1;
            
            fos.write(config);
        } catch (IOException ex) {
            Logger.getLogger(Settings.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static File configFile = new File(System.getenv("AppData") + "\\ChASM\\config");
}
