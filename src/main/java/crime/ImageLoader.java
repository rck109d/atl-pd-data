package crime;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.util.EntityUtils;

import crime.OSM.BoundingBox;
import crime.OSM.Tile;

public class ImageLoader {
  
  static Map<MultiKey, MapSegment>  segments                = Collections.synchronizedMap(new HashMap<MultiKey, MapSegment>());
  static Set<MultiKey>              segmentsToLoadFromDisk  = Collections.synchronizedSet(new HashSet<MultiKey>());
  static Set<MultiKey>              segmentsToLoadFromNet    = Collections.synchronizedSet(new HashSet<MultiKey>());
  private static final DefaultHttpClient    httpClient              = new DefaultHttpClient(new ThreadSafeClientConnManager());
  private static final int                  segmentExpiryAge        = 50;
  private static final ThreadFactory        mptf                    = new ThreadFactory() {
                                                                      @Override
                                                                      public Thread newThread(final Runnable r) {
                                                                        final Thread t = new Thread(r);
                                                                        t.setPriority(Thread.MIN_PRIORITY);
                                                                        return t;
                                                                      }
                                                                    };
  
  static {
    initNetLoaderThread();
    initDiskLoaderThread();
    initCrimeLayerThread();
  }
  
  public static MapSegment loadSegment(final MultiKey imageKey, final boolean immediate) {
    MapSegment segment = segments.get(imageKey);
    if (segment != null) {
      return segment;
    } else if (immediate) {
      final Tile tile = new Tile((Integer)imageKey.getKey(0), (Integer)imageKey.getKey(1), (Integer)imageKey.getKey(2));
      BufferedImage image = loadImageFromDisk(tile);
      if (image == null) {
        try {
          image = ImageIO.read(new ByteArrayInputStream(loadImageBytesFromNet(tile)));
        } catch (final IOException e) {
          e.printStackTrace();
          return null;
        }
      }
      segment = new MapSegment(tile, image);
      ImageLoader.segments.put(imageKey, segment);
      return segment;
    }
    segmentsToLoadFromDisk.add(imageKey);
    return null;
  }
  
  public static void incrementSegmentsAges() {
    synchronized (ImageLoader.segments) {
      for (final MapSegment value : ImageLoader.segments.values()) {
        value.incrementAge();
      }
    }
  }
  
  private static void initDiskLoaderThread() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (true) {
          try {
            doDiskLoaderThreadWork();
            Thread.sleep(100);
          } catch (final Exception e) {
            // do nothing
          }
        }
      }
    }, "Disk Loader") {
      {
        setPriority(Thread.MIN_PRIORITY);
      }
    }.start();
  }
  
  private static void initNetLoaderThread() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (true) {
          doNetLoaderThreadWork();
          try {
            Thread.sleep(100);
          } catch (final InterruptedException e) {
            // do nothing
          }
        }
      }
    }, "Net Loader") {
      {
        setPriority(Thread.MIN_PRIORITY);
      }
    }.start();
  }
  
  static void doDiskLoaderThreadWork() {
    final Set<MultiKey> toLoad;
    synchronized (segmentsToLoadFromDisk) {
      toLoad = new HashSet<MultiKey>(segmentsToLoadFromDisk);
    }
    if (!toLoad.isEmpty()) {
      final ExecutorService threadPool = Executors.newFixedThreadPool(2, mptf);
      for (final MultiKey key : toLoad) {
        threadPool.execute(new Runnable() {
          @Override
          public void run() {
            final Tile tile = new Tile(((Integer)key.getKey(0)).intValue(), ((Integer)key.getKey(1)).intValue(), ((Integer)key.getKey(2)).intValue());
            BufferedImage image = loadImageFromDisk(tile);
            if (image != null) {
              segments.put(key, new MapSegment(tile, image));
              segmentsToLoadFromDisk.remove(key);
            } else {
              segmentsToLoadFromNet.add(key);
            }
          }
        });
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
  }
  
  static void doNetLoaderThreadWork() {
    final Set<MultiKey> toLoad;
    synchronized (segmentsToLoadFromNet) {
      toLoad = new HashSet<MultiKey>(segmentsToLoadFromNet);
    }
    if (!toLoad.isEmpty()) {
      final ExecutorService threadPool = Executors.newFixedThreadPool(2, mptf);
      for (final MultiKey key : toLoad) {
        final Tile tile = new Tile(((Integer)key.getKey(0)).intValue(), ((Integer)key.getKey(1)).intValue(), ((Integer)key.getKey(2)).intValue());
        threadPool.execute(new Runnable() {
          @Override
          public void run() {
            try {
              byte[] bytes = loadImageBytesFromNet(tile);
              final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
              if (image != null) {
                MongoData.saveImage(tile.getImgMongoName(), bytes);
              }
            } catch (final IOException e) {
              e.printStackTrace();
            }
            segmentsToLoadFromNet.remove(key);
          }
        });
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
  }
  
  static BufferedImage loadImageFromDisk(final Tile tile) {
    BufferedImage image = null;
    try {
      image = MongoData.loadImage(tile.getImgMongoName());
      if (image != null) {
        final BufferedImage newImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null).filter(image, newImage);
        image = newImage;
      }
    } catch (final IOException e) {
      e.printStackTrace();
    }
    return image;
  }
  
  static byte[] loadImageBytesFromNet(final Tile tile) throws IOException {
    final String URL = tile.getURL();
    final HttpGet httppost = new HttpGet(URL);
    final HttpResponse response = httpClient.execute(httppost);
    final HttpEntity responseEntity = response.getEntity();
    final byte[] imgBytes = EntityUtils.toByteArray(responseEntity);
    return imgBytes;
  }
  
  static void doCrimeLayerThreadWork() {
    final long fromTime = Explorer.dateFrom.getTime();
    final long toTime = Explorer.dateTo.getTime();
    final Collection<MapSegment> segs;
    synchronized (ImageLoader.segments) {
      segs = new HashSet<MapSegment>(ImageLoader.segments.values());
    }
    final Color redSolid = new Color(1f, 0f, 0f);
    final float radius = 5;
    final float radiusHalf = radius / 2f;
    final ExecutorService threadPool = Executors.newFixedThreadPool(2, mptf);
    for (final MapSegment seg : segs) {
      if (seg.crimeImage == null) {
        threadPool.execute(new Runnable() {
          @Override
          public void run() {
            final BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = img.createGraphics();
            g.setColor(redSolid);
            final boolean doDetail = seg.getTile().z > 16;
            final BoundingBox bbox = seg.getTile().boundingBox;
            
            for (final Incident i : MongoData.getIncidentsWithinBoxAndTime(seg.getTile().boundingBox, fromTime, toTime)) {
              final double latitude = i.getLatitude();
              final double longitude = i.getLongitude();
              final double percX = ((longitude - bbox.west) / (bbox.east - bbox.west));
              final double percY = 1 - ((latitude - bbox.south) / (bbox.north - bbox.south));
              if (doDetail) {
                seg.getBubbles().add(new CrimeBubble(i));
              } else {
                g.fill(new Ellipse2D.Double(256 * percX - radiusHalf, 256 * percY - radiusHalf, radius, radius));
              }
            }
            seg.crimeImage = img;
            g.dispose();
          }
        });
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
  
  private static void initCrimeLayerThread() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (true) {
          doCrimeLayerThreadWork();
          try {
            Thread.sleep(100);
          } catch (final InterruptedException e) {
            // do nothing
          }
        }
      }
    }, "Crime Layer") {
      {
        setPriority(Thread.MIN_PRIORITY);
      }
    }.start();
  }
  
  public static void expireSegments() {
    final List<MultiKey> toRemove = new LinkedList<MultiKey>();
    synchronized (ImageLoader.segments) {
      for (final Entry<MultiKey, MapSegment> entry : segments.entrySet()) {
        if (entry.getValue().getAge() > segmentExpiryAge) {
          toRemove.add(entry.getKey());
        }
      }
    }
    for (final MultiKey key : toRemove) {
      ImageLoader.segments.remove(key);
    }
  }
}
