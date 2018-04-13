package monitor;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.sql.ResultSet;


import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;



import monitor.MysqlConnect;
import monitor.MssqlConnect;
import monitor.SearchServer;
import monitor.InDetailServer.DownPortInfo;
import monitor.IpStatusConfig;

public class monitor {
	
	public static final String VERSION = "1.0.1";	//	2018-04-12	코드 확인.
	
	
	public static final Logger logger = Logger.getLogger(monitor.class);

	private static String myAddress = "121.78.235.247";
	public static ArrayList<DownPortInfo> downPortInfoArray;
	private static final String UP = " is UP!!";
	private static final String DOWN = " is DOWN!!";
	
	private static ArrayList<IpStatusConfig> ipInfo;

	
	private static final int CHECK_FIRST = 0;
	private static final int CHECK_AGAIN = 1;
	private static final int TYPE_ICMP = 1;
	private static final int TYPE_ICMP_PV = 2;
	
	private static final int CONNECT_TIMEOUT = 2000;
	private static final int HTTP_TIMEOUT = 5000;
	
	private static MysqlConnect db;
	private static MssqlConnect mssqldb;
	
	
	
	public static void main(String[] args) throws InterruptedException, SQLException {
		
		/*PropertyConfigurator.configure("E:/eclipse/eclipse/workplace/Monitor/Log4j.properties");*/
		PropertyConfigurator.configure("./conf/Log4j.properties");
		downPortInfoArray = new ArrayList<DownPortInfo>();
	
		if (args.length > 0) {
			if (args[0].toLowerCase().equals("-v") ||
					args[0].toLowerCase().equals("--v") ||
					args[0].toLowerCase().equals("-version") ||
					args[0].toLowerCase().equals("--version")) {
				System.out.println("startMon v " + VERSION);
				System.out.println();
				return;
			}
		}
		
		logger.info("Start IP Check By.Noh");
		
		
		long startTime = System.currentTimeMillis();
//		System.out.println("ftpAddress : " + myAddress);
//		icmpCheck(myAddress);
//		
/*		monitor_test pInfo1=new monitor_test("이정훈", "323-1111", "92,09,12");
		monitor_test pInfo2=new monitor_test("김효준", "321-2222");
		pInfo1.showPhoneInfo();
		pInfo2.showPhoneInfo();
*/		
		
		db = MysqlConnect.getConn();
		mssqldb = MssqlConnect.getConn();
		
		// 모니터링 설정 확인.
		String msqueryMntr = "select top 10 * from ec_Server ORDER BY ServerID desc";
		ResultSet rsMntrmssql = mssqldb.query(msqueryMntr);
		while (rsMntrmssql.next()) {
			System.out.println("SrvIP : " + rsMntrmssql.getString("SrvIP"));
		}
		
		// 모니터링 설정 확인.
		String queryMntr = "select * from test.server";
		String stricmpresult;
		ResultSet rsMntr = db.query(queryMntr);
		ipInfo = new ArrayList<IpStatusConfig>();
		while (rsMntr.next()) {
			
						
			
			if( !rsMntr.getString("server_ip").equals("")  ) {
				
				IpStatusConfig ipStatusConfig = new IpStatusConfig();
				ipStatusConfig.setIp(rsMntr.getString("server_ip"));
				ipStatusConfig.setIdx(rsMntr.getInt("idx"));
				ipStatusConfig.setIcmp_status(rsMntr.getString("icmp_status"));
				ipStatusConfig.setPort_status(rsMntr.getString("port_status"));
				
				ipInfo.add(ipStatusConfig);
				
				
				//System.out.println("ftpAddress : " + rsMntr.getString("server_ip"));
				//boolean icmpresult = icmpCheck(rsMntr.getString("server_ip"));
				//if( Boolean.TRUE.equals(icmpresult) ) stricmpresult = "Y";
				//else stricmpresult = "N";
				
				//if( !rsMntr.getString("icmp_status").equals(icmpresult)  ) {
					
				//	String queryup = "UPDATE test.server  SET icmp_status = '" + stricmpresult + "' WHERE idx = " + rsMntr.getInt("idx");
				//	System.out.println(queryup);
				//	db.update(queryup);
				//}
				
				//Thread.sleep(1000);
				//httpPortCheck(rsMntr.getString("server_ip"),"80", rsMntr.getInt("idx"));
			}
			
			
			
			
		}
		
		logger.info(ipInfo.size() + " ip detected.");
		ArrayList<Thread> threadList = new ArrayList<Thread>();
		for (int i = 0; i < ipInfo.size(); i++) {
			Thread checkThread = new Thread(new SearchServer(ipInfo.get(i)));
			checkThread.start();
			threadList.add(checkThread);
		}
		
		// 모든 쓰레드 종료까지 대기
		for (Thread t : threadList) {
			t.join();
		}
		
		db.close();
		long elapsedTime = System.currentTimeMillis() - startTime;
		String elapsed = String.format("%02d:%02d:%02d",
				TimeUnit.MILLISECONDS.toHours(elapsedTime),
				TimeUnit.MILLISECONDS.toMinutes(elapsedTime) -
				TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(elapsedTime)),
				TimeUnit.MILLISECONDS.toSeconds(elapsedTime) -
				TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedTime)));
		
		
		if (downPortInfoArray.size() > 0) {
			logger.info("┌─────────────────┬─────────┬─────┐");
			logger.info("|       IP        │   PORT  │ CNT |");
			logger.info("├─────────────────┼─────────┼─────┤");
			for (int i = 0; i < downPortInfoArray.size(); i++) {
				logger.info("│ " + String.format("%-15s", downPortInfoArray.get(i).getIpAddress()) + " │ " + String.format("%-7s", downPortInfoArray.get(i).getPort()) + " │  " + String.format("%d", downPortInfoArray.get(i).getDownCnt()) + "  │");
			}
			logger.info("└─────────────────┴─────────┴─────┘");
		}
		logger.info("End IP Check By Noh, Elapsed: " + elapsed);
		
	}
	
	public static boolean icmpCheck( String ipaddr ) {
		String ipAddress = ipaddr;
		String port = null;
		
		System.out.println(ipAddress + " ping Start");
		List<String> sendIcmp = new ArrayList<String>();
		sendIcmp.add("ping");
		
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			sendIcmp.add("-n");
		} else {
			sendIcmp.add("-c");
		}
		
		sendIcmp.add("1");
		sendIcmp.add("-w");
		sendIcmp.add("2");
		sendIcmp.add(ipAddress);
		
		try {
			Process proc = new ProcessBuilder(sendIcmp).start();
			int exitValue = proc.waitFor();
			
			if (exitValue == 0) {
				//downPortList.remove(port);
				//upPortList.add(port);
				//StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + UP);
				System.out.println(ipAddress + " ping OK");
				return true;
			} else {
			
				
				/********************************/
				/** [ICMP or ICMP_PV] NG POINT **/
				/********************************/
				/*String msg = SMS_PREFIX + ipAddress + " " + port + DOWN;
				if (downPortHashMap.containsKey(port)) {
					downPortHashMap.get(port).increaseDownCnt();
				} else {
					downPortHashMap.put(port, new DownPortInfo(ipAddress, msg, port));
				}
				
				StartMonitor.logger.error(LOG_PREFIX + String.format("%-15s", ipAddress) + " " + String.format("%-7s", port) + DOWN + " (" + downPortHashMap.get(port).getDownCnt() + "/" + MAX_TRY + ")");
				*/
				System.out.println(ipAddress + " ping NG");
				return false;
			}
		} catch (IOException e) {
			//StartMonitor.logger.debug(LOG_PREFIX + LOG_IP + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	private static void httpPortCheck(String ipaddr, String port, int stridx) {
		boolean httpFail = false;
		String msg = "";
		
		try {
			int resCode = -1;
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(ipaddr, Integer.parseInt(port)), CONNECT_TIMEOUT);
			socket.close();
			
			httpFail = true;
			HttpURLConnection con = null;
			URL url = new URL("http://" + ipaddr + ":" + port);
			con = (HttpURLConnection) url.openConnection();

			// Add request header
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
			con.setRequestProperty("Content-Length", "length");
			
			// Set timeout
			con.setConnectTimeout(CONNECT_TIMEOUT);
			con.setReadTimeout(HTTP_TIMEOUT);

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.flush();
			
			// Actually request
			resCode = con.getResponseCode();
			if (resCode == -1) {
				String queryup = "UPDATE test.server  SET port_status = 'N' WHERE idx = " + stridx;
				System.out.println(queryup);
				db.update(queryup);
				return;
			}else {
				String queryup = "UPDATE test.server  SET port_status = 'Y' WHERE idx = " + stridx;
				System.out.println(queryup);
				db.update(queryup);
			}
			

		} catch (Exception e) {
			
		
			try {
				
				String queryup = "UPDATE test.server  SET port_status = 'N' WHERE idx = " + stridx;
				System.out.println(queryup);
				db.update(queryup);
			} catch (SQLException e1) {
				e1.printStackTrace();
			}		
		}
	}

	 private static void printUsage()
    {
        System.out.print("Usage : Icmp_Checker [options]");
        System.out.println();
        System.out.println(":: options");
        System.out.println(":::: -addr [IP]               : Kftp IP address");
        System.out.println(":::: -site [Site ID]               : Site ID");
        System.out.println(":::: -f [PREFIX PATH]              : upload file prefix path");
        System.out.println(":::: -s [START_TIME] -e [END_TIME] : Operation time of 24 hours");
        System.out.println(":::: -t [FILE_COUNT]               : Upload file count");
        System.out.println(":::: -type                         : Dump Type(c: Kcloud, m: Kmobile)");
        System.out.println();
    }

    public static int Time2Min(String time)
    {
        if (time.isEmpty())
            return 0;
        if (4 != time.length() && 5 != time.length())
            return 0;

        int pos = time.indexOf(":");
        String h = "00";
        String m = "00";

        if (-1 < pos)
        {
            h = time.substring(0, pos);
            m = time.substring(pos + 1);
        }
        else
        {
            h = time.substring(0, 2);
            m = time.substring(2);
        }

        return Integer.parseInt(h) * 60 + Integer.parseInt(m);
    }

    public static String Speed2String(double speed)
    {
        double d = speed;
        int cnt = 0;

        while (1024 <= d)
        {
            d /= 1024;
            cnt++;

            if (4 <= cnt)
                break;
        }

        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(false);

        String result = nf.format(d);
        switch (cnt)
        {
            case 0: result += "Bps"; break;
            case 1: result += "KBps"; break;
            case 2: result += "MBps"; break;
            case 3: result += "GBps"; break;
            case 4: result += "TBps"; break;
        }

        return result;
    }

    public static String Speed2String_v2(double speed)
    {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(3);
        nf.setGroupingUsed(false);

        String result = nf.format(speed);

        return result;
    }

    public static String Millis2Date(long millis)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(millis));
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(cal.getTime());
    }
	
	
	

}


class monitor_test {
	
	String name;
	String phoneNumber;
	String birth;
	
	public monitor_test(String name, String num, String birth)
	{
		this.name=name;
		phoneNumber=num;
		this.birth=birth;
	}
	public monitor_test(String name, String num)
	{
		this.name=name;
		phoneNumber=num;
		this.birth=null;
	}
	public void showPhoneInfo()
	{
		System.out.println("name: "+name);
		System.out.println("phone: "+phoneNumber);
		if(birth!=null)
			System.out.println("birth: "+birth);
		
		System.out.println("");	// 데이터 구분을 위해
	}

}