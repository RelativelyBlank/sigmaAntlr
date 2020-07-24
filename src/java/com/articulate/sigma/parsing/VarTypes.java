package com.articulate.sigma.parsing;

import com.articulate.sigma.Formula;
import com.articulate.sigma.FormulaPreprocessor;
import com.articulate.sigma.KB;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class VarTypes {

    Collection<FormulaAST> formulas = null;
    KB kb = null;

    // a map of variables and the set of types that constrain them
    HashMap<String,HashSet<String>> varTypeMap = new HashMap<>();

    /** ***************************************************************
     */
    public VarTypes(Collection<FormulaAST> set, KB kbinput) {
        formulas = set;
        kb = kbinput;
        System.out.println("VarTypes(): created with # inputs: " + set.size());
    }

    /** ***************************************************************
     * funterm : '(' FUNWORD argument+ ')' ;
     * argument : (sentence | term) ;
     */
    public String findTypeOfFunterm(SuokifParser.FuntermContext input) {
        return null;
    }

    /** ***************************************************************
     * term : (funterm | variable | string | number | FUNWORD | IDENTIFIER ) ;
     */
    public String findTypeOfTerm(SuokifParser.TermContext input, String sigType) {

        String type = null;
        for (ParseTree c : ((SuokifParser.TermContext) input).children) {
            if (c.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$FuntermContext"))
                type = findTypeOfFunterm((SuokifParser.FuntermContext) c);
            if (c.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$VariableContext")) {
                FormulaPreprocessor.addToMap(varTypeMap, c.getText(),sigType);
            }
            if (c.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$StringContext")) {
                if (!sigType.equals("SymbolicString"))
                    System.out.println("error in findTypeOfTerm(): signature doesn't allow string " + c.getText());
            }
            if (c.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$NumberContext")) {
                if (!kb.kbCache.subclassOf(sigType,"Number"))
                    System.out.println("error in findTypeOfTerm(): signature doesn't allow number " + c.getText());
            }
        }
        return null;
    }

    /** ***************************************************************
     * Go through the argument map of a formula, which consists of all
     * predicates and their arguments in each position, within this formula,
     * and find the type of that argument.  If the argument is a variable,
     * add the type to the variable type map (varTypeMap).  If the argument
     * is not a variable, make sure it is allowed as a argument, given the
     * signature of the predicate.  If not, report an error.
     *
     * argument : (sentence | term) ;
     * sentence : (relsent | logsent | quantsent | variable) ;
     * term : (funterm | variable | string | number | FUNWORD | IDENTIFIER ) ;
     */
    public void findType(FormulaAST f) {

        System.out.println("VarTypes.findType(): " + f);
        for (String pred : f.argMap.keySet()) {
            System.out.println("VarTypes.findType():relation: " + pred);
            HashMap<Integer, HashSet<SuokifParser.ArgumentContext>> argsForIndex = f.argMap.get(pred);
            if (argsForIndex == null || Formula.isVariable(pred))
                continue;
            printContexts(argsForIndex);
            ArrayList<String> sig = kb.kbCache.getSignature(pred);
            System.out.println("VarTypes.findType():signature: " + sig);
            if (argsForIndex.keySet().size() != sig.size()-1) { // signatures have a 0 element for function return type
                System.out.println("Error in VarTypes.findType(): mismatched argument type lists:");
                System.out.println("VarTypes.findType():line and file: " + f.sourceFile + " " + f.startLine);
            }
            else {
                for (Integer i : argsForIndex.keySet()) {
                    String sigTypeAtIndex = sig.get(i);
                    HashSet<SuokifParser.ArgumentContext> args = argsForIndex.get(i);
                    String common = null;
                    for (SuokifParser.ArgumentContext ac : args) {
                        for (ParseTree c : ac.children) {
                            System.out.println("child: " + c.getClass().getName());
                            if (c.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$SentenceContext")) {
                                for (ParseTree c2 : ((SuokifParser.SentenceContext) c).children) {
                                    if (c2.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$RelsentContext") ||
                                            c2.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$LogsentContext") ||
                                            c2.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$QuantsentContext"))
                                        f.higherOrder = true;
                                    if (c2.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$VariableContext") &&
                                            ((SuokifParser.VariableContext) c2).REGVAR() != null) {
                                        FormulaPreprocessor.addToMap(f.varmap,c2.getText(), sigTypeAtIndex);
                                    }
                                }
                            }
                            if (c.getClass().getName().equals("com.articulate.sigma.parsing.SuokifParser$TermContext"))
                                findTypeOfTerm((SuokifParser.TermContext) c,sigTypeAtIndex);
                        }
                    }
                }
            }
        }
    }

    /** ***************************************************************
     */
    public void printContexts(HashMap<Integer, HashSet<SuokifParser.ArgumentContext>> args) {

        for (Integer i : args.keySet()) {
            System.out.print(i + ": {");
            HashSet<SuokifParser.ArgumentContext> argTypes = args.get(i);
            for (SuokifParser.ArgumentContext ac : argTypes) {
                System.out.print(ac.getText() + ", ");
            }
            System.out.println("}");
        }
    }

    /** ***************************************************************
     */
    public void findTypes() {

        for (FormulaAST f : formulas)
            findType(f);
    }
}
