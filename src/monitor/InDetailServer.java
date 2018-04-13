package monitor;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import monitor.MysqlConnect;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class InDetailServer {
	private static MysqlConnect db;
	private IpStatusConfig ipInfo = null;
	private ArrayList<String> downPortList = null;
	private ArrayList<String> upPortList = null;
	private String serverLoad = "fail";
	
	
	public static final Logger logger = Logger.getLogger(monitor.class);

	/** NEW VARIABLE **/
	private String SMS_PREFIX;
	private String SMS_IP;
	private String LOG_PREFIX;
	private String LOG_IP;
	private static final String UP = " is UP!!";
	private static final String DOWN = " is DOWN!!";
	
	private static final int MAX_TRY = 3;
	private static final int CHECK_FIRST = 0;
	private static final int CHECK_AGAIN = 1;
	private static final int TYPE_ICMP = 1;
	private static final int TYPE_ICMP_PV = 2;
	
	private static final int CONNECT_TIMEOUT = 2000;
	private static final int HTTP_TIMEOUT = 5000;
	
	// ���μ��� ���� �� Down�� Port ����� �����ϴ� HashMap
	private LinkedHashMap<String, DownPortInfo> downPortHashMap = new LinkedHashMap<String, DownPortInfo>();
	/** NEW VARIABLE **/
	
	public InDetailServer(long threadId, IpStatusConfig ipInfo) {
		
		PropertyConfigurator.configure("./conf/Log4j2.properties");
		this.ipInfo = ipInfo;
		
		SMS_PREFIX = "[MON-KCLOUD]";
		SMS_IP = this.ipInfo.getIp() + " ";
		LOG_PREFIX = "ThreadId:" + String.format("%-4d", threadId) + " - ";
		LOG_IP = String.format("%-15s", this.ipInfo.getIp()) + " ";

		db = MysqlConnect.getConn();
		downPortList = new ArrayList<String>();
		upPortList = new ArrayList<String>();
	}

	public void detailCheck() throws InterruptedException, SQLException {
		
			//if (ipInfo.getCheck_ICMP()) {
				// 1. LOAD
				getLoadInfo();

				// 2. ICMP
				
				Boolean ssLoad = icmpCheck(CHECK_FIRST, TYPE_ICMP);
				if (!ssLoad.equals(true)) {
					for (int tryCnt = 0; tryCnt < MAX_TRY; tryCnt++) {
						
						Boolean ssLoad2 = icmpCheck(CHECK_FIRST, TYPE_ICMP);
						logger.info("tryCnt :"  + tryCnt + "MAX_TRY :"  + MAX_TRY+ "ssLoad2 :"  + ssLoad2);
						if (!ssLoad2.equals(false)) {
							break;
						}
					}
				}
				
				
			//}

			/*if (ipInfo.getCheck_pv_ICMP()) {
				// 3. ICMP_PV
				icmpCheck(CHECK_FIRST, TYPE_ICMP_PV);
				
			}*/
			
			// ICMP, ICMP_PV�� ������ ������ ��Ʈ
			/*if (ipInfo.getCheck_port_no() != null) {
				if (!ipInfo.getCheck_port_no().isEmpty()) {
					// 4. OPEN PORT
					portCheck(CHECK_FIRST);
				}
			}*/

			// 5. DOWN PORT NO
			/*upCheck();*/

			/*// 6. is exist (1. ~ 5. down port)
			if (downPortList.size() > 0) {
				// 7. ������ ��Ʈ
				downCheck();
			} else {
				// IF NO DOWN
				if (ipInfo.getCalll() > 0) {
					String updateQuery = "UPDATE monitor.ip SET calll = '0', down_port_no = 'null' WHERE ip = '" + ipInfo.getIp() + "' AND rack = '" + ipInfo.getRack() + "'";
					updateDB(updateQuery);
				}
			}*/
			
			// �۾� �Ϸ� �ð� DB Update
			/*String updateQuery = "UPDATE monitor.mntr_config SET daemon_lasttimeV2 = NOW() WHERE refresh = 300";
			updateDB(updateQuery);*/
			
		
	}
	
	// Load Ȯ��
	private void getLoadInfo() {
		try {
			GetLoad getLoad = new GetLoad();
			String sLoad = getLoad.getSnmpLoad(ipInfo.getIp());

			if (sLoad != "ERROR") {
				String[] loadArr = sLoad.split("=");
                serverLoad = loadArr[1].trim();

				if (serverLoad.compareTo("C:") == 0) {
					logger.info(LOG_PREFIX + LOG_IP + "load " + sLoad);
					serverLoad = "fail";
					return;
				}
				
				logger.info(LOG_PREFIX + LOG_IP + "load " + sLoad);
				
			
			} else {
				logger.info(LOG_PREFIX + LOG_IP + "- uptime: " + sLoad);
			}
		} catch (Exception e) {
			logger.info(LOG_PREFIX + LOG_IP + "- uptime: " + e.getMessage());
			
		}
	}

	private void upCheck() throws SQLException {
		String upPort = null;
		String downPort = null;
		final List<String> list = new ArrayList<String>();
		
		// TODO ���� DOWN �� PORT ����Ʈ �α� ���!!
		
		// ���� ���� �� down �ƾ��� port���� ������
		if (ipInfo.getDown_port_no() != null) {
			String[] downPortArr = ipInfo.getDown_port_no().split(",");
			Collections.addAll(list, downPortArr);

			for (int i = 0; i < upPortList.size(); i++) {
				for (int j = 0; j < list.size(); j++) {
					if (list.get(j).compareTo(upPortList.get(i)) == 0) {
						if (upPort == null) {
							upPort = upPortList.get(i);
						} else {
							upPort += "," + upPortList.get(i);
						}
						
						break;
					}
				}
				
				// ���� ���۽� down �ƾ��� port�� �� ���� up ������ port�� ����
				list.remove(upPortList.get(i));
			}
		}
		
		// ���� ���۽� down �ƾ��� port�� �� ���絵 down�� port
		for (int i = 0; i < list.size(); i++) {
			if (downPort == null) {
				downPort = list.get(i);
			} else {
				downPort += "," + list.get(i);
			}
		}

		// ���� üũ ���� �� down port ����Ʈ �� up port�� ������
		if (upPort != null) {
			
			logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", upPort) + UP);
			/*if (MntrConfig.getDebug_admin_sms() & MntrConfig.getRecovery_check()) {
				String msg = SMS_PREFIX + SMS_IP + upPort + UP;
				sendMessage(msg);
				
				// UP Message ���� (monitor.process_ctrl)
				String insertAll = "INSERT INTO process_ctrl (ip_pv, msg, time) VALUES ('" + ipInfo.getIp() + "', '" + msg + "', NOW())";
				updateDB(insertAll);
			}*/
		}

		// DOWN PORT & LOAD ������Ʈ (monitor.ip)
		String updateAll = "UPDATE monitor.ip SET down_port_no = '" + downPort + "' , server_load = '" + serverLoad + "' WHERE ip = '" + ipInfo.getIp() + "' AND rack = '" + ipInfo.getRack() + "'";
		updateDB(updateAll);
	}
	
	/*// ���� �� down port ��Ȯ��
	private void downCheck() {
		int tryCnt = 0;
		
		// monitor.mntr_config �� SLIP_CHECK_COUNT �� ��ŭ �� Ȯ��
		for (; tryCnt < MntrConfig.getSLIP_CHECK_COUNT(); tryCnt++) {
			try {
				// �׽�Ʈ�� ��� sleep time�� ���ݸ� sleep
				int division = (StartMonitor.DEMO_TEST && System.getProperty("os.name").toLowerCase().startsWith("windows")) ?  2 : 1;
				Thread.sleep(MntrConfig.getSlip_time() * 1000 / division);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			// ������ port �� Ȯ��.
			portCheck(CHECK_AGAIN);
			if (downPortList.size() == 0) {
				break;
			}
		}
		
		// �� down port���� down count �� DB�� ���� �� ��� ����
		Set<String> keySet = downPortHashMap.keySet();
		Iterator<String> iter = keySet.iterator();
		while (iter.hasNext()) {
			String port = iter.next();
			DownPortInfo downPortInfo = downPortHashMap.get(port);
			
			// DB ����
			insertCheckDown(downPortInfo);
			
			// LOG�� ��ü ����
			StartMonitor.downPortInfoArray.add(downPortInfo);
		}
		
		// �� Ȯ�� 3ȸ ��� ���� ��(������ Ȯ�� �Ŀ��� Down Port ���� ��)
		if (tryCnt == MntrConfig.getSLIP_CHECK_COUNT()) {
			String downPort = "";
			String resultDown = "";
			int calll = ipInfo.getCalll();
			calll++;
			
			try {
				for (int i = 0; i < downPortList.size(); i++) {
					
//					if (i > 0) {
//						downPort += ", ";
//					}
//					
//					downPort += downPortList.get(i);
					
					if (downPort == null) {
                        downPort = downPortList.get(i).toString();
                    } else {
                        downPort = downPort + "," + downPortList.get(i).toString();
                    }
				}
				
				String queryMntr = "SELECT down_port_no FROM monitor.ip WHERE ip = '" + ipInfo.getIp() + "' AND rack = '" + ipInfo.getRack() + "'";
				ResultSet rsMntr = db.query(queryMntr);
				String nowDownPort = null;
				while (rsMntr.next()) {
					if (rsMntr.getString("down_port_no").compareTo("null") == 1) {
						nowDownPort = rsMntr.getString("down_port_no");
					}
				}
				
				if (nowDownPort != null) {
					nowDownPort += "," + downPort;
				} else {
					nowDownPort = downPort;
				}
				
				String[] resultDownPort = null;
				if (nowDownPort.contains(",")) {
					resultDownPort = nowDownPort.split(",");
				} else {
					resultDownPort = nowDownPort.split(" ");
				}
				
//				Object[] noDupDownPort = removeDuplicateArray(resultDownPort);
//				for (int i = 0; i < noDupDownPort.length; i++) {
//					if (i > 0) {
//						resultDown += ",";
//					}
//					
//					resultDown += noDupDownPort[i].toString();
//				}
				
				Object[] noDupDownPort = removeDuplicateArray(resultDownPort);
                for (int i = 0; i < noDupDownPort.length; i++) {
                    if (resultDown == null && resultDown != "") {
                        resultDown = (String) noDupDownPort[i];
                    } else {
                        resultDown += "," + (String) noDupDownPort[i];
                    }
                }
				
				if (downPort != null) {
					
					String msg = SMS_PREFIX + SMS_IP + downPort + DOWN;
					sendMessage(msg);
					StartMonitor.logger.error(LOG_PREFIX + LOG_IP + resultDown + DOWN);
					
					String insertAll = "INSERT INTO process_ctrl (ip_pv, msg, time) VALUES ('" + ipInfo.getIp() + "', '" + msg + "', NOW())";
					updateDB(insertAll);
				}
				
				String updateAll = "UPDATE monitor.ip SET down_port_no = '" + resultDown + "', calll = '" + calll + "' WHERE ip = '" + ipInfo.getIp() + "' AND rack = '" + ipInfo.getRack() + "'";
				updateDB(updateAll);
			} catch (SQLException e) {
				StartMonitor.logger.debug(LOG_PREFIX + LOG_IP + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	*/
	// check_down�� down �� port ��� ����
	/*private void insertCheckDown(DownPortInfo downPortInfo) {
		*//**
		 * icmp(1), icmp_pv(2), ftp(3), http(4)
		 **//*
		String ip = downPortInfo.getIpAddress();
		String msg = downPortInfo.getMsg();
		String code = downPortInfo.getPort();
		int downCnt = downPortInfo.getDownCnt();
		String query = "INSERT INTO check_down (ip, msg, code, downcnt, getdate) VALUES ('" + ip + "', '" + msg + "', '" + code + "', " + downCnt + ", NOW())";
		
		try {
			db.insert(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}*/
	
	private void updateDB(String query) throws SQLException {
		db.update(query);
	
	}
	
	// Slave DB Insert ��
	private void updateDBSlave(String query) throws SQLException {
		db.insert(query);
	}
	
	private boolean icmpCheck(int checkType, int protocolType) {
		String ipAddress = null;
		String port = null;
		
		switch (protocolType) {
		case TYPE_ICMP:
			ipAddress = ipInfo.getIp();
			port = "icmp";
			break;
			
		case TYPE_ICMP_PV:
			ipAddress = ipInfo.getIp_pv();
			port = "icmp_pv";
			break;
		}
		
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
				downPortList.remove(port);
				upPortList.add(port);
				logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + UP);
				
				return true;
			} else {
				if (checkType == CHECK_FIRST) {
					downPortList.add(port);
				}
				
				/********************************/
				/** [ICMP or ICMP_PV] NG POINT **/
				/********************************/
				String msg = SMS_PREFIX + ipAddress + " " + port + DOWN;
				if (downPortHashMap.containsKey(port)) {
					downPortHashMap.get(port).increaseDownCnt();
				} else {
					downPortHashMap.put(port, new DownPortInfo(ipAddress, msg, port));
				}
				
				logger.info(LOG_PREFIX + String.format("%-15s", ipAddress) + " " + String.format("%-7s", port) + DOWN + " (" + downPortHashMap.get(port).getDownCnt() + "/" + MAX_TRY + ")");
				return false;
			}
		} catch (IOException e) {
			
			logger.info(LOG_PREFIX + LOG_IP + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	/*private void portCheck(int checkType) {
		switch (checkType) {
		case CHECK_FIRST:
			String[] checkPortNo = ipInfo.getCheck_port_no().split(",");
			
			for (int i = 0; i < checkPortNo.length; i++) {
				switch (checkPortNo[i]) {
				case "aws-elastic-ip":
				case "aws-route53":
				case "http_param":
				case "tcp_param":
					// DO NOTHING...
					break;
					
				case "ftp":
					checkFtp(CHECK_FIRST);
					break;
					
				case "80":
				case "1120":
					// TODO NON-ALARM, WRITE LOG
					openPortCheck(CHECK_FIRST, checkPortNo[i]);
					httpPortCheck(CHECK_FIRST, checkPortNo[i]);
					httpPortCheck(CHECK_FIRST, checkPortNo[i]);
					break;
					
				default:
					openPortCheck(CHECK_FIRST, checkPortNo[i]);
					break;
				}
			}
			break;
			
		case CHECK_AGAIN:
			ArrayList<String> tmpArray = new ArrayList<String>();
			tmpArray.addAll(downPortList);

			// ���� �� Down Port ��Ȯ��
			for (int i = 0; i < tmpArray.size(); i++) {
				if (tmpArray.get(i).startsWith("icmp")) {
					if (tmpArray.get(i).equals("icmp")) {
						icmpCheck(CHECK_AGAIN, TYPE_ICMP);	
					} else {
						icmpCheck(CHECK_AGAIN, TYPE_ICMP_PV);
					}
				} else {
					if (tmpArray.get(i).equals("ftp")) {
						checkFtp(CHECK_AGAIN);
					} else {
						openPortCheck(CHECK_AGAIN, tmpArray.get(i));
					}
				}
			}
			break;
		}
	}*/

	/*private void httpPortCheck(int type, String port) {
		boolean httpFail = false;
		String msg = "";
		
		try {
			int resCode = -1;
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(ipInfo.getIp(), Integer.parseInt(port)), CONNECT_TIMEOUT);
			socket.close();
			
			httpFail = true;
			HttpURLConnection con = null;
			URL url = new URL("http://" + ipInfo.getIp() + ":" + port);
//			URL url = new URL("http://127.0.0.1:" + port);
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
				downPortHashMap.get(port).increaseDownCnt();
				StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + DOWN + " (" + downPortHashMap.get(port).getHttpDownCnt() + "/" + MAX_TRY + ")" + (httpFail ? " (HTTP)": ""));
				
				msg = SMS_PREFIX + SMS_IP + port + DOWN + " (" + downPortHashMap.get(port).getHttpDownCnt() + "/" + MAX_TRY + ")";
				String insertAll = "INSERT INTO process_ctrl (ip_pv, msg, time) VALUES ('" + ipInfo.getIp() + "', '" + msg + "', NOW())";
				updateDB(insertAll);
				return;
			}
			downPortList.remove(port);
			StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + UP + " (HttpResponseCode: " + resCode + ")");

//			msg = SMS_PREFIX + SMS_IP + port + UP;
//			String insertAll = "INSERT INTO process_ctrl (ip_pv, msg, time) VALUES ('" + ipInfo.getIp() + "', '" + msg + "', NOW())";
//			updateDB(insertAll);
		} catch (Exception e) {
			downPortHashMap.get(port).increaseDownCnt();
			
			// DB Insert
			StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + DOWN + " (" + downPortHashMap.get(port).getHttpDownCnt() + "/" + MAX_TRY + ")" + (httpFail ? " (HTTP)": ""));

			try {
				msg = SMS_PREFIX + SMS_IP + port + DOWN + " (" + downPortHashMap.get(port).getHttpDownCnt() + "/" + MAX_TRY + ")";
				String insertAll = "INSERT INTO process_ctrl (ip_pv, msg, time) VALUES ('" + ipInfo.getIp() + "', '" + msg + "', NOW())";
				updateDB(insertAll);
			} catch (SQLException e1) {
				e1.printStackTrace();
			}		
		}
	}*/
	/*
	private void openPortCheck(int type, String port) {
		boolean httpFail = false;
		
		try {
			int resCode = -1;
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(ipInfo.getIp(), Integer.parseInt(port)), CONNECT_TIMEOUT);
			socket.close();
			
			
			httpFail = true;
//			if (port.equals("80") || port.equals("1120")) {
//				HttpURLConnection con = null;
//				URL url = new URL("http://" + ipInfo.getIp() + ":" + port);
//				con = (HttpURLConnection) url.openConnection();
//
//				// Add request header
//				con.setRequestMethod("POST");
//				con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
//				con.setRequestProperty("Content-Length", "length");
//				
//				// Set timeout
//				con.setConnectTimeout(CONNECT_TIMEOUT);
//				con.setReadTimeout(HTTP_TIMEOUT);
//
//				// Send post request
//				con.setDoOutput(true);
//				DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//				wr.flush();
//				
//				// Actually request
//				resCode = con.getResponseCode();
//				if (resCode == -1) {
//					throw new ConnectException();
//				}
//			}
			
			StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + UP + (port.equals("80") || port.equals("1120") ? " (HttpResponseCode: " + resCode + ")" : ""));
			upPortList.add(port);
			downPortList.remove(port);
		} catch (Exception e) {
			if (type == CHECK_FIRST) {
				downPortList.add(port);
			}
			
			*//******************************//*
			*//** PORT CONNECTION NG POINT **//*
			*//******************************//*
			String msg = SMS_PREFIX + SMS_IP + port + DOWN;
			if (downPortHashMap.containsKey(port)) {
				downPortHashMap.get(port).increaseDownCnt();
			} else {
				downPortHashMap.put(port, new DownPortInfo(ipInfo.getIp(), msg, port));
			}
			
			StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + DOWN + " (" + downPortHashMap.get(port).getDownCnt() + "/" + MAX_TRY + ")" + (httpFail ? " (HTTP)": ""));
		}
	}
	*/
//	private void openPortCheck(int type, String port) {
//		boolean httpFail = false;
//		
//		try {
//			StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + UP);
//			upPortList.add(port);
//			downPortList.remove(port);
//		} catch (Exception e) {
//			if (type == CHECK_FIRST) {
//				downPortList.add(port);
//			}
//			
//			/******************************/
//			/** PORT CONNECTION NG POINT **/
//			/******************************/
//			String msg = SMS_PREFIX + SMS_IP + port + DOWN;
//			if (downPortHashMap.containsKey(port)) {
//				downPortHashMap.get(port).increaseDownCnt();
//			} else {
//				downPortHashMap.put(port, new DownPortInfo(ipInfo.getIp(), msg, port));
//			}
//			
//			StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", port) + DOWN + " (" + downPortHashMap.get(port).getDownCnt() + "/" + MAX_TRY + ")" + (httpFail ? " (HTTP)": ""));
//		}
//	}
//	

	/*private void checkFtp(int type) {
		String ftp = "ftp";
		
		
		FtpCheck ftp = new FtpCheck();
		if (ftp.init(ipInfo.getIp(), "testuser", "testuserp", 9021)) {
			if (ftp.download("/", "mon_download_test.txt", "./ftp_temp/" + ipInfo.getIp() + "_ftp_file.txt")) {
				StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", "ftp") + " filedownload OK!!");
				ftp.disconnection();
				upPortList.add(port);
				downPortList.remove(port);
			} else {
				portCheckNo++; downPortList.add(port);
				StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", "ftp") + " filedownload FAIL!!");
				sendMessage(SMS_PREFIX + SMS_IP + "ftp filedownload FAIL!!");
			}
		} else {
			portCheckNo++;
			downPortList.add(port);
			StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", "ftp") + " connect Fail!!");
		}
		
		
		// FTP ���� Ȯ��
		FtpTransfer ftpTransfer = new FtpTransfer(ipInfo.getIp(), 9021, "testuser", "testuserp");
		try {
			ftpTransfer.connect();
			StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", ftp) + " connect OK!!");
		} catch (IOException e) {
			if (type == CHECK_FIRST) {
				downPortList.add(ftp);
			}

			*//*****************************//*
			*//** FTP CONNECTION NG POINT **//*
			*//*****************************//*
			String msg = SMS_PREFIX + SMS_IP + ftp + DOWN;
			if (downPortHashMap.containsKey(ftp)) {
				downPortHashMap.get(ftp).increaseDownCnt();
			} else {
				downPortHashMap.put(ftp, new DownPortInfo(ipInfo.getIp(), msg, ftp));
			}

			StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", ftp) + " connect Fail!! (" + downPortHashMap.get(ftp).getDownCnt() + "/" + MAX_TRY + ")");
			
			try {
				ftpTransfer.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			
			return;
		}

		// FTP �ٿ�ε� Ȯ��
		OutputStream os = null;
		try {
			os = new FileOutputStream(new File("./ftp_temp/" + ipInfo.getIp() + "_ftp_file.txt"));
			
			try {
				long resultFileSize = ftpTransfer.downloadFile("mon_download_test.txt", 0, 9, os);
				ftpTransfer.close();

				if (resultFileSize == 9) {
					upPortList.add(ftp);
					downPortList.remove(ftp);
					StartMonitor.logger.info(LOG_PREFIX + LOG_IP + String.format("%-7s", ftp) + " filedownload OK!!");
				} else {
					StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", ftp) + " filedownload SIZE " + resultFileSize);
					throw new IOException();
				}
			} catch (IOException e) {
				if (type == CHECK_FIRST) {
					downPortList.add(ftp);
				}
				
				*//*****************************//*
				*//** FTP DOWNLOAD NG POINT 1 **//*
				*//*****************************//*
				String msg = SMS_PREFIX + SMS_IP + ftp + DOWN;
				if (downPortHashMap.containsKey(ftp)) {
					downPortHashMap.get(ftp).increaseDownCnt();
				} else {
					downPortHashMap.put(ftp, new DownPortInfo(ipInfo.getIp(), msg, ftp));
				}

				StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", ftp) + " filedownload FAIL!! (" + downPortHashMap.get(ftp).getDownCnt() + "/" + MAX_TRY + ")");
				return;
			}
		} catch (FileNotFoundException e) {
			if (type == CHECK_FIRST) {
				downPortList.add(ftp);
			}

			*//*****************************//*
			*//** FTP DOWNLOAD NG POINT 3 **//*
			*//*****************************//*
			String msg = SMS_PREFIX + SMS_IP + ftp + DOWN;
			if (downPortHashMap.containsKey(ftp)) {
				downPortHashMap.get(ftp).increaseDownCnt();
			} else {
				downPortHashMap.put(ftp, new DownPortInfo(ipInfo.getIp(), msg, ftp));
			}
			
			StartMonitor.logger.error(LOG_PREFIX + LOG_IP + String.format("%-7s", ftp) + " fileOutputStream FAIL!! (" + downPortHashMap.get(ftp).getDownCnt() + "/" + MAX_TRY + ")");
			return;
		}

		if (ftpTransfer != null) {
			try {
				ftpTransfer.close();
				ftpTransfer = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if (os != null) {
			try {
				os.close();
				os = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
*/
	/*// ���� ����
	private void sendMessage(String msg) {
		if (MntrConfig.getDebug_admin_sms()) {
			URL url = null;
			URLConnection urlConnection = null;
			String paramPhone = "";
			int num;
			String[] receiverNo = MntrConfig.getReceiverNo().split(",");
			
			// �׽�Ʈ�� ��� Ư�� ��ȣ�� SMS ����
			if (StartMonitor.DEMO_TEST) {
				receiverNo = new String[2];
				receiverNo[0] = "01062880183";
				msg = "[DEMO]" + msg;
			}

			for (int i = 0; i < receiverNo.length; i++) {
				num = i + 1;
				paramPhone += "&NUM" + num + "=" + receiverNo[i];
			}

			try {
				msg = URLEncoder.encode(msg, "utf-8");
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			}
			
			String sUrl = "http://mrtg.kgrid.co.kr:1105/sms_send_oper.php";
			String sUrl2 = "http://admin.kcloud.kgrid.co.kr:1120/monitor/smsadmin_collector_post.php";
			String sms_idx = "5aab1f18d12e7";
			String resultStr = sUrl + "?S_NUM=0221877212" + paramPhone + "&MSG=" + msg;
			String resultStr2 = sUrl2 + "?IDX=" + sms_idx + "&MSG=" + msg;
			StartMonitor.logger.info("sendMessage url: " + resultStr);
			
			try {
				url = new URL(resultStr); urlConnection = url.openConnection();
				urlConnection.getInputStream();
//				printByInputStream(urlConnection.getInputStream()); 
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			StartMonitor.logger.info("sendMessage url2: " + resultStr2);
			try {
				url = new URL(resultStr2); urlConnection = url.openConnection();
				urlConnection.getInputStream();
//				printByInputStream(urlConnection.getInputStream()); 
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
*/
	public void printByInputStream(InputStream is) {
		byte[] buffer = new byte[2048];
		int lenth = -1;

		try {
			while ((lenth = is.read(buffer, 0, buffer.length)) != -1) {
				System.out.write(buffer, 0, lenth);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Object[] removeDuplicateArray(String[] array) {
		Object[] removeArray = null;
		TreeSet<String> ts = new TreeSet<String>();
		for (int i = 0; i < array.length; i++) {
			ts.add(array[i]);
		}

		removeArray = ts.toArray();
		return removeArray;
	}

	public class DownPortInfo {
		private String ipAddress = null;
		private String msg = null;
		private String port = null;
		private int downCnt = 1;
		private int httpDownCnt = 0;

		public DownPortInfo(String ipAddress, String msg, String port) {
			this.ipAddress = ipAddress;
			this.msg = msg;
			this.port = port;
		}
		
		public void increaseHttpDownCnt() {
			httpDownCnt++;
		}
		
		public int getHttpDownCnt() {
			return httpDownCnt;
		}

		public void setIpAddress(String ipAddress) {
			this.ipAddress = ipAddress;
		}

		public String getIpAddress() {
			return this.ipAddress;
		}

		public void setMsg(String msg) {
			this.msg = msg;
		}

		public String getMsg() {
			return this.msg;
		}

		public void setPort(String port) {
			this.port = port;
		}

		public String getPort() {
			return this.port;
		}

		public void setDownCnt(int downCnt) {
			this.downCnt = downCnt;
		}

		public int getDownCnt() {
			return this.downCnt;
		}

		public void increaseDownCnt() {
			this.downCnt++;
		}
	}
}
