package crime;

import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import javax.imageio.ImageIO;

import crime.Explorer;
import crime.OSM.BoundingBox;

public final class Test {
  
  private static final float  sqrt3div2  = (float)Math.sqrt(3f) / 2;
  
  final static class PlotSettings {
    public final BoundingBox  bbox;
    public final boolean      calculateGrey;
    public final boolean      calculateColor;
    public final int          maxPixValue;
    public final long          refTime;
    public final long          timeSpread;
    
    public PlotSettings(BoundingBox bbox, boolean calculateGrey, boolean calculateColor, int maxPixValue, long refTime, long timeSpread) {
      super();
      this.bbox = bbox;
      this.calculateGrey = calculateGrey;
      this.calculateColor = calculateColor;
      this.maxPixValue = maxPixValue;
      this.refTime = refTime;
      this.timeSpread = timeSpread;
    }
  }
  
  final static class PlotResults {
    public int  maxPixValue;
    
    public PlotResults() {
    }
  }
  
  public static final void main(final String[] args) throws Exception {
    long start = System.currentTimeMillis();
    
    for (int i = 0; i < 1; i++) {
      Explorer.launchExplorer();
    }
    
    // println(MongoData.searchIncidentsByType("autotheft").size());
    // DrawHeatMaps(MongoData.searchIncidentsByType("autotheft"),, "autotheft");
    // dailyCrimesByCategory2(30);
    // DrawHeatMap(MongoData.searchIncidentsByType("autotheft"), "autotheft");
    // filterCrimes2KML("homicide");
    // getAllZonesDailyForYear(2010);
    
    long end = System.currentTimeMillis();
    Utilities.println((end - start) / 1000 + " seconds");
  }
  
  @SuppressWarnings("boxing")
  static void dailyCrimesByCategory(final int movingAverageRadius, Iterable<Incident> incidents) throws Exception {
    final Map<String, Map<Date, Collection<Incident>>> catDateMap = new HashMap<>();
    
    Date firstDate = null;
    Date lastDate = null;
    for (final Incident incident : incidents) {
      final Date date = incident.getReportDateAsDate();
      if (firstDate == null || date.before(firstDate)) {
        firstDate = date;
      }
      if (lastDate == null || date.after(lastDate)) {
        lastDate = date;
      }
      final String cat = Incident.marker2category.get(incident.marker);
      if (cat == null) {
        throw new RuntimeException("Unknown category marker: '" + incident.marker + "'");
      }
      if (!catDateMap.containsKey(cat)) {
        catDateMap.put(cat, new TreeMap<Date, Collection<Incident>>());
      }
      final Map<Date, Collection<Incident>> dateMap = catDateMap.get(cat);
      if (!dateMap.containsKey(date)) {
        dateMap.put(date, new LinkedList<Incident>());
      }
      final Collection<Incident> catDayIncidents = dateMap.get(date);
      catDayIncidents.add(incident);
    }
    
    final Map<String, Map<Date, Double>> cateDateMA = new HashMap<>();
    for (final Map.Entry<String, Map<Date, Collection<Incident>>> catDateEntry : catDateMap.entrySet()) {
      final Map<Date, Double> movingAverageForCat = new HashMap<>();
      for (final Date date1 : dateIterator(firstDate, lastDate)) {
        double sum = 0;
        for (final Date date2 : dateIterator(firstDate, lastDate)) {
          double percentOff = Math.abs(1.0d * date1.getTime() - date2.getTime()) / (1000L * 60 * 60 * 24 * movingAverageRadius);
          final Collection<Incident> entry = catDateEntry.getValue().get(date2);
          final long size = entry == null ? 0 : entry.size();
          sum += size * (0.5 / Math.PI) * Math.exp(-Math.pow(percentOff, 2) / 2);
        }
        movingAverageForCat.put(date1, sum);
      }
      cateDateMA.put(catDateEntry.getKey(), movingAverageForCat);
    }
    
    final String outFileName = "out/crimeDaysByCat " + System.currentTimeMillis() + ".txt";
    final File file = new File(outFileName);
    try(final PrintWriter pr = new PrintWriter(file)){
      pr.print("\t");
      for (String cat : Incident.marker2category.values()) {
        pr.print(cat + "\t");
      }
      pr.println();
      
      final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      final Calendar iter = Calendar.getInstance();
      iter.setTime(firstDate);
      final Calendar lastCal = Calendar.getInstance();
      lastCal.setTime(lastDate);
      while (!iter.after(lastCal)) {
        final Date time = iter.getTime();
        pr.print(sdf.format(time));
        for (final String cat : Incident.marker2category.values()) {
          final Double val = cateDateMA.get(cat) == null ? 0 : cateDateMA.get(cat).get(time);
          double num = val == null ? 0 : val;
          pr.print("\t" + num);
        }
        pr.println();
        iter.add(Calendar.DAY_OF_YEAR, 1);
      }
      System.out.println("saved to " + file.getCanonicalPath());
    }
  }
  
  static final Iterable<Date> dateIterator(final Date from, final Date to) {
    return new Iterable<Date>() {
      @Override
      public Iterator<Date> iterator() {
        return new Iterator<Date>() {
          final Calendar curr = Calendar.getInstance();
          {
            this.curr.setTime(from);
          }
          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
          @Override
          public Date next() {
            if(this.curr.getTime().after(to)) {
              throw new java.lang.IndexOutOfBoundsException();
            }
            final Date toReturn = this.curr.getTime();
            this.curr.add(Calendar.DAY_OF_WEEK, 1);
            return toReturn;
          }
          @Override
          public boolean hasNext() {
            return !this.curr.getTime().after(to);
          }
        };
      }
    };
  }
  
  static final void filterCrimes2KML(String type) throws Exception {
    Collection<Incident> incidents = MongoData.searchIncidentsByType(type);
    saveIncidentsToKML(incidents, "out/crimesFilter-" + type + ".kml", "Incidents " + type + " (" + incidents.size() + ")");
  }
  
  static final void saveIncidentsToKML(Collection<Incident> incidents, String filePath, String documentName) throws Exception {
    File file = new File(filePath);
    try(PrintWriter pr = new PrintWriter(file)) {
      pr.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      pr.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
      pr.println("  <Document>");
      for (Incident incident : incidents) {
        pr.println("    <Placemark>");
        pr.println("      <name>" + incident.type + "</name>");
        pr.println("      <description>");
        pr.println("        <![CDATA[");
        pr.println("          <ul>");
        pr.println("            <li>date:" + incident.reportDate + "</li>");
        pr.println("            <li>ID:" + incident.id + "</li>");
        pr.println("          </ul>");
        pr.println("        ]]>");
        pr.println("      </description>");
        pr.println("      <Point>");
        pr.println("        <coordinates>" + incident.getLongitude() + "," + incident.getLatitude() + "," + "0" + "</coordinates>");
        pr.println("      </Point>");
        pr.println("    </Placemark>");
      }
      pr.println("  <name>" + documentName + "</name>");
      pr.println("  </Document>");
      pr.println("</kml>");
    }
  }
  
  static final BoundingBox boundingBoxOfIncidents(Iterable<Incident> incidents) {
    double minX, minY, maxX, maxY;
    minX = minY = Double.MAX_VALUE;
    maxX = maxY = -Double.MAX_VALUE;
    for (final Incident incident : incidents) {
      final double longitude = incident.getLongitude();
      final double latitude = incident.getLatitude();
      minX = Math.min(minX, longitude);
      maxX = Math.max(maxX, longitude);
      minY = Math.min(minY, latitude);
      maxY = Math.max(maxY, latitude);
    }
    return new BoundingBox(maxY, minY, maxX, minX);
  }
  
  static final int getMaxPixVal(final File greyImgFile) throws IOException {
    final BufferedImage grey = ImageIO.read(greyImgFile);
    final int IMG_WIDTH = grey.getWidth();
    final int IMG_HEIGHT = grey.getHeight();
    final ShortProcessor sp = new ShortProcessor(grey);
    int maxPixVal = 0;
    for (int x = 0; x < IMG_WIDTH; x++) {
      for (int y = 0; y < IMG_HEIGHT; y++) {
        maxPixVal = Math.max(maxPixVal, sp.get(x, y));
      }
    }
    return maxPixVal;
  }
  
  @SuppressWarnings("boxing")
  static final PlotResults plotIncidents(final BufferedImage backgroundImg, final Iterable<Incident> incidents, final String name, final PlotSettings settings) throws IOException {
    PlotResults results = new PlotResults();
    
    BoundingBox bbox = settings.bbox;
    
    final int IMG_WIDTH = bbox.getPixelsX();
    final int IMG_HEIGHT = bbox.getPixelsY();
    
    BufferedImage grey = null;
    File greyFile = new File("out/heatmap/" + name + "-grey.png");
    if (settings.calculateGrey) {
      // println("gray");
      double xSpread = bbox.east - bbox.west;
      double ySpread = bbox.north - bbox.south;
      
      grey = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_USHORT_GRAY);
      {
        Graphics2D g = grey.createGraphics();
        g.clearRect(0, 0, IMG_WIDTH, IMG_HEIGHT);
        
        g.setColor(new Color(1f, 1f, 1f, 0.004f)); // 0.004f should be ~1 bit of 255 alpha bits
        for (Incident incident : incidents) {
          double longitude = incident.getLongitude();
          double latitude = incident.getLatitude();
          
          double x = IMG_WIDTH * (longitude - bbox.west) / xSpread;
          double y = IMG_HEIGHT - IMG_HEIGHT * (latitude - bbox.south) / ySpread;
          
          final double radius;
          if (settings.refTime != 0 && settings.timeSpread != 0) {
            final long thisSpread = incident.getReportDateAsDate().getTime() - settings.refTime;
            radius = (25d * thisSpread / settings.timeSpread);
          } else {
            radius = 25;
          }
          g.fill(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
        }
      }
      
      if (!settings.calculateColor) {
        ImageIO.write(grey, "png", greyFile);
      }
      
      ShortProcessor sp = new ShortProcessor(grey);
      int maxPixVal = 0;
      for (int x = 0; x < IMG_WIDTH; x++) {
        for (int y = 0; y < IMG_HEIGHT; y++) {
          maxPixVal = Math.max(maxPixVal, sp.get(x, y));
        }
      }
      results.maxPixValue = maxPixVal;
    } else {
      grey = ImageIO.read(greyFile);
    }
    
    if (settings.calculateColor) {
      // println("color");
      BufferedImage color = null;
      File colorFile = new File("out/heatmap/" + name + ".png");
      ShortProcessor sp = new ShortProcessor(grey);
      color = new BufferedImage(IMG_WIDTH, IMG_HEIGHT + 12, BufferedImage.TYPE_4BYTE_ABGR);
      {
        Graphics2D g = color.createGraphics();
        g.clearRect(0, 0, color.getWidth(), color.getHeight());
      }
      WritableRaster bgRaster = backgroundImg.getRaster();
      WritableRaster colorRaster = color.getRaster();
      {
        int maxPixVal = settings.maxPixValue;
        if (maxPixVal == 0) {
          for (int x = 0; x < IMG_WIDTH; x++) {
            for (int y = 0; y < IMG_HEIGHT; y++) {
              maxPixVal = Math.max(maxPixVal, sp.get(x, y));
            }
          }
        }
        // println("maxPixVal: " + maxPixVal);
        
        for (int x = 0; x < IMG_WIDTH; x++) {
          for (int y = 0; y < IMG_HEIGHT; y++) {
            int pixvalShort = sp.get(x, y);
            byte[] bgByte = new byte[4];
            bgRaster.getDataElements(x, y, bgByte);
            
            int[] backgroundPixel = new int[4];
            for (int i = 0; i < 4; i++) {
              backgroundPixel[i] = bgByte[i] & 0xFF;
            }
            int pixvalByte = 255 * pixvalShort / maxPixVal;
            
            int[] data; // {R,G,B,A} : (0-255)
            if (pixvalByte <= 85) {
              if (pixvalByte == 0) {
                data = new int[] { 0, 0, 0, 0 }; // transparent black
              } else {
                data = new int[] { 0, 0, 255, pixvalByte * 3 }; // translucent blue
              }
            } else if (pixvalByte <= 170) {
              int med = (pixvalByte - 85) * 3;
              data = new int[] { 0, med, 255 - med, 255 }; // blue-green
            } else {
              int med = (pixvalByte - 170) * 3;
              data = new int[] { med, 1 - med, 0, 255 }; // red-blue
            }
            
            float blendBG = 1f - (data[3] / 510f); // between 100% and 50%
            float blendHeat = 1 - blendBG;
            
            float chroma, luma;
            Float hue = null;
            {
              luma = (backgroundPixel[0] * 0.30f + backgroundPixel[1] * 0.59f + backgroundPixel[2] * 0.11f) / 255f;
              
              float r = backgroundPixel[0] / 255f;
              float g = backgroundPixel[1] / 255f;
              float b = backgroundPixel[2] / 255f;
              
              float alpha = (2 * r - g - b) / 2f;
              float beta = sqrt3div2 * (g - b);
              
              if (r == g && g == b) {
                // hue = null;
              } else {
                hue = (float)Math.atan2(beta, alpha);
                if (hue < 0) {
                  hue = (float)(hue + 2 * Math.PI);
                }
              }
              
              chroma = (float)Math.sqrt(alpha * alpha + beta * beta);
              
              // circle->hex
              float max = Math.max(r, Math.max(g, b));
              float min = Math.min(r, Math.min(g, b));
              chroma = max - min;
              Float hPrime = null;
              if (chroma == 0) {
                hue = null;
              } else if (max == r) {
                hPrime = ((g - b) / chroma);
                if (hPrime < 0) {
                  hPrime += 6;
                }
              } else if (max == g) {
                hPrime = (b - r) / chroma + 2;
              } else if (max == b) {
                hPrime = (r - g) / chroma + 4;
              }
              if (hPrime != null) {
                hue = 60 * hPrime;
                if (hue < 0) {
                  hue += 360;
                }
              }
            }
            
            float newColor[];
            {
              chroma = chroma / 3;
              
              Float hPrime = ((hue == null) ? null : (hue / 60));
              Float v = hPrime == null ? null : chroma * (1 - Math.abs((hPrime % 2) - 1));
              if (hPrime == null) {
                newColor = new float[] { 0, 0, 0 };
              } else if (hPrime < 1) {
                newColor = new float[] { chroma, v, 0 };
              } else if (hPrime < 2) {
                newColor = new float[] { v, chroma, 0 };
              } else if (hPrime < 3) {
                newColor = new float[] { 0, chroma, v };
              } else if (hPrime < 4) {
                newColor = new float[] { 0, v, chroma };
              } else if (hPrime < 5) {
                newColor = new float[] { v, 0, chroma };
              } else {
                newColor = new float[] { chroma, 0, v };
              }
              
              float m = luma - (0.30f * newColor[0] + 0.59f * newColor[1] + 0.11f * newColor[2]);
              
              newColor[0] += m;
              newColor[1] += m;
              newColor[2] += m;
              
              newColor[0] *= 255;
              newColor[1] *= 255;
              newColor[2] *= 255;
            }
            
            data[0] = (int)((data[0] & 0xFF) * blendHeat + newColor[0] * blendBG);
            data[1] = (int)((data[1] & 0xFF) * blendHeat + newColor[1] * blendBG);
            data[2] = (int)((data[2] & 0xFF) * blendHeat + newColor[2] * blendBG);
            data[3] = 255;
            
            colorRaster.setPixel(x, y, data); // WritableRaster.setPixel uses {R,G,B,A} : (0-255)
          }
        }
      }
      {
        Graphics2D g = color.createGraphics();
        final int w = color.getWidth();
        final int h = color.getHeight();
        
        final long refTime = settings.refTime;
        final long timeSpread = settings.timeSpread;
        if (refTime > 0 && timeSpread > 0) {
          Date refDate = new Date(refTime);
          final Calendar earlyCal = Calendar.getInstance();
          earlyCal.setTime(refDate);
          earlyCal.set(Calendar.DAY_OF_MONTH, 1);
          earlyCal.add(Calendar.MONTH, -1);
          final Calendar rightSideCal = Calendar.getInstance();
          rightSideCal.setTime(refDate);
          rightSideCal.add(Calendar.HOUR, (int)(timeSpread / 1000 / 60 / 60));
          
          Calendar iter = Calendar.getInstance();
          iter.setTime(earlyCal.getTime());
          g.setColor(Color.white);
          while (!iter.after(rightSideCal)) {
            final String iterStr = Utilities.MM_dd_yyyy().format(iter.getTime());
            final float strPix = (w - 1) - (w - 1) * (1f * rightSideCal.getTimeInMillis() - iter.getTimeInMillis()) / timeSpread;
            g.drawString(iterStr, strPix, h - 1);
            iter.add(Calendar.MONTH, 1);
          }
        }
      }
      sp = null;
      ImageIO.write(color, "png", colorFile);
    }
    
    return results;
  }
  
  static final int stringedInt(Date d) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    int stringed = Integer.valueOf(sdf.format(d)).intValue();
    return stringed;
  }
  
  static final Calendar getCalendar(Date d) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(d);
    return cal;
  }
  
  public static final void DrawHeatMaps(Collection<Incident> incidents, final String name) throws Exception {
    BoundingBox bbox = boundingBoxOfIncidents(incidents);
    
    BufferedImage streetsImg = OSM.stitchData(bbox);
    
    int minStringedDate = Integer.MAX_VALUE;
    int maxStringedDate = -Integer.MAX_VALUE;
    
    for (Incident i : incidents) {
      int stringedInt = stringedInt(i.getReportDateAsDate());
      minStringedDate = Math.min(minStringedDate, stringedInt);
      maxStringedDate = Math.max(maxStringedDate, stringedInt);
    }
    
    Calendar fromCal = Calendar.getInstance();
    Calendar toCal = Calendar.getInstance();
    fromCal.setTime(Utilities.yyyyMMdd().parse(minStringedDate + ""));
    
    // toCal.setTime(sdf2.parse(minStringedDate+""));
    // toCal.add(Calendar.DAY_OF_YEAR, +90);
    toCal.setTime(Utilities.yyyyMMdd().parse(maxStringedDate + ""));
    toCal.add(Calendar.DAY_OF_YEAR, -30);
    
    SimpleDateFormat toyyyyMMdd = new SimpleDateFormat("yyyyMMdd");
    
    List<Date> days = new LinkedList<>();
    for (Calendar day = getCalendar(fromCal.getTime()); day.before(toCal); day.add(Calendar.DAY_OF_YEAR, 1)) {
      days.add(day.getTime());
    }
    
    // run one, greys
    final long timeSpread = 1000L * 60 * 60 * 24 * 30 * 3;
    int maxPixVal = 0;
    int loopLimit = 100;
    int count = 0;
    for (Date day : days) {
      count++;
      if (count > loopLimit) {
        // break;
      }
      // println("on " + day);
      String imageName = "heatmap-" + name + "-" + toyyyyMMdd.format(day) + "-" + OSM.ZOOM_LEVEL;
      
      final long dayTime = day.getTime();
      Collection<Incident> incidentsGroup = new LinkedList<>();
      for (Incident i : incidents) {
        long iMinusDay = i.getReportDateAsDate().getTime() - dayTime;
        if (iMinusDay >= 0 && iMinusDay <= timeSpread) {
          incidentsGroup.add(i);
        }
      }
      
      try {
        PlotResults results = plotIncidents(streetsImg, incidentsGroup, imageName, new PlotSettings(bbox, true, false, 0, dayTime, timeSpread));
        maxPixVal = Math.max(maxPixVal, results.maxPixValue);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    // run two, exposure-matched colors
    count = 0;
    for (Date day : days) {
      count++;
      if (count > loopLimit) {
        // break;
      }
      // println("on " + day);
      String imageName = "heatmap-" + name + "-" + toyyyyMMdd.format(day) + "-" + OSM.ZOOM_LEVEL;
      
      final long dayTime = day.getTime();
      Collection<Incident> incidentsGroup = new LinkedList<>();
      for (Incident i : incidents) {
        long iMinusDay = i.getReportDateAsDate().getTime() - dayTime;
        if (iMinusDay >= 0 && iMinusDay <= timeSpread) {
          incidentsGroup.add(i);
        }
      }
      
      try {
        plotIncidents(streetsImg, incidentsGroup, imageName, new PlotSettings(bbox, false, true, maxPixVal, dayTime, timeSpread));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  static final void DrawHeatMap(Iterable<Incident> incidentsForBoundingBox, Iterable<Incident> incidentsToPlot, final String name) throws Exception {
    BoundingBox bbox = boundingBoxOfIncidents(incidentsForBoundingBox);
    String imageName = "heatmap-" + name + "-" + OSM.ZOOM_LEVEL;
    try {
      BufferedImage streetsImg = OSM.stitchData(bbox);
      plotIncidents(streetsImg, incidentsToPlot, imageName, new PlotSettings(bbox, true, true, 0, 0, 0));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  @Deprecated
  static final Vector<Incident> getAllIncidentsFromXMLFiles() throws Exception {
    Vector<Incident> incidents = new Vector<>();
    
    final File dir = new File("out");
    final File totalFile = new File(dir, "total.txt");
    Properties props = new Properties();
    try (FileReader fr = new FileReader(totalFile)) {
      props.load(fr);
    }
    
    int x = 0;
    while (true) {
      if (!props.containsKey("id_" + x)) {
        break;
      }
      
      incidents.add(
        new Incident(
          props.getProperty("id_" + x),
          props.getProperty("npu_" + x),
          props.getProperty("beat_" + x),
          props.getProperty("marker_" + x),
          props.getProperty("neighborhood_" + x),
          props.getProperty("number_" + x),
          Double.parseDouble(props.getProperty("longitude_" + x)),
          Double.parseDouble(props.getProperty("latitude_" + x)),
          props.getProperty("type_" + x),
          props.getProperty("shift_" + x),
          props.getProperty("location_" + x),
          props.getProperty("reportDate_" + x),
          Utilities.MM_dd_yyyy().parse(props.getProperty("reportDate_" + x)).getTime()
        )
      );
      
      x++;
    }
    
    return incidents;
  }
  
  @SuppressWarnings("unused")
  private final static String readFileAsString(final String filePath) throws java.io.IOException {
    final StringBuffer fileData = new StringBuffer(1000);
    try (final BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      char[] buf = new char[1024];
      int numRead = 0;
      while ((numRead = reader.read(buf)) != -1) {
        final String readData = String.valueOf(buf, 0, numRead);
        fileData.append(readData);
        buf = new char[1024];
      }
    }
    return fileData.toString();
  }
}
