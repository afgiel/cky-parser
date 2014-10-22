package cs224n.assignment;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.util.Triplet;
import cs224n.util.CounterMap;
import cs224n.util.Counter;
import cs224n.util.ICounter;
import cs224n.util.Pair;
import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
  private Grammar grammar;
  private Lexicon lexicon;
  private int sentenceSize; 

  public void train(List<Tree<String>> trainTrees) {
    List<Tree<String>> annotatedTrainTrees = new ArrayList<Tree<String>>(); 
    for (Tree trainTree : trainTrees) {
      Tree<String> anTree =  TreeAnnotations.annotateTree(trainTree);
      /*
      System.out.println(Trees.PennTreeRenderer.render(trainTree));
      System.out.println(Trees.PennTreeRenderer.render(anTree));
      */
      annotatedTrainTrees.add(anTree);    
    }
    lexicon = new Lexicon(annotatedTrainTrees);
    grammar = new Grammar(annotatedTrainTrees);
  }

  public Tree<String> getBestParse(List<String> sentence) { 
    sentenceSize = sentence.size();
    List<ICounter<String>> grid = new ArrayList<ICounter<String>>(); 
    initGrid(grid);
    HashMap< Triplet<Integer, Integer, String>, Triplet<Integer, String, String> > back = new HashMap< Triplet<Integer, Integer, String>, Triplet<Integer, String, String> >();
    fillGrid(grid, back, sentence);
    // Pair(x, y).toString then symbol to float  
    return buildTree(grid, back, sentence);
  }

  private void fillGrid(List<ICounter<String>> grid, HashMap<Triplet<Integer, Integer, String>, Triplet<Integer, String, String>> back, List<String> sentence) {
    for (int i = 0; i < sentenceSize; i++) {
      for (String preterminal : lexicon.getAllTags()) {
        grid.get(getGridIndex(i, i+1)).setCount(preterminal, lexicon.scoreTagging(sentence.get(i), preterminal));
      }
      // Handle unaries
      boolean added = true;
      while (added) {
        added = false;
        // TODO: Reconsider this, was getting concurrency issues with iterating through set being modified
        Counter<Pair<Integer, String>> newProbs = new Counter<Pair<Integer, String>>(); 
        for (String nonterminal : grid.get(getGridIndex(i, i+1)).keySet()) {
          for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
            String parentNonterminal = rule.getParent();
            // TODO: POTENTIAL BUG ALERT -> best score might be in newProbs and not in grid ? 
            double prob = rule.getScore()*grid.get(getGridIndex(i, i+1)).getCount(nonterminal);
            if (prob > grid.get(getGridIndex(i, i+1)).getCount(parentNonterminal) && prob > newProbs.getCount(new Pair<Integer, String>(getGridIndex(i, i+1), parentNonterminal))) {
              newProbs.setCount(new Pair<Integer, String>(getGridIndex(i, i+1), parentNonterminal), prob);
              Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(i, i+1, parentNonterminal);
              // -1 indicates unary rule was used to backtrace
              Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(-1, nonterminal, null);
              back.put(keyTriplet, valTriplet);
              added = true;
            }
          }
        }
        addNewProbs(grid, newProbs);
      }
    }
    // TODO: Robustly handle shorter sentences
    for (int span = 2; span <= sentence.size(); span++) {
      for (int begin = 0; begin <= sentence.size() - span; begin++) {
        int end = begin + span;
        // TODO: Fixed bug by changing split < end -1 to split <=  end -1
        for (int split = begin + 1; split <=  end - 1; split++) {
          int leftIndex = getGridIndex(begin, split);
          int  rightIndex = getGridIndex(split, end);
          Set<String> leftCellNonterms = grid.get(leftIndex).keySet();
          Set<String> rightCellNonterms = grid.get(rightIndex).keySet();
          for (Grammar.BinaryRule rule : getPossibleBinaryRules(leftCellNonterms, rightCellNonterms)) {
            double prob = grid.get(leftIndex).getCount(rule.getLeftChild()) * grid.get(rightIndex).getCount(rule.getRightChild()) * rule.getScore();
            if (prob > grid.get(getGridIndex(begin, end)).getCount(rule.getParent())) {
              grid.get(getGridIndex(begin, end)).setCount(rule.getParent(), prob);
              Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, rule.getParent());
              Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(split, rule.getLeftChild(), rule.getRightChild());
              back.put(keyTriplet, valTriplet);
            }
          }
        }
        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;
          // TODO: Reconsider this, was getting concurrency issues with iterating through set being modified
          Counter<Pair<Integer, String>> newProbs = new Counter<Pair<Integer, String>>(); 
          for (String nonterminal : grid.get(getGridIndex(begin, end)).keySet()) {
            for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
              String parentNonterminal = rule.getParent();
              // TODO: POTENTIAL BUG ALERT -> best score might be in newProbs and not in grid ? 
              double prob = rule.getScore()*grid.get(getGridIndex(begin, end)).getCount(nonterminal);
              if (prob > grid.get(getGridIndex(begin, end)).getCount(parentNonterminal) && prob > newProbs.getCount(new Pair<Integer, String>(getGridIndex(begin, end), parentNonterminal))) {
                newProbs.setCount(new Pair<Integer, String>(getGridIndex(begin, end), parentNonterminal), prob);
                Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, parentNonterminal);
                Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(-1, nonterminal, null);
                back.put(keyTriplet, valTriplet);
                added = true;
              }
            }
          }
          addNewProbs(grid, newProbs);
        }
      }
    }
  }

  private int getGridSize(int sLength) {
    int sum = 0;
    for (int i = 0; i <=  sLength; i++) {
      sum += i; 
    }
    return sum; 
  }

  private void initGrid(List<ICounter<String>> grid) {
    int gridSize = getGridSize(sentenceSize); 
    for(int i = 0; i < gridSize; i++) {
      grid.add(new ICounter<String>()); 
    }
  }

  private int getGridIndex(int x, int y) {
    int index = -1;
    for (int i = x; i > 0; i--) {
      index += sentenceSize-i; 
    }
    index += (y - x);
    return index;
  }

  private void addNewProbs(List<ICounter<String>> grid, Counter<Pair<Integer, String>> newProbs) {
    for (Pair<Integer, String> keyPair : newProbs.keySet()) {
      grid.get(keyPair.getFirst()).setCount(keyPair.getSecond(), newProbs.getCount(keyPair));
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

  private Tree<String> buildTree(List<ICounter<String>> grid, HashMap<Triplet<Integer, Integer, String>,
                                 Triplet<Integer, String, String>> back, List<String> sentence) {
    String maxNonterminal = getMaxNonterminal(grid, 0, sentence.size());
    Tree<String> parseTree = new Tree<String>(maxNonterminal);
    if (! maxNonterminal.equals("ROOT")) {
      parseTree.setLabel("ROOT");
      Tree<String> childTree = addToParseTree(parseTree, maxNonterminal);

      System.out.println("Chosen root node: " + maxNonterminal);
      System.out.println("-------------------------------------------------");
      for (String nonterminal : grid.get(getGridIndex(0, sentenceSize)).keySet()) {
        double thisProb = grid.get(getGridIndex(0, sentenceSize)).getCount(nonterminal);
        System.out.println(nonterminal + " -- " + thisProb);
      }



      backtrace(back, childTree, sentence, 0, sentence.size(), maxNonterminal);
    } else {
      backtrace(back, parseTree, sentence, 0, sentence.size(), maxNonterminal);
    }
    return TreeAnnotations.unAnnotateTree(parseTree);
  }

  private void backtrace(HashMap<Triplet<Integer, Integer, String>, Triplet<Integer, String, String>> back,
                         Tree<String> parseTree, List<String> sentence, int begin, int end,
                         String nonterminal) {
    if (isStartCell(begin, end)) {
      // Check if preterminal
      if (lexicon.getAllTags().contains(nonterminal)) {
       // Add corresponding terminal to tree. 
        addToParseTree(parseTree, sentence.get(begin));
      } else {      // Must be a unary rule then
        Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, nonterminal);
        Triplet<Integer, String, String> valTriplet = back.get(keyTriplet);
        // TODO: Remove
        if (valTriplet == null) {
          System.out.println(lexicon.getAllTags());
          System.out.println(sentence);
          System.out.println(keyTriplet);
          System.out.println(back);
        }
        String childNonterminal = valTriplet.getSecond();
        // Add unary nonterminal to tree
        Tree<String> childTree = addToParseTree(parseTree, childNonterminal);
        backtrace(back, childTree, sentence, begin, end, childNonterminal);
      }
    } else {
      Triplet<Integer, Integer, String> keyTriplet = new Triplet<Integer,Integer,String>(begin, end, nonterminal);
      Triplet<Integer, String, String> valTriplet = back.get(keyTriplet);
      // Check if it is a unary rule using -1 indication.
      // TODO: Remove
      if (valTriplet == null) {
        System.out.println(sentence);
        System.out.println(keyTriplet);
        System.out.println(back);
      }
      if (valTriplet.getFirst() == -1) {
        String childNonterminal = valTriplet.getSecond();
        // Add unary nonterminal to tree
        Tree<String> childTree = addToParseTree(parseTree, childNonterminal);
        backtrace(back, childTree, sentence, begin, end, childNonterminal);
      // Binary rule
      } else {
        // Must add left child first, because children in tree are expected to be ordered.
        String leftChildNonterminal = valTriplet.getSecond();
        Tree<String> leftChildTree = addToParseTree(parseTree, leftChildNonterminal);
        backtrace(back, leftChildTree, sentence, begin, valTriplet.getFirst(), leftChildNonterminal);
        String rightChildNonterminal = valTriplet.getThird();
        Tree<String> rightChildTree = addToParseTree(parseTree, rightChildNonterminal);
        backtrace(back, rightChildTree, sentence, valTriplet.getFirst(), end, rightChildNonterminal);
      }
    }
  }

  private String getMaxNonterminal(List<ICounter<String>> grid, int start, int end) {
    String maxNonterminal = null;
    double maxProb = 0;
    for (String nonterminal : grid.get(getGridIndex(start, end)).keySet()) {
      double thisProb = grid.get(getGridIndex(start, end)).getCount(nonterminal);
      if (thisProb >= maxProb) {
        if (thisProb == maxProb) {
          maxNonterminal = chooseRoot(nonterminal, maxNonterminal);
        } else {
          maxNonterminal = nonterminal;
        }
        maxProb = thisProb;
      }
    }
    return maxNonterminal;
  }

  private String chooseRoot(String nt1, String nt2) {
    if (nt1.equals("ROOT")) return nt1;
    return nt2;
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
