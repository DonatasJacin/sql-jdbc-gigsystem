import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.io.IOException;
import java.util.Properties;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Vector;

public class GigSystem {

    public static void main(String[] args) {
        Connection conn = getSocketConnection();

        boolean repeatMenu = true;
        
        while(repeatMenu){
            System.out.println("_________________________");
            System.out.println("________GigSystem________");
            System.out.println("_________________________");

            System.out.println("1: Gig Line-Up");
            System.out.println("q: Quit");
            

            String menuChoice = readEntry("Please choose an option: ");

            if(menuChoice.length() == 0){
                //Nothing was typed (user just pressed enter) so start the loop again
                continue;
            }
            char option = menuChoice.charAt(0);

            switch(option){
                case '1':
                    break;
                case '2':
                    break;
                case '3':
                    break;
                case '4':
                    break;
                case '5':
                    break;
                case '6':
                    break;
                case '7':
                    break;
                case '8':
                    break;
                case 'q':
                    repeatMenu = false;
                    break;
                default: 
                    System.out.println("Invalid option");
            }
        }
    }


    public static String[][] option1(Connection conn, int gigID){
        String selectQuery = "SELECT actname, ontime::time, (ontime::time + (SELECT CONCAT(act_gig.duration::varchar, ' minutes'))::interval) AS offtime FROM act JOIN act_gig ON act_gig.actID = act.actID WHERE gigID = ? ORDER BY ontime";
        try{
            //preparedStatement made scrollable so rows can be calculated easily
            PreparedStatement preparedStatement = conn.prepareStatement(selectQuery, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            preparedStatement.setInt(1, gigID);
            ResultSet lineUp = preparedStatement.executeQuery();
            lineUp.last(); //Amount of rows in the result of the query calculated
            int totalRows = lineUp.getRow();
            lineUp.beforeFirst();
            int row = 0;
            String[][] output = new String[totalRows][3]; //Output string created using total rows in result of query
            while (lineUp.next()){
                output[row][0] = lineUp.getString(1);
                output[row][1] = lineUp.getString(2);
                output[row][2] = lineUp.getString(3);
                row += 1;
            }

            printTable(output);
            preparedStatement.close();
            lineUp.close();
            return output;

        }catch(SQLException e){//Any database violations will be caught as SQLExceptions
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    public static void option2(Connection conn, String venue, String gigTitle, int[] actIDs, int[] fees, LocalDateTime[] onTimes, int[] durations, int adultTicketPrice){
        try{
            //GET VENUEID USING VENUENAME
            String selectQuery = "SELECT venue.venueid FROM venue, gig WHERE gig.venueid = venue.venueid AND venue.venuename = ? LIMIT 1";
            PreparedStatement queryVenueID = conn.prepareStatement(selectQuery);
            queryVenueID.setString(1, venue);
            ResultSet venueIDRS = queryVenueID.executeQuery();
            venueIDRS.next();
            int venueID = venueIDRS.getInt(1);

            //INSERT INTO GIG
            conn.setAutoCommit(false);
            PreparedStatement insertStatementGig = conn.prepareStatement("INSERT INTO gig(venueid,gigtitle,gigdate) VALUES (?,?,?)");
            LocalDateTime gigDate = onTimes[0];
            Timestamp gigDateTimestamp = Timestamp.valueOf(gigDate); 
            insertStatementGig.setInt(1, venueID);
            insertStatementGig.setString(2, gigTitle);
            insertStatementGig.setTimestamp(3, gigDateTimestamp);
            int rowsChanged = insertStatementGig.executeUpdate();

            //GET GIGID USING GIGNAME AND GIGTITILE
            selectQuery = "SELECT gigID FROM gig WHERE gig.gigtitle = ? AND gig.gigdate = ?";
            PreparedStatement queryGigID = conn.prepareStatement(selectQuery);
            queryGigID.setString(1, gigTitle);
            queryGigID.setTimestamp(2, gigDateTimestamp);
            ResultSet gigIDRS = queryGigID.executeQuery();
            gigIDRS.next();
            int gigID = gigIDRS.getInt(1);

            //INSERT INTO ACT_GIG
            PreparedStatement insertStatementActGig = conn.prepareStatement("INSERT INTO act_gig (actID,gigID,actfee,ontime,duration) VALUES (?,?,?,?,?)");
            for (int i = 0; i < actIDs.length; i++){
                insertStatementActGig.setInt(1, actIDs[i]);
                insertStatementActGig.setInt(2, gigID);
                insertStatementActGig.setInt(3, fees[i]);
                LocalDateTime onTimesLDT = onTimes[i];
                Timestamp onTimesTimestamp = Timestamp.valueOf(onTimesLDT);
                insertStatementActGig.setTimestamp(4, onTimesTimestamp);
                insertStatementActGig.setInt(5, durations[i]);
                rowsChanged += insertStatementActGig.executeUpdate();
            }
            insertStatementActGig.close();
            PreparedStatement insertStatementTicket = conn.prepareStatement("INSERT INTO gig_ticket(gigID,pricetype,cost) VALUES (?,?,?)");
            insertStatementTicket.setInt(1, gigID);
            insertStatementTicket.setString(2, "A");
            insertStatementTicket.setInt(3, adultTicketPrice);
            rowsChanged += insertStatementTicket.executeUpdate();
            //COMMIT IF NO ERRORS OCCURED YET
            conn.commit();

        }catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }
        
    }

    public static void option3(Connection conn, int gigid, String name, String email, String ticketType){
        try{
            conn.setAutoCommit(false);
            int rowsChanged = 0;

            //GET COST OF TICKET USING GIGID
            String selectQuery = "SELECT gig_ticket.cost FROM gig_ticket WHERE gig_ticket.gigID = ? AND gig_ticket.pricetype = ?";
            PreparedStatement queryTicketCost = conn.prepareStatement(selectQuery);
            queryTicketCost.setInt(1, gigid);
            queryTicketCost.setString(2, ticketType);
            ResultSet ticketCostRS = queryTicketCost.executeQuery();
            ticketCostRS.next();
            int tickCost = ticketCostRS.getInt(1); 

            //INSERT TICKET
            PreparedStatement insertStatement = conn.prepareStatement("INSERT INTO ticket(gigID,pricetype,cost,CustomerName,CustomerEmail) VALUES (?,?,?,?,?)");
            insertStatement.setInt(1, gigid);
            insertStatement.setString(2, ticketType);
            insertStatement.setInt(3, tickCost);
            insertStatement.setString(4, name);
            insertStatement.setString(5, email);
            rowsChanged += insertStatement.executeUpdate();

            //COMMIT IF NO ERRORS OCCURED YET
            conn.commit();
        }catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }
    }

    public static String[] option4(Connection conn, int gigID, String actName){
        try{
            conn.setAutoCommit(false);
            String selectQuery = "SELECT (selectedOntime.ontime = lastAct.ontime) FROM (SELECT act_gig.ontime FROM (SELECT act.actID FROM act WHERE act.actname = ?) AS actIDI, act_gig WHERE act_gig.actID = actIDI.actID and act_gig.gigID = ?) AS selectedOntime,(SELECT ontime FROM act_gig WHERE act_gig.gigID = ? ORDER BY ontime DESC LIMIT 1) AS lastAct";
            PreparedStatement queryActLastBool = conn.prepareStatement(selectQuery);
            queryActLastBool.setString(1, actName);
            queryActLastBool.setInt(2, gigID);
            queryActLastBool.setInt(3, gigID);
            ResultSet actLastBoolRS = queryActLastBool.executeQuery();
            actLastBoolRS.next();
            boolean actLastBool = actLastBoolRS.getBoolean(1);
            PreparedStatement deleteStatement = conn.prepareStatement("DELETE FROM act_gig WHERE act_gig.actID IN (SELECT actID FROM act WHERE actname = ?) AND act_gig.gigID = ?");
            deleteStatement.setString(1, actName);
            deleteStatement.setInt(2, gigID);
            int rowsChanged = deleteStatement.executeUpdate();
            if (actLastBool == true){
                PreparedStatement updateStatement = conn.prepareStatement("UPDATE gig SET gigstatus = 'Cancelled' WHERE gigID = ?");
                updateStatement.setInt(1, gigID);
                PreparedStatement updateStatement1 = conn.prepareStatement("UPDATE ticket SET cost = 0 WHERE gigID = ?");
                updateStatement1.setInt(1, gigID);
                rowsChanged += updateStatement.executeUpdate();
                rowsChanged += updateStatement1.executeUpdate();
                selectQuery = "SELECT CustomerEmail FROM ticket WHERE gigID = ?";
                PreparedStatement queryCustEmail = conn.prepareStatement(selectQuery, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
                queryCustEmail.setInt(1, gigID);
                ResultSet custEmailsRS = queryCustEmail.executeQuery();
                custEmailsRS.last();
                int totalEmails = custEmailsRS.getRow();
                custEmailsRS.beforeFirst();
                int row = 0;
                String[] custEmails = new String[totalEmails];
                while (custEmailsRS.next()){
                    custEmails[row] = custEmailsRS.getString(1);
                    row += 1;
                }
                conn.commit();
                return custEmails;

            } else {
                conn.commit();
                return null;
            }

        }catch(SQLException e){ //Errors if gap more than 20 minutes
            if (e.getMessage() == "No more than 20 minute interval between acts in a gig line-up"){
                try{
                    PreparedStatement updateStatement = conn.prepareStatement("UPDATE gig SET gigstatus = 'Cancelled' WHERE gigID = ?");
                    updateStatement.setInt(1, gigID);
                    PreparedStatement updateStatement1 = conn.prepareStatement("UPDATE ticket SET cost = 0 WHERE gigID = ?");
                    updateStatement1.setInt(1, gigID);
                    int rowsChanged = updateStatement.executeUpdate();
                    rowsChanged += updateStatement1.executeUpdate();
                    String selectQuery = "SELECT CustomerEmail FROM ticket WHERE gigID = ?";
                    PreparedStatement queryCustEmail = conn.prepareStatement(selectQuery, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    queryCustEmail.setInt(1, gigID);
                    ResultSet custEmailsRS = queryCustEmail.executeQuery();
                    custEmailsRS.last();
                    int totalEmails = custEmailsRS.getRow();
                    custEmailsRS.beforeFirst();
                    int row = 0;
                    String[] custEmails = new String[totalEmails];
                    while (custEmailsRS.next()){
                        custEmails[row] = custEmailsRS.getString(1);
                        row += 1;
                    }
                    conn.commit();
                    return custEmails;
                }catch(SQLException e1){
                    System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
                    e.printStackTrace();  
                }
            }
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace(); 
        }
        return null;
    }

    public static String[][] option5(Connection conn){
        try{
            String selectQuery = "SELECT gig.gigID, (((SUM(act_gig.actfee) + MAX(venue.hirecost)) / MAX(gig_ticket.cost)) - MAX(countGigTickets.ticketCount)) AS totalcost FROM act_gig JOIN gig ON gig.gigID = act_gig.gigID JOIN venue ON venue.venueid = gig.venueid JOIN gig_ticket ON gig_ticket.gigID = gig.gigID, (SELECT gig.gigID, COUNT(ticket.gigID) AS ticketCount FROM gig LEFT JOIN ticket on gig.gigID = ticket.gigID GROUP BY gig.gigID) AS countGigTickets WHERE gig.gigID = countGigTickets.gigID GROUP BY gig.gigID ORDER BY gig.gigID";
            PreparedStatement queryStatement = conn.prepareStatement(selectQuery, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            ResultSet queryRS = queryStatement.executeQuery();
            queryRS.last();
            int totalRows = queryRS.getRow();
            queryRS.beforeFirst();
            int row = 0;
            String[][] output = new String[totalRows][2];
            while (queryRS.next()){
                output[row][0] = queryRS.getString(1);
                output[row][1] = queryRS.getString(2);
                row += 1;
            }
            printTable(output);
            return output;

        }catch(SQLException e){
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    public static String[][] option6(Connection conn){
        return null;
    }

    public static String[][] option7(Connection conn){
        return null;
    }

    public static String[][] option8(Connection conn){
        return null;
    }

    /**
     * Prompts the user for input
     * @param prompt Prompt for user input
     * @return the text the user typed
     */

    private static String readEntry(String prompt) {
        
        try {
            StringBuffer buffer = new StringBuffer();
            System.out.print(prompt);
            System.out.flush();
            int c = System.in.read();
            while(c != '\n' && c != -1) {
                buffer.append((char)c);
                c = System.in.read();
            }
            return buffer.toString().trim();
        } catch (IOException e) {
            return "";
        }

    }
     
    /**
    * Gets the connection to the database using the Postgres driver, connecting via unix sockets
    * @return A JDBC Connection object
    */
    public static Connection getSocketConnection(){
        Properties props = new Properties();
        props.setProperty("socketFactory", "org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg");
        props.setProperty("socketFactoryArg",System.getenv("HOME") + "/cs258-postgres/postgres/tmp/.s.PGSQL.5432");
        Connection conn;
        try{
          conn = DriverManager.getConnection("jdbc:postgresql://localhost/cwk", props);
          return conn;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets the connection to the database using the Postgres driver, connecting via TCP/IP port
     * @return A JDBC Connection object
     */
    public static Connection getPortConnection() {
        
        String user = "postgres";
        String passwrd = "password";
        Connection conn;

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException x) {
            System.out.println("Driver could not be loaded");
        }

        try {
            conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cwk?user="+ user +"&password=" + passwrd);
            return conn;
        } catch(SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            System.out.println("Error retrieving connection");
            return null;
        }
    }

    public static String[][] convertResultToStrings(ResultSet rs){
        Vector<String[]> output = null;
        String[][] out = null;
        try {
            int columns = rs.getMetaData().getColumnCount();
            output = new Vector<String[]>();
            int rows = 0;
            while(rs.next()){
                String[] thisRow = new String[columns];
                for(int i = 0; i < columns; i++){
                    thisRow[i] = rs.getString(i+1);
                }
                output.add(thisRow);
                rows++;
            }
            // System.out.println(rows + " rows and " + columns + " columns");
            out = new String[rows][columns];
            for(int i = 0; i < rows; i++){
                out[i] = output.get(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static void printTable(String[][] out){
        int numCols = out[0].length;
        int w = 20;
        int widths[] = new int[numCols];
        for(int i = 0; i < numCols; i++){
            widths[i] = w;
        }
        printTable(out,widths);
    }

    public static void printTable(String[][] out, int[] widths){
        for(int i = 0; i < out.length; i++){
            for(int j = 0; j < out[i].length; j++){
                System.out.format("%"+widths[j]+"s",out[i][j]);
                if(j < out[i].length - 1){
                    System.out.print(",");
                }
            }
            System.out.println();
        }
    }

}
