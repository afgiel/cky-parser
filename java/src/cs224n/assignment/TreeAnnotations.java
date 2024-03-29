package cs224n.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.ling.Trees.MarkovizationAnnotationStripper;
import cs224n.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for
 * the parser's use, and debinarizing and unannotating them for
 * scoring.
 */
public class TreeAnnotations {

  /*
   * Adds the Vsplit annotations for extra credit!
   */
	public static Tree<String> extraCredAnnotateTree(Tree<String> unAnnotatedTree) {
    vMarkovize(unAnnotatedTree);
    vSplit(unAnnotatedTree);
		return binarizeTree(unAnnotatedTree);
	}

	public static Tree<String> annotateTree(Tree<String> unAnnotatedTree) {
    vMarkovize(unAnnotatedTree);
		return binarizeTree(unAnnotatedTree);
	}

  private static String vSplit(Tree<String> tree) {
    if (tree.isLeaf()) return null;
    else if (tree.getLabel().indexOf("VB") == -1) {
      for (Tree<String> childTree : tree.getChildren()) {
        String tempVLabel = vSplit(childTree);
        if (tempVLabel != null) {
          if (tree.getLabel().indexOf("VP") == -1) {
            return tempVLabel;
          } else {
            tree.setLabel(tree.getLabel() + "~" + tempVLabel);
            return null;
          }
        }
      }
      return null;
    } else {
      return tree.getLabel();
    }
  }

  private static void vMarkovize(Tree<String> tree) {
    if (!tree.isLeaf()) {
      String parent = tree.getLabel();
      for (Tree<String> childTree : tree.getChildren()) {
        vMarkovizeHelper(childTree, parent);
      }
    }
  }

  private static void  vMarkovizeHelper(Tree<String> tree, String parent) {
    if (!tree.isLeaf()) {
      String newParentLabel = tree.getLabel();
      tree.setLabel(newParentLabel + "^" + parent);
      for (Tree<String> childTree : tree.getChildren()) {
        vMarkovizeHelper(childTree, newParentLabel);
      }
    }
  }

	private static Tree<String> binarizeTree(Tree<String> tree) {
		String label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(label);
		if (tree.getChildren().size() == 1) {
			return new Tree<String>
			(label, 
					Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
		}
		// otherwise, it's a binary-or-more local tree, 
		// so decompose it into a sequence of binary and unary trees.
		String intermediateLabel = "@"+label+"->";
		Tree<String> intermediateTree =
				binarizeTreeHelper(tree, 0, intermediateLabel);
		return new Tree<String>(label, intermediateTree.getChildren());
	}

	private static Tree<String> binarizeTreeHelper(Tree<String> tree,
			int numChildrenGenerated, 
			String intermediateLabel) {
		Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		children.add(binarizeTree(leftTree));
		if (numChildrenGenerated == tree.getChildren().size() - 2){
      Tree<String> rightTree = tree.getChildren().get(numChildrenGenerated + 1);
      children.add(binarizeTree(rightTree));
    } else if (numChildrenGenerated < tree.getChildren().size() - 1) {
			Tree<String> rightTree = 
					binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							intermediateLabel + "_" + leftTree.getLabel());
			children.add(rightTree);
		}
		return new Tree<String>(intermediateLabel, children);
	} 

	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

		// Remove intermediate nodes (labels beginning with "@"
		// Remove all material on node labels which follow their base symbol
		// (cuts at the leftmost - or ^ character)
		// Examples: a node with label @NP->DT_JJ will be spliced out, 
		// and a node with label NP^S will be reduced to NP

		Tree<String> debinarizedTree =
				Trees.spliceNodes(annotatedTree, new Filter<String>() {
					public boolean accept(String s) {
						return s.startsWith("@");
					}
				});
		Tree<String> unAnnotatedTree = 
				(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    Tree<String> unMarkovizedTree =
        (new Trees.MarkovizationAnnotationStripper()).transformTree(unAnnotatedTree);
		return unMarkovizedTree;
	}
}
