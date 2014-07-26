package crime;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class OSM {
  
  public static int  ZOOM_LEVEL  = 15; // 15, 12
  
  public static class BoundingBox {
    final double  north;
    final double  south;
    final double  east;
    final double  west;
    
    public BoundingBox(final double n, final double s, final double e, final double w) {
      this.north = n;
      this.south = s;
      this.east = e;
      this.west = w;
    }
    
    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, new ToStringStyle() {
        private static final long  serialVersionUID  = 1L;
        
        {
          setUseIdentityHashCode(false);
        }
      });
    }
    
    public final int getPixelsX() {
      final Tile tileNW = Tile.create(this.north, this.west, ZOOM_LEVEL);
      final double drawOffsetX = 256 * (this.west - tileNW.boundingBox.west) / (tileNW.boundingBox.east - tileNW.boundingBox.west);
      // final double drawOffsetY = 256 * (this.north - tileNW.boundingBox.north) / (tileNW.boundingBox.south -
      // tileNW.boundingBox.north);
      
      Tile tileSE = Tile.create(this.south, this.east, ZOOM_LEVEL);
      double drawOffsetX2 = 256 - 256 * (this.east - tileSE.boundingBox.west) / (tileSE.boundingBox.east - tileSE.boundingBox.west);
      // double drawOffsetY2 = 256 - 256 * (this.south - tileSE.boundingBox.north) / (tileSE.boundingBox.south -
      // tileSE.boundingBox.north);
      
      final int IMG_WIDTH = 256 * (tileSE.x - tileNW.x + 1) - (int)(drawOffsetX + drawOffsetX2);
      // final int IMG_HEIGHT = 256 * (tileSE.y - tileNW.y + 1) - (int) (drawOffsetY + drawOffsetY2);
      
      return IMG_WIDTH;
    }
    
    public final int getPixelsY() {
      final Tile tileNW = Tile.create(this.north, this.west, ZOOM_LEVEL);
      // final double drawOffsetX = 256 * (this.west - tileNW.boundingBox.west) / (tileNW.boundingBox.east -
      // tileNW.boundingBox.west);
      final double drawOffsetY = 256 * (this.north - tileNW.boundingBox.north) / (tileNW.boundingBox.south - tileNW.boundingBox.north);
      
      Tile tileSE = Tile.create(this.south, this.east, ZOOM_LEVEL);
      // double drawOffsetX2 = 256 - 256 * (this.east - tileSE.boundingBox.west) / (tileSE.boundingBox.east -
      // tileSE.boundingBox.west);
      double drawOffsetY2 = 256 - 256 * (this.south - tileSE.boundingBox.north) / (tileSE.boundingBox.south - tileSE.boundingBox.north);
      
      // final int IMG_WIDTH = 256 * (tileSE.x - tileNW.x + 1) - (int) (drawOffsetX + drawOffsetX2);
      final int IMG_HEIGHT = 256 * (tileSE.y - tileNW.y + 1) - (int)(drawOffsetY + drawOffsetY2);
      
      return IMG_HEIGHT;
    }
    
    public final boolean contains(double lat, double lon) {
      return !(lon < this.west || lon > this.east || lat > this.north || lat < this.south);
    }
  }
  
  static class Tile {
    public final int          x;
    public final int          y;
    public final int          z;            // zoom
    public final BoundingBox  boundingBox;
    
    public static Tile create(final double lat, final double lon, final int zoom) {
      int x = (int)Math.floor((lon + 180) / 360 * (1 << zoom));
      int y = (int)Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
      return new Tile(x, y, zoom);
    }
    
    public Tile(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.boundingBox = new BoundingBox(tile2lat(y, z), tile2lat(y + 1, z), tile2lon(x + 1, z), tile2lon(x, z));
    }
    
    private static double tile2lon(int x, int z) {
      return x / Math.pow(2.0, z) * 360.0 - 180;
    }
    
    private static double tile2lat(int y, int z) {
      double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
      return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
    
    public final String getURL() {
      return "http://tile.openstreetmap.org/" + this.z + "/" + this.x + "/" + this.y + ".png";
    }
    
    public final String getImgMongoName() {
      return "map-" + this.z + "-" + this.y + "-" + this.x + ".png";
    }
    
    static final DefaultHttpClient  httpClient  = new DefaultHttpClient();
    
    final BoundingBox getBoundingBox() {
      return this.boundingBox;
    }
    
    public final Runnable saveImage() {
      Runnable command = new Runnable() {
        @Override
        public void run() {
          try {
            String URL = getURL();
            final HttpGet httppost = new HttpGet(URL);
            final HttpResponse response;
            final HttpEntity responseEntity;
            final byte[] imgBytes;
            synchronized (httpClient) {
              response = httpClient.execute(httppost);
              responseEntity = response.getEntity();
              imgBytes = EntityUtils.toByteArray(responseEntity);
              // must read all bytes from the response before using the single connection for another request
            }
            MongoData.saveImage(Tile.this.getImgMongoName(), imgBytes);
          } catch (final Exception e) {
            // System.out.println("saving from net failed " + e);
          }
        }
      };
      
      return command;
    }
  }
  
  public static void main(String[] args) {
    // downloadData(33.886421, 33.648188, -84.290135, -84.550486);
    stitchData(33.886421, 33.648188, -84.290135, -84.550486);
  }
  
  @SuppressWarnings("boxing")
  public static final void queryCutoff(final double lat, final double lon) {
    final Tile tile = Tile.create(lat, lon, ZOOM_LEVEL);
    double xperc = (lon - tile.boundingBox.west) / (tile.boundingBox.east - tile.boundingBox.west);
    double yperc = (lat - tile.boundingBox.north) / (tile.boundingBox.south - tile.boundingBox.north);
    println(xperc * 256);
    println(yperc * 256);
  }
  
  public static final void downloadData(double north, double south, double east, double west) {
    int zoom = ZOOM_LEVEL;
    double lat = north;
    double lon = west;
    Tile firstTile = Tile.create(lat, lon, zoom);
    
    ExecutorService threadPool = Executors.newFixedThreadPool(16);
    
    int offsetY = 0;
    outerWhile: while (true) {
      int offsetX = 0;
      while (true) {
        Tile tile = new Tile(firstTile.x + offsetX, firstTile.y + offsetY, firstTile.z);
        offsetX++;
        if (tile.boundingBox.west > east) {
          offsetX = -1;
          offsetY++;
          if (tile.boundingBox.south < south) {
            break outerWhile;
          }
          break;
        }
        println(tile.boundingBox);
        threadPool.execute(tile.saveImage());
      }
    }
    
    threadPool.shutdown();
    while (!threadPool.isTerminated()) {
      try {
        threadPool.awaitTermination(60, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        // do nothing
      }
    }
  }
  
  public static final BufferedImage stitchData(BoundingBox bbox) {
    return stitchData(bbox.north, bbox.south, bbox.east, bbox.west);
  }
  
  public static final BufferedImage stitchData(double north, double south, double east, double west) {
    
    final Tile tileNW = Tile.create(north, west, ZOOM_LEVEL);
    final double drawOffsetX = 256 * (west - tileNW.boundingBox.west) / (tileNW.boundingBox.east - tileNW.boundingBox.west);
    final double drawOffsetY = 256 * (north - tileNW.boundingBox.north) / (tileNW.boundingBox.south - tileNW.boundingBox.north);
    
    Tile tileSE = Tile.create(south, east, ZOOM_LEVEL);
    double drawOffsetX2 = 256 - 256 * (east - tileSE.boundingBox.west) / (tileSE.boundingBox.east - tileSE.boundingBox.west);
    double drawOffsetY2 = 256 - 256 * (south - tileSE.boundingBox.north) / (tileSE.boundingBox.south - tileSE.boundingBox.north);
    
    final long startTime = System.currentTimeMillis();
    
    final int IMG_WIDTH = 256 * (tileSE.x - tileNW.x + 1) - (int)(drawOffsetX + drawOffsetX2);
    final int IMG_HEIGHT = 256 * (tileSE.y - tileNW.y + 1) - (int)(drawOffsetY + drawOffsetY2);
    
    BufferedImage bigImg = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR);
    final Graphics2D g = bigImg.createGraphics();
    
    println("Streets");
    
    for (int y = tileNW.y; y <= tileSE.y; y++) {
      for (int x = tileNW.x; x <= tileSE.x; x++) {
        final int finalX = x;
        final int finalY = y;
        
        BufferedImage lilImg = ImageLoader.loadSegment(new MultiKey(Integer.valueOf(finalX), Integer.valueOf(finalY), Integer.valueOf(ZOOM_LEVEL)), true).getOSMImage();
        g.drawImage(lilImg, null, (int)-drawOffsetX + (finalX - tileNW.x) * 256, (int)-drawOffsetY + (finalY - tileNW.y) * 256);
      }
    }
    
    // println("saving " + name);
    // ImageIO.write(bigImg, "png", new File("out" + File.separator + "map" + File.separator + "streets-" + name + "-" +
    // ZOOM_LEVEL + ".png"));
    // println("done");
    final long endTime = System.currentTimeMillis();
    println((endTime - startTime) / 1000 + " seconds");
    
    return bigImg;
  }
  
  public final static void println(final Object o) {
    System.out.println(o.toString());
  }
  
}
