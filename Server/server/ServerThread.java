package server;

import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.cli.CommandLine;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class ServerThread implements Runnable {
	
	private enum Operation {PUBLISH,REMOVE,SHARE,QUERY,FETCH,EXCHANGE;} 

	private Socket client = null;
	
	private String secret;
	
	private ConcurrentHashMap<String, Resource> resourceList;
	
	private List<Host> serverList;

	//private Resource resourceList;
	
	public ServerThread(Socket client, String secret, 
			ConcurrentHashMap<String, Resource> resourceList, List<Host> serverList){
		this.client = client ;
		this.secret = secret;
		this.resourceList = resourceList;
		this.serverList = serverList;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		try{
		
			//PrintStream out = new PrintStream(client.getOutputStream());
	        DataOutputStream out = new DataOutputStream(client.getOutputStream());

			//BufferedReader buf = new BufferedReader(new InputStreamReader(client.getInputStream()));
	        DataInputStream buf = new DataInputStream(client.getInputStream());

			//parse jaon messsage 
			JsonReader reader = new JsonReader(new InputStreamReader(client.getInputStream()));
			//parseJson(reader);
			Boolean flag = true ;
			
			//client.setSoTimeout(100);
			try {
				while(flag){
					String str = buf.readUTF();
					if(str == null || "".equals(str)){
						flag = false ;
					}
					else{
						//out.writeUTF(str);
						//System.out.println("from server :"+str);
						parseJson(str, out);
					}
				}
			} catch (EOFException e) {
				// TODO: handle exception

			}

			out.close();
			client.close();
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private  void parseJson(String  message, DataOutputStream out){
		String key = null;
		ArrayList<String> list = new ArrayList<String>();
		Resource resource = new Resource("", "", list, "", "", "", "");
		//String operation = null;
		try {
			JsonElement root = new JsonParser().parse(message);
			JSONObject reply = new JSONObject();
			HashMap<Boolean, String> response = new HashMap<Boolean, String>();
			
			Gson gson = new Gson();
			String commandName = root.getAsJsonObject().get("command").getAsString();
			Operation operation = Operation.valueOf(commandName);
			switch(operation){
				case PUBLISH:{
					JsonObject object = root.getAsJsonObject().get("resource").getAsJsonObject();
					//Map map = gson.fromJson(message, Map.class);
					resource = gson.fromJson(object, Resource.class);
					System.out.println("publish: "+message);
					response = Function.publish(resource, resourceList);
					if(response.containsKey(true)){
						reply.put("response", "success");
					}
					else{
						reply.put("response", "error");
						reply.put("errorMessage", response.get(false));
						System.out.println(reply.toString());
					}
					out.writeUTF(reply.toString());
					break;
				}
				case REMOVE:{
					JsonObject object = root.getAsJsonObject().get("resource").getAsJsonObject();
					resource = gson.fromJson(object, Resource.class);
					System.out.println("remove: "+message);
					response = Function.remove(resource, resourceList);
					//out.writeUTF(response.toString());
					if(response.containsKey(true)){
						reply.put("response", "success");
					}
					else{
						reply.put("response", "error");
						reply.put("errorMessage", response.get(false));
						System.out.println(reply.toString());
					}
					out.writeUTF(reply.toString());
					break;
				} 
				case SHARE:{
					String secret = root.getAsJsonObject().get("secret").getAsString();
					if(secret.equals(this.secret)){
						JsonObject object = root.getAsJsonObject().get("resource").getAsJsonObject();
						resource = gson.fromJson(object, Resource.class);
						System.out.println("share: "+message);
						response = Function.share(resource, resourceList);
						out.writeUTF(response.toString());
					}
					else{
						reply.put("response", "error");
						reply.put("errorMessage", "incorrect secret");
						out.writeUTF(reply.toString());
					}
					
					break;
				}
				case QUERY:{
					Boolean relay = root.getAsJsonObject().get("relay").getAsBoolean();
					Boolean flag = false;
					Map<Boolean, Map<String, Resource>> result = new HashMap<Boolean, Map<String, Resource>>();
					
					if(root.getAsJsonObject().has("resourceTemplate")){
						JsonObject object = root.getAsJsonObject().get("resourceTemplate").getAsJsonObject();
						resource = gson.fromJson(object, Resource.class);
						System.out.println("query: "+message);
						result = Function.query(resource, resourceList);
						
						if(relay){
							if(result.containsKey(true)){
								reply.put("response", "sucess");
								out.writeUTF(reply.toString());
								int num = 0;
								for(Map.Entry<String, Resource> entry : result.get(true).entrySet()){
									out.writeUTF(gson.toJson(entry.getValue()).toString());
									num++;
								}
								out.writeUTF(new JSONObject().put("resultSize", num).toString());
								
							}
							else{
								//relay = true & query failed => broadcast to server list
								JSONObject trans = new JSONObject(message);
								trans.put("relay", false);
								String str = trans.toString();
								// new query used to broadcast
								Boolean success = false;
								for(Host h: serverList){
							        Socket agent = new Socket(h.getHostname(), h.getPort());  
							        //Socket 输出流， 转发查询
							        DataOutputStream forward = new DataOutputStream(agent.getOutputStream());
							        //获取Socket的输入流，用来接收从服务端发送过来的数据    
							        DataInputStream in = new DataInputStream(agent.getInputStream());
							        Boolean f = true;
							        forward.writeUTF(str);
							        while(f){
								        String info = in.readUTF();
								        if(info.contains("\"success\"")){
								        	success = true;
								        	out.writeUTF(info);
								        }
								        if(success){
									        if(info.contains("resultSize")){
									        	f = false;
									        	out.writeUTF(info);
									        	
									        }
									        else{
									        	out.writeUTF(info);
									        }
								        }
								        

							        }
							        forward.close();
							        in.close();
							        agent.close();
								}		
							}
							
						}
						else{
							//relay = false 
							if(result.containsKey(true)){
								//query succeed => return result
								reply.put("response", "sucess");
								out.writeUTF(reply.toString());
								int num = 0;
								for(Map.Entry<String, Resource> entry : result.get(true).entrySet()){
									out.writeUTF(gson.toJson(entry.getValue()).toString());
									num++;
								}
								out.writeUTF(new JSONObject().put("resultSize", num).toString());
								
							}
							else{
								reply.put("response", "error");
								reply.put("errorMessage", "invalid ResourceTemplate");
								//query failed => return error message
								out.writeUTF(reply.toString());;
							}
						}
					}
					else{
						reply.put("response", "error");
						reply.put("errorMessage", "missing ResourceTemplate");					}
					
					break;
				}
				case FETCH:{
					JsonObject object = root.getAsJsonObject();

					if(object.has("resourceTemplate")){
						object = object.get("resourceTemplate").getAsJsonObject();
						resource = gson.fromJson(object, Resource.class);
						System.out.println("fetch: "+message);
						long fileSize = 0;
						response = Function.fetch(resource, resourceList);
						System.out.println(resource.getKey()+" "+response);
						if(response.containsKey(true)){
							out.writeUTF(new JSONObject().put("response", "success").toString());
							File f = new File(resource.getUri().split(":")[1]);
							fileSize = f.length();
							
							System.out.println(f);
							String str = gson.toJson(object);
							JSONObject temp = new JSONObject(str);
							reply = temp.put("resourceSize", fileSize);
							out.writeUTF(reply.toString());
							System.out.println("after: "+reply.toString());

							
							//read data from local disk
							DataInputStream input = new DataInputStream
									(new BufferedInputStream(new FileInputStream(f)));
							int bufferSize = (int)fileSize;
							byte[] buf = new byte[bufferSize];
							int num =0;
							System.out.println(bufferSize);
							//input.read(buf);
							//out.write(buf);
							//num=input.read(buf);
				            while((num=input.read(buf))!=-1){
				            	System.out.println("num: "+num);
				                out.write(buf, 0, num);
				            }
				            out.flush();
				            System.out.println("文件发送成功！");
							input.close();
							out.writeUTF(new JSONObject().put("resultSize", 1).toString());
						}
						else{
							reply.put("response", "error");
							reply.put("errorMessage", "invalid resourceTemplate");
						}
					}
					else{
						reply.put("response", "error");
						reply.put("errorMessage", "missing resourceTemplate");
					}				
					// return file size

					break;
				}
				case EXCHANGE:{
					JsonArray array = root.getAsJsonObject().get("serverList").getAsJsonArray();
					Host[] host = gson.fromJson(array, Host[].class);
					for(Host h : host){
						if( !serverList.contains(h)){
							serverList.add(h);
							System.out.println("add a new server");
						}
					}
					System.out.println("exchange: "+host[0].getHostname());
					
					break;
				}
				default:{
					
					reply.put("response", "error");
					reply.put("errorMessage", "invalid Command");
					break;
				}
				
			}
			
			
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	
			
		}
		
	}


