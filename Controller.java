import java.io.*;
import java.net.*;
import static java.util.Arrays.copyOfRange;
import java.util.ArrayList;

class FileStore{
	private int port;
	private ArrayList<String> fileNames;
	private ArrayList<Integer> fileSizes;
	public FileStore(int port){
		this.port = port;
		this.fileNames = new ArrayList<String>();
		this.fileSizes = new ArrayList<Integer>();
	}
	
	public int getPort(){
		return this.port;
	}
	
	public ArrayList<String> getFiles(){
		return fileNames;
	}
	
	public int getFile(String filename){
		for(int i = 0; i < fileNames.size(); i++){
			if(fileNames.get(i).equals(filename)){
				return fileSizes.get(i);
			}
		}
		return -1;
	}
	
	public void addFile(String fileName, int fileSize){
		fileNames.add(fileName);
		fileSizes.add(fileSize);
	}
}


class Controller {
	public static ArrayList<FileStore> dStores = new ArrayList<FileStore>();
	public static String index = "";
	public static ServerSocket ss;
	public static Socket client;
	public static int counter;
	public static int repFac;
	public static int port;
	public static int timeOut;
	public static int rPeriod;
	public static void main(String [] args) throws IOException{
		setup(args);
		try{
			ss = new ServerSocket(port);
			for(;;){
					try{
						System.out.println("waiting for connection");
						client = ss.accept();
						new Thread(new Runnable(){public void run(){
							System.out.println("connected");
							try{
								BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
								PrintWriter out = new PrintWriter(client.getOutputStream());
								String line;
								while((line = in.readLine()) != null){
									String [] cmd = line.split(" ");
									if(cmd[0].equals("STORE")){
										System.out.println("Store in progress");
										index = "store in progress";
										send(cmd[1],Integer.parseInt(cmd[2]));
									}
									else if(cmd[0].equals("LOAD")){
										System.out.println("Loading file from system");
										counter = 0;
										retrieve(cmd[1]);
									}
									else if(cmd[0].equals("JOIN")){
										System.out.println("Adding dstore to system");
										add(Integer.parseInt(cmd[1]));
										System.out.println("Dstore added");
									}
									else if(cmd[0].equals("LIST")){
										System.out.println("Listing stored files");
										list();
									}
									else if(cmd[0].equals("REMOVE")){
										System.out.println("Remove in progress");
										index = "remove in progress";
										delete(cmd[1]);
										index = "remove complete";
										out.println("REMOVE_COMPLETE");
										out.flush();
										
									}
									else{
										System.out.println("error : command not recognised");
									}
								}
								client.close();
								System.out.println("socket closed");
							}
							catch (Exception e){
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
	
	public static void send(String filename, int filesize) throws IOException{
		String msg = "STORE_TO";
		for(FileStore dStore : dStores){
			dStore.addFile(filename,filesize);
			msg = msg + " " + dStore.getPort();
		}
		try{
			PrintWriter out = new PrintWriter(client.getOutputStream());
			out.println(msg);
			out.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}
	
	public static void retrieve(String fileName) throws IOException{
		try{
			FileStore fileStore;
			int size;
			while((size = (fileStore = dStores.get(counter)).getFile(fileName)) == -1){
				counter ++;
			}
			PrintWriter out = new PrintWriter(client.getOutputStream());
			out.println("LOAD_FROM "+fileStore.getPort()+" "+Integer.toString(size));
			out.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
		System.out.println();
	}
	
	public static void list(){
		ArrayList<String> list = new ArrayList<String>();
		for(FileStore dStore : dStores){
			for(String fileName : dStore.getFiles()){
				if(!list.contains(fileName)){
					list.add(fileName);
				}
			}
		}
		String msg = "LIST";
		for(String file : list){
			msg = msg + " " + file;
		}
		try{
			PrintWriter out = new PrintWriter(client.getOutputStream());
			out.println(msg);
			out.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}
	
	public static void delete(String fileName) throws IOException{
		//inal int count = 0;
		try{
			for(FileStore dStore : dStores){
				if(dStore.getFile(fileName) != -1){
					new Thread(new Runnable(){public void run(){
						try{
							Socket socket = new Socket(InetAddress.getLocalHost(),dStore.getPort());
							PrintWriter out = new PrintWriter(socket.getOutputStream());
							BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
							out.println("REMOVE "+fileName);
							out.flush();
							String line;
							while((line = in.readLine()) == null){
							}
							String [] lines = line.split(" ");
							if(lines[0].equals("REMOVE_ACK") && lines[1].equals(fileName)){
								//count ++;
								dStores.remove(dStore);
							}
						}
						catch(Exception e){
							System.out.println("error "+e);
						}
					}}).start();
				}
			}
			Thread.sleep(timeOut);
		}
		catch(Exception e){
			System.out.println("error "+ e);
		}
		
	}
	
	public static void add(int port){
		dStores.add(new FileStore(port));
	}
	
	public static void setup(String [] args){
		port = Integer.parseInt(args[0]);
		repFac = Integer.parseInt(args[1]);
		timeOut = Integer.parseInt(args[2]);
		rPeriod = Integer.parseInt(args[3]);
	}
}
