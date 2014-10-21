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
    fillGrid(grid, sentence);
    // Pair(x, y).toString then symbol to float  
    return buildTree(grid, sentence.size());
  }

  private void fillGrid(CounterMap<String, String> grid, List<String> sentence) {
    for (int i = 0; i < sentence.size(); i++) {
      for (String preterminal : lexicon.getAllTags()) {
        grid.setCount(getSpanStr(i, i+1), preterminal, lexicon.scoreTagging(sentence.get(i), preterminal));
      }
      // Handle unaries
      boolean added = true;
      while (added) {
        added = false;
        for (String nonterminal : grid.getCounter(getSpanStr(i, i+1)).keySet()) {
          for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
            String parentNonterminal = rule.getParent();
            double prob = rule.getScore()*grid.getCount(getSpanStr(i, i+1), nonterminal);
            if (prob > grid.getCount(getSpanStr(i, i+1), parentNonterminal)) {
              grid.setCount(getSpanStr(i, i+1), parentNonterminal, prob);
              added = true;
            }
          }
        }
      }
    }
    // TODO: Robustly handle shorter sentences
    for (int span = 2; span < sentence.size(); span++) {
      for (int begin = 0; begin < sentence.size() - span; begin++) {
        int end = begin + span;
        for (int split = begin + 1; split < end - 1; split++) {
          String leftSpan = getSpanStr(begin, split);
          String rightSpan = getSpanStr(split, end);
          Set<String> leftCellNonterms = grid.getCounter(leftSpan).keySet();
          Set<String> rightCellNonterms = grid.getCounter(rightSpan).keySet();
          for (Grammar.BinaryRule rule : getPossibleBinaryRules(leftCellNonterms, rightCellNonterms)) {
            double prob = grid.getCount(leftSpan, rule.getLeftChild()) * grid.getCount(rightSpan, rule.getRightChild()) * rule.getScore();
            if (prob > grid.getCount(getSpanStr(begin, end), rule.getParent())) {
              grid.setCount(getSpanStr(begin, end), rule.getParent(), prob);
            }
          }
        }
        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;
          for (String nonterminal : grid.getCounter(getSpanStr(begin, end)).keySet()) {
            for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
              String parentNonterminal = rule.getParent();
              double prob = rule.getScore()*grid.getCount(getSpanStr(begin, end), nonterminal);
              if (prob > grid.getCount(getSpanStr(begin, end), parentNonterminal)) {
                grid.setCount(getSpanStr(begin, end), parentNonterminal, prob);
                added = true;
              }
            }
          }
        }
      }
    }
  }

  private String getSpanStr(int x, int y) {
    return String.format("%d,%d", x, y);
  }

  private Set<Grammar.BinaryRule> getPossibleBinaryRules(Set<String> leftCellNonterms, Set<String> rightCellNonterms) {
    Set<Grammar.BinaryRule> leftRules = new HashSet();
    for (String nonterminal : leftCellNonterms) {
      for (Grammar.BinaryRule rule : grammar.getBinaryRulesByLeftChild(nonterminal)) {
        leftRules.add(rule);
      }
    }
    Set<Grammar.BinaryRule> rightRules = new HashSet();
    for (String nonterminal : rightCellNonterms) {
      for (Grammar.BinaryRule rule : grammar.getBinaryRulesByRightChild(nonterminal)) {
        rightRules.add(rule);
      }
    }
    // retainAll modifies set on which it is called in place.
    leftRules.retainAll(rightRules);
    return leftRules;
  }

  private Tree<String> buildTree(CounterMap<String, String> grid, int sentenceSize) {
    String maxNonterminal = null;
    double maxProb = 0;
    for (String nonterminal : grid.getCounter(getSpanStr(0, sentenceSize)).keySet()) {
      double thisProb = grid.getCount(getSpanStr(0, sentenceSize), nonterminal);
      if (thisProb > maxProb) {
        maxProb = thisProb;
        maxNonterminal = nonterminal;
      }
    }
    backtrace(grid, 0, sentenceSize, maxNonterminal);
    return null;
  }

  private void backtrace(CounterMap<String, String> grid, int begin, int end) {

  }

}
