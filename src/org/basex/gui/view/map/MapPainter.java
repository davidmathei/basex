package org.basex.gui.view.map;

import java.awt.Color;
import java.awt.Graphics;
import org.basex.data.Nodes;
import org.basex.gui.GUIConstants;

/**
 * Provides an interface for data specific TreeMap visualizations.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
abstract class MapPainter {
  /** Graphics reference. */
  MapView view;

  /**
   * Constructor.
   * @param m map reference.
   */
  MapPainter(final MapView m) {
    view = m;
  }

  /**
   * Returns next color mark.
   * @param rects rectangle array
   * @param ri current position
   * @return next color mark
   */
  final Color color(final MapRects rects, final int ri) {
    // find marked node
    final Nodes marked = view.gui.context.marked();
    final int p = -marked.find(rects.get(ri).pre) - 1;
    if(p < 0) return GUIConstants.colormark1;

    // mark ancestor of invisible node;
    final int i = rects.find(rects.get(ri));
    return p < marked.size() && i + 1 < rects.size && marked.sorted[p] <
      rects.sorted[i + 1].pre ? GUIConstants.colormark2 : null;
  }

  /**
   * Paints node contents.
   * @param g graphics reference
   * @param r rectangle array
   * @param scale scale boarders using this factor
   */
  abstract void drawRectangles(final Graphics g, final MapRects r, 
      final float scale);

  /**
   * Reacts on a mouse over/mouse click on the focused area.
   * @param click mouse click (false: mouse move)
   * @param rect current rectangle
   * @param mx mouse x
   * @param my mouse y
   * @return true if area is mouse sensitive
   */
  @SuppressWarnings("unused")
  boolean mouse(final MapRect rect, final int mx, final int my,
      final boolean click) {
    return false;
  }

  /**
   * Initializes the painter.
   * @param rects rectangle array
   */
  @SuppressWarnings("unused")
  void init(final MapRects rects) { }

  /**
   * Resets the painter.
   */
  void reset() { }

  /**
   * Closes the painter.
   */
  void close() { }
}
