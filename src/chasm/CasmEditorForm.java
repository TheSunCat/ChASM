package chasm;

import static chasm.ChASM.INSTRUCTIONS_MAP;
import static chasm.MathUtils.bytesToShort;
import chasm.compiler.ThreadedCompiler;
import chasm.instructions.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.text.*;

public class CasmEditorForm extends javax.swing.JFrame {
    File file;
    String localPath = java.nio.file.FileSystems
            .getDefault().getPath(".").toAbsolutePath().toString();
    
    public CasmEditorForm() throws IOException, FontFormatException {
        Settings.init();
        
        initComponents();
        
        codeHintsTimer = new javax.swing.Timer(500, (java.awt.event.ActionEvent e) -> {
            errArea.setText("");
            
            if(!compiler.compiling())
                compiler.compile(editor.getText());
        });
        
        saveTimer = new javax.swing.Timer(7000, (java.awt.event.ActionEvent e) -> {
            autoSave();
        }); // Autosave every 7 seconds
        
        codeHintsTimer.setRepeats(false);
        saveTimer.setRepeats(false);
        
        // Load autosaved file
        File f = new File(System.getenv("AppData") + "\\ChASM\\session");
        
        if(Settings.autoSave && f.exists()) {
            byte[] b = Files.readAllBytes(f.toPath());
            
            String str = new String(b, "UTF-8");
            
            int headLen = str.charAt(0);
            
            int caret = 0;
            
            if(!(headLen < 1 || headLen > 5)) {
                for(int i = 1; i < headLen; i++) {
                    char c = str.charAt(i);

                    caret |= ((c & 0xFF) << 8 * (i - 1));
                }
                
                if(caret > str.length() - headLen)
                    caret = str.length() - headLen;

                editor.setText(str.substring(headLen));
                editor.setCaretPosition(caret);

                change();

                lbStatusBar.setText("Recovered code sucessfully!");
            } else {
                System.err.println("Failed to recover code: corrupt header.");
            }
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                initUI();
            } catch (IOException | FontFormatException ex) {
                Logger.getLogger(CasmEditorForm.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        
        commentStyle = ((StyledDocument) editor.getDocument()).addStyle("comment", null);
        StyleConstants.setForeground(commentStyle, java.awt.Color.gray);
        
        keywordStyle = ((StyledDocument) editor.getDocument()).addStyle("keyword", null);
        StyleConstants.setForeground(keywordStyle, new java.awt.Color(0xCC7832)); // orangeish
        
        highlightStyle = ((StyledDocument) editor.getDocument()).addStyle("highlight", null);
        StyleConstants.setBackground(highlightStyle, new java.awt.Color(0x3B514D)); // greenish
        StyleConstants.setForeground(highlightStyle, new java.awt.Color(0xE0D53A)); // yellowish
        
        errorStyle = ((StyledDocument) editor.getDocument()).addStyle("error", null);
        StyleConstants.setBackground(errorStyle, new java.awt.Color(0xD1243B)); // redish
        
        file = null;
        setTitle("ChASM - Editing untitled.chasm");
        
        System.setErr(new PrintStream(new JOutputStream(errArea))); // redirect error output
    }
    
    /** Syntax highlighting. */
    public void doSyntax() {
        if(!Settings.syntaxHighlight)
            return;
        
        programmaticChange = true;

        StyledDocument doc = (StyledDocument) editor.getDocument();

        // clear styles
        doc.setCharacterAttributes(0, doc.getLength(), defaultStyle, true);

        String text = editor.getText().toUpperCase();
        
        // color syntax words
        String curName = "";
        for(int pos = 0; pos < text.length(); pos++) {
            char c = text.charAt(pos);

            if(c >= 'A' && c <= 'Z') {
                curName += c;
            } else {
                if(compiler.keywords.contains(curName))
                    doc.setCharacterAttributes(pos - curName.length(), curName.length(), keywordStyle, true);

                curName = "";
            }
        }
        
        // bracket matching
        OUTER:
        if(text.length() > 0) {
            int caret = editor.getCaretPosition();
            
            char c = 0;
            char cprime = 0; // c` - corresponding character
            boolean direction = false; // true = back, false = forward
            
            for(int i = 0; i < 2; i++) {
                if(caret >= text.length()) {
                    caret = text.length() - 1;
                    continue;
                }
                
                if(caret < 0)
                    break OUTER;
                
                c = text.charAt(caret);
                
                switch(c) {
                    case '(':
                        cprime = ')';
                        direction = false;
                        break;
                    case ')':
                        cprime = '(';
                        direction = true;
                        break;
                    case '{':
                        cprime = '}';
                        direction = false;
                        break;
                    case '}':
                        cprime = '{';
                        direction = true;
                        break;
                    default:
                        caret--;
                }
            }
            
            if(cprime == 0)
                break OUTER;

            int count = 1;

            int primeIndex = -1;

            // Go back or forward
            if(direction) {
                // Scan backwards
                for(int i = caret - 1; i >= 0; i--)  {
                    char x = text.charAt(i);
                    if(x == c)
                        count++ ;
                    else if(x == cprime) {
                        if(--count == 0) {
                            primeIndex = i;
                            break;
                        }
                    }
                }
            } else {
                // Scan forwards
                for(int i = 0; i < text.length() - (caret + 1); i++) {
                    char x = text.charAt(caret + 1 + i);

                    if(x == c)
                        count++ ;
                    else if(x == cprime) {
                        if(--count == 0) {
                            primeIndex = i + caret + 1;
                            break;
                        }
                    }
                }
            }
            
            if(primeIndex == -1) {
                ((StyledDocument) editor.getDocument()).setCharacterAttributes(caret, 1, errorStyle, true);
            } else {
                ((StyledDocument) editor.getDocument()).setCharacterAttributes(caret, 1, highlightStyle, true);
                ((StyledDocument) editor.getDocument()).setCharacterAttributes(primeIndex, 1, highlightStyle, true);
            }
        }
        
        int casmStart;
        curName = "";
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // get cur token name
            if(c >= 'A' && c <= 'Z')
                curName += c;
            else { // end of cur token
                if(curName.equals("CASM")) {
                    while(i++ < text.length() - 2 && text.charAt(i) == ' ') {}
                    
                    if(text.length() > i && text.charAt(i++) == '{') { // begin CASM block
                        casmStart = i + 1;
                        
                        while(i++ < text.length() - 1 && text.charAt(i) != '}') {}
                        
                        ((StyledDocument) editor.getDocument()).setCharacterAttributes(casmStart - 1, i - casmStart + 1, defaultStyle, true);
                    }
                }
                
                curName = "";
            }
        }
        
        // highlight comments
        int pos = 0;
        while((pos = text.indexOf("//", pos)) != -1) {
            int end = text.indexOf('\n', pos);

            if(end == -1)
                end = doc.getLength();

            int length = end - pos;

            doc.setCharacterAttributes(pos, length, commentStyle, true);

            pos += length;
        }
        
        setProgChange();
    }
    
    /** Makes a backup of the current program if there are any unsaved changes. */
    private void autoSave() {
        if(unsavedChanges && Settings.autoSave) {
            try {
                File appData = new File(System.getenv("AppData") + "\\ChASM");
                if(!appData.exists())
                    appData.mkdir();
                
                String str = editor.getText();
                
                ArrayList<Byte> bytes = new ArrayList();
                
                int caret = editor.getCaretPosition();
                
                // extract bytes
                byte first  = (byte) (caret >> 24 & 0xFF);
                byte second = (byte) (caret >> 16 & 0xFF);
                byte third  = (byte) (caret >> 8  & 0xFF);
                byte fourth = (byte) (caret       & 0xFF);
                
                if(first != 0)
                    bytes.add(first);
                if(second != 0)
                    bytes.add(second);
                if(third != 0)
                    bytes.add(third);
                if(fourth != 0)
                    bytes.add(fourth);
                
                byte headLen = (byte) (bytes.size() + 1);
                
                bytes.add(0, headLen);
                
                byte[] byteArray = new byte[bytes.size() + str.length()];
                for(int i = 0; i < bytes.size(); i++)
                    byteArray[i] = bytes.get(i);
                
                for(int i = 0; i < str.length(); i++) {
                    byteArray[i + bytes.size()] = (byte) str.charAt(i);
                }

                new FileOutputStream(new File(appData + "\\session")).write(byteArray);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /** Called when a change is made in the document. */
    private void change() {
        unsavedChanges = true;

        if(codeHintsTimer != null) {
            if(!codeHintsTimer.isRunning())
                codeHintsTimer.start();
            codeHintsTimer.restart();
        }
    }
    
    private static ArrayList<Instruction> readCasm(byte[] bytes) {
        int curPos = 0;
        ArrayList<Instruction> instructions  = new ArrayList<>();
        
        while(curPos < bytes.length) {
            byte b = bytes[curPos];
            curPos++;
            if(b == 0x00) // End program
                break;
            
            if(!INSTRUCTIONS_MAP.containsKey(Byte.toUnsignedInt(b))) {
                System.err.println("Unknown instruction 0x"
                        + Integer.toHexString(Byte.toUnsignedInt(b)).toUpperCase()
                        + "! At 0x" + Integer.toHexString(curPos + 0x10).toUpperCase());
                return null;
            }
            
            Instruction mapped = INSTRUCTIONS_MAP.get(Byte.toUnsignedInt(b));
            
            ArrayList<Argument> args = new ArrayList<>();
            
            for(int i = 0; i < mapped.nargs; i++) {
                if(mapped.args[i].getName().startsWith("s")) { // if argument is 2 bytes long
                    args.add(new Argument(mapped.args[i].getName(),
                            bytesToShort(bytes[curPos], bytes[curPos + 1])));
                    curPos += 2;
                } else {
                    args.add(new Argument(mapped.args[i].getName(),
                            bytes[curPos]));
                    curPos++;
                }
            }
            
            instructions.add(new Instruction(mapped, args.toArray(new Argument[args.size()])));
        }
        
        return instructions;
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        scrErrArea = new javax.swing.JScrollPane();
        errArea = new javax.swing.JTextArea();
        lbStatusBar = new javax.swing.JLabel();
        scrEditor = new javax.swing.JScrollPane();
        editor = new javax.swing.JTextPane();
        tbTopBar = new DarkMenuBar();
        mnuFile = new javax.swing.JMenu();
        itmNew = new javax.swing.JMenuItem();
        itmOpen = new javax.swing.JMenuItem();
        itmSave = new javax.swing.JMenuItem();
        itmSaveAs = new javax.swing.JMenuItem();
        itmImport = new javax.swing.JMenuItem();
        itmExport = new javax.swing.JMenuItem();
        mnuOptions = new javax.swing.JMenu();
        itmAutoSave = new javax.swing.JCheckBoxMenuItem();
        itmSyntaxHighlight = new javax.swing.JCheckBoxMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(31, 31, 31));
        setMinimumSize(new java.awt.Dimension(765, 568));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        errArea.setEditable(false);
        errArea.setBackground(new java.awt.Color(31, 31, 31));
        errArea.setColumns(20);
        errArea.setForeground(java.awt.Color.red);
        errArea.setLineWrap(true);
        errArea.setRows(5);
        scrErrArea.setViewportView(errArea);

        lbStatusBar.setForeground(java.awt.Color.white);
        lbStatusBar.setToolTipText("");
        lbStatusBar.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        editor.setBackground(new java.awt.Color(31, 31, 31));
        editor.setBorder(null);
        editor.setForeground(new java.awt.Color(169, 183, 198));
        editor.setMinimumSize(new java.awt.Dimension(6, 22));
        editor.setPreferredSize(new java.awt.Dimension(6, 22));
        editor.addCaretListener(new javax.swing.event.CaretListener() {
            public void caretUpdate(javax.swing.event.CaretEvent evt) {
                editorCaretUpdate(evt);
            }
        });
        scrEditor.setViewportView(editor);

        tbTopBar.setBorder(null);

        mnuFile.setForeground(new java.awt.Color(255, 255, 255));
        mnuFile.setText("File");

        itmNew.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        itmNew.setText("New");
        itmNew.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmNewActionPerformed(evt);
            }
        });
        mnuFile.add(itmNew);

        itmOpen.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        itmOpen.setText("Open");
        itmOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmOpenActionPerformed(evt);
            }
        });
        mnuFile.add(itmOpen);

        itmSave.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_MASK));
        itmSave.setText("Save");
        itmSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmSaveActionPerformed(evt);
            }
        });
        mnuFile.add(itmSave);

        itmSaveAs.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        itmSaveAs.setText("Save As...");
        itmSaveAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmSaveAsActionPerformed(evt);
            }
        });
        mnuFile.add(itmSaveAs);

        itmImport.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, java.awt.event.InputEvent.CTRL_MASK));
        itmImport.setText("Import...");
        itmImport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmImportActionPerformed(evt);
            }
        });
        mnuFile.add(itmImport);

        itmExport.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_MASK));
        itmExport.setText("Export...");
        itmExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmExportActionPerformed(evt);
            }
        });
        mnuFile.add(itmExport);

        tbTopBar.add(mnuFile);

        mnuOptions.setForeground(new java.awt.Color(255, 255, 255));
        mnuOptions.setText("Options");

        itmAutoSave.setSelected(true);
        itmAutoSave.setText("Autosave Code - " + Settings.autoSave);
        itmAutoSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmAutoSaveActionPerformed(evt);
            }
        });
        mnuOptions.add(itmAutoSave);

        itmSyntaxHighlight.setSelected(true);
        itmSyntaxHighlight.setText("Syntax Highlighting - " + Settings.syntaxHighlight);
        itmSyntaxHighlight.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmSyntaxHighlightActionPerformed(evt);
            }
        });
        mnuOptions.add(itmSyntaxHighlight);

        tbTopBar.add(mnuOptions);

        setJMenuBar(tbTopBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(312, Short.MAX_VALUE)
                .addComponent(scrErrArea, javax.swing.GroupLayout.PREFERRED_SIZE, 453, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addGap(19, 19, 19)
                    .addComponent(lbStatusBar, javax.swing.GroupLayout.DEFAULT_SIZE, 746, Short.MAX_VALUE)))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(scrEditor, javax.swing.GroupLayout.DEFAULT_SIZE, 309, Short.MAX_VALUE)
                    .addGap(456, 456, 456)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(scrErrArea, javax.swing.GroupLayout.DEFAULT_SIZE, 521, Short.MAX_VALUE)
                .addGap(28, 28, 28))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addGap(0, 524, Short.MAX_VALUE)
                    .addComponent(lbStatusBar, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(scrEditor, javax.swing.GroupLayout.DEFAULT_SIZE, 521, Short.MAX_VALUE)
                    .addGap(28, 28, 28)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void itmNewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmNewActionPerformed
        if(!editor.getText().isEmpty()) {
            switch(JOptionPane.showConfirmDialog(rootPane, "Clear current code?",
                    "ChASM Dialogue", JOptionPane.YES_NO_CANCEL_OPTION)) {
                case JOptionPane.CANCEL_OPTION:
                    return;
                case JOptionPane.YES_OPTION:
                    editor.setText("");
            }
        }
        
        file = null; 
        setTitle("ChASM - Editing untitled.chasm");
        
        lbStatusBar.setText("Created new file successfully!");
    }//GEN-LAST:event_itmNewActionPerformed

    private void itmOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmOpenActionPerformed
        try {
            // Prompt user for a file
            JFileChooser filePicker = new JFileChooser();
            filePicker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "ChASM Source Files (.chasm)", "chasm"));
            
            if(file != null)
                filePicker.setSelectedFile(file.getParentFile());
            
            if(filePicker.showOpenDialog(rootPane) != JFileChooser.APPROVE_OPTION)
                return;
            file = filePicker.getSelectedFile();
            
            // Read the file
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String editorText = new String(bytes, "UTF-8");
            editor.setText(editorText);
            doSyntax();
            
            // Success!
            setTitle("ChASM - Editing " + file.getName());
            
            lbStatusBar.setText("Opened " + file.getName() + " successfully!");
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }//GEN-LAST:event_itmOpenActionPerformed

    private void itmSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmSaveActionPerformed
        if(file == null) {
            itmSaveAsActionPerformed(evt);
            return;
        }
        
        try {
            // Write .chasm file in plaintext
            byte[] bytes = editor.getText().getBytes("UTF-8");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }
            
            unsavedChanges = false;
            
            // Success!
            setTitle("Editing " + file.getName());
            
            lbStatusBar.setText("Saved " + file.getName() + " successfully!");
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }//GEN-LAST:event_itmSaveActionPerformed
    private void itmSaveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmSaveAsActionPerformed
        JFileChooser saveLocationPicker = new JFileChooser();
        if(file != null)
            saveLocationPicker.setSelectedFile(file.getParentFile());
        
        saveLocationPicker.setSelectedFile(new File("untitled.chasm"));
        
        if(saveLocationPicker.showSaveDialog(rootPane) != JFileChooser.APPROVE_OPTION)
            return;
        
        file = saveLocationPicker.getSelectedFile();
        itmSaveActionPerformed(null);
    }//GEN-LAST:event_itmSaveAsActionPerformed
    
    private void itmImportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmImportActionPerformed
        if(!editor.getText().isEmpty() && JOptionPane.showConfirmDialog(rootPane,
                "Importing a file will clear the current code. Proceed?",
                    "ChASM Dialogue", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            return;
        
        JFileChooser importFileChooser = new JFileChooser();
        importFileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Compiled CASM Files (.casm)", "casm"));
        
        if(file != null)
            importFileChooser.setSelectedFile(file.getParentFile());
        
        if(importFileChooser.showOpenDialog(rootPane) != JFileChooser.APPROVE_OPTION)
            return;
        
        try {
            // read file
            byte[] fileContents = java.nio.file.Files.readAllBytes(
                    importFileChooser.getSelectedFile().toPath());
            
            // truncate header
            byte[] b = Arrays.copyOfRange(fileContents, 0x10, fileContents.length);
            
            ArrayList<Instruction> instructions = readCasm(b);
            
            String code = "";
            for(Instruction curInst : instructions)
                code += "    " + curInst.toCode() + '\n';
            
            editor.setText("casm {\n" + code + "}");
            
            lbStatusBar.setText("Imported file successfully.");
            
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }//GEN-LAST:event_itmImportActionPerformed

    private void itmExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmExportActionPerformed
        JFileChooser compileFileChooser = new JFileChooser();
        compileFileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Compiled CASM Files (.casm)", "casm"));
        
        if(file != null) {
            compileFileChooser.setSelectedFile(file.getParentFile());
            
            String fName = file.getName();
            
            compileFileChooser.setSelectedFile(new File(
                        (fName.endsWith(".chasm")
                            ? fName.substring(0, fName.lastIndexOf(".chasm"))
                            : fName)
                        + ".casm"));
        } else {
            compileFileChooser.setSelectedFile(new File("untitled.casm"));
        }
        
        if(compileFileChooser.showSaveDialog(rootPane) != JFileChooser.APPROVE_OPTION)
            return;
        
        byte[] byteCode;
        if(!compiler.compiling())
            compiler.compile(editor.getText());

        long startTime = System.currentTimeMillis();
        while(compiler.compiling()) {
//                if(System.currentTimeMillis() - startTime > 10000) {
//                    compiler.error("Compilation took too long! Aborting...");
//                    lbStatusBar.setText("Failed to compile code!");
//                    return;
//                }
        }

        byteCode = compiler.assemble();
        if(byteCode == null) {
            lbStatusBar.setText("Failed to assemble compiled code!");
            return;
        }
        
        File compFile = compileFileChooser.getSelectedFile();
        
        try (FileOutputStream fos = new FileOutputStream(
                compFile.getAbsolutePath())) {
            fos.write(byteCode);
            
            lbStatusBar.setText("Exported " + compFile.getName() + " successfully!");
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }//GEN-LAST:event_itmExportActionPerformed

    private void itmAutoSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmAutoSaveActionPerformed
        Settings.autoSave = !Settings.autoSave;
        Settings.save();
        
        itmAutoSave.setText("Autosave Code - " + Settings.autoSave);
    }//GEN-LAST:event_itmAutoSaveActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        autoSave();
    }//GEN-LAST:event_formWindowClosing

    private void editorCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_editorCaretUpdate
        SwingUtilities.invokeLater(() -> {
            doSyntax();
        });
    }//GEN-LAST:event_editorCaretUpdate

    private void itmSyntaxHighlightActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmSyntaxHighlightActionPerformed
        Settings.syntaxHighlight = !Settings.syntaxHighlight;
        Settings.save();
        
        itmAutoSave.setText("Syntax Highlighting - " + Settings.syntaxHighlight);
    }//GEN-LAST:event_itmSyntaxHighlightActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane editor;
    private javax.swing.JTextArea errArea;
    private javax.swing.JCheckBoxMenuItem itmAutoSave;
    private javax.swing.JMenuItem itmExport;
    private javax.swing.JMenuItem itmImport;
    private javax.swing.JMenuItem itmNew;
    private javax.swing.JMenuItem itmOpen;
    private javax.swing.JMenuItem itmSave;
    private javax.swing.JMenuItem itmSaveAs;
    private javax.swing.JCheckBoxMenuItem itmSyntaxHighlight;
    public static javax.swing.JLabel lbStatusBar;
    private javax.swing.JMenu mnuFile;
    private javax.swing.JMenu mnuOptions;
    private javax.swing.JScrollPane scrEditor;
    private javax.swing.JScrollPane scrErrArea;
    private javax.swing.JMenuBar tbTopBar;
    // End of variables declaration//GEN-END:variables
    private final Style commentStyle, keywordStyle, highlightStyle, errorStyle;
    private final Style defaultStyle =
            StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
    
    // timers used to do tasks after the user stops typing
    private final javax.swing.Timer codeHintsTimer, saveTimer;
    
    private final ThreadedCompiler compiler = new ThreadedCompiler();
    
    private boolean unsavedChanges = false;
    
    /**
     * Set up all the UI elements, including setting the right colors for the elements.
     */
    private void initUI() throws IOException, FontFormatException {
        setLocationRelativeTo(null); // Center frame
        
        setIconImage(javax.imageio.ImageIO
                .read(getClass().getResourceAsStream("/res/icon32.png")));
        
        getContentPane().setBackground(new Color(20, 20, 20));
        
        editor.setFont(Font.createFont(Font.TRUETYPE_FONT,
                getClass().getResourceAsStream("/res/inconsolata.ttf")).deriveFont(16f));
        editor.setCaretColor(Color.white);
        
        UIManager.put("Caret.width", 2);
        
        scrEditor.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scrEditor.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        scrErrArea.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        scrErrArea.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        
        scrEditor.setBackground(new Color(38, 38, 38));
        scrErrArea.setBackground(new Color(38, 38, 38));
        
        CompoundUndoManager um = new CompoundUndoManager(editor);
        
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_Z && e.isControlDown()) {
                    if(e.isShiftDown())
                        um.getRedoAction().actionPerformed(null);
                    else
                        um.getUndoAction().actionPerformed(null);
                } else if(e.getKeyCode() == KeyEvent.VK_Y && e.isControlDown()) {
                    um.getRedoAction().actionPerformed(null);
                } else if(e.getKeyCode() == KeyEvent.VK_TAB) {
                    
                    if(editor.getSelectedText() != null) {
                        boolean backwards = e.isShiftDown();
                        
                        int selectionStart = editor.getSelectionStart();
                        int selectionEnd = editor.getSelectionEnd();
                        String text = "\n" + editor.getText();
                        
                        ArrayList<Integer> newLineIndexes = new ArrayList();
                        for(int i = 0; i < text.length(); i++) {
                            if(text.charAt(i) == '\n') {
                                newLineIndexes.add(i + 1);
                            }
                        }
                        
                        int startOfNewLines = 0;
                        for(int i = 0; i < newLineIndexes.size(); i++) {
                            int curIndex = newLineIndexes.get(i);
                            if(curIndex - 1 == selectionStart) {
                                startOfNewLines = i;
                                break;
                            }
                            
                            if(curIndex > selectionStart) {
                                startOfNewLines = i - 1;
                                break;
                            }
                        }
                        
                        boolean first = true;
                        int offset = 0;
                        for(int i = startOfNewLines; i < newLineIndexes.size(); i++) {
                            int curIndex = newLineIndexes.get(i);
                            if(curIndex <= selectionEnd) {
                                String text1 = text.substring(0, curIndex + offset); // before newline
                                String text2 = text.substring(curIndex + offset);    // after newline
                                
                                if(backwards) {
                                    
                                    for(int j = 0; j < 4; j++) {
                                        if(text2.length() > j) {
                                            if(text2.charAt(j) == ' ')
                                                offset += 1;
                                        }
                                    }

                                    text2 = text2.substring(offset);
                                    text = text1 + text2;
                                    
                                    for(int k = i; k < newLineIndexes.size(); k++) {
                                        newLineIndexes.set(k, newLineIndexes.get(k) - offset);
                                    }
                                    
                                    selectionEnd -= offset;
                                    
                                    if(first) {
                                        selectionStart -= offset;
                                        first = false;
                                    }
                                    
                                    offset = 0;
                                } else {
                                    text = text1 + "    " + text2;
                                    offset += 4;
                                }
                            } else {
                                break;
                            }
                        }

                        text = text.substring(1);
                        
                        programmaticChange = true;
                        editor.setText(text);
                        setProgChange();
                        
                        if(!backwards)
                            editor.setSelectionStart(selectionStart + 4);
                        else
                            editor.setSelectionStart(selectionStart);
                        
                        editor.setSelectionEnd(selectionEnd + offset);
                    } else if(e.isShiftDown()) {
                        int cursor = editor.getCaretPosition() - 1;
                        String text = editor.getText();
                        
                        int spaces = 0;
                        
                        int maxSpaces = 4;
                        if(text.length() < 4)
                            maxSpaces = text.length();
                        
                        for(int i = 0; i < maxSpaces; i++) {
                            if(text.charAt(cursor - i) == ' ')
                                spaces++;
                            else
                                break;
                        }
                        
                        String text1 = text.substring(0, cursor - spaces + 1);
                        String text2 = text.substring(cursor + 1);
                        text = text1 + text2;
                        
                        programmaticChange = true;
                        editor.setText(text);
                        editor.setCaretPosition(cursor - spaces + 1);
                        setProgChange();
                    }
                }
            }
        });
        
        ((DefaultStyledDocument) editor.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void replace(DocumentFilter.FilterBypass fb, int offset, int length,
                    String text, AttributeSet attrs) throws BadLocationException {
                
                if(programmaticChange)
                    return;
                
                boolean isComment = false;
                String txt = editor.getText() + text;
                
                int pos = offset;
                
                while(pos > 0 && txt.charAt(pos) != '\n') {
                    String test = txt.substring(pos - 1, pos + 1);
                    if(test.equals("//")) {
                        isComment = true;
                        break;
                    }
                    pos--;
                }
              
                if(isComment)
                    attrs = commentStyle;
                else
                    attrs = defaultStyle;
                
                if(text.equals("\n") && !editor.getText().isEmpty()) {
                    Document doc = editor.getDocument();
                    Element map = doc.getDefaultRootElement();

                    int lineNum = map.getElementIndex(editor.getCaretPosition());
                    String[] lines = (" " + editor.getText()).replace("\n", "\n ").split("\n");
                    String line = lines[lineNum].substring(1);
                    
                    int whiteSpace = 0;
                    for(int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);
                        if(c == ' ')
                            whiteSpace++;
                        else
                            break;
                    }
                    
                    String leadSpace = "";
                    for(int i = 0; i < whiteSpace; i++)
                        leadSpace += ' ';

                    text += leadSpace;
                    
                    String docText = editor.getText();
                    
                    int caret = editor.getCaretPosition();
                    
                    // auto-add closing '}'
                    if(docText.length() > 1 && caret > 0 && docText.charAt(caret - 1) == '{') {
                        int openCount = 0;
                        for(int i = 0; i < docText.length(); i++) {
                            if(docText.charAt(i) == '{')
                                openCount++;
                        }
                        
                        int closeCount = 0;
                        for(int i = 0; i < docText.length(); i++) {
                            if(docText.charAt(i) == '}')
                                closeCount++;
                        }
                        
                        if(closeCount < openCount) {
                            text += "    ";
                            int newCaret = caret + text.length();

                            text += "\n" + leadSpace + "}";

                            SwingUtilities.invokeLater(() -> {
                                editor.setCaretPosition(newCaret);
                            });
                        } else {
                            text += "    "; // indent for inner block
                        }
                    }
                }
                
                text = text.replace("\t", "    "); // Developers Who Use Spaces Make More Money Than Those Who Use Tabs
                
                if((text.contains(" ") || text.contains("\n")) && !lastEditWasSpace) {
                    um.compoundEditShouldEnd();
                    lastEditWasSpace = true;
                } else {
                    lastEditWasSpace = false;
                }
                
                change();
                super.replace(fb, offset, length, text, attrs);
            }
            
            @Override
            public void remove(DocumentFilter.FilterBypass fb, int offset, int length)
                    throws BadLocationException {
                change();
                
                super.remove(fb, offset, length);
            }
        });
        
        ArrayList<JMenuItem> arrayItems = new ArrayList(Arrays.asList(itmExport, itmImport, itmNew, itmOpen, itmSave, itmSaveAs, itmAutoSave, itmSyntaxHighlight));
        
        arrayItems.stream().map((itm) -> {
            itm.setForeground(Color.lightGray);
            return itm;
        }).forEachOrdered((itm) -> {
            itm.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() {
                @Override
                public void paintMenuItem(Graphics g, JComponent c,
                        Icon checkIcon, Icon arrowIcon,
                        Color background, Color foreground,
                        int defaultTextIconGap) {
                    // Save original graphics font and color
                    Font holdf = g.getFont();
                    Color holdc = g.getColor();

                    JMenuItem mi = (JMenuItem) c;
                    g.setFont(mi.getFont());

                    Rectangle viewRect = new Rectangle(5, 0, mi.getWidth(), mi.getHeight());

                    paintBackground(g, mi, background);
                    paintText(g, mi, viewRect, mi.getText());

                    // Restore original graphics font and color
                    g.setColor(holdc);
                    g.setFont(holdf);
                }
                
                @Override
                protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
                    Color oldColor = g.getColor();
                    int menuWidth = menuItem.getWidth();
                    int menuHeight = menuItem.getHeight();
                    g.setColor(new Color(47,49,54));
                    g.fillRect(0,0, menuWidth, menuHeight);
                    g.setColor(oldColor);
                }
                
                @Override
                protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
                    ButtonModel model = menuItem.getModel();
                    FontMetrics fm = g.getFontMetrics();
                    
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(
                            RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
                    
                    
                    Color oldColor = g2d.getColor();

                    if(model.isArmed())
                        g2d.setColor(Color.lightGray.darker());
                    else
                        g2d.setColor(Color.lightGray);
                    
                    g2d.drawString(text, textRect.x, textRect.y + fm.getAscent());
                    
                    // get keystroke text
                    KeyStroke accelerator = menuItem.getAccelerator();
                    
                    if(accelerator != null) {
                        String accText = "";
                        int modifiers = accelerator.getModifiers();
                        if (modifiers > 0) {
                            accText = java.awt.event.KeyEvent.getKeyModifiersText(modifiers);
                            accText += acceleratorDelimiter;
                        }

                        int keyCode = accelerator.getKeyCode();
                        if (keyCode != 0)
                            accText += java.awt.event.KeyEvent.getKeyText(keyCode);
                        else
                            accText += accelerator.getKeyChar();

                        g2d.drawString(accText,
                                menuItem.getWidth() - fm.stringWidth(accText) - 5,
                                textRect.y + fm.getAscent());
                    }
                    g2d.setColor(oldColor);
                }
            });
        });
        
        editor.getDocument().putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n");
        scrEditor.setRowHeaderView(new TextLineNumber(editor));
        
        editor.getActionMap().put(DefaultEditorKit.deletePrevCharAction, new TextAction("delete-previous") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTextComponent target = getTextComponent(e);
                if ((target != null) && (target.isEditable())) {
                    try {
                        Document doc = target.getDocument();
                        Caret caret = target.getCaret();
                        int dot = caret.getDot();
                        int mark = caret.getMark();
                        if (dot != mark)
                            doc.remove(Math.min(dot, mark), Math.abs(dot - mark));
                        else if (dot > 0) {
                            int delChars = 1;

                            if (dot > 1) {
                                String dotChars = doc.getText(dot - 2, 2);
                                char c0 = dotChars.charAt(0);
                                char c1 = dotChars.charAt(1);

                                if (c0 >= '\uD800' && c0 <= '\uDBFF' &&
                                    c1 >= '\uDC00' && c1 <= '\uDFFF') {
                                    delChars = 2;
                                }
                            }

                            doc.remove(dot - delChars, delChars);
                        }
                    } catch (BadLocationException blarg) {}
                }
            }
        });
    }
    
    boolean programmaticChange, lastEditWasSpace;
    
    public class JOutputStream extends OutputStream {
        
        JTextArea textArea;
        public JOutputStream(JTextArea a) {
            textArea = a;
        }
        
        @Override
        public void write(int b) throws IOException {
            // redirect data to the text area
            textArea.append((char) b + "");
            
            // scroll the text area to the end of data
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }
    }
    
    public static class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return noButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return noButton();
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(32, 34, 37);
            trackColor = new Color(47, 49, 54);
        }
        
        @Override
        public void layoutContainer(Container scrollbarContainer) {
            if(decrButton == null)
                decrButton = noButton();
            
            if(incrButton == null)
                incrButton = noButton();
            
            super.layoutContainer(scrollbarContainer);
        }
        
        private static JButton noButton() {
            JButton jbutton = new JButton();
            jbutton.setPreferredSize(new Dimension(0, 0));
            jbutton.setMinimumSize(new Dimension(0, 0));
            jbutton.setMaximumSize(new Dimension(0, 0));
            return jbutton;
        }
    }
    
    public static class DarkMenuBar extends JMenuBar {

        Color backgroundColor = new Color(38, 38, 38);
        javax.swing.border.Border border = new javax.swing.border.Border() {

            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                //do nothing
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(1, 0, 1, 0);
            }

            @Override
            public boolean isBorderOpaque() {
                return true;
            }
        };

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(backgroundColor);
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        public void setColor(Color color) {
            this.backgroundColor = color;
        }

        @Override
        public void setBorder(javax.swing.border.Border border) {
            this.border = border;
        }

        @Override
        public void paintBorder(Graphics g) {
            //do nothing
        }
    }
    
    private void setProgChange() {
        SwingUtilities.invokeLater(() -> {
            programmaticChange = false;
        });
    }
}
