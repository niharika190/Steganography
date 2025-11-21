import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class SteganographyGUI extends JFrame {

    private JTextArea messageArea;
    private JLabel imageLabel;
    private JLabel statusLabel;
    private BufferedImage currentImage;
    private File currentFile;

    public SteganographyGUI() {
        super("Steganography Tool (LSB Method)");
        setLayout(new BorderLayout(10, 10));
        

        // === Top Panel ===
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());
        JButton openButton = new JButton("Open Image");
        JButton encodeButton = new JButton("Encode");
        JButton decodeButton = new JButton("Decode");
        JButton saveButton = new JButton("Save Image");
        topPanel.add(openButton);
        topPanel.add(encodeButton);
        topPanel.add(decodeButton);
        topPanel.add(saveButton);

        add(topPanel, BorderLayout.NORTH);

        // === Center Panel ===
        imageLabel = new JLabel("No image loaded", SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(500, 300));
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);

        // === Bottom Panel ===
        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageArea = new JTextArea(5, 40);
        bottomPanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);
        statusLabel = new JLabel("Status: Ready");
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // === Button Actions ===
        openButton.addActionListener(e -> openImage());
        encodeButton.addActionListener(e -> encodeMessage());
        decodeButton.addActionListener(e -> decodeMessage());
        saveButton.addActionListener(e -> saveImage());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // -------------------- Open Image --------------------
    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                currentFile = chooser.getSelectedFile();
                currentImage = ImageIO.read(currentFile);
                if (currentImage == null) throw new IOException("Invalid image file!");
                imageLabel.setIcon(new ImageIcon(currentImage.getScaledInstance(500, 300, Image.SCALE_SMOOTH)));
                statusLabel.setText("Loaded: " + currentFile.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        }
    }

    // -------------------- Encode Message --------------------
    private void encodeMessage() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "Please open an image first!");
            return;
        }
        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a message to hide!");
            return;
        }
        try {
            BufferedImage stego = encode(currentImage, message);
            currentImage = stego;
            imageLabel.setIcon(new ImageIcon(currentImage.getScaledInstance(500, 300, Image.SCALE_SMOOTH)));
            statusLabel.setText("Message encoded successfully (not yet saved)");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Encoding error: " + e.getMessage());
        }
    }

    // -------------------- Decode Message --------------------
    private void decodeMessage() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "Please open an image first!");
            return;
        }
        try {
            String decoded = decode(currentImage);
            messageArea.setText(decoded);
            statusLabel.setText("Message decoded successfully");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Decoding error: " + e.getMessage());
        }
    }

    // -------------------- Save Image --------------------
    private void saveImage() {
        if (currentImage == null) {
            JOptionPane.showMessageDialog(this, "No image to save!");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("stego_output.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File outputFile = chooser.getSelectedFile();
                ImageIO.write(currentImage, "png", outputFile);
                statusLabel.setText("Image saved: " + outputFile.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Save error: " + e.getMessage());
            }
        }
    }

    // -------------------- Encode Logic --------------------
    private BufferedImage encode(BufferedImage img, String msg) {
        String secret = msg + '\0';
        byte[] data = secret.getBytes();

        int width = img.getWidth();
        int height = img.getHeight();
        long totalBits = (long) width * height * 3;
        long requiredBits = data.length * 8L;
        if (requiredBits > totalBits)
            throw new IllegalArgumentException("Message too large for this image!");

        BufferedImage stego = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        stego.setData(img.getData());

        long bitIndex = 0;
        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitIndex >= requiredBits) break outer;

                int pixel = stego.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                int[] comps = {r, g, b};
                for (int i = 0; i < 3; i++) {
                    if (bitIndex >= requiredBits) break;
                    int byteIdx = (int) (bitIndex / 8);
                    int bitIdx = 7 - (int) (bitIndex % 8);
                    int bit = (data[byteIdx] >> bitIdx) & 1;
                    comps[i] = (comps[i] & 0xFE) | bit;
                    bitIndex++;
                }

                int newPixel = (0xFF << 24) | (comps[0] << 16) | (comps[1] << 8) | comps[2];
                stego.setRGB(x, y, newPixel);
            }
        }

        return stego;
    }

    // -------------------- Decode Logic --------------------
    private String decode(BufferedImage img) {
        StringBuilder sb = new StringBuilder();
        int currentByte = 0;
        int bitsCollected = 0;

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int pixel = img.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                int[] comps = {r, g, b};
                for (int c = 0; c < 3; c++) {
                    int lsb = comps[c] & 1;
                    currentByte = (currentByte << 1) | lsb;
                    bitsCollected++;
                    if (bitsCollected == 8) {
                        char ch = (char) currentByte;
                        if (ch == '\0') return sb.toString();
                        sb.append(ch);
                        bitsCollected = 0;
                        currentByte = 0;
                    }
                }
            }
        }
        return sb.toString();
    }

    // -------------------- Main --------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SteganographyGUI::new);
    }
}
