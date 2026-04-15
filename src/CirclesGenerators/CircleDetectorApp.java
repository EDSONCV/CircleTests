package CirclesGenerators;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

/**
 * CircleDetectorApp
 * application to detect circles using OpenCV HoughCircles transform.
 * It features a GUI to load images, configure parameters, and view results in a table.
 */
public class CircleDetectorApp extends JFrame {

    private static final long serialVersionUID = 1L;
	// OpenCV Matrices
    private Mat originalImage;
    private Mat processedImage;

    // GUI Components
    private JLabel lblOriginalImage;
    private JLabel lblProcessedImage;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JFileChooser fileChooser;

    // Parameter Inputs
    private JTextField txtDp;
    private JTextField txtMinDist;
    private JTextField txtParam1;
    private JTextField txtParam2;
    private JTextField txtMinRadius;
    private JTextField txtMaxRadius;

    public CircleDetectorApp() {
        setTitle("OpenCV Hough Circle Detector");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        initializeUI();
    }

    private void initializeUI() {
        // --- Top Panel: Controls and Parameters ---
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JButton btnLoad = new JButton("Load Image");
        btnLoad.addActionListener(this::loadImageAction);

        JButton btnDetect = new JButton("Detect Circles");
        btnDetect.addActionListener(this::detectCirclesAction);

        // Parameters inputs with default values
        controlPanel.add(btnLoad);
        controlPanel.add(new JLabel("dp:"));
        txtDp = new JTextField("1.0", 3);
        controlPanel.add(txtDp);

        controlPanel.add(new JLabel("minDist:"));
        txtMinDist = new JTextField("50", 3);
        controlPanel.add(txtMinDist);

        controlPanel.add(new JLabel("param1 (Canny):"));
        txtParam1 = new JTextField("100", 3);
        controlPanel.add(txtParam1);

        controlPanel.add(new JLabel("param2 (Accumulator):"));
        txtParam2 = new JTextField("30", 3);
        controlPanel.add(txtParam2);

        controlPanel.add(new JLabel("minRadius:"));
        txtMinRadius = new JTextField("10", 3);
        controlPanel.add(txtMinRadius);

        controlPanel.add(new JLabel("maxRadius:"));
        txtMaxRadius = new JTextField("100", 3);
        controlPanel.add(txtMaxRadius);

        controlPanel.add(btnDetect);

        add(controlPanel, BorderLayout.NORTH);

        // --- Center Panel: Images Display ---
        JPanel imagesPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        imagesPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        lblOriginalImage = new JLabel("Original Image", SwingConstants.CENTER);
        lblOriginalImage.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        lblProcessedImage = new JLabel("Processed Image", SwingConstants.CENTER);
        lblProcessedImage.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // Wrap labels in ScrollPanes for large images
        imagesPanel.add(new JScrollPane(lblOriginalImage));
        imagesPanel.add(new JScrollPane(lblProcessedImage));

        add(imagesPanel, BorderLayout.CENTER);

        // --- Bottom Panel: Results Table ---
        String[] columnNames = {"ID", "Center X", "Center Y", "Radius"};
        tableModel = new DefaultTableModel(columnNames, 0);
        resultsTable = new JTable(tableModel);
        
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(1180, 150));
        tableScrollPane.setBorder(new EmptyBorder(10, 10, 10, 10));

        add(tableScrollPane, BorderLayout.SOUTH);

        // Initialize File Chooser
        fileChooser = new JFileChooser();
    }

    /**
     * Action to load an image file from disk.
     */
    private void loadImageAction(ActionEvent e) {
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();

            // Load image using OpenCV
            originalImage = Imgcodecs.imread(path);

            if (originalImage.empty()) {
                JOptionPane.showMessageDialog(this, "Could not load image: " + path, "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Display original image
            displayImage(originalImage, lblOriginalImage);
            
            // Clear previous processed image and results
            lblProcessedImage.setIcon(null);
            lblProcessedImage.setText("Click 'Detect Circles'");
            tableModel.setRowCount(0);
        }
    }

    /**
     * Action to execute the Hough Circle Transform.
     */
    private void detectCirclesAction(ActionEvent e) {
        if (originalImage == null || originalImage.empty()) {
            JOptionPane.showMessageDialog(this, "Please load an image first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Parse parameters from GUI
            double dp = Double.parseDouble(txtDp.getText());
            double minDist = Double.parseDouble(txtMinDist.getText());
            double param1 = Double.parseDouble(txtParam1.getText());
            double param2 = Double.parseDouble(txtParam2.getText());
            int minRadius = Integer.parseInt(txtMinRadius.getText());
            int maxRadius = Integer.parseInt(txtMaxRadius.getText());

            // 1. Pre-processing: Convert to Grayscale
            Mat grayImage = new Mat();
            Imgproc.cvtColor(originalImage, grayImage, Imgproc.COLOR_BGR2GRAY);

            // 2. Pre-processing: Blur to reduce noise (Important for Hough)
            Imgproc.GaussianBlur(grayImage, grayImage, new Size(7, 7), 1, 1);

            // 3. Apply Hough Circles
            Mat circles = new Mat();
            Imgproc.HoughCircles(grayImage, circles, Imgproc.HOUGH_GRADIENT, 
                                 dp, minDist, param1, param2, minRadius, maxRadius);

            // 4. Draw results and update table
            // Clone original to draw on without modifying the source
            processedImage = originalImage.clone();
            tableModel.setRowCount(0); // Clear table

            for (int i = 0; i < circles.cols(); i++) {
                double[] c = circles.get(0, i);
                Point center = new Point((int) Math.round(c[0]), (int) Math.round(c[1]));
                int radius = (int) Math.round(c[2]);

                // Draw circle center
                Imgproc.circle(processedImage, new org.opencv.core.Point( c[0], c[1]), 3, new Scalar(0, 255, 0), -1, 8, 0);
                // Draw circle outline
                Imgproc.circle(processedImage, new org.opencv.core.Point( c[0], c[1]), radius, new Scalar(0, 0, 255), 1, 8, 0);

                // Add to JTable
                tableModel.addRow(new Object[]{i + 1, c[0], c[1], c[2]});
                System.out.println("new Circle("+c[0]+","+ c[1] +"," +c[2]+"),");
            }
            for (int i = 0; i < circles.cols(); i++) {
            	 double[] c = circles.get(0, i);
            	 System.out.println((int) c[2]+" ,");
            }

            // Display processed image
            displayImage(processedImage, lblProcessedImage);
            
            // Update status text
            if (circles.cols() == 0) {
                 JOptionPane.showMessageDialog(this, "No circles detected with current parameters.", "Info", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid parameter format. Please check input fields.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error during processing: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Helper method to convert OpenCV Mat to BufferedImage and display in JLabel.
     */
    private void displayImage(Mat mat, JLabel label) {
        BufferedImage img = matToBufferedImage(mat);
        if (img != null) {
            label.setIcon(new ImageIcon(img));
            label.setText(""); // Remove placeholder text
        }
    }

    /**
     * Converts an OpenCV Mat object to a Java BufferedImage.
     * Handles both Color (BGR) and Grayscale images.
     */
    public static BufferedImage matToBufferedImage(Mat m) {
        if (m == null || m.empty()) return null;

        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b); // Get all the pixels

        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);

        return image;
    }

    public static void main(String[] args) {
        // --- LOAD NATIVE LIBRARY ---
        // Option A: Use the default library path (Ensure the DLL is in your system path or project root)
        try {String filePre = "";
        	String fileExt = ".dll";
        	 final File nativeLibrary = new File("lib/java/x64/" + filePre + Core.NATIVE_LIBRARY_NAME + fileExt);
             System.load(nativeLibrary.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            // Option B: Load directly from absolute path if Option A fails
            // System.load("C:/path/to/opencv/build/java/x64/opencv_java4x.dll");
            System.err.println("Native code library failed to load. \n" + e);
            System.exit(1);
        }

        // Run GUI in Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            CircleDetectorApp app = new CircleDetectorApp();
            app.setVisible(true);
        });
    }
}