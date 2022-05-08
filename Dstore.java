import java.io.*;
import java.net.*;

class Dstore {
	public static int cPort;
	public static int port;
	public static int timeOut;
	public static String path;
	public static void main(String [] args) throws IOException{
		setup(args);
		join();
		try{
			ServerSocket ss = new ServerSocket(port);
			for(;;){
					try{
						System.out.println("waiting for connection");
						final Socket client = ss.accept();
						System.out.println("connected");
						new Thread(new Runnable(){
						public void run(){
							try{
								InputStream in = client.getInputStream();
								OutputStream out = client.getOutputStream();
								PrintWriter outPW = new PrintWriter(client.getOutputStream());
								BufferedReader inBR = new BufferedReader(new InputStreamReader(in));
								String [] line = inBR.readLine().split(" ");
								byte [] buf = new byte[1000];
								int buflen;
								if(line[0].equals("STORE")){
									System.out.println("fileName "+line[1]);
									File outputFile = new File(path + File.separator+line[1]);
									FileOutputStream output = new FileOutputStream(outputFile);
									outPW.println("ACK");
									outPW.flush();
									System.out.println("ACK sent");
									while ((buflen=in.read(buf)) != -1){
										output.write(buf,0,buflen);
									} 
									client.close();
								} 
								else if(line[0].equals("LOAD_DATA")){
									System.out.println("Loading "+line[1]);
									File inputFile = new File(path+File.separator+line[1]);
									FileInputStream input = new FileInputStream(inputFile);
									while ((buflen=input.read(buf)) != -1){
										out.write(buf,0,buflen);
									}
									out.close();
								} 
								else if(line[0].equals("REMOVE")){
									System.out.println("Removing "+line[1]);
									File inputFile = new File(path+File.separator+line[1]);
									inputFile.delete();
									outPW.println("REMOVE_ACK " + inputFile.getName());
									outPW.flush();
								}
								else{
									System.out.println("unrecognised command");
									client.close();
								}
							}
							catch(Exception e){
								System.out.println("error " + e);
							}
							}}).start();
					}
					catch(Exception e){
						System.out.println("error " + e);
					}
			}
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}
	
	public static void join(){
		try{
			Socket controller = new Socket(InetAddress.getLocalHost(),cPort);
			PrintWriter out = new PrintWriter(controller.getOutputStream());
			out.println("JOIN "+port);
			out.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}
	
	public static void setup(String [] args){
		port = Integer.parseInt(args[0]);
		cPort = Integer.parseInt(args[1]);
		timeOut = Integer.parseInt(args[2]);
		path = args[3];
		deleteFolder(path);
	}
	
	public static void deleteFolder(String path){
		File folder = new File(path);
		if(!folder.exists()){
			folder.mkdir();
		}
		else{
			File [] files = folder.listFiles();
			for(File file : files){
				if(file.isDirectory()){
					deleteFolder(file.getName());
				}
				else{
					file.delete();
				}
			}
		}
	}
}
