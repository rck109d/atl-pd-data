package crime;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

import crime.OSM.BoundingBox;

public class MongoData {
  
  static Mongo        mongo;
  static DB            db;
  static DBCollection  incidentsCollection;
  static GridFS        images;
  
  static {
    try {
      mongo = new Mongo("localhost", 27017);
      db = mongo.getDB("crime");
      incidentsCollection = db.getCollection("incidents");
      images = new GridFS(db, "images");
    } catch (final UnknownHostException e) {
      e.printStackTrace();
    }
  }
  
  public static void addIncidentsToCollection(final Collection<Incident> incidents) {
    for (final Incident incident : incidents) {
      incidentsCollection.insert(incident2dbo(incident));
    }
  }
  
  @SuppressWarnings("boxing")
  public static BasicDBObject incident2dbo(final Incident incident) {
    return new BasicDBObject()
      .append("id", Integer.valueOf(incident.id))
      .append("npu", incident.npu)
      .append("beat", Integer.valueOf(incident.beat))
      .append("marker", incident.marker)
      .append("neighborhood", incident.neighborhood)
      .append("number", incident.number)
      .append("longlat", new Double[] {
        Double.valueOf(incident.longitudeDouble),
        Double.valueOf(incident.latitudeDouble)
      })
      .append("type", incident.type)
      .append("shift", incident.shift)
      .append("location", incident.location)
      .append("reportDate", incident.reportDate)
      .append("reportDateTime", incident.reportDateTime);
  }
  
  public static Incident dbo2incident(final DBObject dbo) {
    final Incident incident =
      new Incident(
        dbo.get("id").toString(),
        dbo.get("npu").toString(),
        dbo.get("beat").toString(),
        dbo.get("marker").toString(),
        dbo.get("neighborhood").toString(),
        dbo.get("number").toString(),
        ((Double)((BasicDBList)dbo.get("longlat")).get(0)).doubleValue(),
        ((Double)((BasicDBList)dbo.get("longlat")).get(1)).doubleValue(),
        dbo.get("type").toString(),
        dbo.get("shift").toString(),
        dbo.get("location").toString(),
        dbo.get("reportDate").toString(),
        ((Long)dbo.get("reportDateTime")).longValue()
      );
    return incident;
  }
  
  public static final Iterable<Incident> getAllIncidentsFromMongo() {
    final Collection<Incident> incidents = new LinkedList<Incident>();
    final DBCursor cursor = incidentsCollection.find();
    while (cursor.hasNext()) {
      final DBObject dbo = cursor.next();
      incidents.add(dbo2incident(dbo));
    }
    cursor.close();
    return incidents;
  }
  
  public static final Iterable<Date> getAllIncidentDates() {
    return new Iterable<Date>() {
      @Override
      public Iterator<Date> iterator() {
        return new Iterator<Date>() {
          final private DBCursor  cursor  = incidentsCollection.find(new BasicDBObject(), new BasicDBObject().append("reportDate", Boolean.valueOf(true)));
          
          @Override
          public boolean hasNext() {
            return this.cursor.hasNext();
          }
          
          @Override
          public Date next() {
            Date date = null;
            try {
              date = Utilities.MM_dd_yyyy().parse(this.cursor.next().get("reportDate").toString());
            } catch (final Exception e) {
              // do nothing
            }
            return date;
          }
          
          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
      
    };
  }
  
  public static Collection<Incident> getIncidentsWithinBox(final BoundingBox bbox) {
    final Collection<Incident> incidents = new LinkedList<Incident>();
    @SuppressWarnings("boxing")
    final BasicDBObject query = new BasicDBObject().append("longlat",
      new BasicDBObject("$within",
        new BasicDBObject("$box",
          new Double[][] {
            new Double[] { bbox.west, bbox.north },
            new Double[] { bbox.east, bbox.south }
          }
        )
      )
    );
    
    final DBCursor cursor = incidentsCollection.find(query);
    while (cursor.hasNext()) {
      incidents.add(dbo2incident(cursor.next()));
    }
    cursor.close();
    return incidents;
  }
  
  public static Collection<Incident> getIncidentsWithinBoxAndTime(final BoundingBox bbox, long from, long to) {
    final Collection<Incident> incidents = new LinkedList<Incident>();
    @SuppressWarnings("boxing")
    final BasicDBObject query = new BasicDBObject("longlat",
      new BasicDBObject("$within",
        new BasicDBObject("$box",
          new Double[][] {
            new Double[] { bbox.west, bbox.north },
            new Double[] { bbox.east, bbox.south }
          }
        )
      )
    ).append("reportDateTime",
      new BasicDBObject()
        .append("$gte", from)
        .append("$lte", to)
    );
    
    final DBCursor cursor = incidentsCollection.find(query);
    while (cursor.hasNext()) {
      incidents.add(dbo2incident(cursor.next()));
    }
    cursor.close();
    return incidents;
  }
  
  public static Collection<Incident> getIncidentsWithinPolyAndTime(final double[][] polyPoints, long from, long to) {
    final Collection<Incident> incidents = new LinkedList<Incident>();
    final BasicDBList coordinates = new BasicDBList();
    coordinates.add(polyPoints);
    @SuppressWarnings("boxing")
    final BasicDBObject query = new BasicDBObject("longlat",
      new BasicDBObject("$geoWithin",
        new BasicDBObject("$geometry",
          new BasicDBObject().append("type", "Polygon").append("coordinates", coordinates)
        )
      )
    ).append("reportDateTime",
      new BasicDBObject()
        .append("$gte", from)
        .append("$lte", to)
    );
    
    final DBCursor cursor = incidentsCollection.find(query);
    while (cursor.hasNext()) {
      incidents.add(dbo2incident(cursor.next()));
    }
    cursor.close();
    return incidents;
  }
  
  /**
   * map the given image name to the given image bytes
   */
  public static void saveImage(final String imageName, final byte[] imageBytes) {
    final GridFSInputFile file = images.createFile(imageBytes);
    file.setFilename(imageName);
    file.setContentType(StringUtils.substringAfterLast(imageName, "."));
    file.save();
  }
  
  /**
   * @param imageName
   *          unique image name
   * @return return an image mapped from the given name, null if one can't be loaded
   */
  public static BufferedImage loadImage(final String imageName) throws IOException {
    BufferedImage image = null;
    final List<GridFSDBFile> files = images.find(imageName);
    if (files != null && files.size() > 0) {
      final Date uploadDate = files.get(0).getUploadDate();
      final long timeDiff = Math.abs(uploadDate.getTime() - System.currentTimeMillis());
      final long limitTimeDiff = 1000L * 60 * 60 * 24 * 30;
      if (timeDiff > limitTimeDiff) {
        final ObjectId id = (ObjectId)files.get(0).getId();
        images.remove(id);
        return null;
      }
      final InputStream inputStream = files.get(0).getInputStream();
      image = ImageIO.read(inputStream);
      inputStream.close();
    }
    return image;
  }
  
  @SuppressWarnings("boxing")
  public static void extractAndSaveIncidentReportDateTime() {
    System.out.println("main()");
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    final DBCursor all = incidentsCollection.find();
    while (all.hasNext()) {
      try {
        final DBObject dbo = all.next();
        final String dateString = dbo.get("reportDate").toString();
        final Date date = sdf.parse(dateString);
        dbo.put("reportDateTime", date.getTime());
        incidentsCollection.update(new BasicDBObject("_id", dbo.get("_id")), dbo);
      } catch (ParseException e) {
        e.printStackTrace();
        continue;
      }
    }
    all.close();
    System.out.println("System.exit(0)");
    System.exit(0);
  }
  
  public static Set<Incident> searchIncidentsByType(final String type) {
    final Set<Incident> incidents = new HashSet<Incident>();
    final BasicDBObject query = new BasicDBObject("type", new BasicDBObject()
      .append("$regex", type)
      .append("$options", "i")
    );
    final DBCursor cursor = incidentsCollection.find(query);
    while (cursor.hasNext()) {
      final DBObject dbo = cursor.next();
      incidents.add(dbo2incident(dbo));
    }
    cursor.close();
    return incidents;
  }
  
  public static Iterable<Incident> getAllIncidents() {
    return new Iterable<Incident>() {
      @Override
      public Iterator<Incident> iterator() {
        return new Iterator<Incident>() {
          final private DBCursor  cursor  = incidentsCollection.find();
          @Override
          public boolean hasNext() {
            return this.cursor.hasNext();
          }
          @Override
          public Incident next() {
            final DBObject dbo = this.cursor.next();
            return dbo2incident(dbo);
          }
          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }
}
