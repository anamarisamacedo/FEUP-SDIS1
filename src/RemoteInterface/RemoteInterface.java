package RemoteInterface;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

// Creating Remote interface for our application 
public interface RemoteInterface extends Remote {
   void backup(String filePath, int rdegree) throws RemoteException, IOException;

   void restore(String filePath) throws RemoteException, IOException;

   void delete(String filePath) throws RemoteException, IOException;

   void reclaim(int kBytes) throws RemoteException, IOException;

   void state()  throws RemoteException, IOException;
}
