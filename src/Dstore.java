import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.util.ArrayList;
import static java.util.Arrays.copyOfRange;

class ConnectedThread extends Thread{
	private Socket client;
	private String path = Dstore.getPath();
	private int port = -1;
	public ConnectedThread(Socket client){
		this.client = client;
	}

	@Override
	public void run(){
		try{
			InputStream in = client.getInputStream();
			OutputStream out = client.getOutputStream();
			PrintWriter outPW = new PrintWriter(client.getOutputStream());
			BufferedReader inBR = new BufferedReader(new InputStreamReader(in));
			String line;
			while((line = inBR.readLine()) != null) {
				String[] lines = line.split(" ");
				byte[] bytes;
				if (lines[0].equals("STORE")) {
					System.out.println("Storing " + lines[1]);
					File outputFile = new File(path + File.separator + lines[1]);
					FileOutputStream output = new FileOutputStream(outputFile);
					int size = Integer.parseInt(lines[2]);
					outPW.println("ACK");
					outPW.flush();
					System.out.println("ACK sent");
					client.setSoTimeout(Controller.getTimeout());
					bytes = in.readNBytes(size);
					output.write(bytes);
					output.close();
					Dstore.sendStoreAck(lines[1]);
				} else if (lines[0].equals("LOAD_DATA")) {
					System.out.println("Loading " + lines[1]);
					File inputFile = new File(path + File.separator + lines[1]);
					FileInputStream input = new FileInputStream(inputFile);
					bytes = input.readNBytes((int) inputFile.length());
					out.write(bytes);
				} else if (lines[0].equals("REMOVE")) {
					System.out.println("Removing " + lines[1]);
					File inputFile = new File(path + File.separator + lines[1]);
					inputFile.delete();
					Dstore.sendRemoveAck(lines[1]);
				} else if (lines[0].equals("LIST")) {
					System.out.println("Listing");
					File inputFile = new File(path);
					File[] files = inputFile.listFiles();
					String msg = "LIST";
					for (File file : files) {
						msg += " " + file.getName();
					}
					outPW.println(msg);
					outPW.flush();
				} else if (lines[0].equals("REBALANCE")) {
					int count = 1;
					for (int i = 0; i < Integer.parseInt(lines[1]); i++) {
						count += 2;
						count += Integer.parseInt(lines[count]);
					}
					move(copyOfRange(lines, 1, count + 1));
					remove(copyOfRange(lines, count + 1, lines.length));
					outPW.println("REBALANCE_COMPLETE");
					outPW.flush();

				} else if (lines[0].equals("REBALANCE_STORE")) {
					System.out.println("fileName " + lines[1]);
					File outputFile = new File(path + File.separator + lines[1]);
					FileOutputStream output = new FileOutputStream(outputFile);
					int size = Integer.parseInt(lines[2]);
					outPW.println("ACK");
					outPW.flush();
					System.out.println("ACK sent");
					bytes = in.readNBytes(size);
					output.write(bytes);
				} else {
					System.out.println("unrecognised command");
				}
			}
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public void setPort(int port){
		this.port = port;
	}

	public void move(String[] items){
		int counter = 0;
		for(int i = 0; i < Integer.parseInt(items[0]); i++){
			counter ++;
			File inputFile = new File(path + File.separator + items[counter]);
			counter ++;
			int counter2 = counter;
			for(int j = 0; j < Integer.parseInt(items[counter]); j++){
				counter2++;
				sendRebalance(inputFile, Integer.parseInt(items[counter2]));
			}
			counter = counter2;
		}
	}

	public void sendRebalance(File file, int port){
		try {
			Socket socket = new Socket(InetAddress.getLocalHost(), port);
			PrintWriter outPW = new PrintWriter(socket.getOutputStream());
			OutputStream out = socket.getOutputStream();
			BufferedReader inBR = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			byte[] bytes;
			outPW.println("REBALANCE_STORE " + file.getName() + " " + file.length());
			outPW.flush();
			String line;
			while((line = inBR.readLine()) != null){
				if(line.equals("ACK")){
					FileInputStream input = new FileInputStream(file);
					bytes = input.readNBytes((int)file.length());
					out.write(bytes);
					break;
				}
			}
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public void remove(String[] items){
		System.out.println("Remove in progress");
		for(int i = 1 ; i< Integer.parseInt(items[0]) + 1; i++){
			File inputFile = new File(path + File.separator + items[i]);
			inputFile.delete();
		}
		System.out.println("Remove complete");
	}

	public void removeAck(String filename){
		try {
			PrintWriter outPW2 = new PrintWriter(client.getOutputStream());
			outPW2.println("REMOVE_ACK " + filename);
			outPW2.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public void storeAck(String filename){
		try {
			PrintWriter outPW2 = new PrintWriter(client.getOutputStream());
			outPW2.println("STORE_ACK " + filename);
			outPW2.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public int getPort(){
		return port;
	}
}


class Dstore {
	private static int cPort;
	private static int port;
	private static int timeOut;
	private static String path;
	private static ArrayList<ConnectedThread> threads = new ArrayList<>();
	public static void main(String [] args){
		setup(args);
		join();
		try{
			ServerSocket ss = new ServerSocket(port);
			for(;;){
					try{
						System.out.println("waiting for connection");
						ConnectedThread thread = new ConnectedThread(ss.accept());
						System.out.println("connected");
						thread.start();
						threads.add(thread);
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
			ConnectedThread thread = new ConnectedThread(controller);
			thread.start();
			thread.setPort(cPort);
			threads.add(thread);
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
	
	public static void deleteFolder(String path) {
		File folder = new File(path);
		if (!folder.exists()) {
			folder.mkdir();
		} else {
			File[] files = folder.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					deleteFolder(file.getName());
				} else {
					file.delete();
				}
			}
		}
	}

	public static void sendRemoveAck(String filename){
		for(ConnectedThread thread : threads){
			if(thread.getPort() == cPort){
				thread.removeAck(filename);
			}
		}
	}

	public static void sendStoreAck(String filename){
		for(ConnectedThread thread : threads){
			if(thread.getPort() == cPort){
				thread.storeAck(filename);
			}
		}
	}

	public static int getCPort(){
		return cPort;
	}

	public static String getPath(){
		return path;
	}
}
