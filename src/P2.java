
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class P2 {
    static ServerSocket serverSocket;
    static String ip;
    static int port;
    static String hostName;
    static String filePath;
    static List <Address> addressBook;
    static ArrayList<Address> oldBackupAddressBook;
    static Boolean lock = false;
    static String aws;
    static String awsName;
    static int needACK;
    static String errorMessage;
    static Slot[] slotTable = new Slot[192];
    static Integer totalSlot = 192;
    static boolean ifReboot;


    public static void main (String[] args) {

        if (args.length != 1) {
            System.out.println("Please execute the program with one word host name such that 'host_1'...");
            return;
        }

        //get the host name from user input
        hostName = args[0];

        // initialize the slot table
        for (int i = 0; i < slotTable.length; i++) {
            slotTable[i] = new Slot(hostName);
        }

        // get the IP and available port
        try {
            ip = InetAddress.getLocalHost().getHostAddress();

            // specify a port of 0 to the ServerSocket constructor and it will listen on any free port
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            System.out.println(ip + " at port number: " + port);

            addressBook = Collections.synchronizedList(new ArrayList<>());

            Thread thread = new Thread(new ListeningThread(serverSocket));
            thread.start();

            // create a scanner so we can read the command-line input
            Scanner scanner = new Scanner(System.in);
            P2 p2 = new P2();

            // check if rebooted
            filePath = "/tmp/lwang3/linda/" + hostName + "/";
            File net = new File(filePath, "nets.txt");

            Broadcast bc = new Broadcast();
            MessageSender ms = new MessageSender();
            HostManager hm = new HostManager();
            Search search = new Search();

            if (net.exists() && bc.rebootCast("reb" + hostName + ":" + port, filePath)) {  // net file exists
                System.out.println("Detecting if current host is re-booting...");

                // broadcast the net file to get the latest slot table and address book
                lock = true;
                while (lock) {
                    try {
                        Thread.currentThread().sleep(500);
                    } catch (InterruptedException e) {
                        System.out.println(e);
                    }
                }

                if (ifReboot) { // current host is rebooted
                    System.out.println("Current host is rebooted!");

                    // broadcast others
                    bc.broadcast("onn" + hostName + ":" + port, addressBook, hostName);

                    // sync for slot table
                    lock = true;
                    bc.rebootCast("re2" + hostName, filePath);
                    while (lock) {
                        try {
                            Thread.currentThread().sleep(500);
                        } catch (InterruptedException e) {
                            System.out.println(e);
                        }
                    }


                    // sync with backup host
                    System.out.println("syncing with backup host...");
                    int backup = (ms.searchIndex(hostName, addressBook) + 1) % addressBook.size();
                    ms.simpleSend("syo" + hostName, addressBook.get(backup).hostName, addressBook);

                    lock = true;
                    while (lock) {
                        try {
                            Thread.currentThread().sleep(500);
                        } catch (InterruptedException e) {
                            System.out.println(e);
                        }
                    }

                    // sync with original host for backup tuples in local host
                    int original = (ms.searchIndex(hostName, addressBook) - 1 + addressBook.size()) % addressBook.size();
                    ms.simpleSend("syb" + hostName, addressBook.get(original).hostName, addressBook);

                    lock = true;
                    while (lock) {
                        try {
                            Thread.currentThread().sleep(500);
                        } catch (InterruptedException e) {
                            System.out.println(e);
                        }
                    }

                    System.out.println("Rebooting is finished!");


                } else { // current host is newly created
                    // empty all files

                    search.emptyFile(filePath + "nets.txt");
                    search.emptyFile(filePath + "tuples/original.txt");
                    hm.initBackup(filePath, hostName);
                }

            } else { // net file DOES NOT exist. Current host is newly created.

                //create the net file and tuple file first
                filePath = "/tmp/lwang3/linda/" + hostName + "/";
                File tuplesO = new File(filePath + "tuples/", "original.txt");
                tuplesO.getParentFile().mkdirs();
                File tuplesB = new File(filePath + "tuples/", "backup.txt");
                File nets = new File(filePath, "nets.txt");

                addressBook.add(new Address(hostName, ip, port));

                // change permission
                try {
                    nets.createNewFile();
                    tuplesO.createNewFile();
                    tuplesB.createNewFile();
                    File f1 = new File("/tmp/lwang3");
                    f1.setExecutable(true, false);
                    f1.setReadable(true, false);
                    f1.setWritable(true, false);
                    File f2 = new File("/tmp/lwang3/linda");
                    f2.setExecutable(true, false);
                    f2.setReadable(true, false);
                    f2.setWritable(true, false);
                    File f3 = new File("/tmp/lwang3/linda/" + hostName);
                    f3.setExecutable(true, false);
                    f3.setReadable(true, false);
                    f3.setWritable(true, false);
                    File f4 = new File("/tmp/lwang3/linda/" + hostName + "/tuples");
                    f4.setExecutable(true, false);
                    f4.setReadable(true, false);
                    f4.setWritable(true, false);
                    File f5 = new File("/tmp/lwang3/linda/" + hostName + "/nets.txt");
                    f5.setExecutable(false, false);
                    f5.setReadable(true, false);
                    f5.setWritable(true, false);
                    File f6 = new File("/tmp/lwang3/linda/" + hostName + "/tuples/original.txt");
                    f6.setExecutable(false, false);
                    f6.setReadable(true, false);
                    f6.setWritable(true, false);
                    File f7 = new File("/tmp/lwang3/linda/" + hostName + "/tuples/backup.txt");
                    f7.setExecutable(false, false);
                    f7.setReadable(true, false);
                    f7.setWritable(true, false);
                    search.emptyFile(filePath + "nets.txt");
                    search.emptyFile(filePath + "tuples/original.txt");
                    hm.initBackup(filePath, hostName);


                } catch (IOException e) {
                    System.out.println(e);
                }
            }


            while (true) {

                //  prompt for the user's command
                System.out.print(hostName + " linda> ");

                // get their input as a String
                String command = scanner.nextLine();
                if (command.equals("")) {
                    continue;
                }
                p2.parseCommand(command);
            }

        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public void parseCommand (String command) {
        DataProcess dp = new DataProcess();
        Search search = new Search();
        MessageSender ms  = new MessageSender();
        Broadcast bc = new Broadcast();
        ReArranger ra = new ReArranger();
        HostManager hm = new HostManager();

        if (command.length() < 5) {
            System.out.println("Invalid command! Try again...");
            return;
        }
        if (command.substring(0, 3).equals("add")) {
            String hostList = command.substring(3, command.length());
            hostList = hostList.replaceAll("\\s*\\(", "(");
            if (hostList.charAt(0) != '(' || hostList.charAt(hostList.length() - 1) != ')') {
                System.out.println("Invalid User Input! Please add \"()\" for each host." +
                        "Example: add (<host name>, <IP>, <Port>) (<host name>, <IP>, <Port>) (...)");
                return;
            }


            // check if the new hosts reachable

            needACK = hm.checkNewHost(hostList,ip,port);


            if (needACK == -1) {
                return;
            }
            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                System.out.println(e);
            }
            if (needACK != 0) {
                System.out.println(errorMessage);
                System.out.println("Try again...");
                return;
            }

            oldBackupAddressBook = new ArrayList<>();

            //backup the old address book
            for(Address a : addressBook) {
                oldBackupAddressBook.add(new Address(a.hostName, a.ip, a.port, a.ifAlive));
            }

            hostList = hostList.replace(" ", "");
            hostList = hostList.substring(1, hostList.length() - 1);
            String[] newHosts = hostList.split("\\)\\(");

            // ask for address books belonging to new added host
            hm.requireAddressBook(newHosts, ip, port);

            needACK = newHosts.length;
            System.out.println("Adding the new host...");

            while (needACK != 0) {
                try {
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }


            // check if any host name has duplicated
            HashSet<String> nameSet = new HashSet<>();
            if (!hm.ifDuplicated(addressBook, nameSet)) {
                System.out.println("Same name is found for different IP! Please re-enter....");

                // recover the address book
                addressBook = oldBackupAddressBook;
                return;
            }


            // save the address book to disk net file
            hm.flushHostNet(addressBook, filePath, hostName);

            StringBuilder sb = new StringBuilder();
            for (Address each: addressBook) {
                sb.append("(" + each.hostName + " " + each.ip + " " + each.port + " " + each.ifAlive + ")");
            }

            // broadcast everyone in the list to update their address book
            bc.broadcast("add" + sb.toString(), addressBook, hostName);


            // backup the old slot table
            Slot[] oldSlotTable = new Slot[totalSlot];

            for (int i = 0; i < slotTable.length; i ++) {
                Slot cur = new Slot(slotTable[i].hostBelong, slotTable[i].tupleSaved);
                oldSlotTable[i] = cur;
            }


            // sync the slot table with the new added hosts
            for (String eachHost: newHosts) {
                String[] infor = eachHost.split(",");
                ms.directSend("stn" + hostName, infor[1], Integer.parseInt(infor[2]));
            }
            needACK = newHosts.length;
            while (needACK != 0) {
                try {
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }

            // sort address book and re-arrange
            addressBook.sort(Address::compareTo);

            new ReArranger().reArrangeAdd(filePath, totalSlot, hostName, oldSlotTable, slotTable, oldBackupAddressBook, addressBook);

            // broadcast to sync slot table
            StringBuilder st;
            st = new StringBuilder("syt");
            for (Slot each: slotTable) {
                st.append("(" + each.hostBelong + " " + each.tupleSaved + ")");
            }
            bc.broadcast(st.toString(), addressBook, hostName);

            System.out.println("Local original tuples have be relocated!");

            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                System.out.println(e);
            }

            // backup local tuples
            String tuples = dp.getOriginal(filePath);

            Address backup = addressBook.get((ms.searchIndex(hostName, addressBook) + 1) % addressBook.size());
            ms.simpleSend("fla" + hostName + "::" + tuples, backup.hostName, addressBook);

            // broadcast for others to backup
            bc.broadcast("baa", addressBook, hostName);

            System.out.println("Local original tuples have be backed up!");

        } else if (command.substring(0, 3).equals("del")) {
            command = command.replace(" ", "");
            if (command.length() < 8) {
                System.out.println("Invalid User Input!");
                return;
            }
            String hostList = command.substring(6, command.length());
            if (hostList.charAt(0) != '(' || hostList.charAt(hostList.length() - 1) != ')') {
                System.out.println("Invalid User Input! Please add \"()\" for host list to delete." +
                        "Example: delete (<host name 1>, <host name 2>, ...)");
                return;
            }

            // check if duplicated remove same host name
            hostList = hostList.substring(1, hostList.length() - 1).replace(" ", "");
            String[] list = hostList.split(",");
            HashSet<String> set = new HashSet<>();
            for (String each: list) {
                if (set.contains(each)) {
                    System.out.println("Duplicate host name appear in the deleting list! Please re-enter...");
                    return;
                } else {
                    set.add(each);
                }
            }

            // check if the hosts exist in list and if self is deleted
            boolean self = false;
            for (String each: list) {
                if (ms.searchIndex(each, addressBook) == -1) {
                    System.out.println("Host name " + each + " is not in net file! Please re-enter...");
                    return;
                }
                if (each.equals(hostName)) {
                    self = true;
                }
            }

            // check if all hosts got deleted
            if (list.length == addressBook.size()) {
                System.out.println("User tries to delete all hosts, which will cause data lost! Please re-enter...");
                return;
            }

            // broadcast the message of deleted host
            bc.broadcast("del " + hostList, addressBook, hostName);

            if (!self) { // current host is not deleted
                hm.deletehosts(filePath, hostName, set, slotTable, addressBook);

            } else { // current host is deleted
                System.out.println("Current host is deleted. Will exit after done with organizing data...");
                ra.reArrangeDelete(filePath, hostName, slotTable, set, addressBook);

                // delete the net file from local
                File file = new File(filePath + "nets.txt");
                file.delete();

                System.exit(0);
            }

        } else if (command.substring(0, 2).equals("rd") || command.substring(0, 2).equals("in") ) {
            String content = command.substring(2, command.length());
            content = content.replaceAll("\\s*\\(", "(");
            if (content.charAt(0) != '(' || content.charAt(content.length() - 1) != ')') {
                System.out.println("Invalid format! (Please add \"()\" outside the data)");
                return;
            }
            content = content.substring(1, content.length() - 1);
            String[] a = new String[] {content};
            boolean[] validity = dp.checkUserInput(a);
            content = a[0];
            if (!validity[0]) {
                System.out.println("Invalid data! Please re-enter...");
                return;
            }
            if (!validity[1]) { // data doesn't have variable
                String[] sa = content.split("\\s*,\\s*");
                StringBuilder sb = new StringBuilder();
                for (String each: sa) {
                    sb.append(each);
                }
                int hostToGet = dp.md5sum(sb.toString(), totalSlot);
                String whoHas = slotTable[hostToGet].hostBelong;
                String res = "";
                System.out.println("Waiting until the data can be gotten...");
                while (res.equals("")) {
                    if (whoHas.equals(hostName)) {
                        res = search.searchInLocal(content, filePath + "tuples/original.txt");
                        if (!res.equals("")) {

                            System.out.println("get tuple (" + content + ") on " + hostName);

                            if (command.substring(0, 2).equals("in")) {  // delete the tuple
                                search.removeTuple("(" + res + ")", filePath + "tuples/original.txt");
                                if (!search.ifSlotEmpty(res.split("->")[0], filePath + "tuples/original.txt")) {
                                    slotTable[hostToGet].tupleSaved = false;
                                    bc.broadcast("sl0" + hostToGet, addressBook, hostName);
                                }
                                // send to backup to remove this tuple as well
                                int backup = (ms.searchIndex(hostName, addressBook) + 1) % addressBook.size();
                                ms.simpleSend("bacrem (" + hostToGet + "->" + res + ")", addressBook.get(backup).hostName, addressBook);
                                System.out.println("Remove the original tuple (" + content + ") after offering tuple for \"in\" command.");
                                return;
                            }
                        }

                    } else { // data is not in local
                        lock = true;
                        while (lock) {
                            try {
                                whoHas = slotTable[hostToGet].hostBelong;
                                ms.send(command.substring(0, 2) + " " + hostName + " " + content, whoHas, addressBook);
                                Thread.currentThread().sleep(500);
                            } catch (InterruptedException e) {
                                System.out.println(e);
                            }
                        }

                        if (command.substring(0, 2).equals("in")) {
                            ms.simpleSend("orirem (" + hostToGet + "->" + content + ")", whoHas, addressBook);
                            // send to backup to remove this tuple as well
                            int backup = (ms.searchIndex(whoHas, addressBook) + 1) % addressBook.size();
                            ms.simpleSend("bacrem (" + hostToGet + "->" + content + ")", addressBook.get(backup).hostName, addressBook);
                        }

                        System.out.println("get tuple (" + content + ") on " + whoHas);
                        return;
                    }
                }

            } else { // data has variable in it
                System.out.println("Waiting until the data can be gotten ...");
                lock = true;
                while (lock) {
                    // search in local
                    String res = search.searchInLocal(content, filePath + "tuples/original.txt");
                    if (!res.equals("")) {
                        String[] infor = res.split("->");
                        System.out.println("get tuple (" + infor[1] + ") on " + hostName + " " + ip);

                        if (command.substring(0, 2).equals("in")) {  // delete the tuple

                            String[] sa = infor[1].split("\\s*,\\s*");
                            StringBuilder sb = new StringBuilder();
                            for (String each: sa) {
                                sb.append(each);
                            }
                            // update the slot table
                            int hostToGet = dp.md5sum(sb.toString(), totalSlot);
                            search.removeTuple("(" + res + ")", filePath + "tuples/original.txt");

                            if (!search.ifSlotEmpty(infor[0], filePath + "tuples/original.txt")) {
                                slotTable[hostToGet].tupleSaved = false;
                                bc.broadcast("sl0" + hostToGet, addressBook, hostName);
                            }

                            // send to backup to remove this tuple as well
                            int backup = (ms.searchIndex(hostName, addressBook) + 1) % addressBook.size();
                            ms.simpleSend("bacrem (" + hostToGet + "->" + res + ")", addressBook.get(backup).hostName, addressBook);

                            System.out.println("Locally remove the tuple (" + res.split("->")[1] + ") after offering tuple for \"in\" command.");
                        }
                        return;
                    }
                    // broadcast to search
                    bc.broadcastForward(command.substring(0, 2) + " " + hostName + " " + content, addressBook, hostName);
                    try {
                        Thread.currentThread().sleep(1000);
                    } catch (InterruptedException e) {
                        System.out.println(e);
                    }
                }

                if (command.substring(0, 2).equals("in")) {
                    ms.simpleSend("orirem (" + aws + ")", awsName, addressBook);
                    // send to backup to remove this tuple as well
                    int backup = (ms.searchIndex(awsName, addressBook) + 1) % addressBook.size();
                    ms.simpleSend("bacrem (" + aws + ")", addressBook.get(backup).hostName, addressBook);
                }
                String curTuple = aws.split("->")[1];
                System.out.println("get tuple (" + curTuple + ") on " + awsName);
            }

        } else if (command.substring(0, 3).equals("out")) {

            String content = command.substring(3, command.length());
            content = content.replaceAll("\\s*\\(", "(");
            if (content.charAt(0) != '(' || content.charAt(content.length() - 1) != ')') {
                System.out.println("Invalid format for \"out\" command! (Please add \"()\" outside the data)");
                return;
            }
            content = content.substring(1, content.length() - 1);
            String[] a = new String[] {content};
            boolean[] res = dp.checkUserInput(a);
            content = a[0];
            if (!res[0] || res[1]) {
                System.out.println("Invalid data! Please re-enter...");
                return;
            }
            String[] sa = content.split("\\s*,\\s*");
            StringBuilder sb = new StringBuilder();
            for (String each: sa) {
                sb.append(each);
            }
            int hostToPut = dp.md5sum(sb.toString(), totalSlot);
            String whoHas = slotTable[hostToPut].hostBelong;
            if (!slotTable[hostToPut].tupleSaved) {
                slotTable[hostToPut].tupleSaved = true;
                bc.broadcast("sl1" + hostToPut, addressBook, hostName);
            }

            if (whoHas.equals(hostName)) {
                System.out.println("put tuple (" + content + ") on " + hostName);
                search.addNewTuple("(" + hostToPut + "->" + content + ")", filePath + "tuples/original.txt");
            } else {
                ms.simpleSend("out" + hostToPut + "->" + content, whoHas, addressBook);
                System.out.println("put tuple (" + content + ") on " + whoHas);
            }

            // send to backup to add this tuple as well
            int backup = (ms.searchIndex(whoHas, addressBook) + 1) % addressBook.size();
            ms.simpleSend("bacsav" + whoHas + "::(" + hostToPut + "->" + content + ")", addressBook.get(backup).hostName, addressBook);

        } else {
            System.out.println("Invalid Command! Re-enter...");
        }
    }

    public void parseMessage (String message) {
        MessageSender ms = new MessageSender();
        Search search = new Search();
        HostManager hm = new HostManager();
        ReArranger ra = new ReArranger();
        DataProcess dp = new DataProcess();
        Broadcast bc = new Broadcast();

        if (message.substring(0, 3).equals("wro")) { // wrong host name added to list
            String[] infor = message.split("\\s+");
            errorMessage = "Wrong host name for " + infor[2] + ": " + infor[3] + ". Should be " + infor[1];

        } else if (message.substring(0, 3).equals("ack")) { // send back confirm message for correct host name
            needACK --;

        } else if (message.substring(0, 3).equals("baa")) { // send tuples for backup
            String tuples = dp.getOriginal(filePath);
            Address backup = addressBook.get((ms.searchIndex(hostName, addressBook) + 1) % addressBook.size());
            ms.simpleSend("fla" + hostName + "::" + tuples, backup.hostName, addressBook);

        } else if (message.substring(0, 3).equals("onn")) { // sender is back alive now
            message = message.substring(3, message.length());
            String[] infor = message.split(":");
            int i = ms.searchIndex(infor[0], addressBook);
            addressBook.get(i).port = Integer.parseInt(infor[1]);
            addressBook.get(i).ifAlive = true;

        } else if (message.substring(0, 3).equals("sl1")) { // update the slot table
            int index = Integer.parseInt(message.substring(3, message.length()));
            slotTable[index].tupleSaved = true;

        } else if (message.substring(0, 3).equals("syo")) { // be required for the backup tuple
            String receiver = message.substring(3, message.length());
            String tuples = dp.getBackup(filePath);
            ms.simpleSend("sro" + tuples, receiver, addressBook);

        } else if (message.substring(0, 3).equals("sro")) { // received tuples from backup host
            String tuples = message.substring(3, message.length());
            search.sync(tuples, filePath + "tuples/original.txt");
            lock = false;
            System.out.println("The original tuples in current host have been synced!");

        } else if (message.substring(0, 3).equals("syb")) { // be required for the original tuple
            String receiver = message.substring(3, message.length());
            String tuples = dp.getOriginal(filePath);
            ms.simpleSend("srb" + hostName + "::" + tuples, receiver, addressBook);

        } else if (message.substring(0, 3).equals("srb")) { // received tuples from original host
            String tuples = message.substring(3, message.length());
            search.flushBackup(tuples, filePath + "tuples/backup.txt");
            lock = false;
            System.out.println("The backup tuples in current host have been synced!");

        } else if (message.substring(0, 3).equals("adb")) { // receive the address book from others, check if contain self
            String content = message.substring(3, message.length());
            String[] hostList = content.split("\\)");

            ifReboot = false;

            for (String each: hostList) {
                each = each.substring(1, each.length());
                String[] infor = each.split("\\s+");
                String curName  = infor[0];
                String curIP = infor[1];
                if (curName.equals(hostName) && curIP.equals(ip)) {
                    ifReboot = true;
                    break;
                }
            }

            if (ifReboot) {

                for (String each : hostList) {
                    each = each.substring(1, each.length());
                    String[] infor = each.split("\\s+");
                    String curName = infor[0];
                    String curIP = infor[1];
                    int curPort = Integer.parseInt(infor[2]);
                    boolean ifAlive = Boolean.parseBoolean(infor[3]);
                    addressBook.add(new Address(curName, curIP, curPort, ifAlive));
                }
                // sort address book
                addressBook.sort(Address::compareTo);

                hm.flushHostNet(addressBook, filePath, hostName);
                System.out.println("Net file has been synced!");

                lock = false;

            } else {
                lock = false;
            }

        } else if (message.substring(0, 3).equals("stb")) {  // sync the slot table after rebooted

            String content = message.substring(3, message.length());
            String[] slots = content.split("\\)");

            for (int i = 0; i < slotTable.length; i ++) {
                String each = slots[i];
                each = each.substring(1, each.length());
                String[] infor = each.split(",");
                String curName  = infor[0];
                boolean ifSaved = Boolean.parseBoolean(infor[1]);
                slotTable[i].hostBelong = curName;
                slotTable[i].tupleSaved = ifSaved;
            }
            lock  = false;

        } else if (message.substring(0, 3).equals("stc")) { // sync the slot table with received ones
            needACK --;

            String content = message.substring(3, message.length());
            String[] slots = content.split("\\)");

            for (int i = 0; i < slotTable.length; i ++) {
                String each = slots[i];
                each = each.substring(1, each.length());
                String[] infor = each.split(",");
                boolean ifSaved = Boolean.parseBoolean(infor[1]);
                slotTable[i].tupleSaved = slotTable[i].tupleSaved || ifSaved;
            }

        } else if (message.substring(0, 3).equals("reb")) { // be required for the latest address book
            // get new port number
            message = message.substring(3, message.length());
            String[] infor = message.split(":");
            int i = ms.searchIndex(infor[0], addressBook);
            addressBook.get(i).port = Integer.parseInt(infor[1]);
            addressBook.get(i).ifAlive = true;

            // send back the address book
            StringBuilder sb = new StringBuilder("adb");
            for (Address each : addressBook) {
                sb.append("(" + each.hostName + " " + each.ip + " " + each.port + " " + each.ifAlive + ")");
            }
            ms.simpleSend(sb.toString(), infor[0], addressBook);

        } else if (message.substring(0, 3).equals("re2") || message.substring(0, 3).equals("stn")) { // be required for updated slot table
            // send back the slot table
            StringBuilder st;
            if (message.substring(0, 3).equals("re2")) {
                st = new StringBuilder("stb");
            } else {
                st = new StringBuilder("stc");
            }
            for (Slot each: slotTable) {
                st.append("(" + each.hostBelong + "," + each.tupleSaved + ")");
            }

            ms.simpleSend(st.toString(), message.substring(3, message.length()), addressBook);

        } else if (message.substring(0, 3).equals("sl0")) { // update the slot table with certain slot
            int index = Integer.parseInt(message.substring(3, message.length()));
            slotTable[index].tupleSaved = false;

        } else if (message.substring(0, 3).equals("del")) { // delete some hosts
            String deleteList = message.substring(4, message.length());
            String[] list = deleteList.split(",");
            HashSet<String> set = new HashSet<>();

            boolean self = false;
            for (String each: list) {
                set.add(each);
                if (each.equals(hostName)) {
                    self = true;
                }
            }

            if (!self) { // current host is not deleted
                hm.deletehosts(filePath, hostName, set, slotTable, addressBook);

            } else { // current host is deleted
                System.out.println("Current host is deleted. Will exit after done with organizing data...");
                ra.reArrangeDelete(filePath, hostName, slotTable, set, addressBook);

                // delete the net file from local
                File file = new File(filePath + "nets.txt");
                file.delete();

                System.exit(0);
            }

        } else if (message.substring(0, 3).equals("req")) { // received a message asking for address book
            String[] content = message.split("\\s+");
            StringBuilder sb = new StringBuilder("res");
            for (Address each: addressBook) {
                sb.append("(" + each.hostName + " " + each.ip + " " + each.port + " " + each.ifAlive + ")");
            }
            ms.directSend(sb.toString(), content[1], Integer.parseInt(content[2]));

        } else if (message.substring(0, 3).equals("con")) { // received confirm message for host name
            String[] infor = message.split("\\s+");
            String nameConfirm = infor[1];
            if (!nameConfirm.equals(hostName)) {
                ms.directSend("wrong " + hostName + " " + ip + " " + port, infor[2], Integer.parseInt(infor[3]));
            } else {
                ms.directSend("ack", infor[2], Integer.parseInt(infor[3]));
            }

        }  else if (message.substring(0, 3).equals("aws")) { // get the tuple I am waiting for
            awsName = message.split("\\s+")[1];
            aws = message.substring(5 + awsName.length(), message.length());
            lock = false;

        } else if (message.substring(0, 3).equals("add") || message.substring(0, 3).equals("res")) { // add some new hosts
            if (message.substring(0, 3).equals("res")) {
                needACK --;
            }
            String content = message.substring(4, message.length() - 1);
            String[] hostList = content.split("\\)\\(");
            ArrayList<String> addingList = new ArrayList<>();

            ArrayList<Address> tmp = new ArrayList<>();
            for (String each: hostList) {
                String[] infor = each.split("\\s+");
                String curName  = infor[0];
                String curIP = infor[1];
                int curPort = Integer.parseInt(infor[2]);
                boolean ifAlive = Boolean.parseBoolean(infor[3]);

                boolean ifNew = true;

                //remove the duplicate
                for (Address address: addressBook) {
                    if (address.hostName.equals(curName) && address.ip.equals(curIP) && address.port == curPort) {
                        ifNew = false;
                        break;
                    }
                }
                if (ifNew) {
                    tmp.add(new Address(curName, curIP, curPort, ifAlive));
                    addingList.add(curName + " " + curIP + " " + curPort);
                }
            }
            addressBook.addAll(tmp);

            hm.flushHostNet(addressBook, filePath, hostName);
            System.out.println("Following hosts have been successfully added!");
            System.out.println(addingList.toString());


            // sort new address book
            addressBook.sort(Address::compareTo);



        } else if (message.substring(0, 3).equals("syt")) { // sync the slot table and relocate tuples
            String content = message.substring(4, message.length() - 1);
            String[] slots = content.split("\\)\\(");

            Slot[] backup = new Slot[totalSlot];

            for (int i = 0; i < slotTable.length; i ++) {
                Slot cur = new Slot(slotTable[i].hostBelong, slotTable[i].tupleSaved);
                backup[i] = cur;
            }

            for (int i = 0; i < slotTable.length; i ++) {
                String each = slots[i];
                String[] infor = each.substring(0, each.length()).split("\\s+");
                boolean ifSaved = Boolean.parseBoolean(infor[1]);
                slotTable[i].hostBelong = infor[0];
                slotTable[i].tupleSaved = ifSaved;
            }

            // re-arrange the tuples
            ra.reArrangeOB(filePath, hostName, backup, slotTable, addressBook);

            System.out.println("Local tuples and backup have be relocated!");
            System.out.print(hostName +  " linda> ");


        } else if (message.substring(0, 3).equals("flu")) { // save the tuple to backup with checking
            String content = message.substring(4, message.length());
            search.flush(content, filePath + "tuples/backup.txt");

        } else if (message.substring(0, 3).equals("fla")) { // save the tuple to backup by overwriting all
            String content = message.substring(3, message.length());
            search.flushBackup(content, filePath + "tuples/backup.txt");

        } else if (message.substring(0, 3).equals("out")) {
            String content = message.substring(3, message.length());
            search.addNewTuple("(" + content + ")", filePath + "tuples/original.txt");
            System.out.println("Save tuple (" + content.split("->")[1] + ") in local.");
            System.out.print(hostName +  " linda> ");

        } else if (message.substring(0, 3).equals("ori") || message.substring(0, 3).equals("bac")) {  // detect if operation on original or backup
            String accessFile;
            String title = message.substring(0, 3);
            if (title.equals("ori")) {
                accessFile = filePath + "tuples/original.txt"; // operation on original file
            } else {
                accessFile = filePath + "tuples/backup.txt"; // operation on backup file
            }
            message = message.substring(3, message.length());

            if (message.substring(0, 2).equals("rd") || message.substring(0, 2).equals("in")) {
                String res = message.split("\\s+")[1];
                String content = message.substring(4 + res.length(), message.length());
                String aws = search.searchInLocal(content, accessFile);
                if (aws.equals("")) {
                    return;
                }
                String reply;
                if (title.equals("ori")) {
                    reply = hostName;
                } else {
                    reply = addressBook.get(Math.abs(ms.searchIndex(hostName, addressBook) + addressBook.size() - 1) % addressBook.size()).hostName;
                }
                ms.simpleSend("aws " + reply + " " + aws, res, addressBook);
                System.out.println("Provide tuple (" + aws.split("->")[1] + ") to " + res);
                System.out.print(hostName +  " linda> ");



            } else if (message.substring(0, 3).equals("rem")) { // delete this tuple
                String tuple = message.substring(4, message.length());
                search.removeTuple(tuple, accessFile);
                String[] infor = tuple.substring(1, tuple.length() - 1).split("->");
                if (!search.ifSlotEmpty(infor[0], accessFile)) {
                    slotTable[Integer.parseInt(infor[0])].tupleSaved = false;
                    bc.broadcast("sl0" + infor[0], addressBook, hostName);
                }
                System.out.println("Remove the tuple " + infor[1] + " after offering tuple for \"in\" command from " + accessFile);
                System.out.print(hostName +  " linda> ");

            } else if (message.substring(0, 3).equals("sav")) { // save the tuple to backup / original file
                String content = message.substring(3, message.length());
                if (title.equals("ori")) {
                    search.addNewTuple(content, accessFile);
                } else {
                    search.flush(content, accessFile);
                }

            } else {
                System.out.println("Received a unpredicted message: " + message);
            }
        } else {
            System.out.println("Received a unpredicted message: " + message);
        }
    }
}