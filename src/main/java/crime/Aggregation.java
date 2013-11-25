package crime;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
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
  
  final static DocumentBuilderFactory  documentBuilderFactory  = DocumentBuilderFactory.newInstance();
  
  public static void main(final String[] args) throws Exception {
    // crimes2kmlByNPU();
    Aggregation.updateThroughYesterday();
  }
  
  static final void updateThroughYesterday() throws Exception {
    Date mostRecentCapture = getMostRecentCapturedDate();
    Calendar yesterday = Calendar.getInstance();
    yesterday.add(Calendar.DAY_OF_YEAR, -1);
    
    Calendar loop = Calendar.getInstance();
    loop.setTime(mostRecentCapture);
    loop.add(Calendar.DAY_OF_YEAR, 1);
    int daysAdded = 0;
    while (loop.before(yesterday)) {
      Utilities.println(loop.getTime());
      if (!getAllZonesForDay(loop)) {
        break;
      }
      daysAdded++;
      loop.add(Calendar.DAY_OF_YEAR, 1);
    }
    Utilities.println("days added: " + daysAdded);
  }
  
  static final Date getMostRecentCapturedDate() {
    Calendar maxCal = null;
    for (final Date incidentDate : MongoData.getAllIncidentDates()) {
      if (incidentDate == null) {
        continue;
      }
      if (maxCal == null) {
        maxCal = Calendar.getInstance();
        maxCal.setTime(incidentDate);
      } else if (maxCal.getTime().before(incidentDate)) {
        maxCal.setTime(incidentDate);
      }
    }
    return maxCal == null ? null : maxCal.getTime();
  }
  
  /**
   * Save incidents for a given day, returns if any data was added by this call.
   */
  static final boolean getAllZonesForDay(final Calendar cal) throws Exception {
    JSONObject[] stats = new JSONObject[7];
    
    for (int zoneID = 1; zoneID <= 6; zoneID++) {
      stats[zoneID] = getStats(zoneID + "", cal);
    }
    
    for (int zoneID = 1; zoneID <= 6; zoneID++) {
      JSONArray incidents = stats[zoneID].getJSONArray("incidents");
      if (incidents.length() == 0) {
        Utilities.println("halting getAllZonesForDay on " + cal.getTime() + " because zone " + zoneID + " is empty");
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
    final Calendar cal = new GregorianCalendar(year, 00, 01);
    while (cal.get(Calendar.YEAR) == year) {
      getAllZonesForDay(cal);
      cal.add(Calendar.DAY_OF_YEAR, 1);
    }
  }
  
  static final String getCrimeDataFilePath(int zoneID, Calendar cal) {
    return "out/incidents/zone" + zoneID + ",year" + cal.get(Calendar.YEAR) + ",day" + cal.get(Calendar.DAY_OF_YEAR) + ".xml";
  }
  
  static final JSONObject getStats(final String zoneID, final Calendar cal) throws Exception {
    final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    final String responseString = getQueryResponse(zoneID, "1,2,3,4,5,6,7,8,9", sdf.format(cal.getTime()), sdf.format(cal.getTime()));
    final JSONObject responseJSON = new JSONObject(responseString);
    final JSONObject stats = new JSONObject(responseJSON.getString("d"));
    return stats;
  }
  
  @Deprecated
  static final void savePrettyXML(final String xml, final String path) throws Exception {
    final PrintWriter pr = new PrintWriter(new BufferedOutputStream(new FileOutputStream(new File(path))));
    pr.write(XMLFormatter.format(xml));
    pr.close();
  }
  
  public static final String getQueryResponse(final String zoneID, final String offenseCodes, final String startDate, final String endDate) throws Exception {
    final JSONObject jobj = new JSONObject();
    jobj.put("zoneID", zoneID);
    jobj.put("offenseCodes", offenseCodes);
    jobj.put("startDate", startDate);
    jobj.put("endDate", endDate);
    
    // final HttpPost httppost = new HttpPost("http://atlantapd.org/Service.aspx/GetMapData");
    final HttpPost httppost = new HttpPost("http://65.82.136.65:80/Service.aspx/GetMapData");
    final StringEntity se = new StringEntity(jobj.toString());
    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
    httppost.setEntity(se);
    
    final DefaultHttpClient httpclient = new DefaultHttpClient();
    httpclient.getConnectionManager().getSchemeRegistry().register(new Scheme("http", 80, new TorSocketFactory()));
    final HttpResponse response = httpclient.execute(httppost);
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
    Set<Incident> incidents = new HashSet<Incident>();
    DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
    Document dom = db.parse(filePath);
    Element docEle = dom.getDocumentElement();
    NodeList nodes = docEle.getElementsByTagName("incidents");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      incidents.add(Incident.create(element));
    }
    return incidents;
  }
  
  static final Set<Incident> getIncidentsFromXMLString(final String xmlString) throws Exception {
    Set<Incident> incidents = new HashSet<Incident>();
    DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
    Document dom = db.parse(new ByteArrayInputStream(xmlString.getBytes()));
    Element docEle = dom.getDocumentElement();
    NodeList nodes = docEle.getElementsByTagName("incidents");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      incidents.add(Incident.create(element));
    }
    return incidents;
  }
  
  @Deprecated
  static final Set<Incident> combineAllIncidents() throws Exception {
    Set<Incident> incidents = new HashSet<Incident>();
    long total = 0;
    final File dir = new File("out/incidents");
    
    final File totalFile = new File(dir, "total.txt");
    PrintWriter pr = new PrintWriter(totalFile);
    
    long count = 0;
    for (final File file : dir.listFiles()) {
      if (file.getName().endsWith(".xml")) {
        try {
          final Set<Incident> incidentsFromFile = getIncidentsForFile(file.getPath());
          for (final Incident incident : incidentsFromFile) {
            pr.println("id_" + count + "=" + incident.id);
            pr.println("npu_" + count + "=" + incident.npu);
            pr.println("beat_" + count + "=" + incident.beat);
            pr.println("marker_" + count + "=" + incident.marker);
            pr.println("neighborhood_" + count + "=" + incident.neighborhood);
            pr.println("number_" + count + "=" + incident.number);
            pr.println("longitude_" + count + "=" + Double.toString(incident.getLongitude()));
            pr.println("latitude_" + count + "=" + Double.toString(incident.getLatitude()));
            pr.println("type_" + count + "=" + incident.type);
            pr.println("shift_" + count + "=" + incident.shift);
            if (incident.location.contains("\\n")) {
              // do nothing
            }
            pr.println("location_" + count + "=" + incident.location.replaceAll("\\n\\s*", " "));
            pr.println("reportDate_" + count + "=" + incident.reportDate);
            
            count++;
          }
          total += incidentsFromFile.size();
        } catch (final Exception e) {
          // do nothing
        }
      }
    }
    pr.close();
    Utilities.println(Long.valueOf(total));
    return incidents;
  }
  
  @Deprecated
  static final Set<Incident> getIncedentsOfDay(Calendar cal) throws Exception {
    Set<Incident> incidents = new HashSet<Incident>();
    for (int zoneID = 1; zoneID <= 6; zoneID++) {
      incidents.addAll(getIncidentsForZoneAndDate(zoneID, cal));
    }
    return incidents;
  }
  
  static final void saveIncidentsToKML(Collection<Incident> incidents, String filePath, String documentName) throws Exception {
    File file = new File(filePath);
    PrintWriter pr = new PrintWriter(file);
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
    pr.close();
  }
  
  static final void filterCrimes2KML(Pattern patternType, String name) throws Exception {
    Collection<Incident> incidents = getAllIncidents(patternType);
    saveIncidentsToKML(incidents, "out/crimesFilter-" + name + ".kml", "Incidents " + name + " (" + incidents.size() + ")");
  }
  
  static void dailyCrimesByCategory() throws Exception {
    Collection<Incident> incidents = getAllIncidents(null);
    Map<String, Map<Date, Vector<Incident>>> catDateMap = new HashMap<String, Map<Date, Vector<Incident>>>();
    
    Date firstDate = null;
    Date lastDate = null;
    for (Incident incident : incidents) {
      Date date = incident.getReportDateAsDate();
      if (firstDate == null || firstDate.after(date)) {
        firstDate = date;
      }
      if (lastDate == null || firstDate.before(date)) {
        lastDate = date;
      }
      String cat = Incident.marker2category.get(incident.marker);
      if (cat == null) {
        throw new RuntimeException("Unknown category marker: '" + incident.marker + "'");
      }
      
      if (!catDateMap.containsKey(cat)) {
        catDateMap.put(cat, new TreeMap<Date, Vector<Incident>>());
      }
      final Map<Date, Vector<Incident>> dateMap = catDateMap.get(cat);
      
      if (!dateMap.containsKey(date)) {
        dateMap.put(date, new Vector<Incident>());
      }
      final Vector<Incident> catDayIncidents = dateMap.get(date);
      
      catDayIncidents.add(incident);
    }
    
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    for (String cat : Incident.marker2category.values()) {
      File file = new File("out/crimeDaysCat-" + cat + ".txt");
      PrintWriter pr = new PrintWriter(file);
      if (catDateMap.get(cat) == null) {
        pr.close();
        continue;
      }
      Map<Date, Vector<Incident>> datedIncidents = catDateMap.get(cat);
      
      Calendar iter = Calendar.getInstance();
      iter.setTime(firstDate);
      Calendar lastCal = Calendar.getInstance();
      lastCal.setTime(lastDate);
      while (!iter.after(lastCal)) {
        pr.print(sdf.format(iter.getTime()) + " ");
        if (datedIncidents.containsKey(iter.getTime())) {
          pr.println(datedIncidents.get(iter.getTime()).size());
        } else {
          pr.println("0");
        }
        
        iter.add(Calendar.DAY_OF_YEAR, 1);
      }
      pr.close();
    }
  }
  
  static void dailyCrimesByCategory2() throws Exception {
    Collection<Incident> incidents = getAllIncidents(null);
    Map<String, Map<Date, Collection<Incident>>> catDateMap = new HashMap<String, Map<Date, Collection<Incident>>>();
    
    Date firstDate = null;
    Date lastDate = null;
    for (Incident incident : incidents) {
      Date date = incident.getReportDateAsDate();
      if (firstDate == null || date.before(firstDate)) {
        firstDate = date;
      }
      if (lastDate == null || date.after(lastDate)) {
        lastDate = date;
      }
      String cat = Incident.marker2category.get(incident.marker);
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
    
    File file = new File("out/crimeDaysByCat.txt");
    PrintWriter pr = new PrintWriter(file);
    pr.print("\t");
    for (String cat : Incident.marker2category.values()) {
      pr.print(cat + "\t");
    }
    pr.println();
    
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    Calendar iter = Calendar.getInstance();
    iter.setTime(firstDate);
    Calendar lastCal = Calendar.getInstance();
    lastCal.setTime(lastDate);
    while (!iter.after(lastCal)) {
      Date time = iter.getTime();
      pr.print(sdf.format(time));
      for (String cat : Incident.marker2category.values()) {
        Collection<Incident> incidentsOnDate = catDateMap.get(cat).get(time);
        int num = incidentsOnDate != null ? incidentsOnDate.size() : 0;
        pr.print("\t" + num);
      }
      pr.println();
      iter.add(Calendar.DAY_OF_YEAR, 1);
    }
    pr.close();
  }
  
  static final void touchAllCombinedIncidents() throws Exception {
    final File dir = new File("out");
    final File totalFile = new File(dir, "total.txt");
    Properties props = new Properties();
    FileReader fr = new FileReader(totalFile);
    props.load(fr);
    fr.close();
  }
  
  public static final Collection<Incident> getAllIncidents(Pattern typePattern) throws Exception {
    Collection<Incident> incidents = new LinkedList<Incident>();
    
    final File dir = new File("out" + File.separator + "incidents");
    final File totalFile = new File(dir, "total.txt");
    FileReader fr = new FileReader(totalFile);
    BufferedReader br = new BufferedReader(fr);
    
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
        incidents.add(new Incident(
          id,
          npu,
          beat,
          marker,
          neighborhood,
          number,
          Double.parseDouble(longitude),
          Double.parseDouble(latitude),
          type,
          shift,
          location,
          reportDate,
          Utilities.MM_dd_yyyy().parse(reportDate).getTime()
        ));
        hit++;
      }
    }
    br.close();
    
    return incidents;
  }
  
  static final void crimes2kmlByNPU() throws Exception {
    Collection<Incident> incidents = getAllIncidents(null);
    
    for (char npu = 'A'; npu <= 'Z'; npu++) {
      if (npu == 'U') {
        npu++;
      }
      String npuString = String.valueOf(npu);
      for (int year = 2004; year <= 2011; year++) {
        String yearString = String.valueOf(year);
        File file = new File("out/KML/npu" + npuString + "_year" + yearString + ".kml");
        PrintWriter pr = new PrintWriter(file);
        pr.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pr.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        pr.println("  <Document>");
        int hits = 0;
        for (Incident incident : incidents) {
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
        pr.close();
        if (hits == 0) {
          Utilities.println("omitting " + file.getName());
          file.delete();
        }
      }
    }
  }
}
