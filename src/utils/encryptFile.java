package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class encryptFile {
    public static String getSha256ID(File file) {

        try {
           Path path = Paths.get(file.getAbsolutePath());
           String fileName = file.getName();

           BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
           String fileString = fileName + path + attr.lastModifiedTime() + attr.size() + Files.getOwner(path);
           
           MessageDigest digest = MessageDigest.getInstance("SHA-256");
           digest.update(fileString.getBytes());
  
           return bytesToHex(digest.digest());
  
        } catch (NoSuchAlgorithmException e) {
           e.printStackTrace();
           throw new RuntimeException(e);
        } catch (IOException e) {
           e.printStackTrace();
           throw new RuntimeException(e);
        }
  
     }
  
     private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
           String hex = Integer.toHexString(0xff & hash[i]);
           if (hex.length() == 1)
              hexString.append('0');
           hexString.append(hex);
        }
        return hexString.toString();
     }
}