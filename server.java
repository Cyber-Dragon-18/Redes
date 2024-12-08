import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class server {
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    // Map para armazenar pseudônimos e seus canais
    static private final Map<SocketChannel, String> clientNicknames = new HashMap<>();
    static private final Set<String> nicknames = new HashSet<>(); // Conjunto para verificar duplicação de pseudônimos

    public static void main(String args[]) throws Exception {
        int port = Integer.parseInt(args[0]);

        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);

            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            Selector selector = Selector.open();
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while (true) {
                int num = selector.select();
                if (num == 0) {
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();

                    if (key.isAcceptable()) {
                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);

                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel sc = null;
                        try {
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc);

                            if (!ok) {
                                disconnectClient(sc, key);
                            }
                        } catch (IOException ie) {
                            disconnectClient(sc, key);
                        }
                    }
                    it.remove();
                }
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }

    static private boolean processInput(SocketChannel sc) throws IOException {
        buffer.clear();
        int bytesRead = sc.read(buffer);

        if (bytesRead <= 0) {
            return false;
        }

        buffer.flip();
        String message = decoder.decode(buffer).toString().trim();

        // Verificar se o cliente já possui um pseudônimo
        if (!clientNicknames.containsKey(sc)) {
            // Verificar se o pseudônimo já está em uso
            if (nicknames.contains(message)) {
                sendMessage(sc, "Nickname already in use. Please choose another.");
            } else {
                // Registrar pseudônimo
                clientNicknames.put(sc, message);
                nicknames.add(message);
                System.out.println("Pseudonym set for client: " + message);
                sendMessage(sc, "Pseudonym registered as: " + message);
                broadcastMessage(sc, message + " joined the chat.");
            }
        } else {
            // Difundir mensagem para todos os clientes
            String pseudonym = clientNicknames.get(sc);
            String fullMessage = pseudonym + ": " + message;
            broadcastMessage(sc, fullMessage);
        }
        return true;
    }

    static private void sendMessage(SocketChannel sc, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(charset));
        sc.write(buffer);
    }

    static private void broadcastMessage(SocketChannel sender, String message) throws IOException {
        System.out.println("Broadcasting: " + message);
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(charset));

        for (SocketChannel client : clientNicknames.keySet()) {
            if (client != sender) {
                client.write(buffer);
                buffer.rewind();
            }
        }
    }

    static private void disconnectClient(SocketChannel sc, SelectionKey key) throws IOException {
        if (sc != null) {
            String nickname = clientNicknames.get(sc);
            if (nickname != null) {
                System.out.println(nickname + " left the chat.");
                nicknames.remove(nickname);
                broadcastMessage(null, nickname + " left the chat.");
            }
            clientNicknames.remove(sc);
            key.cancel();
            sc.close();
        }
    }
}
