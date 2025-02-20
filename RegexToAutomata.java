package a1;

import java.util.regex.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class RegexToAutomata {

    // ============================
    // Part 1: Combined NFA for Tokens
    // ============================

    // Global counter for unique state IDs.
    static int stateId = 0;
    
    // --------------
    // NFA Components
    // --------------
    
    // Each state holds range transitions and ε-transitions.
    static class State {
        int id;
        boolean isAccept = false;
        List<RangeTransition> rangeTransitions = new ArrayList<>();
        List<State> epsilonTransitions = new ArrayList<>();
        
        public State() {
            this.id = stateId++;
        }
        
        @Override
        public String toString() {
            return "S" + id + (isAccept ? " (accept)" : "");
        }
    }
    
    // Transition on a range of characters.
    static class RangeTransition {
        char from, to;
        State next;
        
        public RangeTransition(char from, char to, State next) {
            this.from = from;
            this.to = to;
            this.next = next;
        }
        
        public boolean matches(char c) {
            return c >= from && c <= to;
        }
        
        @Override
        public String toString() {
            return "[" + from + "-" + to + "] -> " + next;
        }
    }
    
    // --------------
    // NFA Builders
    // --------------
    
    /**
     * Build a chain NFA that exactly matches the literal string.
     */
    static State buildChainNFA(String literal) {
        // Do not reset stateId here.
        State start = new State();
        State current = start;
        for (int i = 0; i < literal.length(); i++) {
            State next = new State();
            current.rangeTransitions.add(new RangeTransition(literal.charAt(i), literal.charAt(i), next));
            current = next;
        }
        current.isAccept = true;
        return start;
    }
    
    /**
     * Build an NFA for identifiers matching the pattern [a-z]+.
     * (Only lowercase letters are valid.)
     */
    static State buildIdentifierNFA() {
        State start = new State();
        State accept = new State();
        accept.isAccept = true;
        start.rangeTransitions.add(new RangeTransition('a', 'z', accept));
        // Loop: only lowercase letters allowed.
        accept.rangeTransitions.add(new RangeTransition('a', 'z', accept));
        return start;
    }
    
    /**
     * Combine a list of individual NFAs into one master NFA.
     */
    static State combineNFAs(List<State> nfaStarts) {
        State newStart = new State();
        for (State s : nfaStarts) {
            newStart.epsilonTransitions.add(s);
        }
        return newStart;
    }
    
    // ------------------
    // NFA Simulation Methods
    // ------------------
    
    /**
     * Compute the ε-closure of a set of states.
     */
    static Set<State> epsilonClosure(Set<State> states) {
        Set<State> closure = new HashSet<>(states);
        Stack<State> stack = new Stack<>();
        stack.addAll(states);
        while (!stack.isEmpty()) {
            State s = stack.pop();
            for (State t : s.epsilonTransitions) {
                if (!closure.contains(t)) {
                    closure.add(t);
                    stack.push(t);
                }
            }
        }
        return closure;
    }
    
    /**
     * For a set of states, for the given input symbol,
     * return the set of states reachable by a matching range transition.
     */
    static Set<State> move(Set<State> states, char symbol) {
        Set<State> result = new HashSet<>();
        for (State s : states) {
            for (RangeTransition rt : s.rangeTransitions) {
                if (rt.matches(symbol)) {
                    result.add(rt.next);
                }
            }
        }
        return result;
    }
    
    /**
     * Simulate the combined NFA on a given token.
     * Returns a list of strings representing the transition table.
     */
    static List<String> simulateCombinedNFA(State start, String token) {
        List<String> table = new ArrayList<>();
        Set<State> current = epsilonClosure(new HashSet<>(Arrays.asList(start)));
        table.add("Initial ε-closure: " + current);
        
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            Set<State> next = move(current, c);
            next = epsilonClosure(next);
            table.add("On '" + c + "' -> " + next);
            current = next;
            if (current.isEmpty()) {
                // Append "(rejected)" so that the error handler can detect it.
                table.add("No valid transition. Token rejected. (rejected)");
                return table;
            }
        }
        boolean accepted = false;
        for (State s : current) {
            if (s.isAccept) {
                accepted = true;
                break;
            }
        }
        table.add("Final state set: " + current + (accepted ? " (accepted)" : " (rejected)"));
        return table;
    }
    
    // ============================
    // Part 2: Global Symbol Table for All Tokens
    // ============================
    
    /**
     * A simple symbol table that collects all tokens.
     */
    public static class SymbolTable {
        
        // Represents a symbol (token) in the symbol table.
        public static class Symbol {
            private String name;
            private String type;
            private int memoryLocation;
            
            public Symbol(String name, String type, int memoryLocation) {
                this.name = name;
                this.type = type;
                this.memoryLocation = memoryLocation;
            }
            
            public String getName() {
                return name;
            }
            
            public String getType() {
                return type;
            }
            
            public int getMemoryLocation() {
                return memoryLocation;
            }
            
            @Override
            public String toString() {
                return "Symbol{name='" + name + "', type='" + type + "', memoryLocation=" + memoryLocation + "}";
            }
        }
        
        // Simply store all symbols in a list.
        private List<Symbol> symbols;
        private int nextMemoryLocation;
        
        public SymbolTable() {
            symbols = new ArrayList<>();
            nextMemoryLocation = 0;
        }
        
        // Add a symbol.
        public void addSymbol(String name, String type) {
            Symbol symbol = new Symbol(name, type, nextMemoryLocation++);
            symbols.add(symbol);
        }
        
        // Print the symbol table.
        public void printSymbolTable() {
            System.out.println("----- Symbol Table -----");
            for (Symbol sym : symbols) {
                System.out.println(sym);
            }
            System.out.println("------------------------");
        }
    }
    
    // ============================
    // Part 3: Error Handler
    // ============================
    
    /**
     * The ErrorHandler collects error messages along with the line number.
     */
    public static class ErrorHandler {
        private List<String> errors;
        
        public ErrorHandler() {
            errors = new ArrayList<>();
        }
        
        /**
         * Report an error with a message and the line number where it occurred.
         */
        public void reportError(String message, int lineNumber) {
            errors.add("Line " + lineNumber + ": " + message);
        }
        
        /**
         * Print all collected error messages.
         */
        public void printErrors() {
            if (errors.isEmpty()) {
                System.out.println("No errors found.");
            } else {
                System.out.println("Errors:");
                for (String error : errors) {
                    System.out.println(error);
                }
            }
        }
        
        /**
         * Check if any errors have been reported.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
    
    // ============================
    // Main: Integration of Tokenization, Combined NFA Simulation, Symbol Table, and Error Handling
    // ============================
    public static void main(String[] args) throws IOException {
    	
    	 String code = new String(Files.readAllBytes(Paths.get("C:\\Users\\LENOVO\\eclipse-workspace\\a1\\src\\a1\\input.txt")));
        
        // Regular expression for different token types.
        // Note: For the IDENTIFIER group, we now match one or more of [a-z$] (so "abc$" is captured)
        // but the identifier NFA built by buildIdentifierNFA() accepts only [a-z]+.
        String tokenPatterns = 
        	      "(?<KEYWORD>\\b(?:const|global|local|int|bool|double|return|if|else|while|for)\\b)"
        	    + "|(?<BOOLEAN>\\b(?:true|false)\\b)"
        	    + "|(?<DECIMAL>\\b\\d+\\.\\d{1,5}(?:\\^[+-]?\\d+)?\\b)"
        	    + "|(?<INTEGER>\\b\\d+(?:\\^[+-]?\\d+)?\\b)"
        	    + "|(?<CHARACTER>'[a-z]')"
        	    + "|(?<STRING>\"(\\\\.|[^\"])*\")"
        	    + "|(?<IDENTIFIER>[a-z]+)"
        	    + "|(?<IOSTREAM>(?:<<|>>))"
        	    + "|(?<OPERATOR>[+\\-*/%^]=?|=)"  // Included '^' in OPERATOR group
        	    + "|(?<PUNCTUATION>[;(),{}<>#])"
        	    + "|(?<COMMENT>//.*|/\\*[\\s\\S]*?\\*/)"
        	    // UNKNOWN group: Matches any single character that isn't caught by the above groups.
        	    + "|(?<UNKNOWN>.)";

        
        code = code.replaceAll("//.*", ""); // Remove single-line comments
        code = code.replaceAll("(?s)/\\*.*?\\*/", ""); // Remove multi-line comment
        
        Pattern tokenPattern = Pattern.compile(tokenPatterns);
        Matcher matcher = tokenPattern.matcher(code);
        
        List<String> tokens = new ArrayList<>();
        List<String> tokenTypes = new ArrayList<>();
        // List to hold individual NFA start states.
        List<State> nfaList = new ArrayList<>();
        
        // Create a global symbol table to store all tokens.
        SymbolTable symbolTable = new SymbolTable();
        // Create an error handler instance.
        ErrorHandler errorHandler = new ErrorHandler();
        
        // For demonstration, simulate line numbers by splitting the code into lines.
        String[] lines = code.split("\n");
        int currentLine = 1;  // Start at line 1.
        
        while (matcher.find()) {
        	if (matcher.group("COMMENT") != null) {
                continue; // Skip comments, do not add them to tokens or symbol table
            }
        	else if (matcher.group("KEYWORD") != null) {
                String tok = matcher.group("KEYWORD");
                tokens.add(tok);
                tokenTypes.add("KEYWORD");
                nfaList.add(buildChainNFA(tok));
                //symbolTable.addSymbol(tok, "KEYWORD");
            } else if (matcher.group("BOOLEAN") != null) {
                String tok = matcher.group("BOOLEAN");
                tokens.add(tok);
                tokenTypes.add("BOOLEAN");
                nfaList.add(buildChainNFA(tok));
                //symbolTable.addSymbol(tok, "BOOLEAN");
            } else if (matcher.group("DECIMAL") != null) {
                String tok = matcher.group("DECIMAL");
                tokens.add(tok);
                tokenTypes.add("DECIMAL");
                nfaList.add(buildChainNFA(tok));
                symbolTable.addSymbol(tok, "DECIMAL");
            } else if (matcher.group("INTEGER") != null) {
                String tok = matcher.group("INTEGER");
                tokens.add(tok);
                tokenTypes.add("INTEGER");
                nfaList.add(buildChainNFA(tok));
                symbolTable.addSymbol(tok, "INTEGER");
            } else if (matcher.group("CHARACTER") != null) {
                String tok = matcher.group("CHARACTER");
                tokens.add(tok);
                tokenTypes.add("CHARACTER");
                nfaList.add(buildChainNFA(tok));
                symbolTable.addSymbol(tok, "CHARACTER");
            } else if (matcher.group("STRING") != null) {
                String tok = matcher.group("STRING");
                tokens.add(tok);
                tokenTypes.add("STRING");
                nfaList.add(buildChainNFA(tok));
                symbolTable.addSymbol(tok, "STRING");
            } else if (matcher.group("IDENTIFIER") != null) {
                String tok = matcher.group("IDENTIFIER");
                tokens.add(tok);
                tokenTypes.add("IDENTIFIER");
                nfaList.add(buildIdentifierNFA());
                symbolTable.addSymbol(tok, "IDENTIFIER");
            } else if (matcher.group("IOSTREAM") != null) {
                String tok = matcher.group("IOSTREAM");
                tokens.add(tok);
                tokenTypes.add("IOSTREAM");
                nfaList.add(buildChainNFA(tok));
                //symbolTable.addSymbol(tok, "IOSTREAM");
            } else if (matcher.group("OPERATOR") != null) {
                String tok = matcher.group("OPERATOR");
                tokens.add(tok);
                tokenTypes.add("OPERATOR");
                nfaList.add(buildChainNFA(tok));
                //symbolTable.addSymbol(tok, "OPERATOR");
            } else if (matcher.group("PUNCTUATION") != null) {
                String tok = matcher.group("PUNCTUATION");
                tokens.add(tok);
                tokenTypes.add("PUNCTUATION");
                nfaList.add(buildChainNFA(tok));
                //symbolTable.addSymbol(tok, "PUNCTUATION");
            }
            currentLine = Math.min(currentLine + 1, lines.length);
        }
        
        System.out.println("Total tokens: " + tokens.size());
        System.out.println("Tokens and their combined NFA transition tables:");
        
        // Build one combined (master) NFA from all individual NFAs.
        State masterStart = combineNFAs(nfaList);
        
        // Simulate the master NFA for each token.
        // If the simulation ends with a rejection, report an error.
        for (int i = 0; i < tokens.size(); i++) {
            String type = tokenTypes.get(i);
            String token = tokens.get(i);
            System.out.println("---------------------------------");
            System.out.println(type + ": " + token);
            List<String> table = simulateCombinedNFA(masterStart, token);
            System.out.println("Transition Table:");
            for (String line : table) {
                System.out.println("  " + line);
            }
            // If the last line of the table indicates rejection, report an error.
            if (table.get(table.size() - 1).contains("(rejected)")) {
                // Simulate error reporting with the current line number.
                errorHandler.reportError("Token '" + token + "' rejected by NFA.", currentLine);
            }
        }
        
        // ============================
        // Print the Global Symbol Table and any errors.
        // ============================
        System.out.println("\n=== Global Symbol Table ===");
        symbolTable.printSymbolTable();
        
        System.out.println("\n=== Error Report ===");
        errorHandler.printErrors();
    }
}
