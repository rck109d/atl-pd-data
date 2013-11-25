package crime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.params.HttpParams;

public class TorSocketFactory implements SchemeSocketFactory {
  
  @Override
  public Socket createSocket(HttpParams params) {
    Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 9150));
    return new Socket(proxy);
  }
  
  @Override
  public boolean isSecure(Socket arg0) throws IllegalArgumentException {
    return false;
  }
  
  @Override
  public Socket connectSocket(Socket sock, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
    Socket socket;
    if (sock != null) {
      socket = sock;
    } else {
      socket = createSocket(null);
    }
    if (localAddress != null) {
      socket.bind(localAddress);
    }
    try {
      socket.connect(remoteAddress, 20 * 1000);
    } catch (SocketTimeoutException ex) {
      throw new ConnectTimeoutException("Connect to " + remoteAddress.getHostName() + "/" + remoteAddress.getAddress() + " timed out");
    }
    return socket;
  }
  
}
