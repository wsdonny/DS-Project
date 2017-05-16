package EZShare;

import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.RandomStringUtils;

import server.HandleSecureRequest;
import server.HandleUnsecureRequest;
import server.ServerThread;
import server.TimerTask;
import support.LogFormatter;
import variable.Host;
import variable.Resource;
import variable.resourceList;
import variable.serverList;

/**
 * Created by xutianyu on 4/25/17.
 * Main server class
 *
 */

public class Server {
	// initial parameters
	private String hostname = "Team_Awesome_Sever";
	private int port  = 20006;
	private int sport = 3781;
	private String interval = "600";
	private int  intervalLimit = 1000;
	private String secret = RandomStringUtils.randomAlphanumeric(20);
	private variable.resourceList resourceList;
	private variable.serverList serverList;
	private variable.secureServerList secureServerList;
	private Boolean debug = false;
	
	public static void main(String[] args)throws Exception{
		Server server = new Server();
		server.initialize(args);
	}
	
	public  void initialize(String[] args)throws Exception{

		//set default parameters
		resourceList = new resourceList();
		resourceList.initialResourceList();
		serverList = new serverList();
		serverList.initialserverList();
		
		try{
			
			//build new command line options
			Options options = new Options();
			//options.addOption("t", false, "display current time");
			options.addOption("advertisedhostname",true,"advertised hostname");
			options.addOption("connectionintervallimit",true,"connection interval limit in seconds");
			options.addOption("exchangeinterval",true,"exchange interval in seconds");
			options.addOption("port",true,"server port, an integer");
			options.addOption("sport",true,"secure server port, an integer");
			options.addOption("secret",true,"secret");
			options.addOption("debug",false,"print debug information");
			
			
			
			//parse Commands and arguments 
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(options,args);
			
			//test CommandLine
			if( cmd.hasOption("advertisedhostname")){
				setHostname(cmd.getOptionValue("advertisedhostname"));
			}
			if( cmd.hasOption("connectionintervallimit")){
				setIntervalLimit(Integer.parseInt( cmd.getOptionValue("connectionintervallimit"))*1000);
			}
			if(cmd.hasOption("port")){
				setPort(Integer.parseInt(cmd.getOptionValue("port")));
			}
			if(cmd.hasOption("sport")){
				setSecurePort(Integer.parseInt(cmd.getOptionValue("sport")));
			}
			if( cmd.hasOption("exchangeinterval")){
				setInterval(cmd.getOptionValue("exchangeinterval"));
			}
			if( cmd.hasOption("secret")){
				setSecret(cmd.getOptionValue("secret"));
			}
			if( cmd.hasOption("debug")){
				setDebug(true);
			}

			 
			//Print log Info
	        LogFormatter formatter = new LogFormatter();
	        ConsoleHandler handler = new ConsoleHandler();
	        handler.setFormatter(formatter);
	        
			Logger log = Logger.getLogger(Server.class.getName());
			log.setUseParentHandlers(false);
			log.addHandler(handler);
			log.setLevel(Level.INFO); 
			log.info("- Starting the EZShare Server");
			log.info("- using secret:"+secret);
			log.info("- using advertised hostname:"+hostname);
			log.info("- bound to port"+port);
			log.info("- bound to secure port" + sport);
			log.info("- started");
			if(debug){
				log.info("- setting debug on");
			}
			
			ServerSocket server = new ServerSocket(getPort());
			
			//Time schedule thread pool
			ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
			Host local = new Host(getHostname(),getPort());
			TimerTask timerTask = new TimerTask(serverList, resourceList,local, debug, log);
			TimerTask timerTaskSecure = new TimerTask(secureServerList,resourceList,local,debug,log);
			exec.scheduleAtFixedRate(timerTask,Integer.parseInt(getInterval()) , Integer.parseInt(getInterval()) , TimeUnit.SECONDS);
			exec.scheduleAtFixedRate(timerTaskSecure,Integer.parseInt(getInterval()) , Integer.parseInt(getInterval()) , TimeUnit.SECONDS);
			// create secured server
			HandleSecureRequest SecureServer = new HandleSecureRequest(getSecurePort(), getSecret() , resourceList,
					 serverList, secureServerList, getDebug(), log, getHostname(), getIntervalLimit());

			// create unsecured server
			HandleUnsecureRequest UnsecureServer = new HandleUnsecureRequest(getPort(), getSecret() , resourceList,
					serverList,  secureServerList, getDebug(), log, getHostname(), getIntervalLimit());
			Thread s1 = new Thread(SecureServer);
			Thread s2 = new Thread(UnsecureServer);
			s1.start();
			s2.start();
		}
		catch(Exception e){
			e.printStackTrace();
		}

	}
	
	
	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getSecurePort(){return sport;}
	
	public void setSecurePort(int sport) {
		this.sport= sport;
	}

	public String getInterval() {
		return interval;
	}

	public void setInterval(String interval) {
		this.interval = interval;
	}

	public int getIntervalLimit() {
		return intervalLimit;
	}

	public void setIntervalLimit(int intervalLimit) {
		this.intervalLimit = intervalLimit;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public Boolean getDebug() {
		return debug;
	}

	public void setDebug(Boolean debug) {
		this.debug = debug;
	}

}