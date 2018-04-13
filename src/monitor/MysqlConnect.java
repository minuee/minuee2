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

public final class MysqlConnect {
	private Connection conn;
	private Statement statement;
	private ResultSet resultSet;
	private static MysqlConnect db;
	public static final Logger logger = Logger.getLogger(MysqlConnect.class);
	

	public static String ip = null;
	public static int port = 0;
	public static String dbName = null;
	public static String userName = null;
	public static String password = null;

	private MysqlConnect() {
		String driver = "com.mysql.jdbc.Driver";
		
		PropertyConfigurator.configure("./conf/Log4j.properties");
		
		try {
    		Properties properties = new Properties();
            properties.load(new FileInputStream("database.properties"));
            ip = properties.get("target.mysqldb.ip").toString();
            port = Integer.parseInt(properties.get("target.mysqldb.port").toString());
            dbName = properties.get("target.mysqldb.name").toString();
            userName = properties.get("target.mysqldb.user").toString();
            password = properties.get("target.mysqldb.password").toString();
        } catch (Exception e) {
            logger.error(e);
            logger.debug(e);
            return;
        }
		
		// Master
    	String url = "jdbc:mysql://" + ip + "/";
		/*String dbName = "test";
		String userName = "minuee";
		String password = "lenapark47";*/
		
		try {
			Class.forName(driver).newInstance();
			this.conn = (Connection) DriverManager.getConnection(url + dbName, userName, password);

		} catch (Exception sqle) {
			sqle.printStackTrace();
		}
	}

	public static synchronized MysqlConnect getConn() {
		if (db == null) {
			db = new MysqlConnect();
		}

		return db;
	}

	public ResultSet query(String query) throws SQLException {
		statement = conn.createStatement();
		resultSet = statement.executeQuery(query);
		return resultSet;
	}

	public int insert(String insertQuery) throws SQLException {
		int result;

		
		statement = conn.createStatement();
		result = statement.executeUpdate(insertQuery);
		
		
		return result;
	}

	public int update(String updateQuery) throws SQLException {
		statement = conn.createStatement();
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

			if (conn != null) {
				conn.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}