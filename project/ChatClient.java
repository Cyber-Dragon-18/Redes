import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ChatClient(String server, int port) throws IOException {
        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    novaMensagem(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Inicializar conexão com o servidor
        this.socket = new Socket(server, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void novaMensagem(String mensagem) throws IOException {
        out.println(mensagem);
    }

    public void run() throws IOException {
        // Thread para receber mensagens do servidor
        ExecutorService executar = Executors.newSingleThreadExecutor();
        executar.submit(() -> {
            try {
                String mensagemServer;
                while ((mensagemServer = in.readLine()) != null) {
                    processarMensagem(mensagemServer);
                }
            } catch (IOException e) {
                printMensagem("Error.\n");
            }
        });
    }

    private void processarMensagem(String mensagemServer) {
        // Processar mensagens recebidas do servidor para exibição amigável
        if (mensagemServer.startsWith("MESSAGE")) {
            String[] parts = mensagemServer.split(" ", 3);
            if (parts.length >= 3) {
                String remetente = parts[1];
                String conteudo = parts[2];
                printMensagem(remetente + ": " + conteudo + "\n");
            }
        } else if (mensagemServer.startsWith("PRIVATE")) {
            String[] parts = mensagemServer.split(" ", 3);
            if (parts.length >= 3) {
                String remetente = parts[1];
                String conteudo = parts[2];
                printMensagem("Private " + remetente + ": " + conteudo + "\n");
            }
        } else if (mensagemServer.startsWith("NEWNICK")) {
            String[] parts = mensagemServer.split(" ");
            if (parts.length >= 3) {
                printMensagem("NEWNICK " + parts[1] + " " + parts[2] + "\n");
            }
        } else if (mensagemServer.startsWith("JOINED")) {
            String[] parts = mensagemServer.split(" ");
            if (parts.length >= 2) {
                printMensagem( "JOINED " + parts[1] + "\n");
            }
        } else if (mensagemServer.startsWith("LEFT")) {
            String[] parts = mensagemServer.split(" ");
            if (parts.length >= 2) {
                printMensagem("LEFT " + parts[1] + "\n");
            }
        } else if (mensagemServer.startsWith("BYE")) {
            printMensagem("BYE\n");
            try {
                socket.close();
            } catch (IOException e) {
                printMensagem("Error\n");
            }
        } else if (mensagemServer.startsWith("ERROR")) {
            printMensagem("Error \n");
        } else if (mensagemServer.startsWith("OK")) {
            printMensagem("OK\n");
        }
    }

    

    public void printMensagem(final String message) {
        chatArea.append(message);
    }

    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
