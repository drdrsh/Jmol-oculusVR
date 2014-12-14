/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-05-18 15:41:42 -0500 (Fri, 18 May 2007) $
 * $Revision: 7752 $

 *
 * Copyright (C) 2003-2005  The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.jmol.export;


import javajs.awt.Font;
import javajs.util.Lst;
import java.util.Hashtable;

import java.util.Map;


import org.jmol.java.BS;
import org.jmol.util.GData;
import javajs.util.P3;

import javajs.util.A4;
import javajs.util.PT;
import javajs.util.Quat;
import javajs.util.T3;
import org.jmol.viewer.Viewer;

public class _VrmlExporter extends __CartesianExporter {

  /*
   * 1/2009 Angel Herraez: # added support for translucency # Jmol info in
   * header # set navigation mode # added support for background color # added
   * support for labels: text, font face and font style; size is hardcoded to
   * 0.4A
   */

  public _VrmlExporter() {
    useTable = new UseTable("USE ");
    commentChar = "# ";
  }
  
  @Override
  protected void output(T3 pt) {
    output(round(scalePt(pt)));
  }
  
  protected UseTable useTable;
  
  @Override
  protected void outputHeader() {
    output("#VRML V2.0 utf8 Generated by Jmol " + Viewer.getJmolVersion()
        + "\n");
    output("WorldInfo { \n");
    output(" title " + PT.esc(vwr.ms.modelSetName) + "\n");
    output(" info [ \"Generated by Jmol " + Viewer.getJmolVersion() + " \", \n");
    output("  \"http://www.jmol.org \", \n");
    output("  \"Creation date: " + getExportDate() + " \" ]\n");
    output("} \n");

    output("NavigationInfo { type \"EXAMINE\" } \n");
    // puts the vwr into model-rotation mode
    output("Background { skyColor [" + rgbFractionalFromColix(backgroundColix)
        + "] } \n");
    // next is an approximation only
    float angle = getViewpoint();
    output("Viewpoint{fieldOfView " + angle);
    output(" position ");
    output(cameraPosition);
    output(" orientation ");
    output(tempP1);
    output(" " + -viewpoint.angle);
    output("\n jump TRUE description \"v1\"\n}\n\n");
    output(getJmolPerspective());
    output("\nTransform{children Transform{translation ");
    tempP1.setT(center);
    tempP1.scale(-1);
    output(tempP1);
    output("\nchildren [\n");
  }

  protected float getViewpoint() {
    vwr.tm.getAxisAngle(viewpoint);
    tempP1.set(viewpoint.x, viewpoint.y, (viewpoint.angle == 0 ? 1
        : viewpoint.z));
    return (float) (aperatureAngle * Math.PI / 180);
  }  
  
  @Override
  protected void outputFooter() {
    useTable = null;
    output("\n]\n");
    output("}}\n");
  }

  protected void outputAppearance(short colix, boolean isText) {
    String def = useTable.getDef((isText ? "T" : "") + colix);
    output(" appearance ");
    if (def.charAt(0) == '_') {
      String color = rgbFractionalFromColix(colix);
      output(" DEF " + def + " Appearance{material Material{diffuseColor ");
      if (isText)
        output(" 0 0 0 specularColor 0 0 0 ambientIntensity 0.0 shininess 0.0 emissiveColor " 
            + color + " }}");
      else
        output(color + " transparency " + translucencyFractionalFromColix(colix) + "}}");
      return;
    }
    output(def);
  }
  
  @Override
  protected void outputCircle(P3 pt1, P3 pt2, float radius, short colix,
                            boolean doFill) {
    if (doFill) {
      // draw filled circle

      output("Transform{translation ");
      tempV1.ave(pt1, pt2);
      output(tempV1);
      output(" children Billboard{axisOfRotation 0 0 0 children Transform{rotation 1 0 0 1.5708");
      outputCylinderChildScaled(pt1, pt2, colix, GData.ENDCAPS_FLAT,
          radius * 2000);
      output("}}}\n");
      return;
    }

    // draw a thin torus

    String child = useTable.getDef("C" + colix + "_" + radius);
    outputTransRot(pt1, pt2, 0, 0, 1);
    tempP3.set(1, 1, 1);
    tempP3.scale(radius);
    output(" scale ");
    output(tempP3);
    output(" children ");
    if (child.charAt(0) == '_') {
      output("DEF " + child);
      output(" Billboard{axisOfRotation 0 0 0 children Transform{children");
      output(" Shape{geometry Extrusion{beginCap FALSE convex FALSE endCap FALSE creaseAngle 1.57");
      output(" crossSection [");
      float rpd = 3.1415926f / 180;
      float scale = 0.02f / radius;
      for (int i = 0; i <= 360; i += 10) {
        output(round(Math.cos(i * rpd) * scale) + " ");
        output(round(Math.sin(i * rpd) * scale) + " ");
      }
      output("] spine [");
      for (int i = 0; i <= 360; i += 10) {
        output(round(Math.cos(i * rpd)) + " ");
        output(round(Math.sin(i * rpd)) + " 0 ");
      }
      output("]}");
      outputAppearance(colix, false);
      output("}}}");
    } else {
      output(child);
    }
    output("}\n");
  }

  @Override
  protected void outputCone(P3 ptBase, P3 ptTip, float radius,
                            short colix) {
    radius = scale(radius);
    float height = scale(ptBase.distance(ptTip));
    outputTransRot(ptBase, ptTip, 0, 1, 0);
    output(" children ");
    String cone = "o" + (int) (height * 100) + "_" + (int) (radius * 100);
    String child = useTable.getDef("c" + cone + "_" + colix);
    if (child.charAt(0) == '_') {
      output("DEF " + child + " Shape{geometry ");
      cone = useTable.getDef(cone);
      if (cone.charAt(0) == '_') {
        output("DEF " + cone + " Cone{height " + round(height)
            + " bottomRadius " + round(radius) + "}");
      } else {
        output(cone);
      }
      outputAppearance(colix, false);
      output("}");
    } else {
      output(child);  
    }
    output("}\n");
  }

  @Override
  protected boolean outputCylinder(P3 ptCenter, P3 pt1, P3 pt2,
                                   short colix, byte endcaps, float radius,
                                   P3 ptX, P3 ptY, boolean checkRadius) {
    if (ptX == null) {
      outputTransRot(pt1, pt2, 0, 1, 0);
    } else {
      output("Transform{translation ");
      output(ptCenter);
      outputQuaternionFrame(ptCenter, ptY, pt1, ptX, 2, " ", "");
      pt1.set(0, 0, -1);
      pt2.set(0, 0, 1);
    }
    outputCylinderChildScaled(pt1, pt2, colix, endcaps, radius);
    output("}\n");
    if (endcaps == GData.ENDCAPS_SPHERICAL) {
      outputSphere(pt1, radius * 1.01f, colix, checkRadius);
      outputSphere(pt2, radius * 1.01f, colix, checkRadius);
    }
    return true;
  }

  protected void outputCylinderChildScaled(P3 pt1, P3 pt2, short colix,
                                   byte endcaps, float radius) {
    output(" children ");    
    float length = scale(pt1.distance(pt2));
    radius = scale(radius);
    String child = useTable.getDef("C" + colix + "_" + (int) (length * 100) + "_" + radius
        + "_" + endcaps);
    if (child.charAt(0) == '_') {
      output("DEF " + child);
      output(" Shape{geometry ");
      String cyl = useTable.getDef("c" + round(length) + "_" + endcaps + "_" + radius);
      if (cyl.charAt(0) == '_') {
        output("DEF " + cyl + " Cylinder{height " 
            + round(length) + " radius " + radius 
            + (endcaps == GData.ENDCAPS_FLAT ? "" : " top FALSE bottom FALSE") + "}");
      } else {
        output(cyl);
      }
      outputAppearance(colix, false);
      output("}");
    } else {
      output(child);
    }
  }

  @Override
  protected void outputEllipsoid(P3 ptCenter, P3[] points, short colix) {
    output("Transform{translation ");
    output(ptCenter);

    // Hey, hey -- quaternions to the rescue!
    // Just send three points to Quaternion to define a plane and return
    // the AxisAngle required to rotate to that position. That's all there is to
    // it.

    outputQuaternionFrame(ptCenter, points[1], points[3], points[5], 1, " ", "");
    output(" children ");
    tempP3.set(0, 0, 0);
    outputSphereChildUnscaled(tempP3, 1.0f, colix);
    output("}\n");
  }

  private P3 tempQ1 = new P3();
  private P3 tempQ2 = new P3();

  protected void outputQuaternionFrame(P3 ptCenter, P3 ptX,
                                       P3 ptY, P3 ptZ, float yScale,
                                       String pre, String post) {

    //Hey, hey -- quaternions to the rescue!
    // Just send three points to Quaternion to define a plane and return
    // the AxisAngle required to rotate to that position. That's all there is to it.

    tempQ1.setT(ptX);
    tempQ2.setT(ptY);
    A4 a = Quat.getQuaternionFrame(ptCenter, tempQ1, tempQ2)
        .toAxisAngle4f();
    if (!Float.isNaN(a.x)) {
      output(" rotation");
      output(pre);
      output(a.x + " " + a.y + " " + a.z + " " + a.angle);
      output(post);
    }
    float sx = scale(ptX.distance(ptCenter));
    float sy = scale(ptY.distance(ptCenter) * yScale);
    float sz = scale(ptZ.distance(ptCenter));
    output(" scale");
    output(pre);
    output(sx + " " + sy + " " + sz);
    output(post);
  }


  @Override
  protected void outputSurface(T3[] vertices, T3[] normals,
                               short[] colixes, int[][] indices,
                               short[] polygonColixes,
                               int nVertices, int nPolygons, int nFaces, BS bsPolygons,
                               int faceVertexMax, short colix,
                               Lst<Short> colorList, Map<Short, Integer> htColixes, P3 offset) {
    output("Shape {\n");
    outputAppearance(colix, false);
    output(" geometry IndexedFaceSet {\n");

    if (polygonColixes != null)
      output(" colorPerVertex FALSE\n");

    // coordinates

    output("coord Coordinate {\n   point [\n");
    outputVertices(vertices, nVertices, offset);
    output("   ]\n");
    output("  }\n");
    output("  coordIndex [\n");
    int[] map = new int[nVertices];
    getCoordinateMap(vertices, map, null);
    outputIndices(indices, map, nPolygons, bsPolygons, faceVertexMax);
    output("  ]\n");

    // normals

    if (normals != null) {
      Lst<String> vNormals = new  Lst<String>();
      map = getNormalMap(normals, nVertices, null, vNormals);
      output("  solid FALSE\n  normalPerVertex TRUE\n   normal Normal {\n  vector [\n");
      outputNormals(vNormals);
      output("   ]\n");
      output("  }\n");
      output("  normalIndex [\n");
      outputIndices(indices, map, nPolygons, bsPolygons, faceVertexMax);
      output("  ]\n");
    }

    map = null;
    
    // colors

    if (colorList != null) {
      output("  color Color { color [\n");
      outputColors(colorList);
      output("  ] } \n");
      output("  colorIndex [\n");
      outputColorIndices(indices, nPolygons, bsPolygons, faceVertexMax, htColixes, colixes, polygonColixes);
      output("  ]\n");
    }

    output(" }\n");
    output("}\n");
  }

  @Override
  protected void outputFace(int[] face, int[] map, int faceVertexMax) {
    output(map[face[0]] + " " + map[face[1]] + " " + map[face[2]] + " -1\n");
    if (faceVertexMax == 4 && face.length == 4)
      output(map[face[0]] + " " + map[face[2]] + " " + map[face[3]] + " -1\n");
  }

  protected void outputNormals(Lst<String> vNormals) {
    int n = vNormals.size();
    for (int i = 0; i < n; i++) {
      output(vNormals.get(i));
    }
  }

  protected void outputColors(Lst<Short> colorList) {
    int nColors = colorList.size();
    for (int i = 0; i < nColors; i++) {
      String color = rgbFractionalFromColix(colorList.get(i).shortValue());
      output(" ");
      output(color);
      output("\n");
    }
  }

  protected void outputColorIndices(int[][] indices, int nPolygons, BS bsPolygons,
                                  int faceVertexMax, Map<Short, Integer> htColixes,
                                  short[] colixes, short[] polygonColixes) {
    boolean isAll = (bsPolygons == null);
    int i0 = (isAll ? nPolygons - 1 : bsPolygons.nextSetBit(0));
    for (int i = i0; i >= 0; i = (isAll ? i - 1 : bsPolygons.nextSetBit(i + 1))) {
      if (polygonColixes == null) {
        output(htColixes.get(Short.valueOf(colixes[indices[i][0]])) + " "
            + htColixes.get(Short.valueOf(colixes[indices[i][1]])) + " "
            + htColixes.get(Short.valueOf(colixes[indices[i][2]])) + " -1\n");
        if (faceVertexMax == 4 && indices[i].length == 4)
          output(htColixes.get(Short.valueOf(colixes[indices[i][0]])) + " "
              + htColixes.get(Short.valueOf(colixes[indices[i][2]])) + " "
              + htColixes.get(Short.valueOf(colixes[indices[i][3]])) + " -1\n");
      } else {
        output(htColixes.get(Short.valueOf(polygonColixes[i])) + "\n");
      }
    }
  }

  private Map<String, Boolean> htSpheresRendered = new Hashtable<String, Boolean>();

  @Override
  protected void outputSphere(P3 ptCenter, float radius, short colix, boolean checkRadius) {
    radius = scale(radius);
    String check = round(scalePt(ptCenter)) + (checkRadius ? " " + (int) (radius * 100) : "");
    if (htSpheresRendered.get(check) != null)
      return;
    htSpheresRendered.put(check, Boolean.TRUE);
    outputSphereChildUnscaled(ptCenter, radius, colix);
  }

  protected void outputSphereChildUnscaled(P3 ptCenter, float radius, short colix) {
    int iRad = (int) (radius * 100);
    String child = useTable.getDef("S" + colix + "_" + iRad);
    output("Transform{translation ");
    output(ptCenter);
    output(" children ");
    if (child.charAt(0) == '_') {
      output("DEF " + child);
      output(" Shape{geometry Sphere{radius " + radius + "}");
      outputAppearance(colix, false);
      output("}");
    } else {
      output(child);
    }
    output("}\n");
  }

  @Override
  protected void outputTextPixel(P3 pt, int argb) {
    String color = rgbFractionalFromArgb(argb);
    output("Transform{translation ");
    output(pt);
    output(" children ");
    String child = useTable.getDef("p" + argb);
    if (child.charAt(0) == '_') {
      output("DEF " + child + " Shape{geometry Sphere{radius 0.01}");
      output(" appearance Appearance{material Material{diffuseColor 0 0 0 specularColor 0 0 0 ambientIntensity 0.0 shininess 0.0 emissiveColor "
          + color + " }}}");
    } else {
      output(child);
    }
    output("}\n");
  }

  protected void outputTransRot(P3 pt1, P3 pt2, int x, int y, int z) {    
    output("Transform{");
    outputTransRot(pt1, pt2, x, y, z, " ", "");
  }
  
  protected void outputTransRot(P3 pt1, P3 pt2, int x, int y, int z,
                                String pre, String post) {
    tempV1.ave(pt2, pt1);
    output("translation");
    output(pre);
    output(tempV1);
    output(post);
    tempV1.sub(pt1);
    tempV1.normalize();
    tempV2.set(x, y, z);
    tempV2.add(tempV1);
    output(" rotation");
    output(pre);
    output(tempV2);
    output(" ");
    output(round((float) Math.PI));
    output(post);
  }

  @Override
  protected void outputTriangle(P3 pt1, P3 pt2, P3 pt3, short colix) {
    // nucleic base
    // cartoons
    output("Shape{geometry IndexedFaceSet{solid FALSE coord Coordinate{point[");
    output(pt1);
    output(" ");
    output(pt2);
    output(" ");
    output(pt3);
    output("]}coordIndex[ 0 1 2 -1 ]}");
    outputAppearance(colix, false);
    output("}\n");
  }

  @Override
  void plotText(int x, int y, int z, short colix, String text, Font font3d) {
    if (z < 3)
      z = (int) tm.cameraDistance;
    String useFontStyle = font3d.fontStyle.toUpperCase();
    String preFontFace = font3d.fontFace.toUpperCase();
    String useFontFace = (preFontFace.equals("MONOSPACED") ? "TYPEWRITER"
        : preFontFace.equals("SERIF") ? "SERIF" : "SANS");
    output("Transform{translation ");
    tempP3.set(x, y, z);
    tm.unTransformPoint(tempP3, tempP1);
    output(tempP1);
    // These x y z are 3D coordinates of echo or the atom the label is attached
    // to.
    output(" children ");
    String child = useTable.getDef("T" + colix + useFontFace + useFontStyle + "_" + text);
    if (child.charAt(0) == '_') {
      output("DEF " + child + " Billboard{axisOfRotation 0 0 0 children Transform{children Shape{");
      outputAppearance(colix, true);
      output(" geometry Text{fontStyle ");
      String fontstyle = useTable.getDef("F" + useFontFace + useFontStyle);
      if (fontstyle.charAt(0) == '_') {
        output("DEF " + fontstyle + " FontStyle{size 0.4 family \"" + useFontFace
            + "\" style \"" + useFontStyle + "\"}");      
      } else {
        output(fontstyle);
      }
      output(" string " + PT.esc(text) + "}}}}");
    } else {
      output(child);
    }
    output("}\n");
  }

  /*
   * Unsolved issues: # Non-label texts: echos, measurements :: need to get
   * space coordinates, not screen coord. # Font size: not implemented; 0.4A
   * is hardcoded (resizes with zoom) Java VRML font3d.fontSize = 13.0 size
   * (numeric), but in angstroms, not pixels font3d.fontSizeNominal = 13.0 #
   * Label offsets: not implemented; hardcoded to 0.25A in each x,y,z #
   * Multi-line labels: only the first line is received # Sub/superscripts not
   * interpreted
   */

}


