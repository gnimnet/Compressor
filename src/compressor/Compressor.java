package compressor;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A compressor for compress javascript and css file
 *
 * @author ming
 */
public class Compressor {

    private static final int DEFAULT_BUFF_SIZE = 1024;
    private static final String USER_DIR = System.getProperty("user.dir");
    private static final String CLOSURE = "closure.jar";
    private static final String YUI = "yui.jar";
    private static Charset charset = Charset.defaultCharset();
    private static final File closure = new File(USER_DIR + File.separator + CLOSURE);
    private static final File yui = new File(USER_DIR + File.separator + YUI);

    private static class CompressException extends Exception {

        private final String error;

        public CompressException(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            echo("usage:java -jar Compressor.jar path");
            echo("you can add argument \"--charset\" like \"UTF-8\"");
            echo("program will solve file suffix with \".js\" or \".css\"");
            echo("program will skip file suffix with \"-min.js\" or \"-min.css\"");
            echo("program compile js use google closure compiler");
            echo("program compress css use yahoo yui compressor");
            return;
        }
        ArrayList<String> paths = new ArrayList<String>();
        int index = 0;
        while (index < args.length) {
            String arg = args[index];
            if (arg.toLowerCase().equals("--charset")) {
                index++;
                if (index < args.length) {
                    charset = Charset.forName(args[index]);
                }
                index++;
            } else {
                paths.add(arg);
                index++;
            }
        }
        if (paths.isEmpty()) {
            echo("missing compile/compress path...");
            return;
        }
        echo("work in dir:" + USER_DIR);
        echo("use charset:" + charset.name());
        try {
            checkResource(CLOSURE, closure);
            checkResource(YUI, yui);
            for (String path : paths) {
                compile(new File(path));
            }
        } catch (IOException ex) {
            Logger.getLogger(Compressor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void checkResource(String name, File dest) throws IOException {
        if (!dest.exists()) {
            File dir = dest.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            InputStream in = Compressor.class.getResourceAsStream(name);
            try {
                FileOutputStream out = new FileOutputStream(dest);
                try {
                    byte[] buff = new byte[DEFAULT_BUFF_SIZE];
                    int read;
                    while ((read = in.read(buff)) != -1) {
                        out.write(buff, 0, read);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        }
    }

    private static void compile(File file) {
        if (file.isDirectory()) {
            echo("search in dir:" + file.getAbsolutePath());
            for (File child : file.listFiles()) {
                compile(child);
            }
        } else {
            String filename = file.getName().toLowerCase();
            if (filename.endsWith(".js") && !filename.endsWith("-min.js")) {
                echo("compile js:" + file.getAbsolutePath());
                try {
                    String minJS = runTool(closure, file, "--charset " + charset.name(), true);
                    write(file, minJS == null ? "" : minJS);
                    echo("success compile file " + file.getAbsolutePath());
                } catch (CompressException ex) {
                    echo("failed compile file " + file.getAbsolutePath());
                    echo("**************************************************");
                    echo(ex.getError());
                    echo("**************************************************");
                }
            } else if (filename.endsWith(".css") && !filename.endsWith("-min.css")) {
                echo("compress css:" + file.getAbsolutePath());
                try {
                    String minCSS = runTool(yui, file, "--type css --charset " + charset.name(), false);
                    write(file, minCSS == null ? "" : minCSS);
                    echo("success compress file " + file.getAbsolutePath());
                } catch (CompressException ex) {
                    echo("failed compile file " + file.getAbsolutePath());
                    echo("**************************************************");
                    echo(ex.getError());
                    echo("**************************************************");
                }
            } else {
                echo("skip file:" + file.getAbsolutePath());
            }
        }
    }

    public static String runTool(File tool, File source, String options, boolean unicode) throws CompressException {
        try {
            if (!tool.exists()) {
                throw new CompressException("compress tool not exist...");
            }
            String optionStr = options == null ? "" : " " + options.trim();
            String sourceCode = read(source);
            if (unicode) {
                sourceCode = toUnicode(sourceCode);
            }
            String name = tool.getName();
            File dir = tool.getParentFile();
            Process pid = Runtime.getRuntime().exec("java -jar " + name + optionStr, null, dir);
            //write code
            OutputStreamWriter writer = new OutputStreamWriter(pid.getOutputStream(), charset);
            try {
                writer.write(sourceCode);
                writer.flush();
            } finally {
                writer.close();
            }
            //get code
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(pid.getInputStream(), charset), 1024);
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            } finally {
                reader.close();
            }
            StringBuilder errorSb = new StringBuilder();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(pid.getErrorStream(), charset), 1024);
            try {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorSb.append(line);
                }
            } finally {
                errorReader.close();
            }
            //get exit value and return
            int exit = pid.waitFor();
            if (exit == 0) {
                return unicode ? toUnicode(sb.toString()) : sb.toString();
            } else {
                throw new CompressException(errorSb.toString());
            }
        } catch (CompressException ex) {
            throw ex;
        } catch (Exception ex) {
            Logger.getLogger(Compressor.class.getName()).log(Level.SEVERE, null, ex);
            throw new CompressException(ex.getMessage());
        }
    }

    public static String read(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
            try {
                char[] chBuff = new char[DEFAULT_BUFF_SIZE];
                int readCnt;
                while ((readCnt = reader.read(chBuff)) != -1) {
                    sb.append(chBuff, 0, readCnt);
                }
            } finally {
                reader.close();
            }
            return sb.toString();
        } catch (IOException ex) {
            Logger.getLogger(Compressor.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static boolean write(File file, String content) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
            try {
                if (content != null) {
                    writer.write(content);
                }
                writer.flush();
            } finally {
                writer.close();
            }
            return true;
        } catch (IOException ex) {
            Logger.getLogger(Compressor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    public static String toUnicode(String str) {
        char chArr[] = str.toCharArray();
        StringBuilder sb = new StringBuilder();
        final String ZERO = "0000";
        for (char c : chArr) {
            if (c >= 0 && c <= 127) {
                sb.append(c);
            } else {
                String val = Integer.toHexString(c);
                sb.append("\\u").append(ZERO.substring(val.length())).append(val);
            }
        }
        return sb.toString();
    }

    private static void echo(String msg) {
        System.out.println(msg);
    }
}
