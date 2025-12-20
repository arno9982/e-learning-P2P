package org.example.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PeerClient {
    private static final String BOOTSTRAP_SERVER_IP = "127.0.0.1";
    private static final int BOOTSTRAP_SERVER_PORT = 9090;
    private static final int PEER_PORT = 9092;

    private static Peer myPeer;
    private static ConcurrentMap<String, List<Course>> networkCourses = new ConcurrentHashMap<>();
    private static List<Peer> connectedPeers = new ArrayList<>();

    public static void main(String[] args) {
        try {
            String pseudo = "bob";
            InetAddress myAddress = InetAddress.getLocalHost();
            myPeer = new Peer(pseudo, myAddress, PEER_PORT,true);

            // Simuler des cours locaux pour Alice
            // Correction ici, en retirant l'UUID
            Course course3 = new Course("Algorithmes de tri", "Cours sur les différents algorithmes de tri", pseudo, "/path/to/algo_tri.pdf");
            List<Course> myLocalCourses = new ArrayList<>();
            myLocalCourses.add(course3);
            myPeer.setSharedCourses(myLocalCourses);

        } catch (UnknownHostException e) {
            System.err.println("Error getting local host address: " + e.getMessage());
            return;
        }

        new PeerListener(myPeer).start();

        System.out.println("Connecting to bootstrap server...");
        try (Socket socket = new Socket(BOOTSTRAP_SERVER_IP, BOOTSTRAP_SERVER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Message joinMessage = new Message(Message.Command.JOIN, myPeer, myPeer.getPeerId());
            out.writeObject(joinMessage);
            System.out.println("Sent JOIN message to server.");

            List<Peer> peersFromBootstrap = (List<Peer>) in.readObject();
            System.out.println("Received peer list from server. Found " + peersFromBootstrap.size() + " other peers.");

            for (Peer peer : peersFromBootstrap) {
                if (!peer.getPeerId().equals(myPeer.getPeerId())) {
                    System.out.println("Found peer: " + peer.getPseudo() + " at " + peer.getAddress() + ":" + peer.getPort());
                    connectToPeer(peer);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error connecting to bootstrap server: " + e.getMessage());
        }
    }

    private static void connectToPeer(Peer peer) {
        try (Socket peerSocket = new Socket(peer.getAddress(), peer.getPort());
             ObjectOutputStream out = new ObjectOutputStream(peerSocket.getOutputStream())) {

            Message shareMessage = new Message(Message.Command.SHARE_COURSES, (Serializable) myPeer.getSharedCourses(), myPeer.getPeerId());
            out.writeObject(shareMessage);
            System.out.println("Sent my courses to peer " + peer.getPseudo());

            connectedPeers.add(peer);

        } catch (IOException e) {
            System.err.println("Could not connect to peer " + peer.getPseudo() + ": " + e.getMessage());
        }
    }

    private static class PeerListener extends Thread {
        private Peer myPeer;

        public PeerListener(Peer peer) {
            this.myPeer = peer;
        }

        @Override
        public void run() {
            try (ServerSocket listenerSocket = new ServerSocket(myPeer.getPort())) {
                System.out.println("Peer " + myPeer.getPseudo() + " is listening on port " + myPeer.getPort());
                while (true) {
                    new PeerConnectionHandler(listenerSocket.accept()).start();
                }
            } catch (IOException e) {
                System.err.println("Error while listening for peer connections: " + e.getMessage());
            }
        }
    }

    private static class PeerConnectionHandler extends Thread {
        private Socket clientSocket;

        public PeerConnectionHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

                System.out.println("New peer connected from: " + clientSocket.getInetAddress());

                Message incomingMessage = (Message) in.readObject();
                if (incomingMessage.getCommand() == Message.Command.SHARE_COURSES) {
                    List<Course> courses = (List<Course>) incomingMessage.getData();
                    System.out.println("Received " + courses.size() + " courses from a peer.");

                    for(Course course : courses) {
                        System.out.println(" - " + course.getTitle() + " by " + course.getAuthorPseudo());
                    }
                }

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error handling incoming peer connection: " + e.getMessage());
            }
        }
    }
}