import java.io.*;
import java.net.*;
import java.sql.*;

public class BankServer {
    private static final int PORT = 5000;
    private static Connection conn;

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/banksystem", "root", "Reset@123");
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Bank Server is running...");

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, conn).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler extends Thread {
    private Socket socket;
    private Connection conn;

    public ClientHandler(Socket socket, Connection conn) {
        this.socket = socket;
        this.conn = conn;
    }

    public void run() {
        try (
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
        ) {
            String requestType = (String) input.readObject();
            switch (requestType) {
                case "DEPOSIT":
                    handleDeposit(input, output);
                    break;
                case "WITHDRAW":
                    handleWithdraw(input, output);
                    break;
                case "TRANSACTION_HISTORY":
                    handleTransactionHistory(input, output);
                    break;
                default:
                    output.writeObject("INVALID_REQUEST");
            }
        } catch (EOFException e) {
            System.out.println("Client closed the connection.");
        } catch (SocketException e) {
            System.out.println("Client disconnected abruptly.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logTransaction(String accountNumber, String type, double amount, String recipient) throws SQLException {
        String query = "INSERT INTO transactions (user_id, amount, transaction_type, transaction_date) VALUES (?, ?, ?, NOW())";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, accountNumber);
        stmt.setDouble(2, amount);
        stmt.setString(3, type);
        stmt.executeUpdate();
    }

    private void handleDeposit(ObjectInputStream input, ObjectOutputStream output) throws Exception {
        try {
            String accountNumber = (String) input.readObject();
            double amount = input.readDouble();

            if (accountNumber == null || amount <= 0) {
                output.writeObject("Invalid deposit request.");
                return;
            }

            // First check if the account exists
            String checkQuery = "SELECT user_id FROM accounts WHERE user_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
            checkStmt.setString(1, accountNumber);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                output.writeObject("Deposit Failed - Account not found!");
                return;
            }

            conn.setAutoCommit(false);
            try {
                String query = "UPDATE accounts SET balance = balance + ? WHERE user_id = ?";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setDouble(1, amount);
                stmt.setString(2, accountNumber);

                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    logTransaction(accountNumber, "Deposit", amount, null);
                    conn.commit();
                    output.writeObject("Deposit Successful!");
                } else {
                    conn.rollback();
                    output.writeObject("Deposit Failed!");
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (EOFException e) {
            System.out.println("Client disconnected while depositing.");
        }
    }

    private void handleWithdraw(ObjectInputStream input, ObjectOutputStream output) throws Exception {
        try {
            String accountNumber = (String) input.readObject();
            double amount = input.readDouble();

            if (accountNumber == null || amount <= 0) {
                output.writeObject("Invalid withdrawal request.");
                return;
            }

            // First check if the account exists
            String checkQuery = "SELECT user_id FROM accounts WHERE user_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
            checkStmt.setString(1, accountNumber);
            ResultSet checkRs = checkStmt.executeQuery();

            if (!checkRs.next()) {
                output.writeObject("Withdrawal Failed - Account not found!");
                return;
            }

            conn.setAutoCommit(false);
            try {
                String balanceQuery = "SELECT balance FROM accounts WHERE user_id = ? FOR UPDATE";
                PreparedStatement balanceStmt = conn.prepareStatement(balanceQuery);
                balanceStmt.setString(1, accountNumber);
                ResultSet rs = balanceStmt.executeQuery();

                if (rs.next() && rs.getDouble("balance") >= amount) {
                    String updateQuery = "UPDATE accounts SET balance = balance - ? WHERE user_id = ?";
                    PreparedStatement stmt = conn.prepareStatement(updateQuery);
                    stmt.setDouble(1, amount);
                    stmt.setString(2, accountNumber);

                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        logTransaction(accountNumber, "Withdrawal", amount, null);
                        conn.commit();
                        output.writeObject("Withdrawal Successful!");
                    } else {
                        conn.rollback();
                        output.writeObject("Withdrawal Failed!");
                    }
                } else {
                    conn.rollback();
                    output.writeObject("Insufficient Balance!");
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (EOFException e) {
            System.out.println("Client disconnected while withdrawing.");
        }
    }

    private void handleTransactionHistory(ObjectInputStream input, ObjectOutputStream output) throws Exception {
        try {
            String accountNumber = (String) input.readObject();

            if (accountNumber == null) {
                output.writeObject("Invalid account number.");
                return;
            }

            // First get the current balance
            String balanceQuery = "SELECT balance FROM accounts WHERE user_id = ?";
            PreparedStatement balanceStmt = conn.prepareStatement(balanceQuery);
            balanceStmt.setString(1, accountNumber);
            ResultSet balanceRs = balanceStmt.executeQuery();

            StringBuilder history = new StringBuilder();
            if (balanceRs.next()) {
                history.append("Current Balance: ₹").append(balanceRs.getDouble("balance")).append("\n\n");
            }

            String query = "SELECT transaction_type, amount, transaction_date FROM transactions WHERE user_id = ? ORDER BY transaction_date DESC";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, accountNumber);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                history.append(rs.getString("transaction_date")).append(" - ")
                       .append(rs.getString("transaction_type")).append(": ₹")
                       .append(rs.getDouble("amount"))
                       .append("\n");
            }
            output.writeObject(history.toString().isEmpty() ? "No Transactions Found!" : history.toString());
        } catch (EOFException e) {
            System.out.println("Client disconnected while fetching transaction history.");
        }
    }
}