package cs224n.assignment;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.util.Triplet;
import cs224n.util.CounterMap;
import cs224n.util.Counter;
import cs224n.util.Pair;
import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {

  private Grammar grammar;
  private Lexicon lexicon;
  private CounterMap<String, String> grid;
  private HashMap< Triplet<Integer, Integer, String>, Triplet<Integer, String, String> > back;

  public void train(List<Tree<String>> trainTrees) {
    List<Tree<String>> annotatedTrainTrees = new ArrayList<Tree<String>>(); 
    for (Tree trainTree : trainTrees) {
      annotatedTrainTrees.add(TreeAnnotations.annotateTree(trainTree));    
    }
    lexicon = new Lexicon(annotatedTrainTrees);
    grammar = new Grammar(annotatedTrainTrees);
  }

  public Tree<String> getBestParse(List<String> sentence) {
    grid = new CounterMap<String, String>(); 
    back = new HashMap< Triplet<Integer, Integer, String>, Triplet<Integer, String, String> >();
    fillGrid(sentence);
    return buildTree(sentence);
  }

  // Combine maps for backtrace and grid
  // Identity Hash Map
  // Reconsider concurrency

  private void fillGrid(List<String> sentence) {
    for (int i = 0; i < sentence.size(); i++) {
      String interval = getSpanStr(i, i+1);
      for (String preterminal : lexicon.getAllTags()) {
        double prob = lexicon.scoreTagging(sentence.get(i), preterminal);
        if (prob > 0) {
          grid.setCount(interval, preterminal, prob);
        }
      }
      // Handle unaries
      boolean added = true;
      while (added) {
        added = false;
        // TODO: Reconsider this, was getting concurrency issues with iterating through set being modified
        Set<String> nonterminals = new HashSet();
        nonterminals.addAll(grid.getCounter(interval).keySet());
        for (String nonterminal : nonterminals) {
          double nontermProb = grid.getCount(interval, nonterminal); 
          for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
            String parentNonterminal = rule.getParent();
            // TODO: POTENTIAL BUG ALERT -> best score might be in newProbs and not in grid ? 
            double prob = rule.getScore()*nontermProb;
            if (prob > grid.getCount(interval, parentNonterminal)) {
              grid.setCount(interval, parentNonterminal, prob);
              Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(i, i+1, parentNonterminal);
              // -1 indicates unary rule was used to backtrace
              Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(-1, nonterminal, null);
              back.put(keyTriplet, valTriplet);
              added = true;
            }
          }
        }
      }
    }
    // TODO: Robustly handle shorter sentences
    for (int span = 2; span <= sentence.size(); span++) {
      for (int begin = 0; begin <= sentence.size() - span; begin++) {
        int end = begin + span;
        String interval = getSpanStr(begin, end);
        for (int split = begin + 1; split <=  end - 1; split++) {
          String leftSpan = getSpanStr(begin, split);
          String rightSpan = getSpanStr(split, end);
          Set<String> leftCellNonterms = grid.getCounter(leftSpan).keySet();
          Set<String> rightCellNonterms = grid.getCounter(rightSpan).keySet();
          for (Grammar.BinaryRule rule : getPossibleBinaryRules(leftCellNonterms, rightCellNonterms)) {
            String parent = rule.getParent();
            String rightChild = rule.getRightChild();
            String leftChild = rule.getLeftChild();
            double prob = grid.getCount(leftSpan, leftChild) * grid.getCount(rightSpan, rightChild) * rule.getScore();
            if (prob > grid.getCount(interval, parent)) {
              grid.setCount(interval, parent, prob);
              Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, parent);
              Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(split, leftChild, rightChild);
              back.put(keyTriplet, valTriplet);
            }
          }
        }
        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;
          // TODO: Reconsider this, was getting concurrency issues with iterating through set being modified
          Set<String> nonterminals = new HashSet();
          nonterminals.addAll(grid.getCounter(interval).keySet());
          for (String nonterminal : nonterminals) {
            double nontermProb = grid.getCount(interval, nonterminal); 
            for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
              String parentNonterminal = rule.getParent();
              // TODO: POTENTIAL BUG ALERT -> best score might be in newProbs and not in grid ? 
              double prob = rule.getScore()*nontermProb;
              if (prob > grid.getCount(interval, parentNonterminal)) {
                grid.setCount(interval, parentNonterminal, prob);
                Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, parentNonterminal);
                Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(-1, nonterminal, null);
                back.put(keyTriplet, valTriplet);
                added = true;
              }
            }
          }
        }
      }
    }
  }
 
  private void addNewProbs(Counter<Pair<String, String>> newProbs) {
    for (Pair<String, String> keyPair : newProbs.keySet()) {
      grid.setCount(keyPair.getFirst(), keyPair.getSecond(), newProbs.getCount(keyPair));
    }
  }

  private String getSpanStr(int x, int y) {
    return String.format("%d,%d", x, y);
  }

  private Set<Grammar.BinaryRule> getPossibleBinaryRules(Set<String> leftCellNonterms, Set<String> rightCellNonterms) {
    Set<Grammar.BinaryRule> leftRules = new HashSet();
    for (String nonterminal : leftCellNonterms) {
      leftRules.addAll(grammar.getBinaryRulesByLeftChild(nonterminal));
    }
    Set<Grammar.BinaryRule> rightRules = new HashSet();
    for (String nonterminal : rightCellNonterms) {
      rightRules.addAll(grammar.getBinaryRulesByRightChild(nonterminal));
    }
    // retainAll modifies set on which it is called in place.
    leftRules.retainAll(rightRules);
    return leftRules;
  }

  private Tree<String> buildTree(List<String> sentence) {
    Tree<String> parseTree = new Tree<String>("ROOT");
    backtrace(parseTree, sentence, 0, sentence.size(), "ROOT");
    return TreeAnnotations.unAnnotateTree(parseTree);
  }

  private void backtrace(Tree<String> parseTree, List<String> sentence, int begin, int end,
                         String nonterminal) {
    if (isStartCell(begin, end)) {
      // Check if preterminal
      if (lexicon.getAllTags().contains(nonterminal)) {
       // Add corresponding terminal to tree. 
        addToParseTree(parseTree, sentence.get(begin));
      } else {      // Must be a unary rule then
        Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, nonterminal);
        Triplet<Integer, String, String> valTriplet = back.get(keyTriplet);
        String childNonterminal = valTriplet.getSecond();
        // Add unary nonterminal to tree
        Tree<String> childTree = addToParseTree(parseTree, childNonterminal);
        backtrace(childTree, sentence, begin, end, childNonterminal);
      }
    } else {
      Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, nonterminal);
      Triplet<Integer, String, String> valTriplet = back.get(keyTriplet);
      // Check if it is a unary rule using -1 indication.
      if (valTriplet.getFirst() == -1) {
        String childNonterminal = valTriplet.getSecond();
        // Add unary nonterminal to tree
        Tree<String> childTree = addToParseTree(parseTree, childNonterminal);
        backtrace(childTree, sentence, begin, end, childNonterminal);
      // Binary rule
      } else {
        // Must add left child first, because children in tree are expected to be ordered.
        String leftChildNonterminal = valTriplet.getSecond();
        Tree<String> leftChildTree = addToParseTree(parseTree, leftChildNonterminal);
        backtrace(leftChildTree, sentence, begin, valTriplet.getFirst(), leftChildNonterminal);
        String rightChildNonterminal = valTriplet.getThird();
        Tree<String> rightChildTree = addToParseTree(parseTree, rightChildNonterminal);
        backtrace(rightChildTree, sentence, valTriplet.getFirst(), end, rightChildNonterminal);
      }
    }
  }

  private boolean isStartCell(int begin, int end) {
    return end - begin == 1;
  }

  /**
   * Adds child to tree passed in. Child is given the label given.
   * The child tree is returned.
   * TODO: Discuss better way of doing this
   */
  private Tree<String> addToParseTree(Tree<String> parseTree, String label) {
    Tree<String> childTree = new Tree<String>(label);
    List<Tree<String>> newChildren = new ArrayList<Tree<String>>();
    List<Tree<String>> children = parseTree.getChildren();
    if (children != null) {
      newChildren.addAll(children);
    }
    newChildren.add(childTree);
    parseTree.setChildren(newChildren);
    return childTree;
  }
}
