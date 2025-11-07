import javax.swing.*;
import java.io.*;
import java.net.*;

public class BankClient extends javax.swing.JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private String currentAccountNumber;

    public BankClient() {
        initComponents();
        setLocationRelativeTo(null);
    }

    public void setCurrentAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be null or empty");
        }
        this.currentAccountNumber = accountNumber.trim();
    }

    private void initComponents() {
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Banking Operations");

        // Main Panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Operation Selection
        JPanel operationPanel = new JPanel();
        JLabel operationLabel = new JLabel("Select Operation:");
        String[] operations = {"Deposit", "Withdrawal"};
        JComboBox<String> operationCombo = new JComboBox<>(operations);
        operationPanel.add(operationLabel);
        operationPanel.add(operationCombo);

        // Amount Panel
        JPanel amountPanel = new JPanel();
        JLabel amountLabel = new JLabel("Amount: ₹");
        JTextField amountField = new JTextField(10);
        amountPanel.add(amountLabel);
        amountPanel.add(amountField);

        // Button Panel
        JPanel buttonPanel = new JPanel();
        JButton submitButton = new JButton("Submit");
        JButton historyButton = new JButton("Transaction History");
        JButton logoutButton = new JButton("Logout");
        buttonPanel.add(submitButton);
        buttonPanel.add(historyButton);
        buttonPanel.add(logoutButton);

        // Add panels to main panel
        mainPanel.add(operationPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(amountPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(buttonPanel);

        // Add main panel to frame
        add(mainPanel);
        pack();

        // Operation Combo Box Listener
        operationCombo.addActionListener(e -> {
            String selected = (String) operationCombo.getSelectedItem();
            pack();
        });

        // Submit Button Action
        submitButton.addActionListener(e -> {
            try {
                String operation = (String) operationCombo.getSelectedItem();
                double amount;
                try {
                    amount = Double.parseDouble(amountField.getText());
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Please enter a valid amount!");
                    return;
                }

                try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                    socket.setSoTimeout(5000); // Set timeout to 5 seconds
                    ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                    
                    switch (operation) {
                        case "Deposit":
                            if (currentAccountNumber == null || currentAccountNumber.isEmpty()) {
                                JOptionPane.showMessageDialog(this, "Please log in first!");
                                return;
                            }
                            output.writeObject("DEPOSIT");
                            output.writeObject(currentAccountNumber);
                            output.writeDouble(amount);
                            output.flush();
                            break;
                        case "Withdrawal":
                            if (currentAccountNumber == null || currentAccountNumber.isEmpty()) {
                                JOptionPane.showMessageDialog(this, "Please log in first!");
                                return;
                            }
                            output.writeObject("WITHDRAW");
                            output.writeObject(currentAccountNumber);
                            output.writeDouble(amount);
                            output.flush();
                            break;

                    }

                    Object responseObj = input.readObject();
                    if (responseObj == null) {
                        JOptionPane.showMessageDialog(this, "Error: No response from server");
                        return;
                    }
                    
                    String response = responseObj.toString();
                    JOptionPane.showMessageDialog(this, response);
                    if (response.contains("Successful")) {
                        amountField.setText("");
                    }
                }

            } catch (SocketTimeoutException ex) {
                JOptionPane.showMessageDialog(this, "Error: Server is not responding. Please try again later.");
            } catch (ConnectException ex) {
                JOptionPane.showMessageDialog(this, "Error: Could not connect to server. Please ensure the server is running.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        // History Button Action
        historyButton.addActionListener(e -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                socket.setSoTimeout(5000); // Set timeout to 5 seconds
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

                output.writeObject("TRANSACTION_HISTORY");
                output.writeObject(currentAccountNumber);

                String history = (String) input.readObject();
                JTextArea textArea = new JTextArea(history);
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new java.awt.Dimension(400, 300));

                JOptionPane.showMessageDialog(this, scrollPane, "Transaction History",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (ConnectException ex) {
                JOptionPane.showMessageDialog(this, "Error: Could not connect to server. Please ensure the server is running.");
            } catch (SocketTimeoutException ex) {
                JOptionPane.showMessageDialog(this, "Error: Server is not responding. Please try again later.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        // Logout Button Action
        logoutButton.addActionListener(e -> {
            loginPage loginForm = new loginPage();
            loginForm.setVisible(true);
            dispose();
        });
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> {
            new BankClient().setVisible(true);
        });
    }
}