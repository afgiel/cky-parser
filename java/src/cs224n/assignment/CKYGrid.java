package cs224n.assignment;

import java.util.Map;
import java.util.HashMap;
import cs224n.util.MapFactory;
import cs224n.util.Pair;
import cs224n.util.Triplet;
import java.util.Set;

public class CKYGrid {

  private MapFactory<String, Pair<Double, Triplet<Integer, String, String>>> mf;
  private Map<String, Map<String, Pair<Double, Triplet<Integer, String, String>>>> grid;

  public double getProb(String interval, String nonterminal) {
    double prob; 
    try {
      prob = grid.get(interval).get(nonterminal).getFirst(); 
     } catch (Exception e) {
      return 0.0;
    }
    return prob;
  }

  public Triplet<Integer, String, String>  getBack(String interval, String nonterminal) {
    return grid.get(interval).get(nonterminal).getSecond(); 
  }

  public void setBoth(String interval, String nonterminal, double prob, Triplet<Integer, String, String> backpointer) {
    Map<String, Pair<Double, Triplet<Integer, String, String>>> innerH = ensureMap(interval);
    innerH.put(nonterminal, new Pair<Double, Triplet<Integer, String, String>>(prob, backpointer));
  }

  private Map<String, Pair<Double, Triplet<Integer, String, String>>> ensureMap(String key) {
    Map<String, Pair<Double, Triplet<Integer, String, String>>> valueMap = grid.get(key);
    if (valueMap == null) {
      valueMap = mf.buildMap();
      grid.put(key, valueMap);
    }
    return valueMap;
  }

  public Set<String> nonterminalSet(String interval) {
    return ensureMap(interval).keySet();
  }
  
  public CKYGrid() {
    this(new MapFactory.HashMapFactory<String, Map<String, Pair<Double, Triplet<Integer, String, String>>>>(), 
         new MapFactory.HashMapFactory<String, Pair<Double, Triplet<Integer, String, String>>>());
  }

  public CKYGrid(MapFactory<String, Map<String, Pair<Double, Triplet<Integer, String, String>>>> outerMF, 
                    MapFactory<String, Pair<Double, Triplet<Integer, String, String>>> innerMF) {
    mf = innerMF;
    grid = outerMF.buildMap();
  }
}
