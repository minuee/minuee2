package monitor;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class MssqlConnect {
	
	private Connection mssqlconn;
	private Statement statement;
	private ResultSet resultSet;
	private static MssqlConnect mssqldb;
	public static final Logger logger = Logger.getLogger(MssqlConnect.class);
	
	
	
	public static String ip = null;
	public static int port = 0;
	public static String dbName = null;
	public static String userName = null;
	public static String password = null;
	
	private MssqlConnect() {
		
		String mssqldriver = "com.microsoft.sqlserver.jdbc.SQLServerDriver"; 
			
		
		PropertyConfigurator.configure("./conf/Log4j.properties");
		
		try {
    		Properties properties = new Properties();
            properties.load(new FileInputStream("database.properties"));
            ip = properties.get("target.mssqldb.ip").toString();
            port = Integer.parseInt(properties.get("target.mssqldb.port").toString());
            dbName = properties.get("target.mssqldb.name").toString();
            userName = properties.get("target.mssqldb.user").toString();
            password = properties.get("target.mssqldb.password").toString();
        } catch (Exception e) {
            logger.error(e);
            logger.debug(e);
            return;
        }
		
		// Master
    	String Mssqlconurl = "jdbc:sqlserver://" + ip + ":1433;databaseName=" + dbName + ";user=" + userName + ";password="+password;
		/*String dbName = "test";
		String userName = "minuee";
		String password = "lenapark47";*/
    	System.out.println("SrvIP : " + Mssqlconurl);
		try {
			Class.forName(mssqldriver);
			this.mssqlconn = DriverManager.getConnection(Mssqlconurl);

		} catch (Exception sqle) {
			sqle.printStackTrace();
		}
	}
	
	public static synchronized MssqlConnect getConn() {
		if (mssqldb == null) {
			mssqldb = new MssqlConnect();
		}

		return mssqldb;
	}

	public ResultSet query(String query) throws SQLException {
		statement = mssqlconn.createStatement();
		resultSet = statement.executeQuery(query);
		return resultSet;
	}

	public int insert(String insertQuery) throws SQLException {
		int result;

		
		statement = mssqlconn.createStatement();
		result = statement.executeUpdate(insertQuery);
		
		
		return result;
	}

	public int update(String updateQuery) throws SQLException {
		statement = mssqlconn.createStatement();
		int result = statement.executeUpdate(updateQuery);
		return result;
	}

	public void close() {
		try {
			if (resultSet != null) {
				resultSet.close();
			}

			if (statement != null) {
				statement.close();
			}

			if (mssqlconn != null) {
				mssqlconn.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
