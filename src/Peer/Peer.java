package Peer;

import java.rmi.registry.Registry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import Peer.Models.Chunk;
import Peer.Models.Storage;
import RemoteInterface.RemoteInterface;
import utils.encryptFile;
import utils.fileExtension;
import utils.getChunksOfFile;
import utils.saveOriginalCopy;

public class Peer implements RemoteInterface {
   MulticastSocket mc_socket = null;
   InetAddress mc_group = null;
   Thread mc_thread = null;
   MulticastSocket mdb_socket = null;
   InetAddress mdb_group = null;
   Thread mdb_thread = null;
   MulticastSocket mdr_socket = null;
   InetAddress mdr_group = null;
   Thread mdr_thread = null;
   private String protocolVersion;
   private int peerId;
   private final String message_type_backup = "PUTCHUNK";
   private final String message_type_stored = "STORED";
   private final String message_type_restore = "GETCHUNK";
   private final String message_type_delete = "DELETE";
   private final String message_type_restore_response = "CHUNK";
   private final String message_type_removed = "REMOVED";
   private final String CRLF = "" + (char) 0x0D + (char) 0x0A;
   private String save_file_directory = "./SaveFiles/P";
   private String save_file_path;
   private String fileID;
   Storage storage;

   public Peer(String protocolVersion, int peerId, String mc_adress, int mc_port, String mdb_adress, int mdb_port,
         String mdr_adress, int mdr_port) throws IOException {
      this.protocolVersion = protocolVersion;
      this.peerId = peerId;
      mc_socket = new MulticastSocket(mc_port);
      mc_group = InetAddress.getByName(mc_adress);
      mc_socket.joinGroup(mc_group);
      mc_thread = new Thread(() -> mcChannel());

      mdb_socket = new MulticastSocket(mdb_port);
      mdb_group = InetAddress.getByName(mdb_adress);
      mdb_thread = new Thread(() -> mdbChannel());

      mdr_socket = new MulticastSocket(mdr_port);
      mdr_group = InetAddress.getByName(mdr_adress);
      mdr_socket.joinGroup(mdr_group);
      mdr_thread = new Thread(() -> mdrChannel());

      save_file_directory += peerId;
      save_file_path = save_file_directory + "/save";
      File saveDir = new File(save_file_directory);
      saveDir.mkdirs();
      File saveFile = new File(save_file_path);
      if (saveFile.exists()) {
         this.storage = new Storage(Storage.load(save_file_path));
      } else {
         this.storage = new Storage();
         saveFile.createNewFile();
         Storage.save(save_file_path, this.storage);
      }

   }

   public void state() throws RemoteException, IOException {
      ConcurrentHashMap<String, List<String>> backedupFiles = this.storage.getBackedUpFiles();

      backedupFiles.forEach((fileId, infoFile) -> {
         System.out.println("Ficheiro cujo Backup foi iniciado por este Peer:");
         System.out.println("ID: " + fileId);
         System.out.println("FilePath: " + infoFile.get(0) + "; Desired ReplicationDegree: " + infoFile.get(1));
         System.out.println("   - Chunks do respetivo ficheiro:");
         List<Chunk> backedupChunks = this.storage.getBackedUpChunks(fileId);
         for (Chunk chunk : backedupChunks) {
            System.out.println("ID: " + chunk.getID() + "; Perceived Replication Degree: " + chunk.getReplDegree());
         }
      });

      System.out.println("Chunks guardados (Stored)");
      List<Chunk> storedChunks = this.storage.getStoredChunksPeer();
      for (Chunk chunk : storedChunks) {
         System.out.println("ID: " + chunk.getID() + "; Size: " + (chunk.getSize() / 1024));
      }

      String directoryPath = "Files/P" + this.peerId + "/Backup/";
      System.out.println("Armazenamento do Peer: " + directoryPath);
      System.out.println("Espaco maximo que pode ser usado para guardar chunks: "
            + (int) (this.storage.getTotalSpace(directoryPath) / 1024) + " KBytes");
      System.out.println(
            "Espaco usado para guardar chunks: " + (this.storage.getUsedSpace(directoryPath) / 1024) + " KBytes");
   }

   public void restore(String filePath) throws IOException {
      File file = new File(filePath);
      // Gerar o ID do ficheiro
      this.fileID = encryptFile.getSha256ID(file);
      String extension = fileExtension.getFileExtension(filePath);

      System.out.println("RESTORE do ficheiro " + this.fileID + " INICIADO");

      // Obter o nº de chunks do ficheiro
      int chunks = getChunksOfFile.countChunks(file);

      File directory = new File("Files/P" + this.peerId + "/Restore");
      if (!directory.exists())
         directory.mkdir();

      String peerPath = directory + "/File" + this.fileID + "." + extension;
      File newFile = new File(peerPath);
      newFile.createNewFile();

      FileOutputStream outputStream = new FileOutputStream(newFile);

      ArrayList<String> chunksRestored = new ArrayList<>();

      // Envio de mensagem para pedido de restore para cada chunk do ficheiro
      for (int chunk = 0; chunk < chunks; chunk++) {

         byte[] message = getMessageRestoreBytes(chunk + 1);

         DatagramPacket p = new DatagramPacket(message, message.length, mc_group, mc_socket.getLocalPort());

         mc_socket.send(p);

         byte[] buffer = new byte[64000];
         DatagramPacket mdrPacket = new DatagramPacket(buffer, buffer.length);

         try {
            loop: while (true) {
               mdr_socket.receive(mdrPacket);
               String mdrMsg = new String(mdrPacket.getData(), mdrPacket.getOffset(), mdrPacket.getLength());

               String[] head_body = mdrMsg.split("\n", 2);
               String[] elements = head_body[0].split(" ");
               String chunkNo = elements[4].replace("\n", "").replace("\r", "");

               if (elements[1].equals(message_type_restore_response)) {

                  this.storage.receivedChunkMessage(this.fileID, chunkNo);
                  Storage.save(save_file_path, this.storage);

                  if ((chunksRestored.isEmpty() || !chunksRestored.contains(chunkNo))
                        && Integer.parseInt(chunkNo) == chunk + 1) {
                     System.out.println("Fez restore do chunk" + (chunk + 1));
                     byte[] strToBytes = head_body[1].getBytes();
                     if (this.storage.getUsableSpace("Files/P" + this.peerId + "/Restore/") - strToBytes.length > 0) {
                        outputStream.write(strToBytes);
                        chunksRestored.add(chunkNo);
                     } else {
                        System.err.println("Não existe espaço suficiente no disco para restaurar o ficheiro");
                     }
                     break loop;
                  }
               }
            }
            Storage.save(save_file_path, this.storage);
         } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
         }
      }
      outputStream.close();

   }

   public void backup(String filePath, int rdegree) throws IOException {

      System.out.println("BACKUP INICIADO");

      File file = new File(filePath);
      File directory = new File("Files/P" + this.peerId + "/Original");
      if (!directory.exists())
         directory.mkdir();
      String homePeer = directory + "/" + file.getName();

      saveOriginalCopy.saveCopyInHomePeer(filePath, homePeer);

      this.fileID = encryptFile.getSha256ID(file);

      this.storage.addnewBackedUpFile(this.fileID, filePath, rdegree);

      List<byte[]> chunks = getChunksOfFile.splitFile(file);

      mc_thread.interrupt();

      for (int chunk = 0; chunk < chunks.size(); chunk++) {

         byte[] message = getMessageBackupBytes(chunks.get(chunk), rdegree, chunk + 1);

         DatagramPacket p = new DatagramPacket(message, message.length, mdb_group, mdb_socket.getLocalPort());

         byte[] buffer = new byte[64000];
         DatagramPacket mcPacket = new DatagramPacket(buffer, buffer.length);
         int count = 0;
         List<Integer> peersReceived = new ArrayList<>();
         loop: for (int i = 0; i < 5; i++) {

            mdb_socket.send(p);

            mc_socket.setSoTimeout(1000 * 2 ^ i);

            loopReceive: while (true) {
               try {
                  mc_socket.receive(mcPacket);
                  String mcMsg = new String(mcPacket.getData(), mcPacket.getOffset(), mcPacket.getLength());

                  String head_body = mcMsg.replace("\n", "").replace("\r", "");
                  String[] elements = head_body.split(" ");
                  int senderPeerId = Integer.parseInt(elements[2]);
                  if (elements[1].equals(message_type_stored) && !peersReceived.contains(senderPeerId)) {

                     count++;
                     peersReceived.add(senderPeerId);
                     System.out.println("[MC] Recebeu confirmacao STORED do peer" + elements[2]);
                     System.out.println(mcMsg);
                  }
               } catch (IOException e) {

                  if (count >= rdegree) {
                     break loop;
                  } else {

                     break loopReceive;
                  }
               }
            }
         }
         this.storage.addBackedUpChunk(this.fileID, (chunk + 1), count);
      }
      Storage.save(save_file_path, this.storage);
      mc_socket.setSoTimeout(0);
      mc_thread = new Thread(() -> mcChannel());
      mc_thread.start();

   }

   public void delete(String filePath) throws IOException {
      File file = new File(filePath);
      this.fileID = encryptFile.getSha256ID(file);

      byte[] message = getMessageDeleteBytes();

      for (int i = 0; i < 3; i++) {
         DatagramPacket p = new DatagramPacket(message, message.length, mc_group, mc_socket.getLocalPort());
         System.out.println("DELETE DO FILE " + this.fileID + " INICIADO");
         mc_socket.send(p);
      }
      Storage.save(save_file_path, this.storage);
   }

   public void reclaim(int kBytes) throws RemoteException, IOException {
      storage.updateDiskSpace(kBytes);
      String directoryPath = "./Files/P" + this.peerId + "/Backup";

      System.out.println("RECLAIM SPACE DISK INICIADO");

      while (storage.checkUsedSpaceAboveMax(directoryPath)) {
         Chunk c = storage.getLastChunk();
         this.fileID = c.getFileId();
         byte[] message = getMessageReclaimBytes(c.getChunkNo());
         DatagramPacket p = new DatagramPacket(message, message.length, mc_group, mc_socket.getLocalPort());

         System.out.println("RECLAIM Chunk " + c.getChunkNo());

         storage.removeChunk(c, directoryPath);

         mc_socket.send(p);
      }
      Storage.save(save_file_path, this.storage);
   }

   public byte[] getMessageReclaimBytes(int chunkNo) {
      String header = this.protocolVersion + ' ' + message_type_removed + ' ' + this.peerId + ' ' + this.fileID + ' '
            + chunkNo + CRLF + CRLF;
      byte[] header_bytes = header.getBytes();
      return header_bytes;
   }

   public byte[] getMessageBackupBytes(byte[] body, int rdegree, int chunkNo) throws IOException {
      String header = this.protocolVersion + ' ' + message_type_backup + ' ' + this.peerId + ' ' + this.fileID + ' '
            + chunkNo + ' ' + rdegree + CRLF + CRLF;
      byte[] header_bytes = header.getBytes();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      outputStream.write(header_bytes);
      outputStream.write(body);

      byte message[] = outputStream.toByteArray();
      return message;
   }

   public byte[] getMessageRestoreBytes(int chunkNo) throws IOException {
      String header = this.protocolVersion + ' ' + message_type_restore + ' ' + this.peerId + ' ' + this.fileID + ' '
            + chunkNo + CRLF + CRLF;
      byte[] header_bytes = header.getBytes();
      return header_bytes;
   }

   public byte[] getMessageRestoreResponseBytes(String chunkNo, String fileId, byte[] body) throws IOException {
      String header = this.protocolVersion + ' ' + message_type_restore_response + ' ' + this.peerId + ' ' + fileId
            + ' ' + chunkNo + CRLF + CRLF;
      byte[] header_bytes = header.getBytes();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      outputStream.write(header_bytes);
      outputStream.write(body);

      byte message[] = outputStream.toByteArray();
      return message;
   }

   public byte[] getMessageDeleteBytes() throws IOException {
      String header = this.protocolVersion + ' ' + message_type_delete + ' ' + this.peerId + ' ' + this.fileID + ' '
            + CRLF + CRLF;
      byte[] header_bytes = header.getBytes();
      return header_bytes;
   }

   public static void main(String args[]) {
      if (validacoes(args) == false)
         return;
      try {
         Peer this_peer = new Peer(args[0], Integer.parseInt(args[1]), args[3], Integer.parseInt(args[4]), args[5],
               Integer.parseInt(args[6]), args[7], Integer.parseInt(args[8]));
         RemoteInterface stub = (RemoteInterface) UnicastRemoteObject.exportObject(this_peer, 0);

         // Bind the remote object's stub in the registry
         Registry registry = LocateRegistry.getRegistry();
        
         registry.bind(args[2], stub);

         this_peer.mc_thread.start();
         this_peer.mdb_thread.start();
         this_peer.mdr_thread.start();

         System.err.println("Server ready");

      } catch (Exception e) {
         System.err.println("Server exception: " + e.toString());
         e.printStackTrace();
      }
   }

   private static boolean validacoes(String[] args) {
      if (args.length < 9) {
         System.out.println(
               "Properly usage: <protocol_version> <peer_id> <acces_point> <MC_mc_adress> <MC_port> <MDB_mc_adress> <MDB_port> <MDR_adress> <MDR_port>");
         return false;
      }
      return true;
   }

   private void mcChannel() {

      while (true) {
         try {
            byte[] buffer = new byte[64000];

            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            this.mc_socket.receive(p);

            if (Thread.interrupted()) {
               Storage.save(save_file_path, this.storage);
               break;
            }

            String msg = new String(p.getData(), p.getOffset(), p.getLength());

            String head_body = msg.replace("\n", "").replace("\r", "");
            String[] elements = head_body.split(" ");

            if (Integer.parseInt(elements[2]) != this.peerId && elements[1].equals(message_type_delete)) {
               System.out.println("[MC] >> Delete File " + elements[3]);
               File dir = new File("Files/P" + this.peerId + "/Backup");
               File[] foundFiles = dir.listFiles(new FilenameFilter() {

                  @Override
                  public boolean accept(File dir, String name) {
                     return name.startsWith("File" + elements[3]);
                  }
               });

               for (File found : foundFiles) {
                  if (found.delete()) {
                     System.out.println("[MC] >> Deleted " + found.getName());
                     this.storage.removeAllChunksFromStored(elements[3]);
                  } else {
                     System.err.println("[MC] >> Couldn't delete file " + found.getName());
                  }
               }
            }

            if (Integer.parseInt(elements[2]) != this.peerId && elements[1].equals(message_type_restore)) {
               String fileId = elements[3];
               String chunkNo = elements[4];
               System.out.println("[MC] >> Recebeu pedido de Restore do ficheiro " + fileId + "Chunk" + chunkNo);

               String filePath = "Files/P" + this.peerId + "/Backup/File" + fileId + "Chunk" + chunkNo;
               File file = new File(filePath);
               if (!file.exists()) {
                  System.err.println("Não existe backups do ficheiro dado");
               } else {
                  try {
                     Path path = Paths.get(filePath);
                     byte[] body = Files.readAllBytes(path);

                     byte[] success = getMessageRestoreResponseBytes(chunkNo, fileId, body);
                     DatagramPacket sPacket = new DatagramPacket(success, success.length, this.mdr_group,
                           this.mdr_socket.getLocalPort());

                     Random random = new Random();
                     int mseconds = (int) (random.nextDouble() * 400);
                     ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                     Runnable task = () -> {

                        Storage storage = new Storage(Storage.load("./SaveFiles/P" + elements[2] + "/save"));
                        if (!storage.hasReceivedChunkMessgage(fileId, chunkNo)) {

                           try {
                              this.mdr_socket.send(sPacket);
                           } catch (IOException e) {
                              e.printStackTrace();
                           }
                        }
                     };
                     scheduler.schedule(task, mseconds, TimeUnit.MILLISECONDS);

                  } catch (NoSuchFileException e) {
                     System.err.println("Server exception: " + e.toString());
                     e.printStackTrace();
                  }
               }

            }

            if (Integer.parseInt(elements[2]) != this.peerId && elements[1].equals(message_type_removed)) {
               System.out.println("[MC] >>" + elements[3] + " Chunk " + elements[4] + " " + elements[1]);
               System.out.println(">> From Peer " + elements[2] + " ");
               Chunk c = this.storage.getChunk(elements[3], elements[4]);
               if (c != null) {
                  this.storage.decrementChunkReplicationDegree(c);
               }
               if (storage.containsLocalFile(elements[3])) {
                  String filePath = storage.getPathOfFile(elements[3]);

                  int desiredReplicationDegree = storage.getDesiredReplicationDegreOfFile(elements[3]);
                  Chunk backedUpChunk = storage.getBackedUpChunk(elements[3], elements[4]);
                  backedUpChunk.decrementReplDegree();

                  if (backedUpChunk.getReplDegree() < desiredReplicationDegree) {
                     System.out.println("[MC] >> Restart Backup");
                     Random random = new Random();
                     int mseconds = (int) (random.nextDouble() * 400);
                     ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                     Runnable task = () -> {
                        try {
                           backup(filePath, desiredReplicationDegree);
                        } catch (IOException e) {
                           System.err.println("Server error: " + e.toString());
                        }
                     };
                     scheduler.schedule(task, mseconds, TimeUnit.MILLISECONDS);
                     // backup(filePath, desiredReplicationDegree);
                  }
               }
            }
         } catch (IOException e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
            break;
         }
         Storage.save(save_file_path, this.storage);
      }
   }

   private void mdbChannel() {
      try {
         this.mdb_socket.joinGroup(mdb_group);
      } catch (IOException e1) {
         e1.printStackTrace();
      }
      while (true) {
         try {
            byte[] buffer = new byte[64000];

            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            this.mdb_socket.receive(p);
            String msg = new String(p.getData(), p.getOffset(), p.getLength());

            String[] head_body = msg.split("\n", 2);
            String[] elements = head_body[0].split(" ");

            if (Integer.parseInt(elements[2]) != this.peerId && elements[1].equals(message_type_backup)) {

               String fileId = elements[3];
               String chunkNo = elements[4];
               System.out.println("[MDB] >> Recebeu pedido para backup do ficheiro " + fileId + "Chunk" + chunkNo);

               String peerPath = "Files/P" + this.peerId + "/Backup/File" + fileId + "Chunk" + chunkNo;

               byte[] strToBytes = head_body[1].getBytes();
               File newFile = new File(peerPath);

               if ((this.storage.getUsedSpace("Files/P" + this.peerId + "/Backup") + strToBytes.length) < this.storage
                     .getMaxDiskSpace()) {

                  FileOutputStream outputStream = new FileOutputStream(newFile);
                  outputStream.write(strToBytes);
                  this.storage.addStoredChunks(fileId, chunkNo, newFile.length());
                  outputStream.close();

                  Random random = new Random();
                  int mseconds = (int) (random.nextDouble() * 400);
                  ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                  Runnable task = () -> {
                     String header = this.protocolVersion + ' ' + this.message_type_stored + ' ' + this.peerId + ' '
                           + fileId + ' ' + chunkNo + this.CRLF + this.CRLF;
                     byte[] success = header.getBytes();
                     DatagramPacket sPacket = new DatagramPacket(success, success.length, this.mc_group,
                           this.mc_socket.getLocalPort());
                     System.out.println("Mensagem a enviar: " + header);
                     try {
                        this.mc_socket.send(sPacket);
                     } catch (IOException e) {
                        e.printStackTrace();
                     }
                  };
                  scheduler.schedule(task, mseconds, TimeUnit.MILLISECONDS);
               } else {
                  System.err.println("Não existe espaço suficiente no disco para guardar o chunk");
               }
            }
         } catch (IOException e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
            break;
         }
         Storage.save(save_file_path, this.storage);
      }

   }

   private void mdrChannel() {

   }

}