package crime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class BatchRename {
  
  public static void main(String[] args) throws IOException {
    doRename("C:/Users/Martin/workspace/Crime/out/heatmap", "AUTOTHEFT");
  }
  
  private static void doRename(final String dirPath, final String newName) throws IOException {
    File dir = new File(dirPath);
    new File(dir, "renamed").mkdirs();
    
    int count = 0;
    for (File f : dir.listFiles()) {
      String name = f.getName();
      if (f.isFile() && name.endsWith(".png")) {
        String numString = count + "";
        while (numString.length() < 6) {
          numString = "0" + numString;
        }
        File newFile = new File(dir + File.separator + "renamed", "heatmap-" + newName + "-" + numString + ".png");
        
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newFile));
        
        byte[] buf = new byte[1024 * 4];
        int len;
        while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
        }
        in.close();
        out.close();
        
        count++;
      }
    }
  }
}
