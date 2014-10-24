package cs224n.assignment;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.util.Triplet;
import cs224n.util.Pair;
import java.util.*;

/**
 * This is the Extra Credit Parser.
 */
public class ExtraCreditParser implements Parser {

  private Grammar grammar;
  private Lexicon lexicon;
  private CKYGrid grid;

  public void train(List<Tree<String>> trainTrees) {
    List<Tree<String>> annotatedTrainTrees = new ArrayList<Tree<String>>(); 
    for (Tree trainTree : trainTrees) {
      annotatedTrainTrees.add(TreeAnnotations.extraCredAnnotateTree(trainTree));    
    }
    lexicon = new Lexicon(annotatedTrainTrees);
    grammar = new Grammar(annotatedTrainTrees);
  }

  public Tree<String> getBestParse(List<String> sentence) {
    grid = new CKYGrid(); 
    fillGrid(sentence);
    return buildTree(sentence);
  }

  private void fillGrid(List<String> sentence) {
    for (int i = 0; i < sentence.size(); i++) {
      String interval = getSpanStr(i, i+1);
      for (String preterminal : lexicon.getAllTags()) {
        double prob = lexicon.scoreTagging(sentence.get(i), preterminal);
        if (prob > 0) {
          grid.setBoth(interval, preterminal, prob, null);
        }
      }
      // Handle unaries
      boolean added = true;
      while (added) {
        added = false;
        Set<String> nonterminals = new HashSet();
        nonterminals.addAll(grid.nonterminalSet(interval));
        for (String nonterminal : nonterminals) {
          double nontermProb = grid.getProb(interval, nonterminal); 
          for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
            String parentNonterminal = rule.getParent();
            double prob = rule.getScore()*nontermProb;
            if (prob > grid.getProb(interval, parentNonterminal)) {
              Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(-1, nonterminal, null);
              grid.setBoth(interval, parentNonterminal, prob, valTriplet);
              added = true;
            }
          }
        }
      }
    }
    for (int span = 2; span <= sentence.size(); span++) {
      for (int begin = 0; begin <= sentence.size() - span; begin++) {
        int end = begin + span;
        String interval = getSpanStr(begin, end);
        for (int split = begin + 1; split <=  end - 1; split++) {
          String leftSpan = getSpanStr(begin, split);
          String rightSpan = getSpanStr(split, end);
          Set<String> leftCellNonterms = grid.nonterminalSet(leftSpan);
          Set<String> rightCellNonterms = grid.nonterminalSet(rightSpan);
          for (Grammar.BinaryRule rule : getPossibleBinaryRules(leftCellNonterms, rightCellNonterms)) {
            String parent = rule.getParent();
            String rightChild = rule.getRightChild();
            String leftChild = rule.getLeftChild();
            double prob = grid.getProb(leftSpan, leftChild) * grid.getProb(rightSpan, rightChild) * rule.getScore();
            if (prob > grid.getProb(interval, parent)) {
              Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(split, leftChild, rightChild);
              grid.setBoth(interval, parent, prob, valTriplet);
            }
          }
        }
        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;
          Set<String> nonterminals = new HashSet();
          nonterminals.addAll(grid.nonterminalSet(interval));
          for (String nonterminal : nonterminals) {
            double nontermProb = grid.getProb(interval, nonterminal); 
            for (Grammar.UnaryRule rule : grammar.getUnaryRulesByChild(nonterminal)) {
              String parentNonterminal = rule.getParent();
              double prob = rule.getScore()*nontermProb;
              if (prob > grid.getProb(interval, parentNonterminal)) {
                Triplet<Integer, String, String> valTriplet = new Triplet<Integer, String, String>(-1, nonterminal, null);
                grid.setBoth(interval, parentNonterminal, prob, valTriplet);
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
        Triplet<Integer, String, String> valTriplet = grid.getBack(getSpanStr(begin, end), nonterminal);
        String childNonterminal = valTriplet.getSecond();
        // Add unary nonterminal to tree
        Tree<String> childTree = addToParseTree(parseTree, childNonterminal);
        backtrace(childTree, sentence, begin, end, childNonterminal);
      }
    } else {
      Triplet<Integer, String, String> valTriplet = grid.getBack(getSpanStr(begin, end), nonterminal);
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
