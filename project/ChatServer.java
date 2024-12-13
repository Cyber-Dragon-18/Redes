import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    private static final ByteBuffer buffer = ByteBuffer.allocate(16384);
    private static final Charset charset = Charset.forName("UTF8");
    private static final CharsetDecoder decoder = charset.newDecoder();
    private static final CharsetEncoder encoder = charset.newEncoder();

    // Estruturas para gerir clientes e salas
    private static final Map<SocketChannel, ClientState> clients = new HashMap<>();
    private static final Map<String, Set<SocketChannel>> rooms = new HashMap<>();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);

        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            Selector selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Servidor de chat iniciado na porta " + port);

            while (true) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()) {
                        handleAccept(serverChannel, selector);
                    } else if (key.isReadable()) {
                        handleRead((SocketChannel) key.channel());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        clients.put(clientChannel, new ClientState());
        System.out.println("Novo cliente conectado: " + clientChannel.getRemoteAddress());
    }

    private static void handleRead(SocketChannel clientChannel) throws IOException {
        buffer.clear();
        int bytesRead = clientChannel.read(buffer);

        if (bytesRead <= 0) {
            disconnectClient(clientChannel);
            return;
        }

        buffer.flip();
        String message = decoder.decode(buffer).toString().trim();
        processMessage(clientChannel, message);
    }

    private static void processMessage(SocketChannel clientChannel, String message) throws IOException {
        ClientState state = clients.get(clientChannel);

        if (message.startsWith("/")) {
            // Processar comandos
            String[] parts = message.split(" ", 2);
            String command = parts[0];
            String argument = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "/nick":
                    handleNick(clientChannel, state, argument);
                    break;
                case "/join":
                    handleJoin(clientChannel, state, argument);
                    break;
                case "/leave":
                    handleLeave(clientChannel, state);
                    break;
                case "/bye":
                    handleBye(clientChannel);
                    break;
                default:
                    sendMessage(clientChannel, "ERROR");
            }
        } else {
            // Processar mensagens simples
            handleMessage(clientChannel, state, message);
        }
    }

    private static void handleNick(SocketChannel clientChannel, ClientState state, String nickname) throws IOException {
        if (nickname.isEmpty() || clients.values().stream().anyMatch(s -> nickname.equals(s.nickname))) {
            sendMessage(clientChannel, "ERROR");
        } else {
            String oldNickname = state.nickname;
            state.nickname = nickname;

            if (oldNickname == null) {
                sendMessage(clientChannel, "OK");
                state.state = "outside";
            } else {
                sendMessage(clientChannel, "OK");
                broadcastToRoom(state.room, "NEWNICK " + oldNickname + " " + nickname, clientChannel);
            }
        }
    }

    private static void handleJoin(SocketChannel clientChannel, ClientState state, String room) throws IOException {
        if (room.isEmpty()) {
            sendMessage(clientChannel, "ERROR");
            return;
        }

        if (state.room != null) {
            handleLeave(clientChannel, state);
        }

        state.room = room;
        rooms.computeIfAbsent(room, k -> new HashSet<>()).add(clientChannel);
        state.state = "inside";
        sendMessage(clientChannel, "OK");
        broadcastToRoom(room, "JOINED " + state.nickname, null);
    }

    private static void handleLeave(SocketChannel clientChannel, ClientState state) throws IOException {
        if (state.room != null) {
            String room = state.room;
            state.room = null;
            state.state = "outside";
            rooms.getOrDefault(room, Collections.emptySet()).remove(clientChannel);
            broadcastToRoom(room, "LEFT " + state.nickname, null);
        }
    }

    private static void handleBye(SocketChannel clientChannel) throws IOException {
        sendMessage(clientChannel, "BYE");
        disconnectClient(clientChannel);
    }

    private static void handleMessage(SocketChannel clientChannel, ClientState state, String message) throws IOException {
        if (!"inside".equals(state.state)) {
            sendMessage(clientChannel, "ERROR");
        } else {
            String escapedMessage = message.replaceFirst("^/", "//");
            broadcastToRoom(state.room, "MESSAGE " + state.nickname + " " + escapedMessage, clientChannel);
        }
    }

    private static void sendMessage(SocketChannel clientChannel, String message) throws IOException {
        clientChannel.write(encoder.encode(CharBuffer.wrap(message + "\n")));
    }


    private static void broadcastToRoom(String room, String message, SocketChannel exclude) throws IOException {
        for (SocketChannel client : rooms.getOrDefault(room, Collections.emptySet())) {
            sendMessage(client, message);
        }
    }
    

    private static void disconnectClient(SocketChannel clientChannel) throws IOException {
        ClientState state = clients.remove(clientChannel);
        if (state != null && state.room != null) {
            rooms.getOrDefault(state.room, Collections.emptySet()).remove(clientChannel);
            broadcastToRoom(state.room, "LEFT " + state.nickname, null);
        }
        clientChannel.close();
    }

    static class ClientState {
        String state = "init";
        String nickname;
        String room;
    }
}
