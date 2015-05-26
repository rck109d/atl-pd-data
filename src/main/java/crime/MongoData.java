package crime;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

import crime.OSM.BoundingBox;

public class MongoData {
  
  static MongoClient   mongoClient;
  static DB            db;
  static DBCollection  incidentsCollection;
  static GridFS        images;
  
  static {
    try {
      mongoClient = new MongoClient("localhost", 27017);
      db = mongoClient.getDB("crime");
      incidentsCollection = db.getCollection("incidents");
      incidentsCollection.createIndex(new BasicDBObject("reportDate", Boolean.TRUE));
      incidentsCollection.createIndex(new BasicDBObject("longLat", "2d"));
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
      .append("reportDate", incident.reportDate);
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
        dbo.get("reportDate").toString()
      );
    return incident;
  }
  
  public static final Iterable<Incident> getAllIncidentsFromMongo() {
    final Collection<Incident> incidents = new LinkedList<>();
    try(final DBCursor cursor = incidentsCollection.find()) {
      while (cursor.hasNext()) {
        incidents.add(dbo2incident(cursor.next()));
      }
    }
    return incidents;
  }
  
  public static final LocalDate getMostRecentIncidentLocalDate() {
    DBObject query = new BasicDBObject();
    DBObject fields = new BasicDBObject("_id", Boolean.FALSE).append("reportDate", Boolean.TRUE);
    DBObject orderBy = new BasicDBObject("reportDate", Integer.valueOf(-1));
    DBObject one = incidentsCollection.findOne(query, fields, orderBy);
    if(one == null) {
      return null;
    }
    return LocalDate.parse(one.get("reportDate").toString());
  }
  
  public static Collection<Incident> getIncidentsWithinBoxAndTime(final BoundingBox bbox, LocalDate from, LocalDate to) {
    final Collection<Incident> incidents = new LinkedList<>();
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
    ).append("reportDate",
      new BasicDBObject()
        .append("$gte", from.toString())
        .append("$lte", to.toString())
    );
    
    try (final DBCursor cursor = incidentsCollection.find(query)) {
      while (cursor.hasNext()) {
        incidents.add(dbo2incident(cursor.next()));
      }
    }
    return incidents;
  }
  
  public static Collection<Incident> getIncidentsWithinPolyAndTime(final double[][] polyPoints, LocalDate from, LocalDate to) {
    final Collection<Incident> incidents = new LinkedList<>();
    final BasicDBList coordinates = new BasicDBList();
    coordinates.add(polyPoints);
    final BasicDBObject query = new BasicDBObject("longlat",
      new BasicDBObject("$geoWithin",
        new BasicDBObject("$geometry",
          new BasicDBObject().append("type", "Polygon").append("coordinates", coordinates)
        )
      )
    ).append("reportDate",
      new BasicDBObject()
        .append("$gte", from.toString())
        .append("$lte", to.toString())
    );
    
    try(final DBCursor cursor = incidentsCollection.find(query)) {
      while (cursor.hasNext()) {
        incidents.add(dbo2incident(cursor.next()));
      }
    }
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
      try(final InputStream inputStream = files.get(0).getInputStream()){
        image = ImageIO.read(inputStream);
      }
    }
    return image;
  }
  
  public static Set<Incident> searchIncidentsByType(final String type) {
    final Set<Incident> incidents = new HashSet<>();
    final BasicDBObject query = new BasicDBObject("type", new BasicDBObject()
      .append("$regex", type)
      .append("$options", "i")
    );
    try(final DBCursor cursor = incidentsCollection.find(query)) {
      while (cursor.hasNext()) {
        final DBObject dbo = cursor.next();
        incidents.add(dbo2incident(dbo));
      }
    }
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
