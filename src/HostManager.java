import com.sun.org.apache.xml.internal.utils.Hashtree2Node;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by longlingwang on 4/6/17.
 */
public class HostManager {

    public int checkNewHost (String hostList, String localIP, int localPort) {

        hostList = hostList.replaceAll("\\s+", "");

        // parse the list of hosts
        String[] hosts = hostList.split("\\)");

        for (String each : hosts) {
            String host = each.substring(1, each.length());
            String[] hostInfor = host.split(",");

            String hostName = hostInfor[0];
            String ip = hostInfor[1];
            String port = hostInfor[2];

            try {
                //try to connect to check the correctness of IP and port
                Socket s = new Socket(ip, Integer.parseInt(port));
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                out.writeUTF("confirm " + hostName + " " + localIP + " " + localPort);
                out.flush();
                out.close();
                s.close();
            } catch (IOException e) {
                System.out.println("Unreachable host for " + hostName + " at " + ip + ": " + port);
                return -1;
            }
        }
        return hosts.length;
    }

    public boolean ifDuplicated (ArrayList<Address> list, HashSet<String> nameSet) {
        for (Address each: list) {
            if (nameSet.contains(each.hostName)) {
                return false;
            } else {
                nameSet.add(each.hostName);
            }
        }
        return true;
    }

    public void requireAddressBook (String hostList, String localIP, int localPort) {

        hostList = hostList.replaceAll("\\s+", "");

        // parse the list of hosts
        String[] hosts = hostList.split("\\)");

        MessageSender ms = new MessageSender();

        for (String each : hosts) {
            String host = each.substring(1, each.length());
            String[] hostInfor = host.split(",");

            String ip = hostInfor[1];
            String port = hostInfor[2];
            ms.directSend("require " + localIP + " " + localPort, ip, Integer.parseInt(port));
        }
    }

    public void flushHostNet(ArrayList<Address> addressBook, String filePath, String localName) {
        File netFile = new File(filePath + "nets.txt");
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(netFile));
            out.write(localName);
            for (Address each: addressBook) {
                out.write("\n" + each.hostName + ", " + each.ip + ", " + each.port);
            }
            out.close();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public void deletehosts (String filePath, String localHost, HashSet<String> deletedList, Slot[] slotTable, ArrayList<Address> addressBook) {
        // update address book
        for (Address cur: addressBook) {
            if (deletedList.contains(cur.hostName)) {
                addressBook.remove(cur);
            }
        }

        // update net file
        flushHostNet(addressBook, filePath, localHost);

        // update slot table
        HashMap<String, Integer> map = new HashMap<>();
        for (String each: deletedList) {
            map.put(each, 0);
        }
        for (Slot cur: slotTable) {
            String curHost = cur.hostBelong;
            if (deletedList.contains(curHost)) {
                int replace = map.get(curHost);
                map.put(curHost, (replace + 1) % addressBook.size());
                cur.hostBelong = addressBook.get(replace).hostName;
            }
        }
    }

}