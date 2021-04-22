package chasm.compiler;

import java.util.*;

// huge thanks to Jack Crenshaws's "Let's Build a Compiler" series, which this is heavily based on.
public class ThreadedCompiler {

    private char look, token;
    private int index = 0;
    
    private int labelCount = 0;
    
    private String text;
    private String finalAsm = "";
    Thread compThread;
    
    public final ArrayList<String> keywords = new ArrayList(Arrays.asList("IF", "ELSE", "WHILE",
                                                                    "VAR", "{", "}", "CASM"));
    private final String keywordTokens = "ilwvbec";
    
    private final ArrayList<String> varTable = new ArrayList();
    
    public void compile(String code) {
        if(compThread != null && compThread.isAlive())
            compThread.stop();
        
        compThread = new Thread(() -> {
            compileOnCurrentThread(code);
        }, "ChASM-WorkThread");
        
        compThread.start();
    }
    
    public boolean compiling() {
        if(compThread == null)
            return false;
        else
            return compThread.isAlive();
    }
    
    private void compileOnCurrentThread(String code) {
        // Set vars to starting values
        text = prepareForParsing(code);
        index = 0;
        look = token = ' ';
        varTable.clear();
        labelCount = 0;
        finalAsm = "";
        
        prog();
        
        if(token != 'p')
            expected("end of program");

        System.out.println(finalAsm);
    }
    
    public byte[] assemble() {
        if(compThread.isAlive()) {
            return null;
        }
        
        Assembler.assemble(finalAsm);
        
        long t = System.currentTimeMillis();
        while(Assembler.assembling()) {
//            if(System.currentTimeMillis() - t > 10000) {
//                Assembler.error("Assembler took too long! Aborting...");
//                return null;
//            }
        }
        
        return Assembler.getByteCode();
    }
    
    private void getToken() {
        skipWhite();
        
        if(isLetter(look)) {
            int pastIndex = index;
            
            String s = getName();
            
            if(keywords.contains(s)) {
                token = keywordTokens.charAt(keywords.indexOf(s));
            } else if(varTable.contains(s)) {
                token = 'a';
                
                // move index back so the assignment routine can get the var name
                index = pastIndex - 1;
                getChar();
            } else {
                encountered("token or variable name", s);
            }
            
        } else if(look == '{') {
            token = 'b';
            getChar();
        } else if(look == '}') {
            token = 'e';
            getChar();
        } else if(look == '\u2009') { // program termination character
            token = 'p';
        } else {
            expected("token");
        }
    }
    
    // Print a CASM line into the final composition
    private void print(String s) {
        finalAsm += s + "\n";
    }
    
    private boolean charsLeft() {
        return index < text.length();
    }
    
    // Read New Character
    private void getChar() {
        if(text.length() <= index)
            error("Reached end of document unexpectedly.");
        else
            look = text.charAt(index++);
    }
    
    // Parse and Translate a Boolean Expression
    private void boolExpression() {
        boolTerm();
        while(isOrOp(look)) {
            print("PSH(r0)");
            switch(look) {
                case '|':
                    boolOr();
                    break;
                case '~':
                    boolXor();
            }
        }
    }
    
    // Report an error
    public void error(String error) {
        System.err.println("Error: " + error);
        finalAsm = "";
        compThread.stop();
    }
    
    // Called when something was expected but not found
    private void expected(String e) {
        errorAtLine("Expected " + e);
    }
    
    private void encountered(String expectedType, String found) {
        errorAtLine("Encountered unknown " + expectedType + ": " + found);
    }
    
    private void errorAtLine(String e) {
        char lineEnd = 'a';
        if(e.endsWith(".") || e.endsWith("!"))
            lineEnd = 'A';
        
        if(index > 1) {
            look = text.charAt(index -= 2);

            // un-skipWhite()
            while(look == ' ' || look == '\n')
                look = text.charAt(--index);

            int lineNum = 1;
            int lastNewLine = 0;

            String s = text.substring(0, index + 1);
            for(int i = 0; i < s.length(); i++) {
                if(s.charAt(i) == '\n') {
                    lastNewLine = i;
                    lineNum++;
                }
            }
            
            error(e + " " + lineEnd + "t line " + lineNum + ".\n"
                + text.substring(lastNewLine, index + 1).trim() + "<- HERE");
        } else {
            error(e + " " + lineEnd + "t line 1.");
        }
    }
    
    // True when c == '+' or '-'
    private boolean isAddOp(char c) {
        return c == '+' || c == '-';
    }
    
    // True when c == '|' or '~'
    private boolean isOrOp(char c) {
        return c == '|' || c == '~';
    }
    
    // True when c == '=' or '#' or '<' or '>'
    private boolean isRelOp(char c) {
        return c == '=' || c == '#' || c == '<' || c == '>';
    }
    
    // True when c is a latin letter
    private boolean isLetter(char c) {
        return (Character.toUpperCase(c) >= 'A' && Character.toUpperCase(c) <= 'Z');
    }
    
    // True when c is a number
    private boolean isDigit(char c) {
        return (c >= '0' && c <= '9');
    }
    
    // Match a Specific Input Character
    private void match(char x) {
        if(look != x)
            expected("\'" + x + "\'");
        else
            getChar();
        
        skipWhite();
    }
    
    // Match a Specific Input String
    private void match(String x) {
        skipWhite();
        
        for(int i = 0; i < x.length(); i++) {
            if(look != x.charAt(i))
                expected("\'" + x + "\'");
            else
                getChar();
        }
        
        skipWhite();
    }
    
    // Skip Whitespace
    private void skipWhite() {
        while(charsLeft() && (look == ' ' || look == '\t' || look == '\n'))
            getChar();
    }
    
    // Get an Identifier
    private String getName() {
        skipWhite();
        
        String name = "";
        
        if(!isLetter(look))
            expected("name");
        else {
            while(isLetter(look)) {
                name += Character.toUpperCase(look);
                
                if(charsLeft())
                    getChar();
            }
        }
        
        skipWhite();
        return name;
    }
    
    // Get a Number
    private int getNum() {
        String value = "";
        
        skipWhite();
       
        if(!isDigit(look))
            expected("integer");
        
        while(isDigit(look)) {
            value += look;
            getChar();
        }
        
        skipWhite();
        return Integer.parseInt(value);
    }
    
    // Parse and Translate an Identifier
    private void identify(boolean var) {
        String name = getName();
        if(look == '(') {
            match('(');
            match(')');
            print("// TODO call function: " + name);
        } else if(var) {
            if(!varTable.contains(name))
                errorAtLine("Variable " + name + " not initialized!");
            
            print("VTR(v" + indexOf(name) + ", r0) // Variable " + name);
        } else {
            expected("variable name or function call");
        }
    }
    
    // Parse and Translate a Boolean Factor with NOT
    private void notFactor() {
        skipWhite();
        if(look == '!') {
            match('!');
            relation();
            print("MOV(r1, b255)");
            print("XOR(r1, r0, r0)"); // XOR r0 with 0xFF
        } else
            relation();
    }
    
    // Parse and Translate a Relation
    private void relation() {
        expression();
        if(isRelOp(look)) {
            print("PSH(r0)");
            switch(look) {
                case '=':
                    equals();
                    break;
                case '!':
                    notEquals();
                    break;
                case '<':
                    less();
                    break;
                case '>':
                    greater();
            }
        }
    }
    
    // Recognize and Translate a Relational "Equal"
    private void equals() {
        match("==");
        expression();
        print("CMP(SP+, r0)");
        
        print("// Test EQUAL flag");
        print("FTR(r0)");
        print("AND(r0, #1, r0)");
    }
    
    // Recognize and Translate a Relational "Not Equal"
    private void notEquals() {
        match("!=");
        expression();
        print("CMP(SP+, r0)");
        
        print("// Test NOT_EQUAL flag");
        print("FTR(r0)");
        print("AND(r0, #32, r0)");
    }
    
    // Recognize and Translate a Relational "Less Than"
    private void less() {
        match('<');
        
        if(look == '=') { // less or equal
            match('=');
            expression();
            print("CMP(SP+, r0)");
            
            print("// Test LESS_EQUAL flag");
            print("FTR(r0)");
            print("AND(r0, #16, r0)");
        } else { // less than
            expression();
            print("CMP(SP+, r0)");
            
            print("// Test LESS flag");
            print("FTR(r0)");
            print("AND(r0, #8, r0)");
        }
    }

    // Recognize and Translate a Relational "Greater Than"
    private void greater() {
        match('>');

        if(look == '=') { // greater or equal
            match('=');
            expression();
            print("CMP(SP+, r0)");
            
            print("// Test GREATER_EQUAL flag");
            print("FTR(r0)");
            print("AND(r0, #4, r0)");
        } else { // greater
            expression();
            print("CMP(SP+, r0)");
            
            print("// Test GREATER flag");
            print("FTR(r0)");
            print("AND(r0, #2, r0)");
        }
    }
    
    // Parse and Translate a Math Factor
    private void factor() {
        skipWhite();
        
        if(look == '(') {
            match('(');
            boolExpression();
            match(')');
        } else if(isLetter(look))
            identify(true);
        else
            print("MOV(r0, #" + getNum() + ")");
    }
    
    // Recognize and Translate a Multiply
    private void multiply() {
        match('*');
        factor();
        print("MUL(SP+, r0, r0)");
    }
    
    // Recognize and Translate a Divide
    private void divide() {
        match('/');
        factor();
        print("DIV(SP+, r0, r0)");
    }
    
    // Parse and Translate a Boolean Term
    private void boolTerm() {
        notFactor();
        while(look == '&') {
            print("PSH(r0)");
            match('&');
            notFactor();
            print("AND(SP+, r0, r0)");
        }
    }
    
    // Parse and Translate a Math Term
    private void term() {
        factor();

        while(look == '*'  || look == '/') {
            print("PSH(r0)");
            
            if(look == '*')
                multiply();
            else
                divide();
        }
    }
    
    // Recognize and Translate an Add
    private void add() {
        match('+');
        term();
        print("ADD(SP+, r0, r0)");
    }
    
    // Recognize and Translate a Subtract
    private void subtract() {
        match('-');
        term();
        print("SUB(r0, SP+, r0)"); // sub top of stack from primary reg
    }
    
    // Parse and Translate a Boolean OR
    private void boolOr() {
        match('|');
        boolTerm();
        print("OR(SP+, r0, r0)");
    }
    
    // Parse and Translate a Boolean XOR
    private void boolXor() {
        match('~');
        boolTerm();
        print("XOR(SP+, r0, r0)");
    }
    
    // Parse and Translate an Expression
    private void expression() {
        skipWhite();
        
        if(isAddOp(look))
            print("MOV(r0, b0)");
        else
            term();
        
        while(isAddOp(look)) {
            print("PSH(r0)");
            switch(look) {
                case '+':
                    add();
                    break;
                case '-':
                    subtract();
                    break;
            }
        }
    }

    // Parse and Translate an Assignment Statement
    private void assignment(String name) {
        match('=');
        
        skipWhite();
        boolExpression();
        print("RTV(r0, v" + indexOf(name) + ") // Variable " + name);
    }
    
    private int indexOf(String name) {
        int c = varTable.indexOf(name);
        
        return c;
    }
    
    // Block of code
    private void block() {
        getToken();
        while(token != 'e' && token != 'l') {
            statement();
            
            getToken();
            
            if(token == 'p')
                expected("}");
        }
    }
    
    private void prog() {
        getToken();
        while(token != 'p') {
            statement();
            
            getToken();
            
            if(token == 'e')
                errorAtLine("Encountered extra '}'");
        }
    }
    
    private void statement() {
        switch(token) {
            case 'i':
                doIf();
                break;
            case 'w':
                doWhile();
                break;
            case 'v':
                addVar();
                break;
            case 'a':
                skipWhite();
                if(!isLetter(look))
                    expected("assignment statement");

                String name = getName();
                if(!varTable.contains(name))
                    errorAtLine("Variable " + name + " not initialized!");

                assignment(name);
                break;
            case 'c':
                doCasm();
                break;
            case 'b':
                block();
                break;
            default:
                expected("}");
        }
    }
    
    private int newLabel() {
        return labelCount++;
    }
    
    private void postLabel(int label) {
        print("LBL(l" + label + ")");
    }
    
    private void doIf() {
        int L1 = newLabel();
        int L2 = L1;
        
        print("// If statement - Boolean condition");
        
        match('(');
        
        boolExpression();
        
        print("// If statement - Boolean condition - Check result (0 = f, other = t)");
        
        print("PSH(r0)");
        print("MOV(r0, #1)");
        print("CMP(SP+, r0)");
        
        match(')');
        
        print("// If statement - Skip block if condition evaluates false");
        
        print("JIF(l" + L1 + ", NOT_EQUAL)");
        
        print("// If statement - Conditional block");
        
        match('{');
        
        block();
        
        if(token == 'l') {
            L2 = newLabel();
            
            print("// If statement - Skip else block");
            
            print("JMP(l" + L2 + ")");
            
            print("// If statement - Else block");
            
            postLabel(L1);
            
            match('{');
            block();
        }
        
        postLabel(L2);
        print("// If statement - End");
    }
    
    private void doWhile() {
        int L1 = newLabel();
        int L2 = newLabel();
        postLabel(L1);
        
        print("// While block - Boolean condition");
        
        match('(');
        
        boolExpression();
        
        print("// While block - Boolean condition - Check result (0 = f, other = t)");
        
        print("PSH(r0)");
        print("MOV(r0, #1)");
        print("CMP(SP+, r0)");
        
        match(")");
        
        print("// While block - Jump out of block if condition evaluates false");
        
        print("JIF(l" + L2 + ", NOT_EQUAL)");
        
        print("// While block - Loop block");
        
        match("{");
        block();
        
        print("// While block - Jump back to beginning of while");
        print("JMP(l" + L1 + ")");
        
        postLabel(L2);
        
        print("// While block - End");
    }
    
    private void addVar() {
        String name = getName();
        
        if(varTable.size() >= 256)
            errorAtLine("Variable " + name + " exceeds maximum of 256!");
        
        if(varTable.contains(name))
            errorAtLine("Variable " + name + " is already declared!");
        
        varTable.add(name);
        
        if(look == '=')
            assignment(name);
        
        if(look == ',') {
            match(',');
            addVar();
        }
    }
    
    private void doCasm() {
        match('{');
        
        skipWhite();
        
        String casmText = "";
        
        while(look != '}') {
            
            if(!charsLeft())
                expected("} to close CASM block");
            
            if(look != ' ')
                casmText += look;
            
            getChar();
        }
        
        getToken();
        
        print("// Start of CASM block");
        print(casmText);
        print("// End of CASM block");
    }
    
    // Remove comments from source code and uppercase it
    private String prepareForParsing(String original) {
        String ret = "";
        
        String[] lines = original.split("\n");
        
        for(String curLine : lines) {
            // Remove comments
            if (curLine.length() > 1) {
                for (int i = 0; i < curLine.length() - 1; i++) {
                    if(curLine.charAt(i) == '/' && curLine.charAt(i + 1) == '/')
                        curLine = curLine.substring(0, i);
                }
            }
            ret += curLine + "\n";
        }
        
        ret = ret.toUpperCase();
        
        ret += "\u2009"; // terminator character
        
        return ret;
    }
}