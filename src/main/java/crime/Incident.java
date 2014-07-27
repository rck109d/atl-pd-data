package crime;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.w3c.dom.Element;

public final class Incident {
  final static Map<String, String>  marker2category;
  
  private static String getElementTextByTag(Element element, String tagName) {
    return element.getElementsByTagName(tagName).item(0).getTextContent().trim();
  }
  
  static {
    Map<String, String> backingMap = new LinkedHashMap<>();
    backingMap.put("yellow", "Aggravated Assault");
    backingMap.put("purple", "Auto Theft");
    backingMap.put("orange", "Drug Arrest");
    backingMap.put("red", "Homicide");
    backingMap.put("green", "Larceny");
    backingMap.put("white", "Non-Residential Burglary");
    backingMap.put("teal", "Non-Residential Burglary");
    backingMap.put("blue", "Residential Burglary");
    backingMap.put("brown", "Robbery");
    backingMap.put("gray", "Vehicle Larceny");
    backingMap.put("grey", "Vehicle Larceny");
    marker2category = Collections.unmodifiableMap(backingMap);
  }
  
  final String                      id;
  final String                      npu;
  final String                      beat;
  final String                      marker;
  final String                      neighborhood;
  final String                      number;
  // final String longitude;
  final double                      longitudeDouble;
  // final String latitude;
  final double                      latitudeDouble;
  final String                      type;
  final String                      shift;
  final String                      location;
  final String                      reportDate;
  final long                        reportDateTime;
  
  public static Incident create(Element element) throws NumberFormatException, ParseException {
    final String id = getElementTextByTag(element, "id");
    final String npu = getElementTextByTag(element, "npu");
    final String beat = getElementTextByTag(element, "beat");
    final String marker = getElementTextByTag(element, "marker");
    final String neighborhood = getElementTextByTag(element, "neighborhood");
    final String number = getElementTextByTag(element, "number");
    final String longitude = getElementTextByTag(element, "longitude");
    final String latitude = getElementTextByTag(element, "latitude");
    final String type = getElementTextByTag(element, "type");
    final String shift = getElementTextByTag(element, "shift");
    final String location = getElementTextByTag(element, "loction");
    final String reportDate = getElementTextByTag(element, "reportdate");
    
    return new Incident(id, npu, beat, marker, neighborhood, number, Double.parseDouble(longitude), Double.parseDouble(latitude), type, shift, location, reportDate, Utilities.MM_dd_yyyy().parse(reportDate).getTime());
  }
  
  public Incident(String id, String npu, String beat, String marker, String neighborhood, String number, double longitude, double latitude, String type, String shift, String location, String reportDate, long reportDateTime) {
    super();
    this.id = id;
    this.npu = npu;
    this.beat = beat;
    this.marker = marker;
    this.neighborhood = neighborhood;
    this.number = number;
    this.longitudeDouble = longitude;
    this.latitudeDouble = latitude;
    this.type = type;
    this.shift = shift;
    this.location = location;
    this.reportDate = reportDate;
    this.reportDateTime = reportDateTime;
  }
  
  public final String getId() {
    return this.id;
  }
  
  public final String getNpu() {
    return this.npu;
  }
  
  public final String getBeat() {
    return this.beat;
  }
  
  public final String getMarker() {
    return this.marker;
  }
  
  public final String getNeighborhood() {
    return this.neighborhood;
  }
  
  public final String getNumber() {
    return this.number;
  }
  
  public final double getLongitude() {
    return this.longitudeDouble;
  }
  
  public final double getLatitude() {
    return this.latitudeDouble;
  }
  
  public final String getType() {
    return this.type;
  }
  
  public final String getShift() {
    return this.shift;
  }
  
  public final String getLocation() {
    return this.location;
  }
  
  public final String getReportDate() {
    return this.reportDate;
  }
  
  public final Date getReportDateAsDate() {
    return new Date(this.reportDateTime);
  }
  
  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, new ToStringStyle() {
      private static final long  serialVersionUID  = 1L;
      {
        setUseIdentityHashCode(false);
        setUseShortClassName(true);
      }
    });
  }
  
}
