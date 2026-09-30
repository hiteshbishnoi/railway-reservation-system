import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* ==========================================================
   DSA TRAIN BOOKING SYSTEM — Java backend (no frameworks).
   Data structures used:
     1. Singly Linked List -> stores train schedules   (TrainLinkedList)
     2. Queue (linked list based) -> booking requests   (BookingQueue)
     3. Stack (array based) -> structured booking log   (HistoryStack)

   Run:
     javac BackendServer.java
     java BackendServer
   Server starts on http://localhost:8080
   ========================================================== */

public class BackendServer {

    /* ---------- 1. LINKED LIST: Train Schedules ---------- */

    static class TrainNode {
        int id;
        String trainNo;
        String name;
        String source;
        String destination;
        int seats;
        Map<String, Integer> fares; // keys: "Sleeper", "3A", "2A", "1A"
        String duration;
        double rating;
        TrainNode next;

        TrainNode(int id, String trainNo, String name, String source, String destination,
                  int seats, Map<String, Integer> fares, String duration, double rating) {
            this.id = id;
            this.trainNo = trainNo;
            this.name = name;
            this.source = source;
            this.destination = destination;
            this.seats = seats;
            this.fares = fares;
            this.duration = duration;
            this.rating = rating;
        }

        int fareFor(String travelClass) {
            if (travelClass != null && fares.containsKey(travelClass)) {
                return fares.get(travelClass);
            }
            return fares.getOrDefault("Sleeper", 0);
        }
    }

    static class TrainLinkedList {
        private TrainNode head;

        void append(int id, String trainNo, String name, String source, String destination,
                    int seats, Map<String, Integer> fares, String duration, double rating) {
            TrainNode node = new TrainNode(id, trainNo, name, source, destination, seats, fares, duration, rating);
            if (head == null) {
                head = node;
                return;
            }
            TrainNode curr = head;
            while (curr.next != null) curr = curr.next;
            curr.next = node;
        }

        TrainNode findById(int id) {
            TrainNode curr = head;
            while (curr != null) {
                if (curr.id == id) return curr;
                curr = curr.next;
            }
            return null;
        }

        String toJson() {
            StringBuilder sb = new StringBuilder("[");
            TrainNode curr = head;
            boolean first = true;
            while (curr != null) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{")
                  .append("\"id\":").append(curr.id).append(",")
                  .append("\"trainNo\":\"").append(escape(curr.trainNo)).append("\",")
                  .append("\"name\":\"").append(escape(curr.name)).append("\",")
                  .append("\"source\":\"").append(escape(curr.source)).append("\",")
                  .append("\"destination\":\"").append(escape(curr.destination)).append("\",")
                  .append("\"seats\":").append(curr.seats).append(",")
                  .append("\"fares\":{")
                  .append("\"Sleeper\":").append(curr.fares.getOrDefault("Sleeper", 0)).append(",")
                  .append("\"3A\":").append(curr.fares.getOrDefault("3A", 0)).append(",")
                  .append("\"2A\":").append(curr.fares.getOrDefault("2A", 0)).append(",")
                  .append("\"1A\":").append(curr.fares.getOrDefault("1A", 0))
                  .append("},")
                  .append("\"duration\":\"").append(escape(curr.duration)).append("\",")
                  .append("\"rating\":").append(curr.rating)
                  .append("}");
                curr = curr.next;
            }
            sb.append("]");
            return sb.toString();
        }
    }

    // Small helper to build a fares map inline when seeding trains
    private static Map<String, Integer> fares(int sleeper, int ac3, int ac2, int ac1) {
        Map<String, Integer> m = new HashMap<>();
        m.put("Sleeper", sleeper);
        m.put("3A", ac3);
        m.put("2A", ac2);
        m.put("1A", ac1);
        return m;
    }

    /* ---------- 2. QUEUE: Booking Requests (linked list based) ---------- */

    static class BookingRequest {
        Map<String, String> fields;

        BookingRequest(Map<String, String> fields) {
            this.fields = fields;
        }
    }

    static class QueueNode {
        BookingRequest value;
        QueueNode next;

        QueueNode(BookingRequest value) {
            this.value = value;
        }
    }

    static class BookingQueue {
        private QueueNode front;
        private QueueNode rear;
        private int size = 0;

        boolean isEmpty() {
            return size == 0;
        }

        void enqueue(BookingRequest request) {
            QueueNode node = new QueueNode(request);
            if (rear == null) {
                front = node;
                rear = node;
            } else {
                rear.next = node;
                rear = node;
            }
            size++;
        }

        BookingRequest dequeue() {
            if (front == null) throw new NoSuchElementException("Queue is empty");
            QueueNode node = front;
            front = front.next;
            if (front == null) rear = null;
            size--;
            return node.value;
        }
    }

    /* ---------- 3. STACK: Structured Booking History (array based) ---------- */

    static class BookingRecord {
        String pnr;
        String passengerName;
        String trainName;
        String trainNo;
        String from;
        String to;
        String date;
        String travelClass;
        String coach;
        int seat;
        int fare;
        String status; // "success" | "error"

        String toJson() {
            return "{"
                + "\"pnr\":\"" + escape(pnr) + "\","
                + "\"passengerName\":\"" + escape(passengerName) + "\","
                + "\"trainName\":\"" + escape(trainName) + "\","
                + "\"trainNo\":\"" + escape(trainNo) + "\","
                + "\"from\":\"" + escape(from) + "\","
                + "\"to\":\"" + escape(to) + "\","
                + "\"date\":\"" + escape(date) + "\","
                + "\"travelClass\":\"" + escape(travelClass) + "\","
                + "\"coach\":\"" + escape(coach) + "\","
                + "\"seat\":" + seat + ","
                + "\"fare\":" + fare + ","
                + "\"status\":\"" + escape(status) + "\""
                + "}";
        }
    }

    static class HistoryStack {
        private BookingRecord[] items = new BookingRecord[8];
        private int top = -1;

        void push(BookingRecord entry) {
            if (top + 1 == items.length) resize();
            items[++top] = entry;
        }

        private void resize() {
            BookingRecord[] bigger = new BookingRecord[items.length * 2];
            System.arraycopy(items, 0, bigger, 0, items.length);
            items = bigger;
        }

        void deleteByPNR(String pnr){

    for(int i=0;i<=top;i++){

        if(items[i]!=null && items[i].pnr.equals(pnr)){

            for(int j=i;j<top;j++){

                items[j]=items[j+1];

            }

            items[top]=null;

            top--;

            break;

        }

    }

}
        // Returns entries most-recent-first (optionally filtered by PNR substring)
        String toJson(String pnrFilter) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (int i = top; i >= 0; i--) {
                BookingRecord r = items[i];
                if (pnrFilter != null && !pnrFilter.isEmpty()
                        && !r.pnr.toLowerCase().contains(pnrFilter.toLowerCase())) {
                    continue;
                }
                if (!first) sb.append(",");
                first = false;
                sb.append(r.toJson());
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /* ---------- Shared Application State ---------- */

    private static final TrainLinkedList trains = new TrainLinkedList();
    private static final BookingQueue bookingQueue = new BookingQueue();
    private static final HistoryStack historyStack = new HistoryStack();
    private static final Random random = new Random();

    private static void seedTrains() {
        // All 200 trains from trainData.js, with their original routes and fares
        trains.append(1, "22436", "Vande Bharat Express", "Delhi", "Varanasi", 156,
                fares(850, 1450, 2100, 3200), "8 Hours", 4.2);
        trains.append(2, "12951", "Rajdhani Express", "Delhi", "Mumbai", 98,
                fares(980, 1750, 2450, 3600), "16 Hours", 4.3);
        trains.append(3, "12045", "Shatabdi Express", "Delhi", "Chandigarh", 72,
                fares(650, 950, 1450, 2100), "5 Hours", 4.4);
        trains.append(4, "12259", "Duronto Express", "Delhi", "Kolkata", 120,
                fares(1100, 1800, 2500, 3700), "17 Hours", 4.5);
        trains.append(5, "12627", "Karnataka Express", "Delhi", "Bengaluru", 180,
                fares(1650, 2800, 3600, 4900), "34 Hours", 4.6);
        trains.append(6, "12621", "Tamil Nadu Express", "Delhi", "Chennai", 165,
                fares(1550, 2650, 3400, 4700), "31 Hours", 4.7);
        trains.append(7, "12724", "Telangana Express", "Delhi", "Hyderabad", 174,
                fares(1320, 2200, 2950, 4100), "22 Hours", 4.8);
        trains.append(8, "12049", "Gatimaan Express", "Delhi", "Agra", 220,
                fares(300, 650, 950, 1350), "2 Hours", 4.9);
        trains.append(9, "12413", "Pooja Express", "Delhi", "Jammu", 168,
                fares(850, 1450, 2050, 2900), "10 Hours", 4.1);
        trains.append(10, "12217", "Dehradun Shatabdi", "Delhi", "Dehradun", 185,
                fares(500, 850, 1250, 1750), "5 Hours", 4.2);
        trains.append(11, "22178", "Intercity Express", "Mumbai", "Pune", 210,
                fares(350, 650, 950, 1350), "4 Hours", 4.3);
        trains.append(12, "12123", "Deccan Queen", "Mumbai", "Pune", 185,
                fares(400, 700, 980, 1450), "4 Hours", 4.4);
        trains.append(13, "12932", "AC Double Decker", "Mumbai", "Ahmedabad", 170,
                fares(650, 1100, 1500, 2200), "7 Hours", 4.5);
        trains.append(14, "22953", "Gujarat Express", "Mumbai", "Ahmedabad", 160,
                fares(700, 1200, 1650, 2350), "8 Hours", 4.6);
        trains.append(15, "12137", "Punjab Mail", "Mumbai", "Delhi", 180,
                fares(980, 1700, 2450, 3600), "17 Hours", 4.7);
        trains.append(16, "12263", "Pune Duronto", "Delhi", "Pune", 172,
                fares(1300, 2150, 2900, 4050), "20 Hours", 4.8);
        trains.append(17, "12301", "Howrah Rajdhani", "Delhi", "Kolkata", 165,
                fares(1150, 1900, 2600, 3800), "17 Hours", 4.9);
        trains.append(18, "12309", "Patna Rajdhani", "Delhi", "Patna", 170,
                fares(900, 1500, 2100, 3100), "12 Hours", 4.1);
        trains.append(19, "12420", "Gomti Express", "Delhi", "Lucknow", 180,
                fares(700, 1200, 1700, 2400), "8 Hours", 4.2);
        trains.append(20, "12230", "Lucknow Mail", "Delhi", "Lucknow", 150,
                fares(750, 1250, 1750, 2500), "9 Hours", 4.3);
        trains.append(21, "12215", "Garib Rath Express", "Delhi", "Jaipur", 190,
                fares(450, 780, 1100, 1550), "5 Hours", 4.4);
        trains.append(22, "12986", "Double Decker Express", "Delhi", "Jaipur", 230,
                fares(420, 720, 1050, 1500), "4 Hours", 4.5);
        trains.append(23, "12156", "Bhopal Express", "Delhi", "Bhopal", 165,
                fares(820, 1350, 1850, 2600), "10 Hours", 4.6);
        trains.append(24, "12002", "Shatabdi Bhopal", "Delhi", "Bhopal", 142,
                fares(760, 1250, 1700, 2450), "8 Hours", 4.7);
        trains.append(25, "12919", "Malwa Express", "Delhi", "Indore", 180,
                fares(890, 1450, 1950, 2750), "12 Hours", 4.8);
        trains.append(26, "19308", "Indore Express", "Delhi", "Indore", 155,
                fares(910, 1490, 1990, 2790), "13 Hours", 4.9);
        trains.append(27, "12957", "Swarna Jayanti", "Delhi", "Ahmedabad", 174,
                fares(980, 1650, 2200, 3050), "14 Hours", 4.1);
        trains.append(28, "12915", "Ashram Express", "Delhi", "Ahmedabad", 160,
                fares(950, 1600, 2150, 3000), "15 Hours", 4.2);
        trains.append(29, "12309", "Patna Rajdhani", "Delhi", "Patna", 148,
                fares(930, 1550, 2050, 2950), "12 Hours", 4.3);
        trains.append(30, "12393", "Sampoorna Kranti", "Delhi", "Patna", 192,
                fares(900, 1500, 2000, 2900), "11 Hours", 4.4);
        trains.append(31, "12802", "Ranchi Rajdhani", "Delhi", "Ranchi", 146,
                fares(1020, 1700, 2250, 3150), "14 Hours", 4.5);
        trains.append(32, "18622", "Ranchi Express", "Delhi", "Ranchi", 171,
                fares(980, 1650, 2200, 3100), "15 Hours", 4.6);
        trains.append(33, "12013", "Amritsar Shatabdi", "Delhi", "Amritsar", 176,
                fares(650, 1100, 1450, 2050), "6 Hours", 4.7);
        trains.append(34, "12459", "Amritsar Express", "Delhi", "Amritsar", 132,
                fares(690, 1160, 1510, 2110), "7 Hours", 4.8);
        trains.append(35, "14205", "Ayodhya Express", "Delhi", "Prayagraj", 180,
                fares(700, 1200, 1650, 2400), "9 Hours", 4.9);
        trains.append(36, "14164", "Sangam Express", "Delhi", "Prayagraj", 170,
                fares(720, 1220, 1680, 2450), "9 Hours", 4.1);
        trains.append(37, "12398", "Mahabodhi Express", "Delhi", "Gaya", 165,
                fares(950, 1600, 2150, 3050), "14 Hours", 4.2);
        trains.append(38, "12487", "Seemanchal Express", "Delhi", "Katihar", 155,
                fares(1120, 1820, 2450, 3450), "17 Hours", 4.3);
        trains.append(39, "14055", "Brahmaputra Mail", "Delhi", "Guwahati", 210,
                fares(1650, 2750, 3550, 4900), "30 Hours", 4.4);
        trains.append(40, "12424", "Dibrugarh Rajdhani", "Delhi", "Guwahati", 168,
                fares(1750, 2850, 3650, 5100), "28 Hours", 4.5);
        trains.append(41, "12901", "Mumbai Rajdhani", "Mumbai", "Surat", 185,
                fares(420, 720, 980, 1450), "4 Hours", 4.6);
        trains.append(42, "22955", "Flying Ranee", "Mumbai", "Surat", 210,
                fares(450, 760, 1050, 1500), "5 Hours", 4.7);
        trains.append(43, "12925", "Paschim Express", "Mumbai", "Vadodara", 175,
                fares(520, 860, 1180, 1700), "6 Hours", 4.8);
        trains.append(44, "22961", "Vadodara Superfast", "Mumbai", "Vadodara", 168,
                fares(500, 850, 1150, 1680), "6 Hours", 4.9);
        trains.append(45, "12931", "Ahmedabad Double Decker", "Mumbai", "Ahmedabad", 220,
                fares(650, 1100, 1500, 2200), "7 Hours", 4.1);
        trains.append(46, "22953", "Gujarat SF Express", "Mumbai", "Ahmedabad", 190,
                fares(700, 1200, 1650, 2350), "8 Hours", 4.2);
        trains.append(47, "10103", "Mandovi Express", "Mumbai", "Goa", 180,
                fares(820, 1380, 1880, 2700), "10 Hours", 4.3);
        trains.append(48, "22119", "Konkan Kanya", "Mumbai", "Goa", 172,
                fares(850, 1420, 1920, 2750), "11 Hours", 4.4);
        trains.append(49, "11061", "Nashik Intercity", "Mumbai", "Nashik", 210,
                fares(350, 650, 900, 1300), "4 Hours", 4.5);
        trains.append(50, "12109", "Panchavati Express", "Mumbai", "Nashik", 205,
                fares(360, 670, 920, 1320), "4 Hours", 4.6);
        trains.append(51, "12139", "Sewagram Express", "Mumbai", "Nagpur", 188,
                fares(850, 1450, 1950, 2850), "12 Hours", 4.7);
        trains.append(52, "12289", "Nagpur Duronto", "Mumbai", "Nagpur", 170,
                fares(900, 1520, 2050, 2950), "11 Hours", 4.8);
        trains.append(53, "12701", "Hussain Sagar Express", "Mumbai", "Hyderabad", 182,
                fares(980, 1650, 2200, 3150), "14 Hours", 4.9);
        trains.append(54, "22731", "Hyderabad SF", "Mumbai", "Hyderabad", 176,
                fares(950, 1600, 2150, 3050), "13 Hours", 4.1);
        trains.append(55, "11301", "Udyan Express", "Mumbai", "Bengaluru", 180,
                fares(1450, 2400, 3150, 4450), "22 Hours", 4.2);
        trains.append(56, "22131", "Bengaluru Superfast", "Mumbai", "Bengaluru", 172,
                fares(1420, 2350, 3100, 4350), "21 Hours", 4.3);
        trains.append(57, "11027", "Chennai Mail", "Mumbai", "Chennai", 184,
                fares(1520, 2500, 3250, 4550), "23 Hours", 4.4);
        trains.append(58, "22159", "Chennai Express", "Mumbai", "Chennai", 176,
                fares(1500, 2450, 3200, 4500), "22 Hours", 4.5);
        trains.append(59, "12979", "Jaipur SF Express", "Mumbai", "Jaipur", 165,
                fares(1100, 1850, 2450, 3500), "16 Hours", 4.6);
        trains.append(60, "12981", "Pink City Express", "Mumbai", "Jaipur", 158,
                fares(1120, 1880, 2480, 3550), "17 Hours", 4.7);
        trains.append(61, "12301", "Howrah Rajdhani", "Kolkata", "Delhi", 180,
                fares(1150, 1900, 2600, 3800), "17 Hours", 4.8);
        trains.append(62, "12313", "Sealdah Rajdhani", "Kolkata", "Delhi", 170,
                fares(1180, 1950, 2650, 3850), "18 Hours", 4.9);
        trains.append(63, "12309", "Patna Rajdhani", "Kolkata", "Patna", 195,
                fares(520, 880, 1200, 1750), "6 Hours", 4.1);
        trains.append(64, "13105", "Patna Express", "Kolkata", "Patna", 205,
                fares(500, 850, 1180, 1700), "7 Hours", 4.2);
        trains.append(65, "12835", "Ranchi Intercity", "Kolkata", "Ranchi", 188,
                fares(480, 820, 1120, 1650), "6 Hours", 4.3);
        trains.append(66, "18615", "Ranchi Express", "Kolkata", "Ranchi", 178,
                fares(520, 860, 1160, 1700), "7 Hours", 4.4);
        trains.append(67, "12517", "Garib Rath", "Kolkata", "Guwahati", 185,
                fares(1100, 1800, 2450, 3550), "18 Hours", 4.5);
        trains.append(68, "15647", "Saraighat Express", "Kolkata", "Guwahati", 176,
                fares(1150, 1850, 2500, 3600), "19 Hours", 4.6);
        trains.append(69, "12801", "Puri Express", "Kolkata", "Bhubaneswar", 190,
                fares(550, 920, 1250, 1800), "7 Hours", 4.7);
        trains.append(70, "12881", "Bhubaneswar SF", "Kolkata", "Bhubaneswar", 198,
                fares(530, 900, 1220, 1780), "6 Hours", 4.8);
        trains.append(71, "13009", "Doon Express", "Kolkata", "Varanasi", 182,
                fares(780, 1320, 1800, 2550), "11 Hours", 4.9);
        trains.append(72, "13151", "Varanasi Express", "Kolkata", "Varanasi", 170,
                fares(760, 1280, 1750, 2500), "10 Hours", 4.1);
        trains.append(73, "12839", "Chennai Mail", "Kolkata", "Chennai", 176,
                fares(1500, 2450, 3200, 4550), "27 Hours", 4.2);
        trains.append(74, "12841", "Coromandel Express", "Kolkata", "Chennai", 185,
                fares(1480, 2420, 3150, 4500), "26 Hours", 4.3);
        trains.append(75, "12245", "Duronto Express", "Kolkata", "Bengaluru", 165,
                fares(1750, 2850, 3650, 5100), "30 Hours", 4.4);
        trains.append(76, "12863", "Humsafar Express", "Kolkata", "Bengaluru", 172,
                fares(1720, 2800, 3600, 5050), "31 Hours", 4.5);
        trains.append(77, "12703", "Falaknuma Express", "Kolkata", "Hyderabad", 178,
                fares(1350, 2250, 2950, 4200), "24 Hours", 4.6);
        trains.append(78, "12705", "Hyderabad Express", "Kolkata", "Hyderabad", 169,
                fares(1380, 2280, 2980, 4250), "25 Hours", 4.7);
        trains.append(79, "13167", "Lucknow Express", "Kolkata", "Lucknow", 182,
                fares(880, 1450, 1980, 2850), "13 Hours", 4.8);
        trains.append(80, "12391", "Shramjeevi Express", "Kolkata", "Lucknow", 176,
                fares(900, 1480, 2020, 2900), "14 Hours", 4.9);
        trains.append(81, "12608", "Lalbagh Express", "Bengaluru", "Chennai", 210,
                fares(520, 880, 1180, 1680), "6 Hours", 4.1);
        trains.append(82, "12028", "Shatabdi Express", "Bengaluru", "Chennai", 198,
                fares(500, 850, 1150, 1650), "5 Hours", 4.2);
        trains.append(83, "12604", "Chennai Mail", "Bengaluru", "Chennai", 188,
                fares(540, 900, 1200, 1700), "7 Hours", 4.3);
        trains.append(84, "12640", "Brindavan Express", "Bengaluru", "Chennai", 205,
                fares(510, 870, 1170, 1670), "6 Hours", 4.4);
        trains.append(85, "12786", "Kacheguda Express", "Bengaluru", "Hyderabad", 182,
                fares(760, 1280, 1750, 2500), "10 Hours", 4.5);
        trains.append(86, "12649", "Hyderabad Express", "Bengaluru", "Hyderabad", 176,
                fares(730, 1250, 1700, 2450), "9 Hours", 4.6);
        trains.append(87, "16511", "Kannur Express", "Bengaluru", "Mysore", 220,
                fares(250, 450, 650, 980), "3 Hours", 4.7);
        trains.append(88, "16215", "Chamundi Express", "Bengaluru", "Mysore", 240,
                fares(240, 420, 620, 950), "3 Hours", 4.8);
        trains.append(89, "12677", "Kochuveli Express", "Bengaluru", "Kochi", 180,
                fares(820, 1380, 1880, 2700), "11 Hours", 4.9);
        trains.append(90, "16315", "Kerala Express", "Bengaluru", "Kochi", 170,
                fares(800, 1350, 1850, 2650), "10 Hours", 4.1);
        trains.append(91, "17309", "Vasco Express", "Bengaluru", "Goa", 176,
                fares(880, 1450, 1980, 2850), "12 Hours", 4.2);
        trains.append(92, "12779", "Goa Superfast", "Bengaluru", "Goa", 184,
                fares(850, 1420, 1950, 2800), "11 Hours", 4.3);
        trains.append(93, "11014", "Udyan Express", "Bengaluru", "Mumbai", 165,
                fares(1450, 2400, 3200, 4500), "22 Hours", 4.4);
        trains.append(94, "22134", "Mumbai SF", "Bengaluru", "Mumbai", 172,
                fares(1420, 2350, 3150, 4450), "21 Hours", 4.5);
        trains.append(95, "12630", "Karnataka Express", "Bengaluru", "Delhi", 160,
                fares(1680, 2800, 3600, 5000), "34 Hours", 4.6);
        trains.append(96, "22692", "Rajdhani Express", "Bengaluru", "Delhi", 158,
                fares(1750, 2850, 3650, 5100), "31 Hours", 4.7);
        trains.append(97, "11302", "Pune Express", "Bengaluru", "Pune", 178,
                fares(980, 1650, 2250, 3200), "15 Hours", 4.8);
        trains.append(98, "22145", "Pune SF", "Bengaluru", "Pune", 170,
                fares(950, 1600, 2200, 3150), "14 Hours", 4.9);
        trains.append(99, "12680", "Coimbatore Express", "Bengaluru", "Coimbatore", 190,
                fares(620, 1050, 1450, 2050), "8 Hours", 4.1);
        trains.append(100, "16320", "Trivandrum Express", "Bengaluru", "Trivandrum", 172,
                fares(1100, 1850, 2450, 3500), "16 Hours", 4.2);
        trains.append(101, "12603", "Chennai Hyderabad Express", "Chennai", "Hyderabad", 182,
                fares(850, 1450, 1950, 2800), "12 Hours", 4.3);
        trains.append(102, "12759", "Charminar Express", "Chennai", "Hyderabad", 176,
                fares(880, 1480, 1980, 2850), "13 Hours", 4.4);
        trains.append(103, "12607", "Lalbagh Express", "Chennai", "Bengaluru", 210,
                fares(520, 880, 1180, 1680), "6 Hours", 4.5);
        trains.append(104, "12027", "Chennai Shatabdi", "Chennai", "Bengaluru", 205,
                fares(500, 850, 1150, 1650), "5 Hours", 4.6);
        trains.append(105, "12675", "Kochi Express", "Chennai", "Kochi", 188,
                fares(820, 1380, 1880, 2700), "11 Hours", 4.7);
        trains.append(106, "16381", "Kerala SF Express", "Chennai", "Kochi", 180,
                fares(800, 1350, 1850, 2650), "10 Hours", 4.8);
        trains.append(107, "12679", "Coimbatore Intercity", "Chennai", "Coimbatore", 195,
                fares(650, 1080, 1450, 2100), "8 Hours", 4.9);
        trains.append(108, "12673", "Cheran Express", "Chennai", "Coimbatore", 185,
                fares(620, 1050, 1420, 2050), "8 Hours", 4.1);
        trains.append(109, "12637", "Pandian Express", "Chennai", "Madurai", 178,
                fares(700, 1180, 1600, 2300), "9 Hours", 4.2);
        trains.append(110, "12651", "Madurai Superfast", "Chennai", "Madurai", 184,
                fares(680, 1150, 1580, 2250), "8 Hours", 4.3);
        trains.append(111, "12695", "Trivandrum Express", "Chennai", "Trivandrum", 172,
                fares(1100, 1850, 2450, 3500), "16 Hours", 4.4);
        trains.append(112, "12697", "Kanyakumari Express", "Chennai", "Trivandrum", 168,
                fares(1080, 1820, 2420, 3450), "15 Hours", 4.5);
        trains.append(113, "12621", "Tamil Nadu Express", "Chennai", "Delhi", 180,
                fares(1650, 2750, 3550, 4950), "32 Hours", 4.6);
        trains.append(114, "12615", "Grand Trunk Express", "Chennai", "Delhi", 174,
                fares(1680, 2780, 3600, 5000), "33 Hours", 4.7);
        trains.append(115, "11028", "Mumbai Mail", "Chennai", "Mumbai", 182,
                fares(1500, 2480, 3250, 4550), "23 Hours", 4.8);
        trains.append(116, "22160", "Chennai Mumbai SF", "Chennai", "Mumbai", 176,
                fares(1480, 2450, 3200, 4500), "22 Hours", 4.9);
        trains.append(117, "12840", "Coromandel Express", "Chennai", "Kolkata", 188,
                fares(1550, 2550, 3350, 4700), "26 Hours", 4.1);
        trains.append(118, "12842", "Howrah Mail", "Chennai", "Kolkata", 180,
                fares(1580, 2580, 3380, 4750), "27 Hours", 4.2);
        trains.append(119, "12711", "Pinakini Express", "Chennai", "Vijayawada", 210,
                fares(550, 920, 1250, 1800), "7 Hours", 4.3);
        trains.append(120, "17209", "Vijayawada Intercity", "Chennai", "Vijayawada", 198,
                fares(580, 950, 1280, 1850), "8 Hours", 4.4);
        trains.append(121, "12785", "Kacheguda Express", "Hyderabad", "Bengaluru", 182,
                fares(760, 1280, 1750, 2500), "10 Hours", 4.5);
        trains.append(122, "12648", "Hyderabad SF Express", "Hyderabad", "Bengaluru", 176,
                fares(730, 1250, 1700, 2450), "9 Hours", 4.6);
        trains.append(123, "12760", "Charminar Express", "Hyderabad", "Chennai", 180,
                fares(880, 1480, 1980, 2850), "13 Hours", 4.7);
        trains.append(124, "12604", "Chennai Express", "Hyderabad", "Chennai", 175,
                fares(850, 1450, 1950, 2800), "12 Hours", 4.8);
        trains.append(125, "12701", "Hussain Sagar Express", "Hyderabad", "Mumbai", 188,
                fares(980, 1650, 2200, 3150), "14 Hours", 4.9);
        trains.append(126, "22732", "Mumbai SF Express", "Hyderabad", "Mumbai", 176,
                fares(950, 1600, 2150, 3050), "13 Hours", 4.1);
        trains.append(127, "12438", "Telangana Rajdhani", "Hyderabad", "Delhi", 170,
                fares(1380, 2250, 2950, 4150), "20 Hours", 4.2);
        trains.append(128, "12723", "Telangana Express", "Hyderabad", "Delhi", 180,
                fares(1320, 2200, 2900, 4050), "21 Hours", 4.3);
        trains.append(129, "17013", "Hyderabad Pune Express", "Hyderabad", "Pune", 184,
                fares(700, 1200, 1650, 2350), "9 Hours", 4.4);
        trains.append(130, "12729", "Pune SF Express", "Hyderabad", "Pune", 178,
                fares(680, 1180, 1600, 2300), "8 Hours", 4.5);
        trains.append(131, "12722", "Dakshin Express", "Hyderabad", "Nagpur", 190,
                fares(650, 1100, 1500, 2150), "8 Hours", 4.6);
        trains.append(132, "12655", "Nagpur Intercity", "Hyderabad", "Nagpur", 186,
                fares(620, 1050, 1450, 2100), "7 Hours", 4.7);
        trains.append(133, "17201", "Golconda Express", "Hyderabad", "Vijayawada", 220,
                fares(480, 820, 1120, 1650), "6 Hours", 4.8);
        trains.append(134, "12795", "Vijayawada SF", "Hyderabad", "Vijayawada", 210,
                fares(450, 780, 1080, 1600), "5 Hours", 4.9);
        trains.append(135, "12727", "Godavari Express", "Hyderabad", "Visakhapatnam", 188,
                fares(850, 1450, 1950, 2800), "12 Hours", 4.1);
        trains.append(136, "18520", "Vizag Express", "Hyderabad", "Visakhapatnam", 182,
                fares(820, 1400, 1900, 2750), "11 Hours", 4.2);
        trains.append(137, "16331", "Kerala Superfast", "Hyderabad", "Kochi", 176,
                fares(1180, 1980, 2650, 3800), "18 Hours", 4.3);
        trains.append(138, "12683", "Kochi Express", "Hyderabad", "Kochi", 170,
                fares(1150, 1950, 2600, 3750), "17 Hours", 4.4);
        trains.append(139, "12741", "Konkan Express", "Hyderabad", "Goa", 184,
                fares(980, 1650, 2200, 3150), "14 Hours", 4.5);
        trains.append(140, "17320", "Goa Superfast", "Hyderabad", "Goa", 176,
                fares(950, 1600, 2150, 3050), "13 Hours", 4.6);
        trains.append(141, "12124", "Deccan Queen", "Pune", "Mumbai", 220,
                fares(350, 650, 900, 1350), "4 Hours", 4.7);
        trains.append(142, "22178", "Intercity Express", "Pune", "Mumbai", 210,
                fares(360, 670, 920, 1380), "4 Hours", 4.8);
        trains.append(143, "11003", "Mandovi Express", "Pune", "Goa", 185,
                fares(720, 1250, 1750, 2500), "8 Hours", 4.9);
        trains.append(144, "17317", "Goa Express", "Pune", "Goa", 176,
                fares(760, 1280, 1780, 2550), "9 Hours", 4.1);
        trains.append(145, "11302", "Udyan Express", "Pune", "Bengaluru", 182,
                fares(980, 1650, 2250, 3200), "15 Hours", 4.2);
        trains.append(146, "22146", "Bengaluru SF", "Pune", "Bengaluru", 175,
                fares(950, 1600, 2200, 3150), "14 Hours", 4.3);
        trains.append(147, "17014", "Hyderabad Express", "Pune", "Hyderabad", 190,
                fares(700, 1200, 1650, 2350), "9 Hours", 4.4);
        trains.append(148, "12730", "Hyderabad SF", "Pune", "Hyderabad", 180,
                fares(680, 1180, 1600, 2300), "8 Hours", 4.5);
        trains.append(149, "11027", "Chennai Mail", "Pune", "Chennai", 170,
                fares(1180, 1980, 2650, 3800), "18 Hours", 4.6);
        trains.append(150, "22159", "Chennai Express", "Pune", "Chennai", 168,
                fares(1150, 1950, 2600, 3750), "17 Hours", 4.7);
        trains.append(151, "22944", "Ahmedabad Express", "Pune", "Ahmedabad", 180,
                fares(780, 1320, 1800, 2550), "10 Hours", 4.8);
        trains.append(152, "19436", "Sabarmati Express", "Pune", "Ahmedabad", 172,
                fares(820, 1360, 1840, 2600), "11 Hours", 4.9);
        trains.append(153, "12980", "Jaipur Express", "Pune", "Jaipur", 176,
                fares(1200, 2050, 2750, 3900), "18 Hours", 4.1);
        trains.append(154, "12982", "Pink City Express", "Pune", "Jaipur", 168,
                fares(1180, 2000, 2700, 3850), "17 Hours", 4.2);
        trains.append(155, "12138", "Punjab Mail", "Pune", "Delhi", 185,
                fares(1450, 2400, 3200, 4500), "22 Hours", 4.3);
        trains.append(156, "12264", "Delhi Duronto", "Pune", "Delhi", 175,
                fares(1400, 2350, 3150, 4450), "20 Hours", 4.4);
        trains.append(157, "12140", "Nagpur Express", "Pune", "Nagpur", 180,
                fares(820, 1380, 1880, 2700), "11 Hours", 4.5);
        trains.append(158, "12290", "Nagpur Duronto", "Pune", "Nagpur", 170,
                fares(800, 1350, 1850, 2650), "10 Hours", 4.6);
        trains.append(159, "19314", "Indore Express", "Pune", "Indore", 176,
                fares(880, 1480, 1980, 2850), "12 Hours", 4.7);
        trains.append(160, "22912", "Malwa Superfast", "Pune", "Indore", 168,
                fares(850, 1450, 1950, 2800), "11 Hours", 4.8);
        trains.append(161, "12958", "Swarna Jayanti Express", "Ahmedabad", "Delhi", 182,
                fares(980, 1650, 2200, 3050), "14 Hours", 4.9);
        trains.append(162, "12916", "Ashram Express", "Ahmedabad", "Delhi", 176,
                fares(950, 1600, 2150, 3000), "15 Hours", 4.1);
        trains.append(163, "22954", "Gujarat Superfast", "Ahmedabad", "Mumbai", 190,
                fares(700, 1200, 1650, 2350), "8 Hours", 4.2);
        trains.append(164, "12932", "AC Double Decker", "Ahmedabad", "Mumbai", 210,
                fares(650, 1100, 1500, 2200), "7 Hours", 4.3);
        trains.append(165, "12983", "Jaipur SF Express", "Ahmedabad", "Jaipur", 172,
                fares(820, 1380, 1880, 2700), "11 Hours", 4.4);
        trains.append(166, "19703", "Aravali Express", "Ahmedabad", "Jaipur", 168,
                fares(850, 1420, 1920, 2750), "12 Hours", 4.5);
        trains.append(167, "19001", "Surat Intercity", "Ahmedabad", "Surat", 220,
                fares(350, 620, 850, 1250), "4 Hours", 4.6);
        trains.append(168, "22948", "Surat Superfast", "Ahmedabad", "Surat", 228,
                fares(320, 600, 820, 1200), "3 Hours", 4.7);
        trains.append(169, "19309", "Indore Express", "Ahmedabad", "Indore", 180,
                fares(680, 1180, 1600, 2300), "8 Hours", 4.8);
        trains.append(170, "19340", "Malwa SF", "Ahmedabad", "Indore", 175,
                fares(650, 1150, 1580, 2250), "7 Hours", 4.9);
        trains.append(171, "12991", "Udaipur Express", "Ahmedabad", "Udaipur", 182,
                fares(520, 900, 1250, 1800), "6 Hours", 4.1);
        trains.append(172, "19601", "Mewar Express", "Ahmedabad", "Udaipur", 176,
                fares(550, 920, 1280, 1850), "7 Hours", 4.2);
        trains.append(173, "19570", "Rajkot Express", "Ahmedabad", "Rajkot", 195,
                fares(450, 780, 1080, 1550), "5 Hours", 4.3);
        trains.append(174, "22959", "Rajkot Superfast", "Ahmedabad", "Rajkot", 188,
                fares(420, 750, 1050, 1500), "4 Hours", 4.4);
        trains.append(175, "19035", "Vadodara Intercity", "Ahmedabad", "Vadodara", 240,
                fares(220, 420, 620, 920), "2 Hours", 4.5);
        trains.append(176, "22961", "Vadodara SF", "Ahmedabad", "Vadodara", 235,
                fares(250, 450, 650, 950), "2 Hours", 4.6);
        trains.append(177, "12811", "Nagpur Express", "Ahmedabad", "Nagpur", 180,
                fares(980, 1650, 2200, 3100), "13 Hours", 4.7);
        trains.append(178, "22975", "Nagpur Superfast", "Ahmedabad", "Nagpur", 175,
                fares(950, 1600, 2150, 3050), "12 Hours", 4.8);
        trains.append(179, "19435", "Pune Express", "Ahmedabad", "Pune", 184,
                fares(780, 1320, 1800, 2550), "10 Hours", 4.9);
        trains.append(180, "22138", "Pune Superfast", "Ahmedabad", "Pune", 176,
                fares(750, 1280, 1750, 2500), "9 Hours", 4.1);
        trains.append(181, "12216", "Jaipur Garib Rath", "Jaipur", "Delhi", 210,
                fares(450, 780, 1100, 1550), "5 Hours", 4.2);
        trains.append(182, "12984", "Pink City Express", "Jaipur", "Delhi", 220,
                fares(420, 750, 1050, 1500), "4 Hours", 4.3);
        trains.append(183, "12980", "Jaipur Mumbai Express", "Jaipur", "Mumbai", 175,
                fares(1100, 1850, 2450, 3500), "16 Hours", 4.4);
        trains.append(184, "12982", "Pink City SF", "Jaipur", "Mumbai", 168,
                fares(1120, 1880, 2480, 3550), "17 Hours", 4.5);
        trains.append(185, "19704", "Aravali Express", "Jaipur", "Ahmedabad", 180,
                fares(820, 1380, 1880, 2700), "11 Hours", 4.6);
        trains.append(186, "12992", "Ahmedabad SF", "Jaipur", "Ahmedabad", 176,
                fares(800, 1350, 1850, 2650), "10 Hours", 4.7);
        trains.append(187, "12230", "Lucknow Mail", "Lucknow", "Delhi", 185,
                fares(750, 1250, 1750, 2500), "9 Hours", 4.8);
        trains.append(188, "12420", "Gomti Express", "Lucknow", "Delhi", 192,
                fares(700, 1200, 1700, 2400), "8 Hours", 4.9);
        trains.append(189, "14206", "Ayodhya Express", "Lucknow", "Varanasi", 205,
                fares(450, 780, 1080, 1550), "5 Hours", 4.1);
        trains.append(190, "14218", "Kashi Express", "Lucknow", "Varanasi", 210,
                fares(420, 750, 1050, 1500), "4 Hours", 4.2);
        trains.append(191, "12310", "Patna Rajdhani", "Patna", "Delhi", 178,
                fares(930, 1550, 2050, 2950), "12 Hours", 4.3);
        trains.append(192, "12394", "Sampoorna Kranti", "Patna", "Delhi", 184,
                fares(900, 1500, 2000, 2900), "11 Hours", 4.4);
        trains.append(193, "13106", "Patna Express", "Patna", "Kolkata", 198,
                fares(500, 850, 1180, 1700), "7 Hours", 4.5);
        trains.append(194, "12314", "Howrah Intercity", "Patna", "Kolkata", 205,
                fares(520, 880, 1200, 1750), "6 Hours", 4.6);
        trains.append(195, "15648", "Saraighat Express", "Guwahati", "Kolkata", 176,
                fares(1150, 1850, 2500, 3600), "18 Hours", 4.7);
        trains.append(196, "12518", "Garib Rath", "Guwahati", "Kolkata", 182,
                fares(1120, 1820, 2450, 3550), "17 Hours", 4.8);
        trains.append(197, "12426", "Jammu Rajdhani", "Jammu", "Delhi", 180,
                fares(820, 1380, 1780, 2580), "9 Hours", 4.9);
        trains.append(198, "14034", "Jammu Mail", "Jammu", "Delhi", 188,
                fares(850, 1420, 1820, 2620), "10 Hours", 4.1);
        trains.append(199, "14612", "Kashmir Express", "Srinagar", "Jammu", 215,
                fares(350, 620, 900, 1350), "4 Hours", 4.2);
        trains.append(200, "14614", "Valley Express", "Srinagar", "Jammu", 220,
                fares(380, 650, 920, 1380), "5 Hours", 4.3);

    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /* ---------- Minimal JSON parsing (only needs to handle our own flat payloads) ---------- */

    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> map = new HashMap<>();
        // Matches "key":"value"  or  "key":123
        Pattern pattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"([^\"]*)\"|-?\\d+(?:\\.\\d+)?)");
        Matcher m = pattern.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(3) != null ? m.group(3) : m.group(2);
            map.put(key, value);
        }
        return map;
    }

    /* ---------- HTTP Handlers ---------- */

    static class TrainsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            withCors(exchange);
            if (isPreflight(exchange)) return;
            sendJson(exchange, 200, trains.toJson());
        }
    }

    static class HistoryHandler implements HttpHandler {

    public void handle(HttpExchange exchange) throws IOException {

        withCors(exchange);

        if(isPreflight(exchange)) return;

        if(exchange.getRequestMethod().equalsIgnoreCase("DELETE")){

            String query=exchange.getRequestURI().getQuery();

            String pnr="";

            if(query!=null){

                for(String pair:query.split("&")){

                    String[] kv=pair.split("=",2);

                    if(kv.length==2 && kv[0].equals("pnr")){

                        pnr=java.net.URLDecoder.decode(kv[1],"UTF-8");

                    }

                }

            }

            historyStack.deleteByPNR(pnr);

            sendJson(exchange,200,"{\"status\":\"success\"}");

            return;

        }

        String query=exchange.getRequestURI().getQuery();

        String pnr=null;

        if(query!=null){

            for(String pair:query.split("&")){

                String[] kv=pair.split("=",2);

                if(kv.length==2 && kv[0].equals("pnr")){

                    pnr=java.net.URLDecoder.decode(kv[1],"UTF-8");

                }

            }

        }

        sendJson(exchange,200,historyStack.toJson(pnr));

    }

}
    

    static class BookHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            withCors(exchange);
            if (isPreflight(exchange)) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }

            String body;
            try (Scanner scanner = new Scanner(exchange.getRequestBody(), StandardCharsets.UTF_8)
                    .useDelimiter("\\A")) {
                body = scanner.hasNext() ? scanner.next() : "";
            }

            Map<String, String> fields = parseFlatJson(body);
            bookingQueue.enqueue(new BookingRequest(fields));

            // Process this request now (FIFO order preserved for anything already queued)
            BookingRequest request = bookingQueue.dequeue();
            Map<String, String> f = request.fields;

            int trainId;
            int passengerCount;
            try {
                trainId = Integer.parseInt(f.getOrDefault("trainId", "-1"));
                passengerCount = Integer.parseInt(f.getOrDefault("passengers", "1"));
                if (passengerCount < 1) passengerCount = 1;
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid train ID or passenger count.\"}");
                return;
            }

            String passengerName = f.getOrDefault("passengerName", "Passenger").trim();
            TrainNode train = trains.findById(trainId);

            BookingRecord record = new BookingRecord();
            record.passengerName = passengerName.isEmpty() ? "Passenger" : passengerName;
            record.from = f.getOrDefault("from", train != null ? train.source : "-");
            record.to = f.getOrDefault("to", train != null ? train.destination : "-");
            record.date = f.getOrDefault("date", "-");
            record.travelClass = f.getOrDefault("travelClass", "Sleeper");

            String status;
            String message;
            String pnr = null;
            String coach = null;
            int seat = 0;
            int fare = 0;
            int seatsLeft = train != null ? train.seats : 0;

            if (train == null) {
                status = "error";
                message = "Booking failed: train not found.";
            } else if (train.seats < passengerCount) {
                status = "error";
                message = "Booking failed: only " + train.seats + " seat(s) left on " + train.name + ".";
            } else {
                train.seats -= passengerCount;
                seatsLeft = train.seats;
                status = "success";
                pnr = "PNR" + (100000000 + random.nextInt(900000000));
                coach = "B" + (1 + random.nextInt(9));
                seat = 1 + random.nextInt(72);
                fare = train.fareFor(record.travelClass) * passengerCount;
                message = "Success! Seat booked for " + record.passengerName + " on " + train.name + ".";
            }

            record.pnr = pnr != null ? pnr : "PNR" + (100000000 + random.nextInt(900000000));
            record.trainName = train != null ? train.name : "Unknown";
            record.trainNo = train != null ? train.trainNo : "-";
            record.coach = coach != null ? coach : "-";
            record.seat = seat;
            record.fare = fare;
            record.status = status;

            historyStack.push(record);

            StringBuilder json = new StringBuilder("{");
            json.append("\"status\":\"").append(status).append("\",");
            json.append("\"message\":\"").append(escape(message)).append("\",");
            json.append("\"seatsLeft\":").append(seatsLeft);
            if ("success".equals(status)) {
                json.append(",\"pnr\":\"").append(record.pnr).append("\"");
                json.append(",\"coach\":\"").append(record.coach).append("\"");
                json.append(",\"seat\":").append(record.seat);
                json.append(",\"fare\":").append(record.fare);
            }
            json.append("}");

            sendJson(exchange, 200, json.toString());
        }
    }

    /* ---------- Helpers ---------- */

    private static void withCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static boolean isPreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /* ---------- Entry Point ---------- */

    public static void main(String[] args) throws IOException {
        seedTrains();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/trains", new TrainsHandler());
        server.createContext("/api/history", new HistoryHandler());
        server.createContext("/api/book", new BookHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("DSA Train Booking backend running at http://localhost:8080");
    }
}