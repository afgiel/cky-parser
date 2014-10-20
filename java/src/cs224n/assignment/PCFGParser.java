package cs224n.assignment;

import cs224n.ling.Tree;
import cs224n.util.CounterMap;
import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
    private Grammar grammar;
    private Lexicon lexicon;

    public void train(List<Tree<String>> trainTrees) {
        List<Tree<String>> annotatedTrainTrees = new ArrayList<Tree<String>>(); 
        for (Tree trainTree : trainTrees) {
          annotatedTrainTrees.add(TreeAnnotations.annotateTree(trainTree));    
        }
        lexicon = new Lexicon(annotatedTrainTrees);
        grammar = new Grammar(annotatedTrainTrees);
    }

    public Tree<String> getBestParse(List<String> sentence) {
        CounterMap<String, String> grid = new CounterMap<String, String>(); 
        // Pair(x, y).toString then symbol to float  
        return null;
    }
}
