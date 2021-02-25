package utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class getChunksOfFile {
    public static List<byte[]> splitFile(File f) throws IOException {
        List<byte[]> chunks = new ArrayList<byte[]>();
        int sizeOfChunks = 64000;// 64KB
        byte[] buffer = new byte[sizeOfChunks];
        byte[] soundBytes;
        // try-with-resources to ensure closing stream
        try (FileInputStream fis = new FileInputStream(f); BufferedInputStream bis = new BufferedInputStream(fis)) {
  
           int bytesAmount = 0;
           while ((bytesAmount = bis.read(buffer)) > 0) {
              try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                 out.write(buffer, 0, bytesAmount);
                 soundBytes = out.toByteArray();
              }
  
              chunks.add(soundBytes);
  
           }
           return chunks;
        }
  
     }

     public static int countChunks(File f) throws IOException {
      int count = 0;
      int sizeOfChunks = 64000;// 64KB
      byte[] buffer = new byte[sizeOfChunks];
      
      // try-with-resources to ensure closing stream
      try (FileInputStream fis = new FileInputStream(f); BufferedInputStream bis = new BufferedInputStream(fis)) {

        
         while ((bis.read(buffer)) > 0) {
            count++;
         }
         return count;
      }

   }

}