package org.jmol.modelset;

import org.jmol.java.BS;
import org.jmol.util.C;
import javajs.util.P3;
import org.jmol.viewer.JC;

public abstract class Object2d {

  // Echo, Label

  public boolean isLabelOrHover;
  public P3 xyz;
  public String target;
  public String script;
  public short colix;
  public short bgcolix;
  public int pointer;
  public float fontScale;

  public int align;
  public int valign;
  public int atomX, atomY, atomZ = Integer.MAX_VALUE;
  public int movableX, movableY, movableZ; // Echo only
  public int movableXPercent = Integer.MAX_VALUE; // Echo only
  public int movableYPercent = Integer.MAX_VALUE; // Echo only
  public int movableZPercent = Integer.MAX_VALUE; // Echo only

  public int z = 1; // front plane
  public int zSlab = Integer.MIN_VALUE; // z for slabbing purposes -- may be near an atom

  // PyMOL-type offset
  // [mode, screenoffsetx,y,z (applied after tranform), positionOffsetx,y,z (applied before transform)]
  public float[] pymolOffset;

  protected int windowWidth;
  protected int windowHeight;
  public boolean adjustForWindow;
  public float boxWidth;
  public float boxHeight;
  public float boxX;
  public float boxY;

  public int modelIndex = -1;
  public boolean visible = true;
  public boolean hidden = false;

  public float[] boxXY;

  public float scalePixelsPerMicron;

  public void setScalePixelsPerMicron(float scalePixelsPerMicron) {
    fontScale = 0;//fontScale * this.scalePixelsPerMicron / scalePixelsPerMicron;
    this.scalePixelsPerMicron = scalePixelsPerMicron;
  }

  abstract protected void recalc();

  public void setXYZ(P3 xyz, boolean doAdjust) {
    this.xyz = xyz;
    if (xyz == null)
      this.zSlab = Integer.MIN_VALUE;
    if (doAdjust) {
      valign = (xyz == null ? JC.ECHO_XY : JC.ECHO_XYZ);
     adjustForWindow = (xyz == null);
    }
  }

  public void setTranslucent(float level, boolean isBackground) {
    if (isBackground) {
      if (bgcolix != 0)
        bgcolix = C.getColixTranslucent3(bgcolix, !Float.isNaN(level), level);
    } else {
      colix = C.getColixTranslucent3(colix, !Float.isNaN(level), level);
    }
  }

  public void setMovableX(int x) {
    valign = (valign == JC.ECHO_XYZ ? JC.ECHO_XYZ : JC.ECHO_XY);
    movableX = x;
    movableXPercent = Integer.MAX_VALUE;
  }

  public void setMovableY(int y) {
    valign = (valign == JC.ECHO_XYZ ? JC.ECHO_XYZ : JC.ECHO_XY);
    movableY = y;
    movableYPercent = Integer.MAX_VALUE;
  }

  //  public void setMovableZ(int z) {
  //    if (valign != VALIGN_XYZ)
  //      valign = VALIGN_XY;
  //    movableZ = z;
  //    movableZPercent = Integer.MAX_VALUE;
  //  }

  public void setMovableXPercent(int x) {
    valign = (valign == JC.ECHO_XYZ ? JC.ECHO_XYZ : JC.ECHO_XY);
    movableX = Integer.MAX_VALUE;
    movableXPercent = x;
  }

  public void setMovableYPercent(int y) {
    valign = (valign == JC.ECHO_XYZ ? JC.ECHO_XYZ : JC.ECHO_XY);
    movableY = Integer.MAX_VALUE;
    movableYPercent = y;
  }

  public void setMovableZPercent(int z) {
    if (valign != JC.ECHO_XYZ)
      valign = JC.ECHO_XY;
    movableZ = Integer.MAX_VALUE;
    movableZPercent = z;
  }

  public void setZs(int z, int zSlab) {
    this.z = z;
    this.zSlab = zSlab;
  }

  public void setXYZs(int x, int y, int z, int zSlab) {
    setMovableX(x);
    setMovableY(y);
    setZs(z, zSlab);
  }

  public void setScript(String script) {
    this.script = (script == null || script.length() == 0 ? null : script);
  }

  public boolean setAlignmentLCR(String align) {
    if ("left".equals(align))
      return setAlignment(JC.TEXT_ALIGN_LEFT);
    if ("center".equals(align))
      return setAlignment(JC.TEXT_ALIGN_CENTER);
    if ("right".equals(align))
      return setAlignment(JC.TEXT_ALIGN_RIGHT);
    return false;
  }

  public boolean setAlignment(int align) {
    if (this.align != align) {
      this.align = align;
      recalc();
    }
    return true;
  }

  public void setBoxOffsetsInWindow(float margin, float vMargin, float vTop) {
    // not labels

    // these coordinates are (0,0) in top left
    // (user coordinates are (0,0) in bottom left)
    float bw = boxWidth + margin;
    float x = boxX;
    if (x + bw > windowWidth)
      x = windowWidth - bw;
    if (x < margin)
      x = margin;
    boxX = x;

    float bh = boxHeight;
    float y = vTop;
    if (y + bh > windowHeight)
      y = windowHeight - bh;
    if (y < vMargin)
      y = vMargin;
    boxY = y;
  }

  public void setWindow(int width, int height, float scalePixelsPerMicron) {
    windowWidth = width;
    windowHeight = height;
    if (pymolOffset == null && this.scalePixelsPerMicron < 0
        && scalePixelsPerMicron != 0)
      setScalePixelsPerMicron(scalePixelsPerMicron);
  }

  public boolean checkObjectClicked(boolean isAntialiased, int x, int y,
                                    BS bsVisible) {
    if (hidden || script == null || modelIndex >= 0 && !bsVisible.get(modelIndex))
      return false;
    if (isAntialiased) {
      x <<= 1;
      y <<= 1;
    }
    return (x >= boxX && x <= boxX + boxWidth && y >= boxY && y <= boxY
        + boxHeight);
  }

}
