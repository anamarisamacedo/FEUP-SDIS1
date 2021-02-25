package utils;

public class fileExtension {
    public static String getFileExtension(String filePath){
        String extension = "";
        int i = filePath.lastIndexOf('.');
        int p = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (i > p) {
            extension = filePath.substring(i+1);
        }
        return extension;
     }
}