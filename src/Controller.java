import java.io.*;
import java.net.*;
import static java.util.Arrays.copyOfRange;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

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

	public ArrayList<Integer> getSizes(){
		return fileSizes;
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

class ClientThread extends Thread{
	private ArrayList<FileStore> dStores;
	private Socket client;
	private int repFactor = Controller.getRepFac();

	public ClientThread(Socket client, ArrayList<FileStore> dStores){
		this.client = client;
		this.dStores = dStores;
	}

	@Override
	public void run(){
		System.out.println("connected");
		try{
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			PrintWriter out = new PrintWriter(client.getOutputStream());
			String line;
			while(in != null && (line = in.readLine()) != null) {
				String[] cmd = line.split(" ");
				System.out.println("Client: " + line + " " + client.getPort());
				if (cmd[0].equals("STORE")) {
					if(dStores.size() >= repFactor) {
						System.out.println("Store in progress");
						boolean bool = true;
						for (Index ind : Controller.getIndexes()) {
							if (ind.getFilename().equals(cmd[1]) && !ind.getStatus().equals("remove in progress") && !ind.getStatus().equals("store complete") && !ind.getStatus().equals("store in progress")) {
								bool = false;
							}
						}
						if (bool) {
							Index index = new Index(cmd[1]);
							Controller.addIndex(index);
							index.setStoreInProgress(this);
							send(cmd[1], Integer.parseInt(cmd[2]));
						} else {
							System.out.println("ERROR_FILE_ALREADY_EXISTS");
							out.println("ERROR_FILE_ALREADY_EXISTS");
							out.flush();
						}
					}
					else {
						System.out.println("ERROR_NOT_ENOUGH_DSTORES " + dStores.size() + " : " + repFactor);
						out.println("ERROR_NOT_ENOUGH_DSTORES");
						out.flush();
					}
				} else if (cmd[0].equals("LOAD")) {
					if(dStores.size() >= repFactor) {
						boolean bool = false;
						for (Index ind : Controller.getIndexes()) {
							if (ind.getFilename().equals(cmd[1]) && !ind.getStatus().equals("remove in progress") && !ind.getStatus().equals("store in progress") && !ind.getStatus().equals("remove complete")) {
								bool = true;
							}
						}
						if (bool) {
							System.out.println("Loading file from system");
							retrieve(cmd[1]);
						} else {
							out.println("ERROR_FILE_DOES_NOT_EXIST");
							out.flush();
						}
					}
					else {
						out.println("ERROR_NOT_ENOUGH_DSTORES");
						out.flush();
					}
				} else if (cmd[0].equals("JOIN")) {
					System.out.println("Adding dstore to system");
					Controller.addDStore(client, Integer.parseInt(cmd[1]), this);
					break;
				} else if (cmd[0].equals("LIST")) {

					if(dStores.size() >= repFactor) {
						System.out.println("Listing stored files");
						list();
					}
					else {
						out.println("ERROR_NOT_ENOUGH_DSTORES");
						out.flush();
					}
				} else if (cmd[0].equals("REMOVE")) {
					if (dStores.size() >= repFactor) {
						Index index = null;
						for (Index ind : Controller.getIndexes()) {
							if (ind.getFilename().equals(cmd[1]) && !ind.getStatus().equals("remove in progress") && !ind.getStatus().equals("remove complete") && !ind.getStatus().equals("store in progress")) {
								index = ind;
							}
						}
						if (index != null) {
							System.out.println("Remove in progress");
							delete(cmd[1]);
							index.setRemoveInProgress(this);
						}
						else{
							out.println("ERROR_FILE_DOES_NOT_EXIST");
							out.flush();
						}
					}
					else {
						out.println("ERROR_NOT_ENOUGH_DSTORES");
						out.flush();
					}
				} else {
					System.out.println("error : command not recognised");
				}
			}
			System.out.println("socket closed");
		}
		catch (Exception e){
			System.out.println("error " + e);
		}
	}

	public ArrayList<FileStore> getDstores(String fileName){
		ArrayList<FileStore> stores = new ArrayList<>();
		for(FileStore dStore : dStores){
			if(dStore.getFile(fileName) != -1){
				stores.add(dStore);
			}
		}
		return stores;
	}


	public void send(String filename, int filesize) throws IOException{
		String msg = "STORE_TO";
		Collections.sort(dStores, new Comparator<FileStore>() {
			@Override
			public int compare(FileStore first, FileStore second) {
				return first.getFiles().size() < second.getFiles().size() ? -1 : (first.getFiles().size() > second.getFiles().size()) ? 1 : 0;
			}
		});

		for(int i = 0; i < repFactor; i ++){
			dStores.get(i).addFile(filename,filesize);
			msg = msg + " " + dStores.get(i).getPort();
		}
		System.out.println(msg);
		try{
			PrintWriter out = new PrintWriter(client.getOutputStream());
			out.println(msg);
			out.flush();
		}
		catch(Exception e) {
			System.out.println("error " + e);
		}

	}

	public void retrieve(String fileName) throws IOException{
		try{
			Index index = null;
			for(Index ind : Controller.getIndexes()){
				if(ind.getFilename().equals(fileName)){
					index = ind;
				}
			}
			FileStore fileStore = index.getStore();
			PrintWriter out = new PrintWriter(client.getOutputStream());
			if(fileStore != null) {
				out.println("LOAD_FROM " + fileStore.getPort() + " " + fileStore.getFile(index.getFilename()));
			}
			else{
				out.println("ERROR_LOAD");
			}
			out.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
		System.out.println();
	}

	public void list(){
		ArrayList<String> list = Controller.getList();
		String msg = "LIST";
		for(String file : list){
			msg = msg + " " + file;
		}
		System.out.println(msg);
		try{
			PrintWriter out = new PrintWriter(client.getOutputStream());
			out.println(msg);
			out.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public void delete(String fileName) throws IOException{
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
							System.out.println("REMOVE "+fileName);
							String line = in.readLine();
							System.out.println(line);
							String [] lines = line.split(" ");
							if(lines[0].equals("REMOVE_ACK") && lines[1].equals(fileName)){
								dStores.remove(dStore);
							}
						}
						catch(Exception e){
							System.out.println("error "+e);
						}
					}}).start();
				}
			}
		}
		catch(Exception e){
			System.out.println("error "+ e);
		}

	}

	public void sendStoreComplete(){
		try{
			PrintWriter outPW = new PrintWriter(client.getOutputStream());
			outPW.println("STORE_COMPLETE");
			outPW.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
	}

	public void sendRemoveComplete(){
		try{
			PrintWriter outPW = new PrintWriter(client.getOutputStream());
			outPW.println("REMOVE_COMPLETE");
			outPW.flush();
		}
		catch(Exception e){
			System.out.println("error " + e);
			System.out.println("Test");
		}
	}

	public Socket getClient(){
		return client;
	}
}

class DStoreThread extends Thread{
	private Socket client;
	private int port;
	private OutputStream out;
	private InputStream in;
	private PrintWriter outPW;
	private BufferedReader inBR;
	private String response = null;

	public DStoreThread(Socket client, int port){
		this.client = client;
		this.port = port;
		try {
			this.out = client.getOutputStream();
			this.outPW = new PrintWriter(this.out);
			this.in = client.getInputStream();
			this.inBR = new BufferedReader(new InputStreamReader(this.in));
		}
		catch(Exception e){
			System.out.println("error " + e);
		}

	}

	@Override
	public void run(){
		System.out.println("connected");
		try{
			String line;
			while((line = inBR.readLine()) != null) {
				System.out.println("DStore: "+line+" "+port);
				String[] lines = line.split(" ");
				if(lines[0].equals("LIST")){
					response = line;
				}
				else if(lines[0].equals("REMOVE_ACK")){
					Index changed = null;
					for(Index index : Controller.getIndexes()){
						if(index.getFilename().equals(lines[1]) && index.getStatus().equals("remove in progress")){
							changed = index;
							index.reduceRemove();
						}
					}
					if(changed.getStatus().equals("remove complete")){
						Controller.removeIndex(changed);
					}
				}
				else if(lines[0].equals("STORE_ACK")){
					for(Index index : Controller.getIndexes()){
						if(index.getFilename().equals(lines[1]) && index.getStatus().equals("store in progress")){
							index.reduceStore();
						}
					}
				}
			}
			Controller.removeDStore(this);
		}
		catch (Exception e){
			System.out.println("error " + e);
		}
	}

	public ArrayList<String> getFiles(){
		ArrayList<String> list = new ArrayList<String>();
		response = null;
		try{
			outPW.println("LIST");
			outPW.flush();
			while(response == null) {
				System.out.print("");
			}
			String[] lines = response.split(" ");
			if (lines[0].equals("LIST")) {
				for (String file : copyOfRange(lines, 1, lines.length)) {
					list.add(file);
				}
				return list;
			}
		}
		catch(Exception e){
			System.out.println("error " + e);
		}
		return null;
	}

	public int getPort(){
		return port;
	}

	public void sendRebalance(String msg){
		try{
			outPW.println(msg);
			outPW.flush();
		}
		catch(Exception e){

		}
	}

	public Socket getClient(){
		return client;
	}
}

class Index {
	private AtomicReference<ClientThread> storeClient = new AtomicReference<>();
	private AtomicReference<ClientThread> removeClient = new AtomicReference<>();
	private final String filename;
	private AtomicReference<String> status = new AtomicReference<>("");
	private AtomicInteger storeCount;
	private AtomicInteger removeCount;
	private int counter;
	public Index(String filename){
		this.filename = filename;
		counter = 0;
	}

	public void setRemoveInProgress(ClientThread client){
		this.status.set("remove in progress");
		removeCount = new AtomicInteger(Controller.getRepFac());
		removeClient.set(client);
		Index current = this;
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(Controller.getTimeout());
					if (!status.get().equals("store complete")) {
						Controller.removeIndex(current);
					}
				}
				catch(Exception e){
					System.out.println("error " + e);
				}
			}
		}).start();
	}

	public void setStoreInProgress(ClientThread client){
		System.out.println(Controller.getDstores().size());
		this.status.set("store in progress");
		storeCount = new AtomicInteger(Controller.getRepFac());
		storeClient.set(client);
		Index current = this;
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(Controller.getTimeout());
					if (!status.get().equals("store complete")) {
						Controller.removeIndex(current);
					}
				}
				catch(Exception e){
					System.out.println("error " + e);
				}
			}
		}).start();
	}

	public String getStatus(){
		return status.get();
	}

	public String getFilename(){
		return filename;
	}

	public FileStore getStore(){
		FileStore store = null;
		ArrayList<FileStore> dStores = Controller.getDstores();
		System.out.println(dStores.size());
		if(counter < dStores.size()){
			store = dStores.get(counter);
			counter++;
		}
		return store;
	}

	public void reduceStore(){
		if(storeCount.decrementAndGet() == 0){
			status.set("store complete");
			storeClient.get().sendStoreComplete();
		}
	}

	public void reduceRemove(){
		if(removeCount.decrementAndGet() == 0){
			status.set("remove complete");
			removeClient.get().sendRemoveComplete();
		}
	}
}

class Controller {
	private static ServerSocket ss;
	private static AtomicReference<ArrayList<Index>> indexes = new AtomicReference<>(new ArrayList<>());
	private static int repFac;
	private static int port;
	private static int timeOut;
	private static int rPeriod;
	private static ArrayList<DStoreThread> dStoreThreads = new ArrayList<>();
	private static ArrayList<ClientThread> clients = new ArrayList<>();
	private static ArrayList<FileStore> dStores = new ArrayList<>();

	private static Object indexLock = new Object();

	public static void main(String [] args){
		setup(args);
		try{
			ss = new ServerSocket(port);
			for(;;){
				try{
					System.out.println("waiting for connection");
					boolean newThread = true;
					Socket client = ss.accept();
					for(ClientThread thr : clients){
						if(thr.getClient() == client){
							newThread = false;
						}
					}
					for(DStoreThread thr : dStoreThreads){
						if(thr.getClient() == client){
							newThread = false;
						}
					}
					if(newThread) {
						ClientThread thread = new ClientThread(client, dStores);
						thread.start();
						clients.add(thread);
					}
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

	public static int getTimeout(){
		return timeOut;
	}

	public static void rebalance(){
		ArrayList<FileStore> current = update();
		for(String fileName : getList()){
			int size = -1;
			int count = repFac;
			for(FileStore dstore : dStores){
				for(String fileName2 : dstore.getFiles()){
					if(fileName2.equals(fileName)){
						size = dstore.getFile(fileName2);
						count --;
					}
				}
			}
			for(int i = 0; i < count; i++){
				for(FileStore dstore : dStores){
					boolean contains = false;
					for(String fileName2 : dstore.getFiles()){
						if(fileName2.equals(fileName)){
							contains = true;
						}
					}
					if(!contains){
						dstore.addFile(fileName, size);
					}
				}
			}
		}

		while(!checkBalanced()) {
			dStores.sort((first, second) -> first.getFiles().size() < second.getFiles().size() ? -1 : (first.getFiles().size() > second.getFiles().size()) ? 1 : 0);
			boolean changed = false;
			int count = 0;
			while(!changed){
				count ++;
				FileStore largest = dStores.get(dStores.size() - count);
				FileStore smallest = dStores.get(0);
				for(int i = 0; (i<largest.getFiles().size() && !changed); i++){
					if(!smallest.getFiles().contains(largest.getFiles().get(i))){
						changed = true;
						smallest.addFile(largest.getFiles().get(i), largest.getSizes().get(i));
						largest.getFiles().remove(i);
						largest.getSizes().remove(i);
					}
				}
			}
		}
		HashMap<Integer, HashMap<String,ArrayList<Integer>>> moves = new HashMap<>();
		HashMap<Integer, HashMap<String,ArrayList<Integer>>> removes = new HashMap<>();
		for(FileStore filestore1 : dStores) {
			for(FileStore filestore2 : current){
				if(filestore1.getPort() == filestore2.getPort()){
					for(String file1: filestore1.getFiles()){
						if(!filestore2.getFiles().contains(file1)){
							boolean cont = true;
							for(int i = 0; (i < current.size() && cont); i++){
								FileStore filestore3 = current.get(i);
								if(filestore3.getFiles().contains(file1)){
									HashMap<String, ArrayList<Integer>> link = moves.get(filestore3.getPort());
									if(link != null){
										ArrayList<Integer> link2 = link.get(file1);
										if(link2 != null){
											link2.add(filestore2.getPort());
										}
										else{
											ArrayList<Integer> list = new ArrayList<>();
											list.add(filestore2.getPort());
											link.put(file1,list);
										}
									}
									else {
										ArrayList<Integer> list = new ArrayList<>();
										list.add(filestore2.getPort());
										HashMap<String, ArrayList<Integer>> map = new HashMap<>();
										map.put(file1,list);
										moves.put(filestore3.getPort(), map);
									}
									cont = false;
								}
							}
						}
					}
					for(String file1: filestore2.getFiles()){
						if(!filestore1.getFiles().contains(file1)){
							HashMap<String, ArrayList<Integer>> link = removes.get(filestore2.getPort());
							if(link != null){
								ArrayList<Integer> link2 = link.get(file1);
								if(link2 != null){
									link2.add(filestore2.getPort());
								}
								else{
									ArrayList<Integer> list = new ArrayList<>();
									list.add(filestore2.getPort());
									link.put(file1,list);
								}
							}
							else {
								ArrayList<Integer> list = new ArrayList<>();
								list.add(filestore2.getPort());
								HashMap<String, ArrayList<Integer>> map = new HashMap<>();
								map.put(file1,list);
								removes.put(filestore2.getPort(), map);
							}
						}
					}
				}
			}
		}
		for(FileStore dstore : current){
			HashMap<String, ArrayList<Integer>> link = moves.get(dstore.getPort());
			String msg;
			if(link != null) {
				msg = "REBALANCE " + link.keySet().size();
				for (String key : link.keySet()) {
					ArrayList<Integer> link2 = link.get(key);
					msg += " " + key + " " + link2.size();
					for (Integer port : link2) {
						msg += " " + port;
					}
				}
			}
			else{
				msg = "REBALANCE 0";
			}
			link = removes.get(dstore.getPort());
			if(link != null) {
				msg += " " + link.keySet().size();
				for (String key : link.keySet()) {
					ArrayList<Integer> link2 = link.get(key);
					msg += " " + key;
				}
			}
			else{
				msg += " 0";
			}
			for (DStoreThread thread : dStoreThreads) {
				if (thread.getPort() == dstore.getPort() && !msg.equals("REBALANCE 0 0")) {
					thread.sendRebalance(msg);
				}
			}
			System.out.println(msg);
		}
		System.out.println("REBALANCE COMPLETE " + dStoreThreads.size() + " " + clients.size());
	}

	public static ArrayList<FileStore> update(){
		ArrayList<FileStore> current = new ArrayList<>();
		try{
			for (DStoreThread thread : dStoreThreads) {
				ArrayList<String> files = thread.getFiles();
				if (files != null) {
					FileStore fs = new FileStore(thread.getPort());
					for (String file : files) {
						fs.addFile(file, -1);
					}
					current.add(fs);
				}
			}
		}
		catch(Exception e){
			System.out.println("error" + e);
		}
		return current;
	}

	public static boolean checkBalanced(){
		int lower, upper,counter = 0, numFiles = 0;
		double val;
		ArrayList<String> files = new ArrayList<>();
		for(FileStore dStore : dStores){
			for(String file : dStore.getFiles()){
				if(!files.contains(file)){
					files.add(file);
				}
			}
		}
		numFiles = files.size();
		val = (double)repFac * numFiles / dStores.size();
		lower = (int)Math.floor(val);
		upper = (int)Math.ceil(val);
		for(FileStore dStore : dStores){
			if(dStore.getFiles().size() == upper || dStore.getFiles().size() == lower){
				counter ++;
			}
		}
		return counter == dStores.size();
	}

	public static void addDStore(Socket dStore, int port, ClientThread thread){
		dStores.add(new FileStore(port));
		clients.remove(thread);
		DStoreThread newThread = new DStoreThread(dStore, port);
		newThread.start();
		dStoreThreads.add(newThread);
		System.out.println(dStores.size());
		if(dStores.size() >= repFac) {
			Controller.rebalance();
		}
		System.out.println("Dstore added");
	}

	public static ArrayList<String> getList(){
		ArrayList<String> list = new ArrayList<>();
		for(FileStore dStore : dStores){
			for(String fileName : dStore.getFiles()){
				boolean contains = false;
				for(String file : list) {
					if (file.equals(fileName)) {
						contains = true;
					}
				}
				if(!contains){
					for (Index ind : indexes.get()) {
						if (!ind.getStatus().equals("store in progress") && ind.getFilename().equals(fileName)) {
							list.add(fileName);
						}
					}
				}
			}
		}
		return list;
	}

	public static int getRepFac(){
		return repFac;
	}

	public static void removeDStore(DStoreThread thread){
		dStores.removeIf(dstore -> dstore.getPort() == thread.getPort());
		dStoreThreads.remove(thread);
		System.out.println(dStoreThreads.size());
	}

	public static void setup(String [] args){
		port = Integer.parseInt(args[0]);
		repFac = Integer.parseInt(args[1]);
		timeOut = Integer.parseInt(args[2]);
		rPeriod = Integer.parseInt(args[3]);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while(true) {
						Thread.sleep(rPeriod * 1000);
						if (dStoreThreads.size() >= repFac) {
							Controller.rebalance();
						}
					}
				}
				catch(Exception e){
					System.out.println("error " + e);
				}
			}
		}).start();
	}

	public static void removeIndex(Index index){
		synchronized (indexLock) {
			indexes.get().remove(index);
		}
	}

	public static ArrayList<FileStore> getDstores(){
			return dStores;
	}

	public static ArrayList<Index> getIndexes(){
		synchronized (indexLock) {
			return indexes.get();
		}
	}

	public static void addIndex(Index index){
		synchronized (indexLock) {
			indexes.get().add(index);
		}
	}
}
