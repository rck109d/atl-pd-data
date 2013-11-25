package crime;


public class CrimeBubble {
  final Incident  incident;
  final double[]  screenPosOffset;
  
  CrimeBubble(final Incident incident) {
    super();
    this.incident = incident;
    this.screenPosOffset = new double[] { 0, 0 };
  }
  
  public double[] getScreenPosOffset() {
    return this.screenPosOffset;
  }
  
  public Incident getIncident() {
    return this.incident;
  }
}
