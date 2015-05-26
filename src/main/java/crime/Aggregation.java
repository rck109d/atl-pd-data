package crime;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Aggregation {
  
  final static DocumentBuilderFactory                     documentBuilderFactory = DocumentBuilderFactory.newInstance();
  private static final PoolingHttpClientConnectionManager phccm;
  private static final HttpClient                         httpClient;
  
  static {
    Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register("http", new TorSocketFactory()).build();
    phccm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    httpClient = HttpClients.custom().setConnectionManager(phccm).build();
  }
  
  public static void main(final String[] args) throws Exception {
    // crimes2kmlByNPU();
    Aggregation.updateThroughYesterday();
  }
  
  static final void updateThroughYesterday() throws Exception {
    LocalDate mostRecentCapture = MongoData.getMostRecentIncidentLocalDate();
    LocalDate yesterday = LocalDate.now().minusDays(1);
    LocalDate iter = mostRecentCapture.plusDays(1);
    int daysAdded = 0;
    while (iter.isBefore(yesterday)) {
      Utilities.println(iter);
      if (!getAllZonesForDay(iter)) {
        break;
      }
      daysAdded++;
      iter = iter.plusDays(1);
    }
    Utilities.println("days added: " + daysAdded);
  }
  
  /**
   * Save incidents for a given day, returns if any data was added by this call.
   */
  static final boolean getAllZonesForDay(final LocalDate localDate) throws Exception {
    JSONObject[] stats = new JSONObject[7];
    
    for (int zoneID = 1; zoneID <= 6; zoneID++) {
      stats[zoneID] = getStats(zoneID + "", localDate);
    }
    
    for (int zoneID = 1; zoneID <= 6; zoneID++) {
      JSONArray incidents = stats[zoneID].getJSONArray("incidents");
      if (incidents.length() == 0) {
        Utilities.println("halting getAllZonesForDay on " + localDate + " because zone " + zoneID + " is empty");
        return false;
      }
    }
    
    for (int zoneID = 1; zoneID <= 6; zoneID++) {
      String xmlString = "<stats>" + XML.toString(stats[zoneID]) + "</stats>";
      MongoData.addIncidentsToCollection(getIncidentsFromXMLString(xmlString));
    }
    
    return true;
  }
  
  static final void getAllZonesDailyForYear(final int year) throws Exception {
    LocalDate localDate = LocalDate.of(year, 1, 1);
    while (localDate.getYear() == year) {
      getAllZonesForDay(localDate);
      localDate = localDate.plusDays(1);
    }
  }
  
  static final String getCrimeDataFilePath(int zoneID, Calendar cal) {
    return "out/incidents/zone" + zoneID + ",year" + cal.get(Calendar.YEAR) + ",day" + cal.get(Calendar.DAY_OF_YEAR) + ".xml";
  }
  
  static final JSONObject getStats(final String zoneID, final LocalDate localDate) throws Exception {
    final String responseString = getQueryResponse(zoneID, "1,2,3,4,5,6,7,8,9", localDate, localDate);
    final JSONObject responseJSON = new JSONObject(responseString);
    final JSONObject stats = new JSONObject(responseJSON.getString("d"));
    return stats;
  }
  
  public static final String getQueryResponse(final String zoneID, final String offenseCodes, final LocalDate startDate, final LocalDate endDate) throws Exception {
    final JSONObject jobj = new JSONObject();
    jobj.put("zoneID", zoneID);
    jobj.put("offenseCodes", offenseCodes);
    DateTimeFormatter slashyMdy = Utilities.slashyMdy();
    jobj.put("startDate", startDate.format(slashyMdy));
    jobj.put("endDate", endDate.format(slashyMdy));
    
    // http://atlantapd.org/Service.aspx/GetMapData
    final HttpPost httppost = new HttpPost("http://65.82.136.65:80/Service.aspx/GetMapData");
    final StringEntity se = new StringEntity(jobj.toString());
    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
    httppost.setEntity(se);
    
    final HttpResponse response = httpClient.execute(httppost);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = EntityUtils.toString(responseEntity);
    return responseString;
  }
  
  static final Set<Incident> getIncidentsForZoneAndDate(int zoneID, Calendar cal) throws Exception {
    String filePath = getCrimeDataFilePath(zoneID, cal);
    Set<Incident> incidents = getIncidentsForFile(filePath);
    return incidents;
  }
  
  static final Set<Incident> getIncidentsForFile(String filePath) throws Exception {
    Set<Incident> incidents = new HashSet<>();
    DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
    Document dom = db.parse(filePath);
    Element docEle = dom.getDocumentElement();
    NodeList nodes = docEle.getElementsByTagName("incidents");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element)nodes.item(i);
      incidents.add(Incident.create(element));
    }
    return incidents;
  }
  
  static final Set<Incident> getIncidentsFromXMLString(final String xmlString) throws Exception {
    Set<Incident> incidents = new HashSet<>();
    DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
    Document dom = db.parse(new ByteArrayInputStream(xmlString.getBytes()));
    Element docEle = dom.getDocumentElement();
    NodeList nodes = docEle.getElementsByTagName("incidents");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element)nodes.item(i);
      incidents.add(Incident.create(element));
    }
    return incidents;
  }
  
  static final void saveIncidentsToKML(Collection<Incident> incidents, String filePath, String documentName) throws Exception {
    File file = new File(filePath);
    try (PrintWriter pr = new PrintWriter(file)) {
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
  
  static void dailyCrimesByCategory() throws Exception {
    Map<String, Map<LocalDate, Vector<Incident>>> catDateMap = new HashMap<>();
    
    LocalDate firstDate = null;
    LocalDate lastDate = null;
    for (Incident incident : MongoData.getAllIncidents()) {
      LocalDate localDate = incident.getReportDateAsLocalDate();
      if (firstDate == null || firstDate.isAfter(localDate)) {
        firstDate = localDate;
      }
      if (lastDate == null || firstDate.isBefore(localDate)) {
        lastDate = localDate;
      }
      String cat = Incident.marker2category.get(incident.marker);
      if (cat == null) {
        throw new RuntimeException("Unknown category marker: '" + incident.marker + "'");
      }
      
      if (!catDateMap.containsKey(cat)) {
        catDateMap.put(cat, new TreeMap<LocalDate, Vector<Incident>>());
      }
      final Map<LocalDate, Vector<Incident>> dateMap = catDateMap.get(cat);
      
      if (!dateMap.containsKey(localDate)) {
        dateMap.put(localDate, new Vector<Incident>());
      }
      final Vector<Incident> catDayIncidents = dateMap.get(localDate);
      
      catDayIncidents.add(incident);
    }
    
    for (String cat : Incident.marker2category.values()) {
      File file = new File("out/crimeDaysCat-" + cat + ".txt");
      try (PrintWriter pr = new PrintWriter(file)) {
        if (catDateMap.containsKey(cat)) {
          Map<LocalDate, Vector<Incident>> datedIncidents = catDateMap.get(cat);
          LocalDate iter = firstDate;
          LocalDate lastCal = lastDate;
          while (!iter.isAfter(lastCal)) {
            pr.print(iter);
            if (datedIncidents.containsKey(iter)) {
              pr.println(datedIncidents.get(iter).size());
            } else {
              pr.println("0");
            }
            iter = iter.plusDays(1);
          }
        }
      }
    }
  }
  
  static void dailyCrimesByCategory2() throws Exception {
    Map<String, Map<LocalDate, Collection<Incident>>> catDateMap = new HashMap<>();
    
    LocalDate firstDate = null;
    LocalDate lastDate = null;
    for (Incident incident : MongoData.getAllIncidents()) {
      LocalDate date = incident.getReportDateAsLocalDate();
      if (firstDate == null || date.isBefore(firstDate)) {
        firstDate = date;
      }
      if (lastDate == null || date.isAfter(lastDate)) {
        lastDate = date;
      }
      String cat = Incident.marker2category.get(incident.marker);
      if (cat == null) {
        throw new RuntimeException("Unknown category marker: '" + incident.marker + "'");
      }
      
      if (!catDateMap.containsKey(cat)) {
        catDateMap.put(cat, new TreeMap<LocalDate, Collection<Incident>>());
      }
      final Map<LocalDate, Collection<Incident>> dateMap = catDateMap.get(cat);
      
      if (!dateMap.containsKey(date)) {
        dateMap.put(date, new LinkedList<Incident>());
      }
      final Collection<Incident> catDayIncidents = dateMap.get(date);
      catDayIncidents.add(incident);
    }
    
    File file = new File("out/crimeDaysByCat.txt");
    try (PrintWriter pr = new PrintWriter(file)) {
      pr.print("\t");
      for (String cat : Incident.marker2category.values()) {
        pr.print(cat + "\t");
      }
      pr.println();
      LocalDate iter = firstDate;
      if(iter == null) {
        return;
      }
      while (!iter.isAfter(lastDate)) {
        pr.print(iter);
        for (String cat : Incident.marker2category.values()) {
          Collection<Incident> incidentsOnDate = catDateMap.get(cat).get(iter);
          int num = incidentsOnDate != null ? incidentsOnDate.size() : 0;
          pr.print("\t" + num);
        }
        pr.println();
        iter = iter.plusDays(1);
      }
    }
  }
  
  static final void touchAllCombinedIncidents() throws Exception {
    final File dir = new File("out");
    final File totalFile = new File(dir, "total.txt");
    Properties props = new Properties();
    try (FileReader fr = new FileReader(totalFile)) {
      props.load(fr);
    }
  }
  
  /**
   * @deprecated replaced by {@link MongoData#searchIncidentsByType(String)}
   */
  @Deprecated
  public static final Collection<Incident> getAllIncidents(Pattern typePattern) throws Exception {
    Collection<Incident> incidents = new LinkedList<>();
    final File dir = new File("out" + File.separator + "incidents");
    final File totalFile = new File(dir, "total.txt");
    try (FileReader fr = new FileReader(totalFile); BufferedReader br = new BufferedReader(fr);) {
      @SuppressWarnings("unused")
      int hit = 0;
      String[] parts = null;
      for (int count = 0; true; count++) {
        if (!br.ready()) {
          break;
        }
        
        final String id;
        final String npu;
        final String beat;
        final String marker;
        final String neighborhood;
        final String number;
        final String longitude;
        final String latitude;
        final String type;
        final String shift;
        final String location;
        final String reportDate;
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("id_" + count)) {
          break;
        }
        id = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("npu_" + count)) {
          break;
        }
        npu = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("beat_" + count)) {
          break;
        }
        beat = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("marker_" + count)) {
          break;
        }
        marker = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("neighborhood_" + count)) {
          break;
        }
        neighborhood = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("number_" + count)) {
          break;
        }
        number = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("longitude_" + count)) {
          break;
        }
        longitude = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("latitude_" + count)) {
          break;
        }
        latitude = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("type_" + count)) {
          break;
        }
        type = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("shift_" + count)) {
          break;
        }
        shift = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("location_" + count)) {
          break;
        }
        location = parts.length > 1 ? parts[1] : "";
        
        parts = br.readLine().split("=");
        if (!parts[0].equals("reportDate_" + count)) {
          break;
        }
        reportDate = parts.length > 1 ? parts[1] : "";
        
        if (typePattern == null || typePattern.matcher(type).matches()) {
          double lat = Double.parseDouble(latitude);
          double lng = Double.parseDouble(longitude);
          final long reportDateTime = Utilities.isoDate().parse(reportDate).getTime();
          incidents.add(new Incident(id, npu, beat, marker, neighborhood, number, lng, lat, type, shift, location, reportDate, reportDateTime));
          hit++;
        }
      }
    }
    return incidents;
  }
  
  static final void crimes2kmlByNPU() throws Exception {
    for (char npu = 'A'; npu <= 'Z'; npu++) {
      if (npu == 'U') {
        npu++;
      }
      String npuString = String.valueOf(npu);
      for (int year = 2004; year <= 2011; year++) {
        String yearString = String.valueOf(year);
        File file = new File("out/KML/npu" + npuString + "_year" + yearString + ".kml");
        int hits = 0;
        try (PrintWriter pr = new PrintWriter(file)) {
          pr.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
          pr.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
          pr.println("  <Document>");
          for (Incident incident : MongoData.getAllIncidents()) {
            if (incident.npu.equalsIgnoreCase(npuString)) {
              if (incident.reportDate.endsWith(yearString)) {
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
                hits++;
              }
            }
          }
          pr.println("  <name>NPU " + npu + ", Year " + year + " (" + hits + ")</name>");
          pr.println("  </Document>");
          pr.println("</kml>");
        }
        if (hits == 0) {
          Utilities.println("omitting " + file.getName());
          file.delete();
        }
      }
    }
  }
}
