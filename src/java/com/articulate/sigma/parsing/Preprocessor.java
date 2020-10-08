package com.articulate.sigma.parsing;

import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

// call the functions needed to make SUMO syntactically first order
// and ready for conversion to TPTP
public class Preprocessor {

    public static KB kb = null;

    public static boolean debug = false;

    /** ***************************************************************
     */
    public Preprocessor(KB kbin) {kb = kbin;}

    /** ***************************************************************
     * utility to remove explosive rules
     */
    public static void removeMultiplePredVar(SuokifVisitor sv) {

        sv.hasPredVar.removeAll(sv.multiplePredVar); // remove explosive rules with multiple predicate variables
        sv.rules.removeAll(sv.multiplePredVar);
        sv.hasRowVar.removeAll(sv.multiplePredVar);
    }

    /** ***************************************************************
     */
    public Collection<FormulaAST> preprocess(HashSet<FormulaAST> rowvar,
                                            HashSet<FormulaAST> predvar,
                                            HashSet<FormulaAST> rules) { // includes rowvar and predvar

        System.out.println("Preprocessor.preprocess()");
        long start = System.currentTimeMillis();
        if (debug) System.out.println("Preprocessor.preprocess() # rules: " + rules.size());
        HashSet<FormulaAST> mismatch = new HashSet<>();
        mismatch.addAll(predvar);
        mismatch.removeAll(rowvar);
        if (mismatch.size() > 0) {
            System.out.println("Preprocessor.preprocess() rowvar statements without predvar: " + mismatch.size());
            if (debug)
                System.out.println(mismatch);
        }
        mismatch = new HashSet<>();
        mismatch.addAll(rowvar);
        mismatch.removeAll(predvar);
        if (mismatch.size() > 0) {
            System.out.println("Preprocessor.preprocess() predvar statements without rowvar: " + mismatch.size());
            if (debug)
                System.out.println(mismatch);
        }
        long end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time for prelims: " + end);
        start = System.currentTimeMillis();
        VarTypes vt = new VarTypes(rules,kb);
        vt.findTypes();
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time for to find var types: " + end);
        start = System.currentTimeMillis();

        Sortals sortals = new Sortals(kb);
        for (FormulaAST f : rules)
            if (!f.higherOrder && !f.containsNumber)
                sortals.elimSubsumedTypes(f);
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time elim subsumed types: " + end);
        start = System.currentTimeMillis();

        PredVarInst pvi = new PredVarInst(kb);
        HashSet<FormulaAST> pviResults = pvi.processAll(predvar);
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time to instantiate pred vars: " + end);
        start = System.currentTimeMillis();

        RowVar rv = new RowVar(kb);
        HashSet<FormulaAST> rvResults = rv.expandRowVar(pviResults);
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time to expand row vars: " + end);
        start = System.currentTimeMillis();

        if (debug) {
            for (FormulaAST r : rvResults) {
                if (r.getFormula().contains("@"))
                    System.out.println("Error in Preprocessor.preprocess(): rvresults contains rowvar: " + r);
            }
        }
        HashSet<FormulaAST> newRules = new HashSet<>();
        for (FormulaAST r : rules) {
            if (!rowvar.contains(r) && !predvar.contains(r) && !r.higherOrder && !r.containsNumber) { // only add rules without pred and row vars
                if (r.getFormula().contains("@"))
                    System.out.println("Error in Preprocessor.preprocess(): contains rowvar: " + r);
                else
                    newRules.add(r);
            }
        }
        // newRules.addAll(pviResults); // now add the new rules expanded from pred vars <- should not be needed
        newRules.addAll(rvResults); // now add the new rules expanded from row vars
        ArrayList<FormulaAST> finalRuleSet = new ArrayList<>();
        if (debug)
            System.out.println("Preprocessor.preprocess(): before reparse");
        newRules = reparse(newRules);
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time to reparse: " + end);
        start = System.currentTimeMillis();
        long crossCheck = 0;
        long sortalTimes = 0;
        long reparseTimes = 0;
        long addallTimes = 0;
        if (debug)
            System.out.println("Preprocessor.preprocess(): after reparse");
        for (FormulaAST r : newRules) {
            if (r.higherOrder || r.containsNumber) continue;
            long crossStart = System.currentTimeMillis();
            if (debug) System.out.println("Preprocessor.preprocess(): add sortals to r: " + r);
            long sortalStart = System.currentTimeMillis();
            sortals.addSortals(r);
            sortalTimes = sortalTimes + (System.currentTimeMillis()-sortalStart);
            if (debug) System.out.println("Preprocessor.preprocess(): result adding sortals to r: " + r);
            if (r.getFormula().contains("@"))
                System.out.println("Error in Preprocessor.preprocess(): before reparsing, contains rowvar: " + r);
            else {
                long reparseStart = System.currentTimeMillis();
                SuokifVisitor visitor = SuokifVisitor.parseFormula(r); // need to parse a third time after sortals are added
                reparseTimes = reparseTimes + (System.currentTimeMillis()-reparseStart);
                if (debug) System.out.println("Preprocessor.preprocess(): parsed r: " + visitor.result);
                long addallStart = System.currentTimeMillis();
                finalRuleSet.addAll(visitor.result.values());
                addallTimes = addallTimes + (System.currentTimeMillis()-addallStart);
            }
            crossCheck = crossCheck + (System.currentTimeMillis()-crossStart);
        }
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): # time to add sortals and reparse again: " + end);
        System.out.println("# Preprocessor.preprocess(): # of which, " + sortals.disjointTime + " millis was checking type disjointness");
        System.out.println("# Preprocessor.preprocess(): # of which, " + sortalTimes + " millis was adding sortals");
        System.out.println("# Preprocessor.preprocess(): # of which, " + reparseTimes + " millis was reparsing");
        System.out.println("# Preprocessor.preprocess(): # of which, " + addallTimes + " millis was addall");
        System.out.println("# Preprocessor.preprocess(): # of which, " + crossCheck + " millis cross-check");
        start = System.currentTimeMillis();
        HashMap<String,FormulaAST> res = new HashMap<>();
        for (FormulaAST f : finalRuleSet)
            res.put(f.getFormula(),f);
        end = (System.currentTimeMillis()-start)/1000;
        System.out.println("# Preprocessor.preprocess(): time to remove duplicates: " + end);
        return res.values();
    }

    /** ***************************************************************
     * After preprocessing, parse the new formula string in order to
     * set the caches correctly
     */
    public HashSet<FormulaAST> reparse(Collection<FormulaAST> rules) {

        if (debug) System.out.println("Preprocessor.reparse()");
        HashSet<FormulaAST> result = new HashSet<>();
        for (FormulaAST f : rules) {
            if (f.higherOrder) continue;
            if (f.getFormula().contains("@")) {
                System.out.println("Error in Preprocessor.reparse(): Shouldn't have row variable after preprocessing: " + f);
                continue;
            }
            if (debug) System.out.println("Preprocessor.reparse(): " + f);
            SuokifVisitor visitor = SuokifVisitor.parseFormula(f);
            if (debug) System.out.println("Preprocessor.reparse(): result" + visitor.result);
            if (visitor.result != null && visitor.result.values().size() > 0)
                result.add(visitor.result.values().iterator().next());
        }
        return result;
    }
}
