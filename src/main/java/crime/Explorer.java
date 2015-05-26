package crime;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToolBar;

import org.apache.commons.collections4.keyvalue.MultiKey;

import crime.OSM.BoundingBox;
import crime.OSM.Tile;

public class Explorer extends JComponent {
  
  private class ExplorerRunnable implements Runnable {
    private static final int dragHistoryWindow = 10;
    private Thread           thread            = null;
    
    public ExplorerRunnable() {
      
    }
    
    /**
     * run method of the runnable instance, at 100 Hz
     */
    @Override
    public void run() {
      Thread me = Thread.currentThread();
      while (this.thread == me) { // this.thread is set null when stop is called
        tick();
        repaint();
        { // try to sleep, accepting an interruption without throwing an exception
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            break;
          }
        }
      }
      this.thread = null;
    }
    
    /**
     * Create this.thread and start it
     */
    public void start() {
      this.thread = new Thread(this, "Explorer Thread");
      this.thread.setPriority(Thread.MAX_PRIORITY);
      this.thread.start();
    }
    
    /**
     * sets this.thread to null so that the run loop will terminate
     */
    public synchronized void stop() {
      this.thread = null;
    }
    
    /**
     * ExplorerEunnable update loop, called by {@link #run()}
     */
    private void tick() {
      if (Explorer.this.dragging) { // get the newest drag vector, enqueue it into history, and apply it to the view
        double diffX = Explorer.this.mml.x - Explorer.this.mouseDragLastX;
        double diffY = Explorer.this.mml.y - Explorer.this.mouseDragLastY;
        
        { // enqueue the latest drag vector
          Explorer.this.mouseDragHistory.add(new double[] { diffX, diffY });
          if (Explorer.this.mouseDragHistory.size() > dragHistoryWindow) {
            Explorer.this.mouseDragHistory.poll();
          }
        }
        
        Explorer.this.screenCenterLongitude -= diffX * Explorer.this.longitudePerPixel;
        Explorer.this.screenCenterLatitude += diffY * Explorer.this.lattitudePerPixel;
        
        Explorer.this.mouseDragLastX = Explorer.this.mml.x;
        Explorer.this.mouseDragLastY = Explorer.this.mml.y;
      } else { // sliding
        Explorer.this.screenCenterLongitudeSpeed *= 0.98;
        Explorer.this.screenCenterLatitudeSpeed *= 0.98;
        
        if (Explorer.this.screenCenterLongitudeSpeed * Explorer.this.screenCenterLongitudeSpeed + Explorer.this.screenCenterLatitudeSpeed * Explorer.this.screenCenterLatitudeSpeed < 0.05) {
          Explorer.this.screenCenterLongitudeSpeed = 0;
          Explorer.this.screenCenterLatitudeSpeed = 0;
        }
        
        Explorer.this.screenCenterLongitude -= Explorer.this.screenCenterLongitudeSpeed * Explorer.this.longitudePerPixel;
        Explorer.this.screenCenterLatitude += Explorer.this.screenCenterLatitudeSpeed * Explorer.this.lattitudePerPixel;
      }
    }
  }
  
  enum MouseMode {
    DRAG_MAP("Drag Map", "Drag"), RECENTER("Pick a new view center", "Recenter"), POLYGON("Outline a polygon", "Polygon"), GRAPH("Graph crime history", "Graph");
    
    final String buttonName;
    final String statusName;
    
    MouseMode(final String statusName, final String buttonName) {
      this.statusName = statusName;
      this.buttonName = buttonName;
    }
    
    public String getStatusName() {
      return this.statusName;
    }
  }
  
  class MyMouseListener extends MouseAdapter {
    int x = 0;
    int y = 0;
    
    @Override
    public void mouseClicked(final MouseEvent e) {
      // screen coordinates
      this.x = e.getX();
      this.y = e.getY();
      if (Explorer.this.mouseMode == MouseMode.RECENTER) {
        double[] coords = getLongLatFromScreenCoords(new double[] { this.x, this.y });
        Explorer.this.screenCenterLongitude = coords[0];
        Explorer.this.screenCenterLatitude = coords[1];
        setMouseMode(MouseMode.DRAG_MAP);
      } else if (Explorer.this.mouseMode == MouseMode.POLYGON) {
        int button = e.getButton();
        if (button == 1) {
          if (Explorer.this.polyPoints == null) {
            Explorer.this.polyPoints = new LinkedList<>();
          }
          double[] coords = getLongLatFromScreenCoords(new double[] { this.x, this.y });
          Explorer.this.polyPoints.add(coords);
        } else if (button == 3) {
          if (Explorer.this.polyPoints.size() <= 2) {
            Explorer.this.polyPoints = null;
          } else {
            double[] firstPoint = Explorer.this.polyPoints.iterator().next();
            Explorer.this.polyPoints.add(firstPoint);
          }
          Explorer.this.mouseMode = MouseMode.DRAG_MAP;
        }
      }
      repaint();
    }
    
    @Override
    public void mouseDragged(final MouseEvent e) {
      this.x = e.getX();
      this.y = e.getY();
    }
    
    @Override
    public void mouseMoved(final MouseEvent e) {
      this.x = e.getX();
      this.y = e.getY();
    }
    
    @Override
    public void mousePressed(final MouseEvent e) {
      if (Explorer.this.mouseMode == MouseMode.DRAG_MAP) {
        { // stop any sliding
          Explorer.this.screenCenterLongitudeSpeed = 0;
          Explorer.this.screenCenterLatitudeSpeed = 0;
        }
        this.x = e.getX();
        this.y = e.getY();
        { // init dragging
          Explorer.this.mouseDragLastX = this.x;
          Explorer.this.mouseDragLastY = this.y;
          Explorer.this.dragging = true;
        }
      }
      repaint();
    }
    
    @Override
    public void mouseReleased(final MouseEvent e) {
      if (Explorer.this.mouseMode == MouseMode.DRAG_MAP) {
        this.x = e.getX();
        this.y = e.getY();
        
        if (Explorer.this.mouseDragHistory.size() > 0) {
          double sumX = 0d;
          double sumY = 0d;
          int count = 0;
          
          while (!Explorer.this.mouseDragHistory.isEmpty()) {
            double[] pair = Explorer.this.mouseDragHistory.poll();
            sumX += pair[0];
            sumY += pair[1];
            count++;
          }
          
          Explorer.this.screenCenterLongitudeSpeed = sumX / count;
          Explorer.this.screenCenterLatitudeSpeed = sumY / count;
        } else {
          Explorer.this.screenCenterLongitudeSpeed = 0;
          Explorer.this.screenCenterLatitudeSpeed = 0;
        }
        
        Explorer.this.dragging = false;
      }
      
      repaint();
    }
    
    @Override
    public void mouseWheelMoved(final MouseWheelEvent e) {
      int clicks = e.getWheelRotation();
      Explorer.this.screenZoom = Math.max(4, Math.min(18, Explorer.this.screenZoom - clicks));
      OSM.ZOOM_LEVEL = Explorer.this.screenZoom;
      repaint();
    }
  }
  
  class StatusBar extends JPanel {
    
    private static final long serialVersionUID = 1L;
    
    public StatusBar() {
      setLayout(new BorderLayout());
      setPreferredSize(new Dimension(10, 23));
      
      JPanel rightPanel = new JPanel(new BorderLayout());
      rightPanel.setOpaque(false);
      
      add(rightPanel, BorderLayout.EAST);
      setBackground(SystemColor.control);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      
      int y = 0;
      g.setColor(new Color(156, 154, 140));
      g.drawLine(0, y, getWidth(), y);
      y++;
      g.setColor(new Color(196, 194, 183));
      g.drawLine(0, y, getWidth(), y);
      y++;
      g.setColor(new Color(218, 215, 201));
      g.drawLine(0, y, getWidth(), y);
      y++;
      g.setColor(new Color(233, 231, 217));
      g.drawLine(0, y, getWidth(), y);
      
      y = getHeight() - 3;
      g.setColor(new Color(233, 232, 218));
      g.drawLine(0, y, getWidth(), y);
      y++;
      g.setColor(new Color(233, 231, 216));
      g.drawLine(0, y, getWidth(), y);
      y = getHeight() - 1;
      g.setColor(new Color(221, 221, 220));
      g.drawLine(0, y, getWidth(), y);
      
      g.setColor(Color.black);
      g.drawString(Explorer.this.getStatusText(), 3, 15);
    }
    
  }
  
  class ToolBar extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;
    
    public ToolBar() {
      super(new BorderLayout());
      
      // Create the toolbar.
      JToolBar toolBar = new JToolBar("Still draggable");
      toolBar.setFloatable(false);
      toolBar.setRollover(true);
      addButtons(toolBar);
      
      // Lay out the main panel.
      setPreferredSize(new Dimension(450, 30));
      add(toolBar, BorderLayout.PAGE_START);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      String cmd = e.getActionCommand();
      setMouseMode(Explorer.MouseMode.valueOf(cmd));
    }
    
    protected void addButtons(JToolBar toolBar) {
      toolBar.add(makeNavigationButton("Drag", MouseMode.DRAG_MAP, "Click and drag the map with the mouse", MouseMode.DRAG_MAP.buttonName));
      toolBar.add(makeNavigationButton("Point", MouseMode.RECENTER, "Click to select a point for more info", MouseMode.RECENTER.buttonName));
      toolBar.add(makeNavigationButton("Polygon", MouseMode.POLYGON, "Click to outline a polygon", MouseMode.POLYGON.buttonName));
      toolBar.add(makeNavigationButton("Graph", MouseMode.GRAPH, "Click to graph crime history", MouseMode.GRAPH.buttonName));
    }
    
    protected JButton makeNavigationButton(String imageName, Explorer.MouseMode mode, String toolTipText, String altText) {
      // Look for the image.
      String imgLocation = "images/" + imageName + ".gif";
      URL imageURL = ToolBar.class.getResource(imgLocation);
      
      // Create and initialize the button.
      JButton button = new JButton();
      button.setActionCommand(mode.toString());
      button.setToolTipText(toolTipText);
      button.addActionListener(this);
      
      if (imageURL != null) { // image found
        button.setIcon(new ImageIcon(imageURL, altText));
      } else { // no image found
        button.setText(altText);
        System.err.println("Resource not found: " + imgLocation);
      }
      
      return button;
    }
  }
  
  public static LocalDate               localDateFrom    = null;
  
  public static LocalDate               localDateTo      = null;
  // static Collection<Incident> incidents = null;
  
  final static Map<String, String>      marker2category  = Collections.unmodifiableMap(new LinkedHashMap<String, String>() {
                                                           private static final long serialVersionUID = 1L;
                                                           {
                                                             put("yellow", "Aggravated Assault");
                                                             put("purple", "Auto Theft");
                                                             put("orange", "Drug Arrest");
                                                             put("red", "Homicide");
                                                             put("green", "Larceny");
                                                             put("white", "Non-Residential Burglary");
                                                             put("teal", "Non-Residential Burglary");
                                                             put("blue", "Residential Burglary");
                                                             put("brown", "Robbery");
                                                             put("gray", "Vehicle Larceny");
                                                             put("grey", "Vehicle Larceny");
                                                           }
                                                         });
  
  private static final long             serialVersionUID = 1L;
  
  static {
    { // init the date range
      localDateTo = LocalDate.now();
      localDateFrom = localDateTo.minusMonths(2);
    }
  }
  
  static final void launchExplorer() {
    final Explorer explorer = new Explorer();
    final JFrame mainFrame = new JFrame("Crime Explorer");
    explorer.setMainFrame(mainFrame);
    Container contentPane = mainFrame.getContentPane();
    
    mainFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent winEvt) {
        System.exit(0);
      }
      
      @Override
      public void windowDeiconified(WindowEvent e) {
        explorer.er.start();
      }
      
      @Override
      public void windowIconified(WindowEvent e) {
        explorer.er.stop();
      }
    });
    
    StatusBar statusBar = explorer.new StatusBar();
    explorer.setStatusBar(statusBar);
    
    contentPane.setLayout(new BorderLayout());
    contentPane.add(statusBar, BorderLayout.SOUTH);
    contentPane.add(explorer.new ToolBar(), BorderLayout.NORTH);
    contentPane.add(explorer, BorderLayout.CENTER);
    
    mainFrame.pack();
    mainFrame.setVisible(true);
    explorer.er.start();
  }
  
  /** whether the view is sliding (false) or not sliding (true) */
  boolean          dragging                   = false;
  
  ExplorerRunnable er                         = new ExplorerRunnable();
  
  double           lattitudePerPixel          = 0d;
  
  double           longitudePerPixel          = 0d;
  
  JFrame           mainFrame                  = null;
  
  MyMouseListener  mml                        = new MyMouseListener();
  
  Queue<double[]>  mouseDragHistory           = new ArrayDeque<>();
  
  double           mouseDragLastX             = 0d;
  
  double           mouseDragLastY             = 0d;
  
  MouseMode        mouseMode                  = MouseMode.DRAG_MAP;
  
  double           screenCenterLatitude       = 33.784;                    // Y
                                                                            
  double           screenCenterLatitudeSpeed  = 0d;
  
  double           screenCenterLongitude      = -84.3995;                  // X
                                                                            
  double           screenCenterLongitudeSpeed = 0d;
  
  int              screenZoom                 = 17;
  
  StatusBar        statusBar                  = null;
  
  List<double[]>   polyPoints                 = null;
  
  Explorer() {
    this.addMouseListener(this.mml);
    this.addMouseMotionListener(this.mml);
    this.addMouseWheelListener(this.mml);
  }
  
  /*
   * private static Color m_tRed = new Color(255, 0, 0, 150);
   * 
   * private static Color m_tGreen = new Color(0, 255, 0, 150);
   * 
   * private static Color m_tBlue = new Color(0, 0, 255, 150);
   * 
   * private static Font monoFont = new Font("Monospaced", Font.BOLD | Font.ITALIC, 36);
   * 
   * private static Font sanSerifFont = new Font("SanSerif", Font.PLAIN, 12);
   * 
   * private static Font serifFont = new Font("Serif", Font.BOLD, 24);
   * 
   * private static ImageIcon java2sLogo = new ImageIcon("java2s.gif");
   */
  
  double[] getLongLatFromScreenCoords(double[] screenCoords) {
    double[] longLat = new double[2];
    
    double screenMidX = getWidth() / 2;
    double screenMidY = getHeight() / 2;
    
    double diffX = screenCoords[0] - screenMidX;
    double diffY = (getHeight() - screenCoords[1]) - screenMidY;
    
    double projectedX = this.screenCenterLongitude + this.longitudePerPixel * diffX;
    double projectedY = this.screenCenterLatitude + this.lattitudePerPixel * diffY;
    
    longLat[0] = projectedX;
    longLat[1] = projectedY;
    
    return longLat;
  }
  
  double[] getScreenCoordsFromLongLat(double[] longLat) {
    double[] screenCoords = new double[2];
    
    double screenMidX = getWidth() / 2;
    double screenMidY = getHeight() / 2;
    
    double diffX = longLat[0] - this.screenCenterLongitude;
    double diffY = longLat[1] - this.screenCenterLatitude;
    
    double projectedX = screenMidX + diffX / this.longitudePerPixel;
    double projectedY = screenMidY - diffY / this.lattitudePerPixel;
    
    screenCoords[0] = projectedX;
    screenCoords[1] = projectedY;
    
    return screenCoords;
  }
  
  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }
  
  @Override
  public Dimension getPreferredSize() {
    return new Dimension(400, 400);
  }
  
  String getStatusText() {
    return "Mode: " + this.mouseMode.getStatusName() + "  Date ( " + Explorer.localDateFrom + " to " + Explorer.localDateTo + " )  " + this.screenCenterLongitude + " , "
        + this.screenCenterLatitude + " , " + this.screenZoom;
  }
  
  @Override
  public void paintComponent(final Graphics g) {
    super.paintComponent(g);
    
    final int width = getWidth();
    final int height = getHeight();
    
    final Tile screenMiddleTile = Tile.create(this.screenCenterLatitude, this.screenCenterLongitude, this.screenZoom);
    this.longitudePerPixel = (screenMiddleTile.boundingBox.east - screenMiddleTile.boundingBox.west) / 256d;
    this.lattitudePerPixel = (screenMiddleTile.boundingBox.north - screenMiddleTile.boundingBox.south) / 256d;
    
    final Tile topLeft = Tile.create(this.screenCenterLatitude + this.lattitudePerPixel * height / 2, this.screenCenterLongitude - this.longitudePerPixel * width / 2, this.screenZoom);
    final Tile botRight = Tile.create(this.screenCenterLatitude - this.lattitudePerPixel * height / 2, this.screenCenterLongitude + this.longitudePerPixel * width / 2, this.screenZoom);
    
    ImageLoader.incrementSegmentsAges();
    
    for (int x = topLeft.x; x <= botRight.x; x++) {
      for (int y = topLeft.y; y <= botRight.y; y++) {
        final MultiKey<Integer> key = new MultiKey<>(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(this.screenZoom));
        final MapSegment segment = ImageLoader.loadSegment(key, false);
        if (segment != null) {
          final BoundingBox bbox = segment.getTile().getBoundingBox();
          final double[] segmentScreenCoord = getScreenCoordsFromLongLat(new double[] { bbox.west, bbox.north });
          final int[] segmentIncidentScreenPos = new int[] { (int)segmentScreenCoord[0], (int)segmentScreenCoord[1] };
          
          final BufferedImage streets = segment.getOSMImage();
          final BufferedImage crime = segment.getCrimeImage();
          g.drawImage(streets, segmentIncidentScreenPos[0], segmentIncidentScreenPos[1], null);
          g.drawImage(crime, segmentIncidentScreenPos[0], segmentIncidentScreenPos[1], null);
          segment.resetAge();
        }
      }
    }
    
    final Color redAlpha = new Color(1, 0, 0, 0.4f);
    final Set<CrimeBubble> allBubbles = new HashSet<>();
    for (int x = topLeft.x; x <= botRight.x; x++) {
      for (int y = topLeft.y; y <= botRight.y; y++) {
        final MultiKey<Integer> key = new MultiKey<>(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(this.screenZoom));
        final MapSegment segment = ImageLoader.loadSegment(key, false);
        if (segment != null) {
          final BoundingBox bbox = segment.getTile().getBoundingBox();
          final double[] segmentScreenCoord = getScreenCoordsFromLongLat(new double[] { bbox.west, bbox.north });
          final int[] segmentIncidentScreenPos = new int[] { (int)segmentScreenCoord[0], (int)segmentScreenCoord[1] };
          final Set<CrimeBubble> bubbles = segment.getBubbles();
          if (bubbles != null) {
            for (CrimeBubble bubble : bubbles) {
              allBubbles.add(bubble);
              Incident incident = bubble.getIncident();
              double[] incidentScreenCoord = getScreenCoordsFromLongLat(new double[] { incident.getLongitude(), incident.getLatitude() });
              int[] diff = new int[] { (int)(incidentScreenCoord[0] - segmentScreenCoord[0]), (int)(incidentScreenCoord[1] - segmentScreenCoord[1]) };
              int[] incidentScreenPos = new int[] { segmentIncidentScreenPos[0] + diff[0], segmentIncidentScreenPos[1] + diff[1] };
              g.setColor(Color.RED);
              g.fillOval(incidentScreenPos[0] - 3, incidentScreenPos[1] - 3, 6, 6);
              String incidentCategory = Explorer.marker2category.get(incident.getMarker());
              if (incidentCategory == null) {
                throw new RuntimeException("Unhandled marker " + incident.getMarker());
              }
              g.drawString(incidentCategory, (int)(incidentScreenPos[0] + bubble.getScreenPosOffset()[0]), (int)(incidentScreenPos[1] + bubble.getScreenPosOffset()[1]));
              g.drawString(incident.getReportDate(), (int)(incidentScreenPos[0] + bubble.getScreenPosOffset()[0]), (int)(incidentScreenPos[1] + bubble.getScreenPosOffset()[1]) + 12);
              g.drawString(incident.getId(), (int)(incidentScreenPos[0] + bubble.getScreenPosOffset()[0]), (int)(incidentScreenPos[1] + bubble.getScreenPosOffset()[1]) - 12);
              g.setColor(redAlpha);
              g.drawLine(incidentScreenPos[0], incidentScreenPos[1], (int)(incidentScreenPos[0] + bubble.getScreenPosOffset()[0]), (int)(incidentScreenPos[1] + bubble.getScreenPosOffset()[1]));
            }
          }
        }
      }
    }
    
    g.setColor(Color.WHITE);
    for (CrimeBubble bubble1 : allBubbles) {
      for (CrimeBubble bubble2 : allBubbles) {
        if (bubble1 != bubble2) {
          double[] bubble1ScreenPos = getScreenCoordsFromLongLat(new double[] { bubble1.getIncident().getLongitude(), bubble1.getIncident().getLatitude() });
          double[] bubble2ScreenPos = getScreenCoordsFromLongLat(new double[] { bubble2.getIncident().getLongitude(), bubble2.getIncident().getLatitude() });
          
          bubble1ScreenPos[0] += bubble1.getScreenPosOffset()[0];
          bubble1ScreenPos[1] += bubble1.getScreenPosOffset()[1];
          bubble2ScreenPos[0] += bubble2.getScreenPosOffset()[0];
          bubble2ScreenPos[1] += bubble2.getScreenPosOffset()[1];
          
          double[] diff = new double[] { bubble2ScreenPos[0] - bubble1ScreenPos[0], bubble2ScreenPos[1] - bubble1ScreenPos[1] };
          double quad = diff[0] * diff[0] + diff[1] * diff[1];
          if (quad < 100 * 100) {
            if (quad < 0.0001) {
              Random rand = new Random();
              bubble1.getScreenPosOffset()[0] += rand.nextDouble();
              bubble1.getScreenPosOffset()[1] += rand.nextDouble();
              bubble2.getScreenPosOffset()[0] += rand.nextDouble();
              bubble2.getScreenPosOffset()[1] += rand.nextDouble();
            } else {
              double quadRoot = Math.sqrt(quad);
              double unitDiff[] = new double[] { diff[0] / quadRoot, diff[1] / quadRoot };
              
              bubble1.getScreenPosOffset()[0] -= unitDiff[0];
              bubble1.getScreenPosOffset()[1] -= unitDiff[1];
              bubble2.getScreenPosOffset()[0] += unitDiff[0];
              bubble2.getScreenPosOffset()[1] += unitDiff[1];
            }
          }
        } else {
          break;
        }
      }
    }
    
    if (this.polyPoints != null) {
      g.setColor(Color.BLUE);
      double[] lastMapPoint = null;
      for (double[] point : this.polyPoints) {
        if (lastMapPoint != null) {
          double[] screenPoint1 = getScreenCoordsFromLongLat(lastMapPoint);
          double[] screenPoint2 = getScreenCoordsFromLongLat(point);
          ((Graphics2D)g).draw(new Line2D.Double(screenPoint1[0], screenPoint1[1], screenPoint2[0], screenPoint2[1]));
        }
        lastMapPoint = point;
      }
      if (this.mouseMode == MouseMode.POLYGON) {
        double[] screenPoint1 = getScreenCoordsFromLongLat(lastMapPoint);
        double[] screenPoint2 = new double[] { this.mml.x, this.mml.y };
        ((Graphics2D)g).draw(new Line2D.Double(screenPoint1[0], screenPoint1[1], screenPoint2[0], screenPoint2[1]));
      }
    }
    
    ImageLoader.expireSegments();
    
    this.statusBar.repaint();
    
    /*
     * g.setColor(Color.white);
     * 
     * /* g.setFont(Explorer.monoFont); FontMetrics fm = g.getFontMetrics(); w = fm.stringWidth("Java Source"); h =
     * fm.getAscent(); g.drawString("Java Source " + System.nanoTime(), 120 - (w / 2), 120 + (h / 4));
     * 
     * g.setFont(Explorer.sanSerifFont); fm = g.getFontMetrics(); w = fm.stringWidth("and"); h = fm.getAscent();
     * g.drawString("and", 200 - (w / 2), 200 + (h / 4));
     * 
     * g.setFont(Explorer.serifFont); fm = g.getFontMetrics(); w = fm.stringWidth("Support."); h = fm.getAscent();
     * g.drawString("Support.", 280 - (w / 2), 280 + (h / 4));
     */
  }
  
  private final void setMainFrame(JFrame frame) {
    this.mainFrame = frame;
  }
  
  void setMouseMode(MouseMode newMouseMode) {
    this.mouseMode = newMouseMode;
    
    switch (this.mouseMode) {
    case DRAG_MAP: {
      Explorer.this.mainFrame.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      break;
    }
    case RECENTER: {
      Explorer.this.mainFrame.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      break;
    }
    case POLYGON: {
      Explorer.this.mainFrame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      this.polyPoints = null;
      break;
    }
    case GRAPH: {
      final Collection<Incident> incidents = MongoData.getIncidentsWithinPolyAndTime(Explorer.this.polyPoints.toArray(new double[][] {}), localDateTo.minusYears(5), localDateTo);
      System.out.println(incidents.size());
      try {
        Test.dailyCrimesByCategory(30, incidents);
      } catch (Exception e) {
        e.printStackTrace();
      }
      setMouseMode(MouseMode.DRAG_MAP);
      break;
    }
    default:
      break;
    }
  }
  
  private final void setStatusBar(StatusBar statusBar) {
    this.statusBar = statusBar;
  }
}
