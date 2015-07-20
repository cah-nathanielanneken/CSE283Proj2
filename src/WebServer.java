import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

final class HttpRequest implements Runnable {
	final static int BUF_SIZE = 1024000;
	final static String CRLF = "\r\n";

	byte[] buffer;
	Socket socket;

	// Constructor
	public HttpRequest(Socket socket) throws Exception {
		this.socket = socket;
		buffer = new byte[BUF_SIZE];
	}

	// Implement the run() method of the Runnable interface.
	public void run() {
		try {
			processRequest();
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	private int getContent() throws Exception {
		int total = 0, rcv = 0;

		while (rcv != -1) {
			rcv = socket.getInputStream().read(buffer, total,
					BUF_SIZE - total - 1);
			String msg = new String(buffer, total, rcv);
			System.out.println(msg);
			total += rcv;

			// Only loop if it is not a GET message and have not reached
			// end of POST message, Upload+CRLF represents end of request
			if (msg.startsWith("GET") || msg.indexOf("Upload" + CRLF) != -1) {
				System.out.println("EXITING");
				break;
			}
		}
		// returns the total bytes in the buffer
		return total;
	}

	private void processRequest() throws Exception {

		int total = getContent();

		// Get a reference to the socket's input and output streams.
		// InputStream is = socket.getInputStream();
		InputStream is = new ByteArrayInputStream(buffer, 0, total);
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());

		// Set up input stream filters.
		// BufferedReader br = new BufferedReader(new InputStreamReader(is));
		BufferedReader br = new BufferedReader(new InputStreamReader(is, "US-ASCII"));

		// Get the request line of the HTTP request message.
		String requestLine = br.readLine();

		// Extract the filename from the request line.
		StringTokenizer tokens = new StringTokenizer(requestLine);

		String method = tokens.nextToken();

		if (method.equals("GET")) {
			String fileName = tokens.nextToken();

			// Prepend a "." so that file request is within the current
			// directory.
			fileName = "." + fileName;

			// Open the requested file.
			FileInputStream fis = null;
			boolean fileExists = true;
			try {
				fis = new FileInputStream(fileName);
			} catch (FileNotFoundException e) {
				fileExists = false;
			}

			// Construct the response message.
			String statusLine = null;
			String contentTypeLine = null;
			String entityBody = null;
			if (fileExists) {
				statusLine = "HTTP/1.0 200 OK" + CRLF;
				contentTypeLine = "Content-Type: " + contentType(fileName)
						+ CRLF;
			} else {
				statusLine = "HTTP/1.0 404 Not Found" + CRLF;
				contentTypeLine = "Content-Type: text/html" + CRLF;
				entityBody = "<HTML>" + "<HEAD><TITLE>Not Found</TITLE></HEAD>"
						+ "<BODY>Not Found</BODY></HTML>";
			}
			// Send the status line.
			os.writeBytes(statusLine);

			// Send the content type line.
			os.writeBytes(contentTypeLine);

			// Send a blank line to indicate the end of the header lines.
			os.writeBytes(CRLF);

			// Send the entity body.
			if (fileExists) {
				sendBytes(fis, os);
				fis.close();
			} else {
				os.writeBytes(entityBody);
			}
		} else if (method.trim().equals("POST")) {
			String contentLengthLine = br.readLine();
			String dataLines = br.readLine();
			while (!dataLines.startsWith("------WebKitFormBoundary")) {
				dataLines = br.readLine();
			}
			dataLines = br.readLine();
			while (!dataLines.startsWith("------WebKitFormBoundary")) {
				dataLines = br.readLine();
			}
			int footerLength = dataLines.length() + 2;
			String fileName = br.readLine(), temp = "";
			footerLength += fileName.length() + 2;
			while (!fileName.startsWith("------WebKitFormBoundary")) {
				temp = fileName;
				fileName = br.readLine();
				footerLength += fileName.length() + 2;
			}
			while ((fileName = br.readLine()) != null) {
				footerLength += fileName.length() + 2;
			}
			footerLength += 2;
			is = new ByteArrayInputStream(buffer, 0, total);
			br = new BufferedReader(new InputStreamReader(is, "US-ASCII"));

			contentLengthLine = br.readLine();
			int headerLength = contentLengthLine.length() + 2;
			while (!(contentLengthLine = br.readLine()).equals("")) {
				headerLength += contentLengthLine.length() + 2;
			}
			while (!contentLengthLine.startsWith("Content-Type")) {
				contentLengthLine = br.readLine();
				headerLength += contentLengthLine.length() + 2;
			}
			contentLengthLine = br.readLine();
			headerLength += contentLengthLine.length() + 4;

			is = new ByteArrayInputStream(buffer, 0, total);

			FileOutputStream fos = new FileOutputStream("./"+temp);
			byte[] data = new byte[1024];

			int totalCount = 0, count = 0, bytes = 0;

			while ((bytes = is.read(data)) != -1) {
				totalCount += bytes;
			}

			bytes = 0;
			data = new byte[1024];
			is = new ByteArrayInputStream(buffer, 0, total);
			is.skip(headerLength);

			while (count < (totalCount - headerLength - footerLength)
					&& (bytes = is.read(data)) != -1) {
				if (bytes < (totalCount - headerLength - footerLength) - count) {
					fos.write(data, 0, bytes);
				} else {
					fos.write(data, 0,
							(totalCount - headerLength - footerLength) - count);
				}
				count += bytes;
			}

			fos.flush();
			fos.close();

			Socket socket = new Socket("127.0.0.1", 5000);
			System.out.println("Connected to server...sending file");

			OutputStream out = socket.getOutputStream();

			FileInputStream fileInputStream = new FileInputStream("./"+temp);
			byte[] byteBuffer = new byte[1024];

			int bytesRcvd = 0; // Bytes received in last read
			
			out.write((temp+".zip").getBytes());
			out.write(-2);
			out.flush();
			while ((bytesRcvd = fileInputStream.read(byteBuffer)) != -1) {

				out.write(byteBuffer, 0, bytesRcvd);
				out.flush();

			}
			
			out.close();
			socket.close();

			String statusLine = "HTTP/1.0 200 OK" + CRLF;
			String contentTypeLine = "Content-Type: text/html" + CRLF;
			String entityBody = "<HTML>"
					+ "<HEAD><TITLE>File uploaded successfully</TITLE></HEAD>"
					+ "<BODY>File uploaded successfully</BODY></HTML>";

			// Send the status line.
			os.writeBytes(statusLine);

			// Send the content type line.
			os.writeBytes(contentTypeLine);

			// Send a blank line to indicate the end of the header lines.
			os.writeBytes(CRLF);

			os.writeBytes(entityBody);
		}

		// Close streams and socket.
		os.close();
		br.close();
		socket.close();
	}

	private static void sendBytes(FileInputStream fis, OutputStream os)
			throws Exception {
		// Construct a 1K buffer to hold bytes on their way to the socket.
		byte[] buffer = new byte[1024];
		int bytes = 0;

		// Copy requested file into the socket's output stream.
		while ((bytes = fis.read(buffer)) != -1) {
			os.write(buffer, 0, bytes);
		}
	}

	private static String contentType(String fileName) {
		if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
			return "text/html";
		}
		if (fileName.endsWith(".png")) {
			return "image/png";
		}
		if (fileName.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (fileName.endsWith(".zip")) {
			return "application/zip";
		}
		if (fileName.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		return "application/octet-stream";
	}
}

public final class WebServer {
	public static void main(String argv[]) throws Exception {
		// Get the port number from the command line.
		int port = Integer.parseInt(argv[0]);

		// Establish the listen socket.
		ServerSocket socket = new ServerSocket(port);

		// Process HTTP service requests in an infinite loop.
		while (true) {
			// Listen for a TCP connection request.
			Socket connection = socket.accept();

			// Construct an object to process the HTTP request message.
			HttpRequest request = new HttpRequest(connection);

			// Create a new thread to process the request.
			Thread thread = new Thread(request);

			// Start the thread.
			thread.start();
		}
	}
}
