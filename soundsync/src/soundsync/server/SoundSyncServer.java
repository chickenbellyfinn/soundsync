package soundsync.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Timer;

import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import soundsync.Command;


/**
 * 
 * @author Akshay
 */
public class SoundSyncServer implements Runnable {
	
	public static final int PORT = 1980;
	public static final String SERVER_ADDR = "130.215.234.149";
	
	public static final int PLAY_DELAY = 500;
	public static final int COL_URL = 1;
	
	private ServerSocket serverSocket;
	private HashMap<String, ClientHandler> clientList;
	
	private ServerFrame frame;
	
	private String currentStream = "";
	private DefaultTableModel songTable;
	
	private boolean startNextTrack = true;
	private long trackStartTime;
	private long trackLength;
	private int loadCount = 0;
	
	private Timer timer;
	
	private boolean isRunning = false;
	
	public SoundSyncServer() {
		try {
			serverSocket = new ServerSocket(SoundSyncServer.PORT, 0, InetAddress.getLocalHost());
			System.out.format("Starting server at %s:%05d%n", serverSocket.getInetAddress().getHostAddress(), SoundSyncServer.PORT);
			serverSocket.setSoTimeout(0);
		}
		catch (Exception e) {
			System.err.format("Error starting server: %s%n", e);
			System.exit(1);
		}
		
		timer = new Timer();
		
		setupGUI();
		
		clientList = new HashMap<String, ClientHandler>();
		
	}
	
	@Override
	public void run() {
		isRunning = true;
		
		while (isRunning) {
			try {
				Socket newClient = serverSocket.accept();
				newClient.setSoTimeout(0);
				DataInputStream tmpIn = new DataInputStream(newClient.getInputStream());
				DataOutputStream tmpOut = new DataOutputStream(newClient.getOutputStream());
				
				String user = tmpIn.readUTF();
				String userVersion = tmpIn.readUTF();
				System.out.format("\"%s\" is trying to connect with protocol %s%n", user, userVersion);
				if (userVersion.equals(Command.PROTOCOL_VERSION)) {
					tmpOut.writeUTF(Command.GOOD);
				}
				else {
					System.out.printf("%s is had outdated client (user: %d, current:%s)", user, userVersion, Command.PROTOCOL_VERSION);
					tmpOut.writeUTF(Command.BAD);
				}
				tmpOut.flush();
				
				ClientHandler newClientHandler = new ClientHandler(user, newClient, this);
				//String newClientAddr = newClient.getInetAddress().getHostAddress();
				clientList.put(user, newClientHandler);
				newClientHandler.pingTest();
				newClientHandler.start();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void setTrackLength(long time) {
		trackLength = time;
	}
	
	public void clientLoaded() {
		loadCount++;
		if (loadCount == clientList.size()) {
			loadCount = 0;
			sendPlay();
		}
	}
	
	private void setupGUI() {
		frame = new ServerFrame();
		songTable = (DefaultTableModel)frame.songList.getModel();
		
		frame.playButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				int selectedRow = frame.songList.getSelectedRow();
				if (selectedRow >= 0) {
					broadcast(Command.CLIENT_CLEAR_QUEUE);
					sendLoad((String)songTable.getValueAt(selectedRow, SoundSyncServer.COL_URL));
				}
			}
		});
		
	}
	
	private void sendLoad(final String url) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				for (ClientHandler h : clientList.values()) {
					try {
						h.sendLoad(url);
					}
					catch (Exception e) {}
				}
			}
		}).start();
	}
	
	private void sendPlay() {
		System.out.println("SERVER PLAY");
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				trackStartTime = System.currentTimeMillis() + SoundSyncServer.PLAY_DELAY;
				for (ClientHandler h : clientList.values()) {
					try {
						if (h.isLoaded()) {
							h.send(Command.formatCmd(Command.CLIENT_PLAY, trackStartTime - h.lag));
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			}
		}).start();
		
	}
	
	private void broadcast(final String b) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				for (ClientHandler h : clientList.values()) {
					try {
						h.send(b);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
		
	}
	
	public void addSong(String song, String user) {
		for (int i = 0; i < songTable.getRowCount(); i++) {
			if (songTable.getValueAt(i, SoundSyncServer.COL_URL).equals(song)) { //song is already in the list
				return;
			}
		}
		songTable.addRow(new Object[] { "", song, user });
		frame.adjuster.adjustColumns();
		frame.repaint();		
		broadcast(Command.formatCmd(Command.CLIENT_ADD, user, song));
	}
	
	public void removeClient(ClientHandler h) {
		clientList.remove(h.id);
	}
	
	public static void main(String[] args) {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		SoundSyncServer server = new SoundSyncServer();
		server.run();
	}
	
}
