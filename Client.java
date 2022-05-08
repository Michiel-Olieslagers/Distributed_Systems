import java.io.*;
import java.net.*;
import static java.util.Arrays.copyOfRange;

class Client {
	public static int cPort;
	public static int timeOut;

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
			File file;
			FileInputStream fileStrm;
			do {
				System.out.println("\n1: Store file\n2: Load file\n3: Remove file\n4: List file\n5: Quit");
				str = obj.readLine();
				if(str.equals("1")){
					System.out.println("Please enter the desired filename");
					str = obj.readLine();
					socket = new Socket(InetAddress.getLocalHost(),cPort);
					in = socket.getInputStream();
					out = socket.getOutputStream();
					outPW = new PrintWriter(out);
					inBR = new BufferedReader(new InputStreamReader(in));
					file = new File(str);
					fileStrm = new FileInputStream(file);
					msg = "STORE "+file.getName()+" 100";
					outPW.println(msg);
					outPW.flush();
					while((line = inBR.readLine()) == null){
						System.out.println("test");
					}
					lines = line.split(" ");
					if(lines[0].equals("STORE_TO")){
						for(String port : copyOfRange(lines,1,lines.length)){
							socket = new Socket(InetAddress.getLocalHost(),Integer.parseInt(port));
							out = socket.getOutputStream();
							outPW = new PrintWriter(out);
							in = socket.getInputStream();
							inBR = new BufferedReader(new InputStreamReader(in));
							msg = "STORE "+file.getName()+" 100";
							outPW.println(msg);
							outPW.flush();
							while((line = inBR.readLine()) == null){
							}
							if(line.equals("ACK")){
								byte [] buf = new byte[1000];
								int buflen;
								while((buflen = fileStrm.read(buf)) != -1){
									out.write(buf,0,buflen);
								}
							}
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
					while((line = inBR.readLine()) == null){
					}
					lines = line.split(" ");
					if(lines[0].equals("LOAD_FROM")){
						socket = new Socket(InetAddress.getLocalHost(),Integer.parseInt(lines[1]));
						out = socket.getOutputStream();
						outPW = new PrintWriter(out);
						in = socket.getInputStream();
						inBR = new BufferedReader(new InputStreamReader(in));
						FileOutputStream output = new FileOutputStream(file);
						msg = "LOAD_DATA test.txt";
						outPW.println(msg);
						outPW.flush();
						while((line = inBR.readLine()) == null){
						}
						output.write(line.getBytes());
						while ((line=inBR.readLine()) != null){
							output.write(line.getBytes());
						} 
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
					while((line = inBR.readLine()) == null){
					}
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
