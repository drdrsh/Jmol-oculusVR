package org.jmol.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Map;

import javajs.util.AU;
import javajs.util.Lst;
import javajs.util.M3;
import javajs.util.M4;
import javajs.util.Quat;
import javajs.util.P3;
import javajs.util.V3;
import javajs.util.T3;

/**
 * A class to properly cap a convoluted, closed slice of an isosurface
 * 
 * inspired by: Computational Geometry: Algorithms and Applications Mark de
 * Berg, Marc van Kreveld, Mark Overmars, and Otfried Schwarzkopf
 * Springer-Verlag, Berlin Heidelberg 1997 Chapter 3. Polygon Triangulation
 * 
 * Thanks given to Olaf Hall-Holt for pointing me to this reference.
 * 
 * Extensively modified:
 * 
 * - quaternion transform from 3D plane to XY plane for best precision
 * 
 * - using directional edges -- no angle measurements necessary
 * 
 * - continuous splitting off of triangles
 * 
 * - independent dynamic monotonic regions created and processed as one stream
 * 
 * - no need for vertex typing
 * 
 * - no push/pop stacks
 * 
 * INPUT: stream of [a b] ordered-vertex edges such that triangle a-b-c is
 * interior if (ab.cross.ac).dot.planeNormal > 0 (right-hand rule;
 * counter-clockwise edge flow)
 * 
 * Bob Hanson - Jan 11, 2015
 * 
 * @author Bob Hanson, hansonr@stolaf.edu
 */
public class MeshCapper {

  public MeshCapper() {
    // for reflection
  }

  /**
   * source of edges; consumer of triangles
   */
  private MeshSlicer slicer;

  /**
   * for debugging
   */
  private boolean dumping;

  /**
   * initialization only
   */
  private Map<Integer, CapVertex> capMap;
  private Lst<CapVertex> vertices;

  /**
   * dynamic region processing. These are just [DESCENDER, ASCENDER, LAST] for
   * each region
   * 
   */
  private Lst<CapVertex[]> lstRegions;

  private static final int DESCENDER = 0;
  private static final int ASCENDER = 1;
  private static final int LAST = 2;

  /**
   * informational only
   */
  private int nTriangles, nRegions;

  private Lst<int[]> lstTriangles;

  private int nPoints;

  /////////////// initialization //////////////////

  /**
   * @param slicer
   * @return this
   */
  public MeshCapper set(MeshSlicer slicer) {
    // resolution has not been necessary
    this.slicer = slicer;
    dumping = Logger.debugging;
    return this;
  }

  void clear() {
    capMap = new Hashtable<Integer, CapVertex>();
    vertices = new Lst<CapVertex>();
  }

  /**
   * generic entry for a polygon
   * 
   * @param points
   * @return int[][i j k mask]
   */
  public int[][] triangulatePolygon(P3[] points) {
    clear();
    // this is winding backward 
    nPoints = points.length;
    CapVertex v0 = null;
    for (int i = 0; i < nPoints; i++) {
      CapVertex v = new CapVertex(points[i], i);
      vertices.addLast(v);
      if (v0 != null) {
        v0.link(v);
      }
      v0 = v;
    }
    v0.link(vertices.get(0));
    lstTriangles = new Lst<int[]>();
    createCap(null);
    int[][] a = AU.newInt2(lstTriangles.size());
    for (int i = lstTriangles.size(); --i >= 0;)
      a[i] = lstTriangles.get(i);
    return a;
  }

  /**
   * Input method from MeshSlicer. Pointers are into MeshSlicer.m.vs[]
   * 
   * @param ipt1
   * @param ipt2
   * @param thisSet
   */

  void addEdge(int ipt1, int ipt2, int thisSet) {
    CapVertex v1 = addPoint(thisSet, ipt1);
    CapVertex v2 = addPoint(thisSet, ipt2);
    v1.link(v2);
  }

  /**
   * The MeshSlicer class manages all introduction of vertices; we must pass on
   * to it the subset of vertices from the original Jmol isosurface being
   * capped.
   * 
   * @param thisSet
   * @param i
   * @return a CapVertex pointing to this new point in the isosurface or one we
   *         already have
   */
  private CapVertex addPoint(int thisSet, int i) {
    Integer ii = Integer.valueOf(i);
    CapVertex v = capMap.get(ii);
    if (v == null) {
      T3 pt = slicer.m.vs[i];
      // copy this point to a new one to avoid shading across the edge
      i = slicer.addIntersectionVertex(pt, 0, -1, thisSet, null, -1, -1);
      v = new CapVertex(pt, i);
      vertices.addLast(v);
      capMap.put(ii, v);
    }
    if (dumping)
      Logger.info(i + "\t" + slicer.m.vs[i]);
    return v;
  }

  /**
   * for debugging
   * 
   * @param v
   * @return external point or test point
   */
  private T3 getInputPoint(CapVertex v) {
    return (slicer == null ? P3.newP(v) : slicer.m.vs[v.ipt]);
  }

  /**
   * Export triangle to MeshSlicer
   * 
   * @param ipt1
   * @param ipt2
   * @param ipt3
   */
  private void outputTriangle(int ipt1, int ipt2, int ipt3) {
    if (slicer == null) {
      int mask = 0;
      if (isEdge(ipt1, ipt2))
        mask |= 1;
      if (isEdge(ipt2, ipt3))
        mask |= 2;
      if (isEdge(ipt3, ipt1))
        mask |= 4;
      lstTriangles.addLast(new int[] { ipt1, ipt2, ipt3, mask });
    } else {
      slicer.addTriangle(ipt1, ipt2, ipt3);
    }
  }

  private boolean isEdge(int i, int j) {
    return (j == (i + 1) % nPoints);
  }

  private CapVertex[] test(CapVertex[] vs) {
    //  dumping = testing = true;
    //  int n = 0;
    //  vs = new CapVertex[] {
    //   new CapVertex(P3.new3(0,  10,  0), n++),
    //   new CapVertex(P3.new3(0,  0,  0), n++), 
    //   new CapVertex(P3.new3(1,  0,  0), n++), 
    //   new CapVertex(P3.new3(2,  0,  0), n++)

    //      new CapVertex(P3.new3(0,  10,  0), n++)
    //      new CapVertex(P3.new3(-2,  0,  0), n++),
    //      new CapVertex(P3.new3(-1,  0,  0), n++), 
    //      new CapVertex(P3.new3(0,  0,  0), n++)

    //      new CapVertex(P3.new3(0,  10,  0), n++),
    //      new CapVertex(P3.new3(-2,  0,  0), n++),
    //      new CapVertex(P3.new3(-1,  0,  0), n++), 
    //      new CapVertex(P3.new3(0,  0,  0), n++), 
    //      new CapVertex(P3.new3(0,  6,  0), n++), 
    //      new CapVertex(P3.new3(0,  8,  0), n++) 
    //  };
    //  for (int i = 0; i < n; i++)
    //    vs[i].link(vs[(i + 1)%n]);
    return vs;
  }

  /////////////// processing //////////////////

  /**
   * Entry point when finished generating edges.
   * 
   * @param norm
   */
  void createCap(V3 norm) {


    capMap = null;

    CapVertex[] vs = new CapVertex[vertices.size()];

    if (vs.length < 3)
      return;

    // get transform to xy plane
    // to give best precision

    V3 vab = V3.newVsub(vertices.get(0), vertices.get(1));
    V3 vac;
    if (norm == null) {
      vac = V3.newVsub(vertices.get(0), vertices.get(vertices.size() - 1));
    } else {
      vac = V3.newV(norm);
      vac.cross(vac, vab);
    }

    //Get xy plane points
    Quat q = Quat.getQuaternionFrameV(vab, vac, null, false);
    M3 m3 = q.getMatrix();
    M4 m4 = M4.newMV(m3, vertices.get(0));
    M4 m4inv = M4.newM4(m4).invert();
    vertices.toArray(vs);
    vertices = null;
        
    for (int i = vs.length; --i >= 0;)
      m4inv.rotTrans2(vs[i], vs[i]);

    // link by Y,X sort

    vs = test(vs);

    Logger.info("MeshCapper using " + vs.length + " vertices");
    
    CapVertex v0 = vs[0].sort(vs);
    if (v0 == null) {
      Logger.error("two identical points -- aborting");
      return;
    }

    lstRegions = new Lst<CapVertex[]>();

    // scan the plane

    CapVertex v = v0;
    try {
    do {
      v = process(v);
    } while (v != v0);
    } catch (Exception e) {
      System.out.println("MeshCapper exception " + e);
      e.printStackTrace();
    }
    if (slicer != null)
      clear();
    Logger.info("MeshCapper created " + nTriangles + " triangles " + nRegions
        + " regions");

  }

  /**
   * Handle the point; mark as processed.
   * 
   * @param v
   * 
   * @return next point to process
   */
  private CapVertex process(CapVertex v) {
    //
    //                    /\
    //                   /  \ascending
    //       descending /    \
    //                 /      \
    //                /        \
    //              -/----------*-<
    //              /            \
    CapVertex q = v.qnext;
    v.qnext = null; // indicates already processed
    if (dumping)
      Logger.info(v.toString());
    if (v.prev == v.next)
      return q;

    if (v.next == null)
      System.out.println("OHO");
    boolean isDescending = (v.prev.region != null);
    boolean isAscending = (v.next.region != null);

    if (dumping)
      Logger.info("#" + (isAscending ? v.next.id : "    ") + "    "
          + (isDescending ? v.prev.id : "") + "\n#"
          + (isAscending ? "   \\" : "    ")
          + (isDescending ? "    /\n" : "\n") + "#    " + v.id);

    if (!isDescending && !isAscending) {
      CapVertex last = getLastPoint(v);
      if (last == null) {
        // start vertex -- just create a new region
        newRegion(v);
        return q;
      }
      CapVertex p = processSplit(v, last);
      // patch in new point as the next to process
      p.qnext = q;
      q = p;
      // process left branch
      isAscending = true;
    }

    // note that a point may be both ascending and descending:

    //
    //                    /\         /\
    //                   /  \       /  \
    //                  /    \     /    \
    //                 /   next   prev   \
    //                /        \ /        \
    //              -/----------*----------\<
    //              /                       \

    if (isDescending) {
      processMonotonic(v, true);
    }
    if (isAscending) {
      processMonotonic(v, false);
    }

    if (isDescending && isAscending) {
      if (v.prev.prev == v.next) {
        // end vertex -- draw last triangle
        lstRegions.removeObj(v.region);
        addTriangle(v.prev, v, v.next, "end");
        
        clearV(v.prev);
        clearV(v.next);
      } else {
        // merge vertex -- linking two separate regions
        // just mark as having no region yet
        v.region = null;
      }

    }
    return q;
  }

  private static void clearV(CapVertex v) {
    if (v != null)
      v.clear();
  }

  /**
   * Process a standard monotonic region, cleaving off as many triangles as
   * possible.
   * 
   * @param v
   * @param isDescending
   */
  private void processMonotonic(CapVertex v, boolean isDescending) {
    CapVertex vEdge = (isDescending ? v.prev : v.next);
    v.region = vEdge.region;
    CapVertex last = v.region[LAST];
    if (last == v) {
      // single triangle processed already by descender
      lstRegions.removeObj(v.region);
      return;
    }
    CapVertex v2, v1;

    if (last == vEdge) {

      // same side

      v1 = last;
      v2 = (isDescending ? v1.prev : v1.next);
      while (v2 != v && v2.qnext == null
          && isDescending == (v.x > v.interpolateX(v2, v1))) {
        if (isDescending) {
          // same side descending
          //
          //                    /\
          //                  v2  \
          //                  /    \
          //         --(last)v-----------
          //                /        \
          //              -*----------\-<
          //              /            \

          addTriangle(v2, v1, v, "same desc " + v.ipt);
          v1 = v2;
          v2 = v2.prev;
        } else {
          // same side ascending
          //
          //                    /\
          //                   /  v2
          //                  /    \
          //              ----------v(last)--
          //                /        \
          //              ------------*-<
          //              /            \

          addTriangle(v, v1, v2, "same asc " + v.ipt);
          v1 = v2;
          v2 = v2.next;
        }
      }
    } else {
      // opposite side
      v2 = vEdge;
      do {
        v1 = v2;
        if (isDescending) {
          v2 = v1.prev;

          // opposite side descending
          //
          //                     v(vEdge)
          //                    / \
          //                   /   v2
          //                  /     \
          //               ----------last
          //                /         \
          //              -*-----------\-<
          //              /             \

          addTriangle(v2, v1, v, "opp desc " + v.id);
        } else {
          v2 = v1.next;

          // opposite side ascending
          //
          //                    /\
          //                   /  \
          //                  /    v(vEdge)
          //            --last------\----
          //                /        \
          //              -/----------*-<
          //              /            \

          addTriangle(v, v1, v2, "opp asc " + v.id);
        }
      } while (v2 != last && v2 != v && v2.qnext == null);
      if (last.region == null) {
        // done with this region
        lstRegions.removeObj(v.region);
        v.region = last.region = (isDescending ? last.prev : last.next).region;
      }
    }
    v.region[LAST] = v.region[isDescending ? DESCENDER : ASCENDER] = v;
  }

  /**
   * 
   * Process what M3O refer to as a "split" vertex, which we handle differently
   * here, cloning the "helper" point and the "split" point, creating a new
   * region if necessary, and then swapping pointers.
   * 
   * @param v
   * 
   * @param last
   *        "helper" or left edge
   * @return new point clone of this
   * 
   */
  private CapVertex processSplit(CapVertex v, CapVertex last) {

    CapVertex pv = last.cloneV();
    if (dumping)
      pv.id += "a";
    CapVertex p = v.cloneV();
    if (dumping)
      p.id += "a";

    if (last.region == null) {

      // split is to a merge vertex
      //
      //                    /\
      //       last.next   /  \
      //                \ /    \
      //              last(pv)--\-
      //                         \
      //              ------*(p)--\-<
      //                   / \     
      // becomes
      //
      //                      /\
      //       last.next     /  \
      //                \   /    \
      //              last pv-----\-
      //                  \ \      \
      //                   * p------\-<
      //                  /   \     

      last.region = last.next.region;
      pv.region = last.prev.region;

    } else {

      // split is to an edge, requiring a new region

      //                    /\
      //                   /  \
      //                  /    \
      //              last(pv)----
      //                /        \
      //              -/----*(p)--\-<
      //        last.next  / \     \

      // becomes
      //
      //                      /\
      //                     /  \
      //                    /    \
      //              last pv     \    
      //                / \ \      \
      //              -/---* p------\-<
      //        last.next /   \      \     

      newRegion(last);

      // It is possible for v.next to be above. This will happen
      // in the case where we have just a single edge with d above a.

      CapVertex cv = last;
      while (cv.next.region != null) {
        cv.next.region = cv.region;
        cv = cv.next;
        cv.region[DESCENDER] = cv;
      }
    }

    // fix region references

    CapVertex[] r = pv.region;
    if (r[LAST] == last)
      r[LAST] = pv;
    r[DESCENDER] = pv;
    if (r[ASCENDER] == last)
      r[ASCENDER] = pv;

    // patch new edges

    v.link(last);
    pv.prev.link(pv);
    pv.link(p);
    p.link(p.next);

    //System.out.println("#split v=" + v + "\n#p=" + pv);

    return p;
  }

  /**
   * Add a new region to the list of regions.
   * 
   * @param v
   */
  private void newRegion(CapVertex v) {
    //System.out.println("\n\n#new region for " + id);
    nRegions++;
    lstRegions.addLast(v.region = new CapVertex[] { v, v, v });
  }

  /**
   * Find the lowest ascender or descender above scan line bounding the region
   * for this point. In the case of a region that consists of a single edge with
   * descender above ascender, this will return the ascender. In the case where
   * there are two independent regions, it is possible for the lowest to be
   * missed, but to no import.
   * 
   * [This is MOST confusing in the M3O book.]
   * 
   * @param v
   * 
   * @return pt
   */
  private CapVertex getLastPoint(CapVertex v) {

    //  return a:
    //                    /\
    //                   /  \
    //                  /    \
    //                 d      \
    //                /        a
    //              -/----*-----\-<
    //              /

    CapVertex closest = null;
    float ymin = Float.MAX_VALUE;
    for (int i = lstRegions.size(); --i >= 0;) {
      CapVertex[] r = lstRegions.get(i);
      // check that the descending edge is to the left
      CapVertex d = r[DESCENDER];
      if (d == r[ASCENDER])
        continue;
      boolean isEdge = (d.region != null);
      boolean isOK = ((isEdge ? v.interpolateX(d, d.next) : d.x) < v.x);
      if (isEdge && closest != null && closest.x != d.x
          && isOK == (closest.x < d.x)) {
        // d is an unfinished edge on the left 
        // and closest is further to the left
        // or
        // d is an unfinished edge on the right
        // and closest is further to the right
        closest = null;
        ymin = Float.MAX_VALUE;
      }
      if (!isOK)
        continue;
      // check that the ascending edge is to the right
      CapVertex a = r[ASCENDER];
      isEdge = (a.region != null);
      isOK = ((isEdge ? v.interpolateX(a, a.prev) : a.x) >= v.x);
      if (isEdge && closest != null && closest.x != a.x
          && isOK == (closest.x > a.x)) {
        // a is an unfinished edge on the left 
        // and closest is further to the left
        // or 
        // a is an unfinished edge on the right 
        // and closest is further to the right
        closest = null;
        ymin = Float.MAX_VALUE;
      }
      if (!isOK)
        continue;
      if (r[LAST].y < ymin) {
        ymin = r[LAST].y;
        closest = r[LAST];
      }
    }
    return closest;
  }

  /**
   * Check for CCW winding.
   * 
   * @param v0
   * @param v1
   * @param v2
   * @return true if properly wound -- (v1-v0).cross.(v2-v0).dot.norm > 0
   */
  private boolean checkWinding(CapVertex v0, CapVertex v1, CapVertex v2) {
    return (v1.x - v0.x) * (v2.y - v0.y) > (v1.y - v0.y) * (v2.x - v0.x);
  }

  /**
   * Add the triangle and remove v1 from the chain.
   * 
   * @param v0
   * @param v1
   * @param v2
   * @param note
   */
  private void addTriangle(CapVertex v0, CapVertex v1, CapVertex v2, String note) {
    //System.out.println("#" + test + " " + note);
    ++nTriangles;
    if (checkWinding(v0, v1, v2)) {
      if (dumping)
        drawTriangle(nTriangles, v0, v1, v2, "red");
      outputTriangle(v0.ipt, v1.ipt, v2.ipt);
    } else if (dumping) {
      // probably a 180-degree triangle, which can happen with
      //
      //         0
      //        /|
      //       / 5
      //      /  |
      //     /   4
      //    /    |
      //   1--2--3

      Logger.info("#!!!BAD WINDING " + note);
    }
    v1.link(null);
  }

  /**
   * for debugging
   * 
   * @param index
   * @param v0
   * @param v1
   * @param v2
   * @param color
   */
  private void drawTriangle(int index, CapVertex v0, CapVertex v1,
                            CapVertex v2, String color) {
    T3 p0 = getInputPoint(v0);
    T3 p1 = getInputPoint(v1);
    T3 p2 = getInputPoint(v2);
    Logger.info("draw " + color + index + "/* " + v0.id + " " + v1.id + " "
        + v2.id + " */" + p0 + p1 + p2 + " color " + color);
  }

  /**
   * A class to provide linked vertices for MeshCapper
   * 
   */
  private class CapVertex extends T3 implements Cloneable,
      Comparator<CapVertex> {

    /**
     * external reference
     */
    int ipt;

    /**
     * for debugging
     */
    String id = "";

    /**
     * Y-X scan queue forward link
     */
    protected CapVertex qnext;

    /**
     * edge double links
     */
    CapVertex prev;
    CapVertex next;

    /**
     * dynamic region pointers
     */

    CapVertex[] region;

    /**
     * unique vertex test
     * 
     */
    protected int ok = 1;

    boolean disabled;

    CapVertex(T3 p, int i) {
      ipt = i;
      id = "" + i;
      setT(p);
    }

    public CapVertex cloneV() {
      try {
        return (CapVertex) clone();
      } catch (Exception e) {
        return null;
      }
    }

    /**
     * Generate qnext links based on scanning Y large to Y small and if Y1==Y2,
     * then X small to large
     * 
     * @param vs
     * @return null if there are two identical points (edge crossings)
     */
    public CapVertex sort(CapVertex[] vs) {
      CapVertex v0 = null;
      CapVertex v1 = null;
      int n = vs.length;
      for (int i = n; --i >= 0;) {
        if (vs[i].next == null) {
          if (v0 == null) {
            v1 = vs[i];
          } else {
            vs[i].link(v0);            
            v0 = null;
          }
        } else if (vs[i].prev == null) {
          if (v1 == null) {
            v0 = vs[i];
          } else {
            v1.link(vs[i]);            
            v1 = null;
          }
        }
      }

      ok = 0;
      while (ok == 0) {
        ok = 1;
        Arrays.sort(vs, this);
        System.out.println(ok);
      }
      for (int i = n; --i >= 0;) {
        if (vs[i].x == Float.MAX_VALUE)
          n = i;
        vs[i].qnext = vs[(i + 1) % n];
      }
      vs[n - 1].qnext = vs[0];
      return vs[0];
    }

    @Override
    public int compare(CapVertex v1, CapVertex v2) {
      // first HIGHEST Y to LOWEST Y, then LOWEST X to HIGHEST X
      return (v1.y < v2.y ? 1 : v1.y > v2.y || v1.x < v2.x ? -1
          : v1.x > v2.x ? 1 
              : disable(v1, v2));
    }

    private int disable(CapVertex v1, CapVertex v2) {
      if (v2.x == Float.MAX_VALUE)
        return 0; // both are disabled
      CapVertex v = (v1.x == Float.MAX_VALUE ? v1 : v2);
      v.x = Float.MAX_VALUE;
      v.y = -Float.MAX_VALUE;
      v.link(null);
      ok = 0;
      return (v1.x > v2.x ? 1 : -1);
    }

    /**
     * Get interpolated x for the scan line intersection with an edge. This
     * method is used both in finding the last point for a split and for
     * checking winding on same-side addition.
     * 
     * determine
     * 
     * @param v1
     * @param v2
     * @return x
     */
    protected float interpolateX(CapVertex v1, CapVertex v2) {
      float dy12 = v2.y - v1.y;
      float dx12 = v2.x - v1.x;
      return (dy12 != 0 ? v1.x + (y - v1.y) * dx12 / dy12
          : dx12 > 0 ? Float.MAX_VALUE : -Float.MAX_VALUE);
    }

    /**
     * Link this vertex with v or remove it from the chain.
     * 
     * @param v
     *        null to remove
     */
    protected void link(CapVertex v) {
      if (v == null) {
        prev.next = next;
        next.prev = prev;
        clear();
      } else {
        next = v;
        v.prev = this;
      }
    }

    /**
     * Free all links.
     */
    protected void clear() {
      qnext = next = prev = null;
      region = null;
    }

    /**
     * for debugging
     * 
     * @return listing of vertices currently in a region
     * 
     */
    private String dumpRegion() {
      String s = "\n#REGION d=" + region[MeshCapper.DESCENDER].id + " a="
          + region[MeshCapper.ASCENDER].id + " last="
          + region[MeshCapper.LAST].id + "\n# ";
      CapVertex v = region[MeshCapper.ASCENDER];
      while (true) {
        s += v.id + " ";
        if (v == region[DESCENDER])
          break;
        v = v.next;
      }
      return s + "\n";
    }

    @Override
    public String toString() {
      return "draw p"
          + id
          + " {"
          + x
          + " "
          + y
          + " "
          + z
          + "} # "
          + (prev == null ? "null" : prev.id)
          + (next == null ? " null" : " " + next.id)
              + (region == null ? "" : dumpRegion());
    }

  }

}
