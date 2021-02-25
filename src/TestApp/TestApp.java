package TestApp;

import java.io.File;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import RemoteInterface.RemoteInterface;

public class TestApp {
   private TestApp() {
   }

   public static void main(String[] args) throws IOException {
      if (validacoes(args) == false)
         return;

      try {
         // Getting the registry
         Registry registry = LocateRegistry.getRegistry(null);

         // Looking up the registry for the remote object
         RemoteInterface stub = (RemoteInterface) registry.lookup(args[0]);

         if (args[1].equals("BACKUP"))
            stub.backup(args[2], Integer.parseInt(args[3]));
         else if (args[1].equals("RESTORE"))
            stub.restore(args[2]);
         else if (args[1].equals("DELETE"))
            stub.delete(args[2]);
         else if (args[1].equals("RECLAIM"))
            stub.reclaim(Integer.parseInt(args[2]));
         else if (args[1].equals("STATE")) {
            stub.state();
         }

      } catch (Exception e) {
         System.err.println("Client exception: " + e.toString());
         e.printStackTrace();
      }
   }

   private static boolean validacoes(String[] args) {
      if (args.length < 2) {
         System.out.println("You must indicate the protocol (BACKUP, RESTORE, DELETE ou RECLAIM)");
         return false;
      }
      if (!args[1].equals("BACKUP") && !args[1].equals("RESTORE") && !args[1].equals("DELETE")
            && !args[1].equals("RECLAIM") && !args[1].equals("STATE")) {
         System.out.println(args[1]);
         System.out.println("<sub_protocol> must be one of these: BACKUP, RESTORE, DELETE ou RECLAIM");
         return false;
      }
      if (args[1].equals("STATE") && args.length != 2) {
         System.out.println("Properly usage: java TestApp <peer_ap> <sub_protocol>");
         return false;
      }
      if (!args[1].equals("BACKUP") && !args[1].equals("STATE") && args.length != 3) {
         System.out.println("Properly usage: java TestApp <peer_ap> <sub_protocol> <opnd_1>");
         return false;
      }
      if (args[1].equals("BACKUP")) {
         if (args.length != 4) {
            System.out.println("Properly usage: java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>");
            return false;
         }
         try {
            int degree = Integer.parseInt(args[3]);
            if (degree < 0 || degree > 9) {
               System.out.println("Replication degree must be a number up to 9");
               return false;
            }
         } catch (NumberFormatException e) {
            System.out.println("Replication degree must be a number up to 9");
            return false;
         }

      }
      if (args[1].equals("RECLAIM")) {
         try {
            Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            System.out.println("<opnd_1> must be a number (disk space (in KByte))");
            return false;
         }

      }
      return true;
   }
}