package Env;

import cartago.*;
import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketArtifact extends Artifact {
    private MyWebSocketServer server;
    private Map<String, WebSocket> connections = new ConcurrentHashMap<>();

    @OPERATION
    public void init(int port) {
        server = new MyWebSocketServer(new InetSocketAddress(port), this);
        server.start();
        log("WebSocket server started on port " + port);
    }

    @OPERATION
    public void broadcast(String message) {
        if (server != null) {
            server.broadcast(message);
            log("Broadcasted message: " + message);
        }
    }

    @OPERATION
    public void send(String address, String message) {
        WebSocket conn = connections.get(address);
        if (conn != null && conn.isOpen()) {
            JSONObject json = new JSONObject();
            json.put("msg", message);
            json.put("sender", "bot");
            conn.send(json.toString());
            log("Sent message to " + address + ": " + message);
        } else {
            failed("Connection not found or closed: " + address);
        }
    }

    @OPERATION
    public void shutdown() {
        if (server != null) {
            try {
                server.stop();
                log("WebSocket server stopped");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    @INTERNAL_OPERATION
    void notifyServerStarted() {
        signal("serverStarted");
    }

    @INTERNAL_OPERATION
    void notifyOpened(WebSocket conn, ClientHandshake handshake) {
        String address = conn.getRemoteSocketAddress().toString();
        connections.put(address, conn);
        signal("clientConnected", address);
    }
   
    @INTERNAL_OPERATION
    void notifyClosed(WebSocket conn, int code, String reason, boolean remote) {
        String address = conn.getRemoteSocketAddress().toString();
        connections.remove(address);
        signal("clientDisconnected", address);
    }
    
    @INTERNAL_OPERATION
    void notifyNewMessage(WebSocket conn, String message) {
        String address = conn.getRemoteSocketAddress().toString();
        signal("messageReceived", address, message);
    }

    @INTERNAL_OPERATION
    void notifyError(WebSocket conn, Exception ex) {
        signal("error", ex.getMessage());
    }

    
    private class MyWebSocketServer extends WebSocketServer {
    	private WebSocketArtifact art;
    	
        public MyWebSocketServer(InetSocketAddress address, WebSocketArtifact art) {
            super(address);
            this.art = art;
        }

        @Override
        public void onStart() {
        	try {
        		art.beginExtSession();
    	        art.notifyServerStarted();
            } finally {
            	art.endExtSession();
            }
        }
        
        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
        	try {
        		art.beginExtSession();
    	        art.notifyOpened(conn, handshake);
            } finally {
            	art.endExtSession();
            }
        }
        
 
        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        	try {
        		art.beginExtSession();
    	        art.notifyClosed(conn, code, reason, remote);
            } finally {
            	art.endExtSession();
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
        	try {
        		art.beginExtSession();
    	        art.notifyNewMessage(conn, message);
            } finally {
            	art.endExtSession();
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
           	try {
        		art.beginExtSession();
    	        art.notifyError(conn, ex);
            } finally {
            	art.endExtSession();
            }
        }

    }
}