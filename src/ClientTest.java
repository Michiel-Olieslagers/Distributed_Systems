import java.io.*;
import java.net.*;
import static java.util.Arrays.copyOfRange;

class ClientTest {
	public static int cPort;
	public static int timeOut;
	public static File file;
	public static FileInputStream fileStrm;

	public static void main(String [] args){
		setup(args);
		try{
			Socket socket;
			InputStream in;
			OutputStream out;
			PrintWriter outPW;
			BufferedReader inBR;
			BufferedReader obj = new BufferedReader(new InputStreamReader(System.in));
			String str;
			String msg;
			String line;
			String [] lines;
			do {
				System.out.println("\n1: Store file\n2: Load file\n3: Remove file\n4: List file\n5: Quit");
				str = obj.readLine();
				Socket controller = new Socket(InetAddress.getLocalHost(),cPort);
				if(str.equals("1")){
					System.out.println("Please enter the desired filename");
					str = obj.readLine();
					in = controller.getInputStream();
					out = controller.getOutputStream();
					outPW = new PrintWriter(out);
					inBR = new BufferedReader(new InputStreamReader(in));
					file = new File(str);
					fileStrm = new FileInputStream(file);
					msg = "STORE "+file.getName()+" "+file.length();
					outPW.println(msg);
					outPW.flush();
					line = inBR.readLine();
					lines = line.split(" ");
					if(lines[0].equals("STORE_TO")){
						for(String port : copyOfRange(lines,1,lines.length)){
							System.out.println(port);
							new Thread(new Runnable() {
								@Override
								public void run() {
									try {
										Socket socket2 = new Socket(InetAddress.getLocalHost(), Integer.parseInt(port));
										OutputStream out2 = socket2.getOutputStream();
										PrintWriter outPW2 = new PrintWriter(out2);
										InputStream in2 = socket2.getInputStream();
										BufferedReader inBR2 = new BufferedReader(new InputStreamReader(in2));
										String msg = "STORE " + file.getName() + " " + file.length();
										String line;
										outPW2.println(msg);
										outPW2.flush();
										System.out.println(msg);
										line = inBR2.readLine();
										System.out.println(line);
										if (line.equals("ACK")) {
											FileInputStream input = new FileInputStream(file);
											byte[] bytes = input.readNBytes((int) file.length());
											out2.write(bytes);
										}
										socket2.close();
									}
									catch(Exception e){
										System.out.println("error " + e);
									}
								}
							}).start();
						}
						line = inBR.readLine();
						if (line.equals("STORE_COMPLETE")) {
							System.out.println("Store complete");
						}

					}
				}
				else if(str.equals("2")){
					System.out.println("Please enter the desired filename");
					str = obj.readLine();
					socket = new Socket(InetAddress.getLocalHost(),cPort);
					out = socket.getOutputStream();
					outPW = new PrintWriter(out);
					in = socket.getInputStream();
					inBR = new BufferedReader(new InputStreamReader(in));;
					file = new File(str);
					msg = "LOAD "+file.getName();
					outPW.println(msg);
					outPW.flush();
					line = inBR.readLine();
					lines = line.split(" ");
					if(lines[0].equals("LOAD_FROM")){
						socket = new Socket(InetAddress.getLocalHost(),Integer.parseInt(lines[1]));
						out = socket.getOutputStream();
						outPW = new PrintWriter(out);
						in = socket.getInputStream();
						inBR = new BufferedReader(new InputStreamReader(in));
						FileOutputStream output = new FileOutputStream(file);
						int size = Integer.parseInt(lines[2]);
						msg = "LOAD_DATA test.txt";
						outPW.println(msg);
						outPW.flush();
						output.write(in.readNBytes(size));
					}
				}
				else if(str.equals("3")){
					System.out.println("Please enter the desired filename");
					str = obj.readLine();
					file = new File(str);
					socket = new Socket(InetAddress.getLocalHost(),cPort);
					out = socket.getOutputStream();
					outPW = new PrintWriter(out);
					in = socket.getInputStream();
					inBR = new BufferedReader(new InputStreamReader(in));;
					msg = "REMOVE " + file.getName();
					outPW.println(msg);
					outPW.flush();
					line = inBR.readLine();
					System.out.println(line);
				}
				else if(str.equals("4")){
					socket = new Socket(InetAddress.getLocalHost(),cPort);
					out = socket.getOutputStream();
					outPW = new PrintWriter(out);
					in = socket.getInputStream();
					inBR = new BufferedReader(new InputStreamReader(in));;
					msg = "LIST";
					outPW.println(msg);
					outPW.flush();
					while((line = inBR.readLine()) == null){
					}
					lines = line.split(" ");
					for(String fileName : copyOfRange(lines,1,lines.length)){
						System.out.println(fileName);
					}
				}
				else if(str.equals("5")){
				}
				else{
					System.out.println("Please enter one of the five options");
				}
			} while (!str.equals("5"));
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public static void setup(String [] args){
		cPort = Integer.parseInt(args[0]);
		timeOut = Integer.parseInt(args[1]);
	}
}
