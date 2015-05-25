package crime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public final class Utilities {
  
  /** yyyy-MM-dd */
  public static SimpleDateFormat isoDate() {
    return new SimpleDateFormat("yyyy-MM-dd");
  }
  
  public static DateTimeFormatter slashyMdy() {
    return DateTimeFormatter.ofPattern("MM/dd/yyyy");
  }
  
  public static final void println(final Object o) {
    System.out.println(o.toString());
  }
  
  static final void printReflection(final Object o) {
    Utilities.println(ToStringBuilder.reflectionToString(o, new ToStringStyle() {
      private static final long serialVersionUID = 1L;
      {
        setUseIdentityHashCode(false);
        setUseShortClassName(true);
      }
    }));
  }
  
  public static byte[] readFileBytes(File file) throws IOException {
    try (final RandomAccessFile f = new RandomAccessFile(file, "r")) {
      byte[] b = new byte[(int)f.length()];
      f.read(b);
      return b;
    }
  }
  
  public static byte[] readBytes(final InputStream in) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int count;
    byte[] buffer = new byte[8192];
    while ((count = in.read(buffer)) > 0) {
      baos.write(buffer, 0, count);
    }
    return baos.toByteArray();
  }
  
  public static boolean pointInPoly(double[] point, double[][] polyPoints) {
    int i;
    int j;
    boolean result = false;
    for (i = 0, j = polyPoints.length - 1; i < polyPoints.length; j = i++) {
      if ((polyPoints[i][1] > point[1]) != (polyPoints[j][1] > point[1])
          && (point[0] < (polyPoints[j][0] - polyPoints[i][0]) * (point[1] - polyPoints[i][1]) / (polyPoints[j][1] - polyPoints[i][1]) + polyPoints[i][0])) {
        result = !result;
      }
    }
    return result;
    
  }
}
