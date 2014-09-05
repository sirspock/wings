package edu.isi.wings.catalog.resource.classes;

import java.io.File;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.gridkit.internal.com.jcraft.jsch.ChannelSftp;
import org.gridkit.internal.com.jcraft.jsch.JSch;
import org.gridkit.internal.com.jcraft.jsch.JSchException;
import org.gridkit.internal.com.jcraft.jsch.Session;
import org.gridkit.nanocloud.Cloud;
import org.gridkit.nanocloud.SimpleCloudFactory;
import org.gridkit.nanocloud.telecontrol.ssh.SshSpiConf;
import org.gridkit.vicluster.ViConf;
import org.gridkit.vicluster.ViNode;

public class Machine extends Resource {
  private static final long serialVersionUID = 5211295601774494163L;

  private String hostIP;
  private String hostName;
  private String userId;
  private String userKey;
  private float memoryGB;
  private float storageGB;
  private boolean is64Bit;
  private boolean isHealthy;
  
  private String storageFolder;
  private String executionFolder;
  
  private ArrayList<EnvironmentValue> environmentValues;
  private ArrayList<String> softwareIds;
  private String osid;
  
  private transient ViNode node;
  private transient Cloud cloud;
  
  public Machine(String id) {
    super(id);
    environmentValues = new ArrayList<EnvironmentValue>();
    softwareIds = new ArrayList<String>();
  }

  public String getHostIP() {
    return hostIP;
  }

  public void setHostIP(String hostIP) {
    this.hostIP = hostIP;
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUserKey() {
    return userKey;
  }

  public void setUserKey(String userKey) {
    this.userKey = userKey;
  }

  public float getMemoryGB() {
    return memoryGB;
  }

  public void setMemoryGB(float memoryGB) {
    this.memoryGB = memoryGB;
  }

  public float getStorageGB() {
    return storageGB;
  }

  public void setStorageGB(float storageGB) {
    this.storageGB = storageGB;
  }

  public boolean is64Bit() {
    return is64Bit;
  }

  public void setIs64Bit(boolean is64Bit) {
    this.is64Bit = is64Bit;
  }

  public boolean isHealthy() {
    return isHealthy;
  }

  public void setHealthy(boolean isHealthy) {
    this.isHealthy = isHealthy;
  }

  public String getStorageFolder() {
    return storageFolder;
  }

  public void setStorageFolder(String storageFolder) {
    this.storageFolder = storageFolder;
  }

  public String getExecutionFolder() {
    return executionFolder;
  }

  public void setExecutionFolder(String executionFolder) {
    this.executionFolder = executionFolder;
  }

  public ArrayList<EnvironmentValue> getEnvironmentValues() {
    return environmentValues;
  }

  public void setEnvironmentValues(ArrayList<EnvironmentValue> environmentValues) {
    this.environmentValues = environmentValues;
  }
  
  public void addEnvironmentValues(EnvironmentValue environmentValue) {
    this.environmentValues.add(environmentValue);
  }
  
  public String getEnvironmentValue(String variable) {
    for(EnvironmentValue evalue : this.environmentValues) {
      if(evalue.getVariable().equals(variable))
        return evalue.getValue();
    }
    return null;
  }

  public ArrayList<String> getSoftwareIds() {
    return softwareIds;
  }

  public void setSoftwares(ArrayList<String> softwareIds) {
    this.softwareIds = softwareIds;
  }

  public void addSoftware(String softwareId) {
    this.softwareIds.add(softwareId);
  }
  
  public String getOSid() {
    return osid;
  }

  public void setOSid(String osid) {
    this.osid = osid;
  }

  public ViNode getCallableNode() throws Exception {
    if(this.node != null)
      return this.node;
    
    String host = hostIP != null ? hostIP : hostName;
    this.cloud = SimpleCloudFactory.createSimpleSshCloud();
    this.node = cloud.node(host);
    
    String jhome = this.getEnvironmentValue("JAVA_HOME");
    String javaexec = "java";
    if(jhome != null && !jhome.equals(""))
      javaexec = jhome + "/bin/java";
    
    node.setProp(ViConf.REMOTE_HOST, host);
    node.setProp(ViConf.REMOTE_ACCOUNT, userId);
    node.setProp(SshSpiConf.SPI_SSH_PRIVATE_KEY_FILE, userKey);
    node.setProp(SshSpiConf.SPI_JAR_CACHE, storageFolder + "/nanocloud");
    node.setProp(ViConf.JVM_EXEC_CMD, javaexec);
    node.touch();
    return node;
  }
  
  public void shutdown() {
    if(this.node != null)
      this.node.shutdown();
    if(this.cloud != null)
      this.cloud.shutdown();
    this.node = null;
    this.cloud = null;
  }
  
  private Session getSSHSession() 
      throws JSchException {
    JSch ssh = new JSch();
    if (this.getUserKey() != null)
      ssh.addIdentity(this.getUserKey());
    Session ssh_session = ssh.getSession(this.getUserId(), this.getHostName());
    java.util.Properties config = new java.util.Properties();
    config.put("StrictHostKeyChecking", "no");
    ssh_session.setConfig(config);
    ssh_session.connect();
    return ssh_session;
  }
  
  public boolean uploadFiles(HashMap<String, String> localRemoteMap) {
    try {
      Session ssh_session = this.getSSHSession();
      if(ssh_session.isConnected()) {
        ChannelSftp sftpChannel = (ChannelSftp) ssh_session.openChannel("sftp");
        sftpChannel.connect();
        for(String local: localRemoteMap.keySet()) {
          String remote = localRemoteMap.get(local);
          sftpChannel.put(local, remote);
        }
        sftpChannel.disconnect();
        ssh_session.disconnect();
        return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }
  
  public boolean downloadFiles(HashMap<String, String> localRemoteMap) {
    try {
      Session ssh_session = this.getSSHSession();
      if(ssh_session.isConnected()) {
        ChannelSftp sftpChannel = (ChannelSftp) ssh_session.openChannel("sftp");
        sftpChannel.connect();
        for(String local: localRemoteMap.keySet()) {
          String remote = localRemoteMap.get(local);
          try {
            sftpChannel.get(remote, local);
          }
          catch (Exception e) {
            // Ignore
          }
        }
        sftpChannel.disconnect();
        ssh_session.disconnect();
        return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }
  
  public MachineDetails getMachineDetails() {
    MachineDetails details = new MachineDetails();
    try {      
      MachineDetailsGrabber mdg = new MachineDetailsGrabber(this);
      Future<MachineDetails> job = this.getCallableNode().submit(mdg);
      details = job.get();
    }
    catch (Exception e) {
      details.setCanConnect(false);
      details.addError(e.getMessage());
      e.printStackTrace();
    }
    return details;
  }
}

class MachineDetailsGrabber implements Callable<MachineDetails>, Serializable {
  private static final long serialVersionUID = 5960512182954001309L;
  private final Machine machine;

  public MachineDetailsGrabber(Machine machine) {
    this.machine = machine;
  }

  @SuppressWarnings("restriction")
  @Override
  public MachineDetails call() {
    MachineDetails details = new MachineDetails();
    details.setCanConnect(true);;
    File f = new File(machine.getStorageFolder());
    if (!f.exists())
      details.addError("Cannot find Wings Storage Folder: "
          + machine.getStorageFolder());
    if (!f.canWrite())
      details.addError("Cannot write to Wings Storage Folder: "
          + machine.getStorageFolder());

    f = new File(machine.getExecutionFolder());
    if (!f.exists())
      details.addError("Cannot find Wings Execution Folder: "
          + machine.getExecutionFolder());
    if (!f.canWrite())
      details.addError("Cannot write to Wings Execution Folder: "
          + machine.getExecutionFolder());
    
    details.setNumCores(Runtime.getRuntime().availableProcessors());
    details.setMaxMemory(((com.sun.management.OperatingSystemMXBean) 
        ManagementFactory.getOperatingSystemMXBean())
          .getTotalPhysicalMemorySize());
    details.setFreeMemory(((com.sun.management.OperatingSystemMXBean) 
        ManagementFactory.getOperatingSystemMXBean())
          .getFreePhysicalMemorySize());
    File[] roots = File.listRoots();
    for (File root : roots) {
      details.setStorageRoot(root.getAbsolutePath());
      details.setTotalStorage(root.getTotalSpace());
      details.setFreeStorage(root.getFreeSpace());
      break;
    }

    details.setArchitecture(
        ManagementFactory.getOperatingSystemMXBean().getName() + " - "+
        ManagementFactory.getOperatingSystemMXBean().getArch());
    details.setSystemLoad(
        ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
    return details;
  }
}

class MachineDetails implements Serializable {
  private static final long serialVersionUID = -2690736677192673940L;
  private boolean connect;
  private float memoryMax;
  private float memoryFree;
  private int numCores;
  private String storageRoot;
  private float storageRootMax;
  private float storageRootFree;
  private String systemArch;
  private double systemLoad;
  private ArrayList<String> errors;

  public MachineDetails() {
    errors = new ArrayList<String>();
  }
  
  public boolean isCanConnect() {
    return connect;
  }

  public void setCanConnect(boolean canConnect) {
    this.connect = canConnect;
  }

  public float maxMemory() {
    return memoryMax;
  }

  public void setMaxMemory(float memoryMax) {
    this.memoryMax = memoryMax;
  }

  public float getFreeMemory() {
    return memoryFree;
  }

  public void setFreeMemory(float memoryFree) {
    this.memoryFree = memoryFree;
  }

  public int getNumCores() {
    return numCores;
  }

  public void setNumCores(int numCores) {
    this.numCores = numCores;
  }

  public String getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(String storageRoot) {
    this.storageRoot = storageRoot;
  }

  public float getTotalStorage() {
    return storageRootMax;
  }

  public void setTotalStorage(float totalStorage) {
    this.storageRootMax = totalStorage;
  }

  public float getFreeStorage() {
    return storageRootFree;
  }

  public void setFreeStorage(float freeStorage) {
    this.storageRootFree = freeStorage;
  }

  public String getArchitecture() {
    return systemArch;
  }

  public void setArchitecture(String architecture) {
    this.systemArch = architecture;
  }

  public double getSystemLoad() {
    return systemLoad;
  }

  public void setSystemLoad(double systemLoad) {
    this.systemLoad = systemLoad;
  }

  public ArrayList<String> getErrors() {
    return errors;
  }

  public void addError(String error) {
    this.errors.add(error);
  }
}