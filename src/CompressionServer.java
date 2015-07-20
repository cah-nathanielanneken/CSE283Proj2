import java.net.*; // for Socket, ServerSocket, and InetAddress
import java.io.*; // for IOException and Input/OutputStream
import java.util.zip.*;

public class CompressionServer {
	private static final int BUFSIZE = 1024; // Size of receive buffer

	public static void main(String[] args) throws IOException {

		int servPort = Integer.parseInt(args[0]);

		// Create a server socket to accept client connection requests
		ServerSocket servSock = new ServerSocket(servPort);

		int recvMsgSize; // Size of received message
		byte[] byteBuffer = new byte[BUFSIZE]; // Receive buffer

		for (;;) { // Run forever, accepting and servicing connections
			Socket clntSock = servSock.accept(); // Get client connection

			System.out.println("Handling client at "
					+ clntSock.getInetAddress().getHostAddress() + " on port "
					+ clntSock.getPort());

			InputStream in = clntSock.getInputStream();

			BufferedInputStream origin = null;

			byte[] byteBuffer2 = new byte[BUFSIZE];
			int count = 0;
			while ((byteBuffer2[count]=(byte)in.read()) != -2) {
				count++;
			}
			String newFile = new String(byteBuffer2).substring(0, count);
			System.out.println(newFile);

			// Create a file output stream
			FileOutputStream dest = new FileOutputStream(newFile.trim());

			ZipOutputStream outZip = new ZipOutputStream(
					new BufferedOutputStream(dest));

			origin = new BufferedInputStream(in, BUFSIZE);

			ZipEntry entry = new ZipEntry("../"+newFile.substring(0, newFile.length()-4));
			outZip.putNextEntry(entry);
			while ((recvMsgSize = origin.read(byteBuffer, 0, BUFSIZE)) != -1) {
				outZip.write(byteBuffer, 0, recvMsgSize);
			}
			origin.close();
			outZip.close();

			clntSock.close(); // Close the socket. We are done with this client!
		}
		/* NOT REACHED */
	}
}
