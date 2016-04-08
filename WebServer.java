package webserver;

import in2011.http.Request;
import in2011.http.Response;
import in2011.http.StatusCodes;
import in2011.http.EmptyMessageException;
import in2011.http.MessageFormatException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.apache.http.client.utils.DateUtils;



public class WebServer {

    private int port;
    private String rootDir;

    public WebServer(int port, String rootDir) {
        this.port = port;
        this.rootDir = rootDir;
    }
    
    //used to access the rootDir
    public String getRootDir(){
        return rootDir;
    }
    
    public void start() throws IOException {
        ServerSocket server = null; 
        try{
            server = new ServerSocket(port); 
        }catch(Exception e){
            JOptionPane.showMessageDialog(null, e);
        }
        
        while(true){
            Socket conn; 
            try{
                conn = server.accept(); 
                Thread t = new Thread(new MultiThread(conn, this));
                //create and start a new thread
                t.start(); 
            }catch(Exception e){
                System.out.println(e.toString());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2) {
            throw new Error(usage);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Error(usage + "\n" + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        WebServer server = new WebServer(port, rootDir);
        server.start();
    }
}

class MultiThread implements Runnable{
    Socket connection; 
    WebServer w;  
    InputStream s; 
    OutputStream os; 
    
    
    public MultiThread(Socket sock, WebServer ws){
            connection = sock;
            w = ws;
        }

    @Override
        public void run(){
            try{
                //reads & writes
                s = connection.getInputStream(); 
                os = connection.getOutputStream();  
                //parses  request from input stream
                Request req = Request.parse(s); 
                //retrieve methods 
                String meth = req.getMethod(); 
                
                //retrieve and decode uri  
                String uri = new java.net.URI(req.getURI()).getPath();
                //retrieve the path name
                String pathName = w.getRootDir() + uri;              
                
                //get and normalise the absolute path
                java.nio.file.Path path = Paths.get(pathName);
                java.nio.file.Path comPath = path.toAbsolutePath().normalize(); 
                
                //current date & time to string
                Date d = new Date();
                String date = d.toString(); 
                
                //checks the different types of methods
                switch (meth) {
                    //extract from webserver 
                    case "GET": 
                        //check if the HTTP version is 1.1 
                        if(!req.getVersion().equals("1.1")){ 
                            Response wrongHTTP = new Response(505); 
                            wrongHTTP.addHeaderField("Date", date);
                            wrongHTTP.write(os);
                        }else{
                            //check if the URI is legit
                            if(!path.startsWith(Paths.get(w.getRootDir()))){ 
                                Response notFound = new Response(404);
                                headerFile(notFound,path);
                                notFound.write(os);
                            }
                            if(path.toFile().isFile() && path.toFile().exists() 
                               && path.startsWith(w.getRootDir()) && Files.isReadable(path)){ 
                            //carry out various check
                                try{ 
                                    //process the request
                                    //prepares response to client
                                    Response res = new Response(200); 
                                    headerFile(res,path);
                                    //reponse 200 (success)
                                    res.write(os); 
                                    os.write(Files.readAllBytes(path));

                                }catch(Exception e){
                                    System.out.println(e.toString());
                                }
                            }else{
                                //if fails
                                Response wrongHTTP = new Response(400); 
                                //bad request
                                headerFile(wrongHTTP,path);   
                                wrongHTTP.write(os);     
                            }
                        }
                        break;
                        
                    
                        
                    case "HEAD":
                        //check if the HTTP version is 1.1 
                        if(!req.getVersion().equals("1.1")){ 
                            Response res = new Response(505); 
                            res.addHeaderField("Date", date);
                            res.write(os);
                        }else{
                            if(!path.startsWith(Paths.get(w.getRootDir()))){
                                Response notFound = new Response(404);
                                headerFile(notFound,path);
                                notFound.write(os);
                            }
                            //file not found
                            if(!path.toFile().exists()){ 
                                Response notFound = new Response(404);
                                headerFile(notFound,path);
                                notFound.write(os);
                            }
                            //bad request
                            if(path.toFile().isDirectory()){ 
                                Response badRequest = new Response(400); 
                                headerFile(badRequest,path);
                                badRequest.write(os);
                            }
                        
                            if(path.toFile().isFile() && path.toFile().exists() 
                               && path.startsWith(w.getRootDir())){
                                //creates response for the client
                                try{
                                    Response res = new Response(200); 
                                    headerFile(res,path);
                                    //write response 200 (success)
                                    res.write(os);     
                                }catch(Exception e){
                                    System.out.println(e.toString());
                                }
                            }
                        }
                        break;
                        
                        //upload content to webserver    
                    case "PUT": 
                        //check if the HTTP version is 1.1 
                        if(!req.getVersion().equals("1.1")){
                            Response res = new Response(505);
                            res.addHeaderField("Date", date);
                            res.write(os);
                        }else{
                            if(!path.startsWith(Paths.get(w.getRootDir()))){
                                Response notFound = new Response(404);
                                notFound.addHeaderField("Date", date);
                                notFound.write(os);
                            }
                            //if the file already exists
                            if(path.toFile().exists()){ 
                                Response res = new Response(403);
                                os.write(("The file at: "+ path.toFile().toString() +" already exists").getBytes());
                                res.write(os);
                            }
                             
                           if(Files.notExists(path) && Files.notExists(path.getParent()) && path.startsWith(w.getRootDir())){
                                Files.createDirectories(path.getParent());
                                OutputStream oss;
                                oss = Files.newOutputStream(path, CREATE_NEW);
                                this.readBytesWrites(s, oss);
                                oss.close();
                                Response res1 = new Response(201);
                                res1.addHeaderField("Date", date);
                                res1.write(os);
                           }else if(Files.notExists(path) && path.startsWith(w.getRootDir()) && Files.exists(path.getParent())){
                                OutputStream oss;
                                //creates file
                                oss = Files.newOutputStream(path); 
                                //writes to file
                                this.readBytesWrites(s, oss); 
                                oss.close();
                                Response res1 = new Response(201);
                                res1.addHeaderField("Date", date);
                                res1.write(os);
                           }
                        }
                        break;
                        
                    default:
                    {
                        //not implememted
                        Response res = new Response(501);
                        res.write(os);
                    }
                }
             
                os.close();
                connection.close();
            } catch (IOException | MessageFormatException ex) {
                System.out.println(ex.toString());
            } catch (URISyntaxException ex) {
                Logger.getLogger(MultiThread.class.getName()).log(Level.SEVERE, null, ex);
            }
               
        }
        

        public void readBytesWrites(InputStream i, OutputStream o) throws IOException{
            try{
                int b = i.read();
                while(b!=-1) {
                    o.write(b);
                    b = i.read();
                }
            }catch(Exception e){
                System.out.println(e.toString());
            }
        }
        
        public void headerFile(Response rs, java.nio.file.Path p) throws IOException{
            rs.addHeaderField("Date", new Date().toString());
            rs.addHeaderField("Content Length", Long.toString(p.toFile().length()));
            rs.addHeaderField("Last Modified", Files.getLastModifiedTime(p).toString());
        }

        
}