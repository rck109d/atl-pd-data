package crime;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
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
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import crime.OSM.BoundingBox;
import crime.OSM.Tile;

public class ImageLoader {
  
  static Map<MultiKey<Integer>, MapSegment>         segments               = Collections.synchronizedMap(new HashMap<MultiKey<Integer>, MapSegment>());
  static Set<MultiKey<Integer>>                     segmentsToLoadFromDisk = Collections.synchronizedSet(new HashSet<MultiKey<Integer>>());
  static Collection<MultiKey<Integer>>              segmentsToLoadFromNet  = Collections.synchronizedSet(new HashSet<MultiKey<Integer>>());
  private static PoolingHttpClientConnectionManager phccm                  = new PoolingHttpClientConnectionManager();
  private static final HttpClient                   httpClient             = HttpClients.custom().setConnectionManager(phccm).build();
  private static final int                          segmentExpiryAge       = 50;
  private static final ThreadFactory                mptf                   = new ThreadFactory() {
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
  
  public static MapSegment loadSegment(final MultiKey<Integer> imageKey, final boolean immediate) {
    MapSegment segment = segments.get(imageKey);
    if (segment != null) {
      return segment;
    } else if (immediate) {
      @SuppressWarnings("boxing")
      final Tile tile = new Tile(imageKey.getKey(0), imageKey.getKey(1), imageKey.getKey(2));
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
    final Set<MultiKey<Integer>> toLoad;
    synchronized (segmentsToLoadFromDisk) {
      toLoad = new HashSet<>(segmentsToLoadFromDisk);
    }
    if (!toLoad.isEmpty()) {
      final ExecutorService threadPool = Executors.newFixedThreadPool(2, mptf);
      for (final MultiKey<Integer> key : toLoad) {
        threadPool.execute(new Runnable() {
          @Override
          public void run() {
            final Tile tile = new Tile(key.getKey(0).intValue(), key.getKey(1).intValue(), key.getKey(2).intValue());
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
    final Set<MultiKey<Integer>> toLoad;
    synchronized (segmentsToLoadFromNet) {
      toLoad = new HashSet<>(segmentsToLoadFromNet);
    }
    if (!toLoad.isEmpty()) {
      final ExecutorService threadPool = Executors.newFixedThreadPool(2, mptf);
      for (final MultiKey<Integer> key : toLoad) {
        final Tile tile = new Tile(key.getKey(0).intValue(), key.getKey(1).intValue(), key.getKey(2).intValue());
        threadPool.execute(new Runnable() {
          @Override
          public void run() {
            try {
              byte[] bytes = loadImageBytesFromNet(tile);
              final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
              if (image != null) {
                MongoData.saveImage(tile.getImgMongoName(), bytes);
              } else {
                throw new RuntimeException("cannot load tile " + tile);
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
  
  public static void main(String[] args) throws IOException {
    // test to load a tile
    @SuppressWarnings("boxing")
    MultiKey<Integer> key = new MultiKey<>(34807, 52454, 17);
    final Tile tile = new Tile(key.getKey(0).intValue(), key.getKey(1).intValue(), key.getKey(2).intValue());
    byte[] bytes = loadImageBytesFromNet(tile);
    String fileName = key.getKey(0) + "-" + key.getKey(1) + "-" + key.getKey(2) + ".png";
    File imgFile = new File(fileName);
    FileUtils.writeByteArrayToFile(imgFile, bytes);
    System.out.println(imgFile);
    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    if (image != null) {
      MongoData.saveImage(tile.getImgMongoName(), bytes);
    }
    System.exit(0);
  }
  
  static BufferedImage loadImageFromDisk(final Tile tile) {
    BufferedImage image = null;
    try {
      image = MongoData.loadImage(tile.getImgMongoName());
      if (image != null) {
        final BufferedImage newImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        new ColorConvertOp(gray, null).filter(image, newImage);
        image = newImage;
      }
    } catch (final IOException e) {
      e.printStackTrace();
    }
    return image;
  }
  
  static byte[] loadImageBytesFromNet(final Tile tile) throws IOException {
    final String URL = tile.getURL();
    //System.out.println(URL);
    final HttpGet get = new HttpGet(URL);
    get.addHeader("User-Agent", "atl-pd-data");
    final HttpResponse response = httpClient.execute(get);
    final HttpEntity responseEntity = response.getEntity();
    final byte[] imgBytes = EntityUtils.toByteArray(responseEntity);
    return imgBytes;
  }
  
  static void doCrimeLayerThreadWork() {
    final LocalDate localDateFrom = Explorer.localDateFrom;
    final LocalDate localDateTo = Explorer.localDateTo;
    final Collection<MapSegment> segs;
    synchronized (ImageLoader.segments) {
      segs = new HashSet<>(ImageLoader.segments.values());
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
            
            for (final Incident i : MongoData.getIncidentsWithinBoxAndTime(seg.getTile().boundingBox, localDateFrom, localDateTo)) {
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
    final List<MultiKey<Integer>> toRemove = new LinkedList<>();
    synchronized (ImageLoader.segments) {
      for (final Entry<MultiKey<Integer>, MapSegment> entry : segments.entrySet()) {
        if (entry.getValue().getAge() > segmentExpiryAge) {
          toRemove.add(entry.getKey());
        }
      }
    }
    for (final MultiKey<Integer> key : toRemove) {
      ImageLoader.segments.remove(key);
    }
  }
}
