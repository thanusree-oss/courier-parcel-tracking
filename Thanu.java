 
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDateTime;

public class Main {
    static ArrayList<Parcel> parcels = new ArrayList<>();
    static int count = 1001;
    static final String FILE = "parcels.txt";

    static class Parcel {
        String id, sender, receiver, phone, pickup, delivery;
        String description, status = "BOOKED";
        String failedReason = "", redelivery = "";
        ArrayList<String> history = new ArrayList<>();

        Parcel(String sender, String receiver, String phone,
               String pickup, String delivery, String description) {
            id = "CR" + count++;
            this.sender = sender;
            this.receiver = receiver;
            this.phone = phone;
            this.pickup = pickup;
            this.delivery = delivery;
            this.description = description;
            history.add("Parcel booked - " + LocalDateTime.now());
        }
    }

    static String esc(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String page(String content) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Courier Tracking System</title>
        <style>
        *{box-sizing:border-box}
        body{margin:0;font-family:Arial;background:#f2f5fb;color:#172554}
        header{background:#142b55;color:white;padding:24px;text-align:center}
        nav{background:#203e70;padding:14px;text-align:center}
        nav a{color:white;text-decoration:none;margin:9px;font-size:14px}
        main{max-width:950px;margin:30px auto;padding:15px}
        .card{background:white;padding:25px;margin-bottom:20px;
        border-radius:12px;box-shadow:0 3px 12px #0001}
        input,select,textarea{width:100%;padding:12px;margin:8px 0;
        border:1px solid #ccd5e5;border-radius:6px}
        button{background:#2458a6;color:white;padding:12px 20px;
        border:0;border-radius:6px;cursor:pointer}
        button:hover{background:#142b55}
        .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px}
        .stat{background:#e8efff;padding:20px;border-radius:10px;text-align:center}
        .stat h2{margin:5px}
        .success{color:green;font-weight:bold}
        table{width:100%;border-collapse:collapse}
        td,th{padding:10px;border-bottom:1px solid #ddd;text-align:left}
        footer{text-align:center;padding:20px;color:#64748b}
        </style>
        </head>
        <body>
        <header>
        <h1>Courier & Parcel Tracking</h1>
        <p>Fast • Safe • Reliable Delivery</p>
        </header>
        <nav>
        <a href="/">Home</a>
        <a href="/book">Book Parcel</a>
        <a href="/track">Track Parcel</a>
        <a href="/register">Register</a>
        <a href="/dashboard">Dashboard</a>
        <a href="/search">Search</a>
        <a href="/admin">Admin</a>
        </nav>
        <main>
        """ + content + """
        </main>
        <footer>Courier & Parcel Tracking System | Java Application</footer>
        </body></html>
        """;
    }

    static String field(String label, String name) {
        return "<label>" + label + "</label><input name='" +
                name + "' required>";
    }

    static String input(HttpExchange ex, String key) throws Exception {
        String body = new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String,String> data = new HashMap<>();
        for(String part : body.split("&")) {
            String[] pair = part.split("=",2);
            if(pair.length == 2)
                data.put(URLDecoder.decode(pair[0],"UTF-8"),
                         URLDecoder.decode(pair[1],"UTF-8"));
        }
        return data.getOrDefault(key,"").trim();
    }

    static void send(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
        ex.sendResponseHeaders(200,bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void redirect(HttpExchange ex, String url) throws IOException {
        ex.getResponseHeaders().set("Location",url);
        ex.sendResponseHeaders(303,-1);
        ex.close();
    }

    static Parcel find(String id) {
        for(Parcel p:parcels)
            if(p.id.equalsIgnoreCase(id)) return p;
        return null;
    }

    static void save() {
        try(PrintWriter w = new PrintWriter(FILE)) {
            for(Parcel p:parcels) {
                w.println(String.join("|",
                    p.id,p.sender,p.receiver,p.phone,p.pickup,
                    p.delivery,p.description,p.status,
                    p.failedReason,p.redelivery));
                for(String h:p.history)
                    w.println("H|"+p.id+"|"+h.replace("|"," "));
            }
        } catch(Exception e) {
            System.out.println("Save error: "+e.getMessage());
        }
    }

    static void load() {
        if(!Files.exists(Path.of(FILE))) return;
        try {
            Map<String,Parcel> map = new LinkedHashMap<>();
            for(String line:Files.readAllLines(Path.of(FILE))) {
                String[] a=line.split("\\|",-1);
                if(a[0].equals("H")) {
                    Parcel p=map.get(a[1]);
                    if(p!=null) p.history.add(a[2]);
                } else if(a.length>=10) {
                    Parcel p=new Parcel(a[1],a[2],a[3],a[4],a[5],a[6]);
                    p.id=a[0]; p.status=a[7];
                    p.failedReason=a[8]; p.redelivery=a[9];
                    p.history.clear();
                    map.put(p.id,p);
                    parcels.add(p);
                    try {count=Math.max(count,
                        Integer.parseInt(p.id.substring(2))+1);}
                    catch(Exception ignored){}
                }
            }
        } catch(Exception e) {
            System.out.println("Load error: "+e.getMessage());
        }
    }

    static void add(HttpExchange ex) throws Exception {
        String sender=input(ex,"sender");
        String receiver=input(ex,"receiver");
        String phone=input(ex,"phone");
        String pickup=input(ex,"pickup");
        String delivery=input(ex,"delivery");
        String description=input(ex,"description");

        Parcel p=new Parcel(sender,receiver,phone,pickup,delivery,description);
        parcels.add(p);
        save();
        redirect(ex,"/track?id="+p.id);
    }

    static void handle(HttpExchange ex) throws Exception {
        String path=ex.getRequestURI().getPath();
        String query=ex.getRequestURI().getRawQuery();

        if(path.equals("/book")) {
            send(ex,page("<div class='card'><h2>Book a Parcel</h2>"+
                "<form method='POST' action='/add'>"+
                field("Sender Name","sender")+
                field("Receiver Name","receiver")+
                field("Receiver Phone","phone")+
                field("Pickup Address","pickup")+
                field("Delivery Address","delivery")+
                field("Parcel Description","description")+
                "<button>Book Parcel</button></form></div>"));
            return;
        }

        if(path.equals("/add") && ex.getRequestMethod().equals("POST")) {
            add(ex); return;
        }

        if(path.equals("/register")) {
            send(ex,page("<div class='card'><h2>Customer Registration</h2>"+
                "<form method='POST' action='/registered'>"+
                field("Customer Name","name")+
                field("Phone Number","phone")+
                field("Address","address")+
                "<button>Register</button></form></div>"));
            return;
        }

        if(path.equals("/registered") && ex.getRequestMethod().equals("POST")) {
            String record=input(ex,"name")+" | "+input(ex,"phone")+
                " | "+input(ex,"address")+System.lineSeparator();
            Files.writeString(Path.of("customers.txt"),record,
                StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            send(ex,page("<div class='card'><h2 class='success'>Customer registered successfully!</h2></div>"));
            return;
        }

        if(path.equals("/track")) {
            String id="";
            if(query!=null) for(String q:query.split("&"))
                if(q.startsWith("id=")) id=URLDecoder.decode(q.substring(3),"UTF-8");

            send(ex,page("<div class='card'><h2>Track Your Parcel</h2>"+
                "<form method='GET'><input name='id' placeholder='Enter Tracking ID' required>"+
                "<button>Track</button></form></div>"+trackResult(id)));
            return;
        }

        if(path.equals("/search")) {
            send(ex,page("<div class='card'><h2>Search Parcels</h2>"+
                "<form method='GET'><input name='q' placeholder='Tracking ID or Receiver Name'>"+
                "<button>Search</button></form></div>"+
                searchResult(query)));
            return;
        }

        if(path.equals("/dashboard")) {
            int booked=0,transit=0,delivered=0,failed=0;
            for(Parcel p:parcels) {
                switch(p.status) {
                    case "BOOKED":booked++;break;
                    case "IN TRANSIT":transit++;break;
                    case "DELIVERED":delivered++;break;
                    case "FAILED":failed++;break;
                }
            }
            send(ex,page("<h2>Admin Dashboard</h2><div class='stats'>"+
                stat("Customers",customerCount())+
                stat("Total Parcels",parcels.size())+
                stat("Booked",booked)+stat("In Transit",transit)+
                stat("Delivered",delivered)+stat("Failed",failed)+
                "</div><div class='card'><h3>All Parcels</h3>"+table(parcels)+"</div>"));
            return;
        }

        if(path.equals("/admin")) {
            send(ex,page("<div class='card'><h2>Update Parcel Status</h2>"+
                "<form method='POST' action='/update'>"+
                field("Tracking ID","id")+
                "<label>Status</label><select name='status'>"+
                "<option>BOOKED</option><option>IN TRANSIT</option>"+
                "<option>DELIVERED</option><option>FAILED</option></select>"+
                "<label>Failure Reason (if failed)</label><input name='reason'>"+
                "<label>Redelivery Date and Time</label><input name='redelivery'>"+
                "<button>Update Parcel</button></form></div>"));
            return;
        }

        if(path.equals("/update") && ex.getRequestMethod().equals("POST")) {
            Parcel p=find(input(ex,"id"));
            if(p!=null) {
                p.status=input(ex,"status");
                p.failedReason=input(ex,"reason");
                p.redelivery=input(ex,"redelivery");
                p.history.add("Updated to "+p.status+" - "+LocalDateTime.now());
                save();
                send(ex,page("<div class='card'><h2 class='success'>Parcel updated successfully!</h2><p>"+esc(p.id)+"</p></div>"));
            } else send(ex,page("<div class='card'>Parcel not found.</div>"));
            return;
        }

        send(ex,page("<div class='card'><h2>Welcome to Courier Services</h2>"+
            "<p>Book, track and manage your parcels easily.</p>"+
            "<a href='/book'><button>Book a Parcel</button></a> "+
            "<a href='/track'><button>Track Parcel</button></a></div>"));
    }

    static String trackResult(String id) {
        if(id.isEmpty()) return "";
        Parcel p=find(id);
        if(p==null) return "<div class='card'>Parcel not found.</div>";
        StringBuilder s=new StringBuilder("<div class='card'><h2>Parcel Details</h2>");
        s.append("<p>Tracking ID: <b>").append(esc(p.id)).append("</b></p>");
        s.append("<p>Sender: ").append(esc(p.sender)).append("</p>");
        s.append("<p>Receiver: ").append(esc(p.receiver)).append("</p>");
        s.append("<p>Phone: ").append(esc(p.phone)).append("</p>");
        s.append("<p>Delivery Address: ").append(esc(p.delivery)).append("</p>");
        s.append("<h3>Status: ").append(esc(p.status)).append("</h3>");
        s.append("<p>Failure Reason: ").append(esc(p.failedReason)).append("</p>");
        s.append("<p>Redelivery: ").append(esc(p.redelivery)).append("</p><h3>History</h3>");
        for(String h:p.history) s.append("<p>").append(esc(h)).append("</p>");
        return s.append("</div>").toString();
    }

    static String searchResult(String query) {
        if(query==null) return "";
        String q="";
        for(String x:query.split("&"))
            if(x.startsWith("q=")) q=URLDecoder.decode(x.substring(2),StandardCharsets.UTF_8);
        StringBuilder s=new StringBuilder("<div class='card'>");
        for(Parcel p:parcels)
            if(p.id.toLowerCase().contains(q.toLowerCase())||
               p.receiver.toLowerCase().contains(q.toLowerCase()))
                s.append(esc(p.id)).append(" | ").append(esc(p.receiver))
                 .append(" | ").append(esc(p.status)).append("<hr>");
        return s.append("</div>").toString();
    }

    static String stat(String name,int value) {
        return "<div class='stat'><h2>"+value+"</h2>"+name+"</div>";
    }

    static int customerCount() {
        try {
            if(!Files.exists(Path.of("customers.txt"))) return 0;
            return (int)Files.lines(Path.of("customers.txt")).count();
        } catch(Exception e) {return 0;}
    }

    static String table(ArrayList<Parcel> list) {
        StringBuilder s=new StringBuilder("<table><tr><th>ID</th><th>Receiver</th><th>Status</th></tr>");
        for(Parcel p:list)
            s.append("<tr><td>").append(esc(p.id)).append("</td><td>")
             .append(esc(p.receiver)).append("</td><td>")
             .append(esc(p.status)).append("</td></tr>");
        return s.append("</table>").toString();
    }

    public static void main(String[] args) throws Exception {
        load();
        HttpServer server=HttpServer.create(new InetSocketAddress("0.0.0.0",8080),0);
        server.createContext("/",ex->{
            try {handle(ex);}
            catch(Exception e) {
                try {send(ex,page("<div class='card'>Error: "+esc(e.getMessage())+"</div>"));}
                catch(Exception ignored){}
            }
        });
        server.start();
        System.out.println("Courier Website running on port 8080");
    }
}
