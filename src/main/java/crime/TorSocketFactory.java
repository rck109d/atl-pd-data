package crime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

public class TorSocketFactory implements ConnectionSocketFactory {
  @Override
  public Socket createSocket(HttpContext context) throws IOException {
    Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 9150));
    return new Socket(proxy);
  }
  @Override
  public Socket connectSocket(int connectTimeout, Socket sock, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
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
