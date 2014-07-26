package crime;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import crime.OSM.Tile;

public class MapSegment {
  
  /** image to use for a tile that is currently being loaded */
  private static BufferedImage  loading  = null;
  
  static BufferedImage getLoadingImage() {
    if (loading == null) {
      loading = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g = loading.createGraphics();
      g.setColor(Color.RED);
      FontMetrics fm = g.getFontMetrics();
      final String displayString = "Loading Crime Data...";
      int w = fm.stringWidth(displayString);
      int h = fm.getAscent();
      g.drawString(displayString, 128 - (w / 2), 128 + (h / 2));
      g.dispose();
    }
    return loading;
  }
  
  int                        age;
  private Set<CrimeBubble>  bubbles      = new HashSet<>();
  BufferedImage              crimeImage  = null;
  private BufferedImage      image        = null;
  
  private Tile              tile        = null;
  
  MapSegment(Tile tile, BufferedImage image) {
    this.tile = tile;
    this.image = image;
  }
  
  final int getAge() {
    return this.age;
  }
  
  final Set<CrimeBubble> getBubbles() {
    return this.bubbles;
  }
  
  final BufferedImage getCrimeImage() {
    if (this.crimeImage == null) {
      return getLoadingImage();
    }
    return this.crimeImage;
  }
  
  final void resetCrimeImage() {
    this.crimeImage = null;
  }
  
  final BufferedImage getOSMImage() {
    return this.image;
  }
  
  final Tile getTile() {
    return this.tile;
  }
  
  final void incrementAge() {
    this.age++;
  }
  
  final void resetAge() {
    this.age = 0;
  }
}
