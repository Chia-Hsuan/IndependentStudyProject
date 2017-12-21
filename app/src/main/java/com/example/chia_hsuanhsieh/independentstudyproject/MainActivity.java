package com.example.chia_hsuanhsieh.independentstudyproject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.content.Context;
import android.content.DialogInterface;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.String;
import java.util.ArrayList;
import java.net.Socket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class MainActivity extends Activity {

    private HashTable dirTable;
    private String topPath;
    private String dirPath;
    private String currentPath;
    private String host_ip;
    private String server_ip;
    private String fileName = "Let'Share";
    private WifiManager wifi;
    private DatagramSocket datagramSocket;
    private static final int PORT = 11235;
    private volatile boolean isConnectingToClient;
    private volatile boolean isConnectingToServer;
    private volatile boolean serverSocketIsStopped = false;
    private volatile boolean stopCounting = false;
    private TextView textCurrentPath;
    private ListView listDir;
    private FileAdapter adapter;
    private ProgressDialog progressDialog;
    private volatile int progressBarStatus = 0;
    private volatile long sentSize = 0;
    private Handler progressBarHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildDirectory();
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        host_ip = Formatter.formatIpAddress(wifi.getDhcpInfo().ipAddress);
        try {
            datagramSocket = new DatagramSocket(PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        jumpToActivityMain();
    }

    public void goThroughDirectory(String path) {
        textCurrentPath.setText(path);

        ArrayList<String> dataList = new ArrayList<String>();
        adapter = new FileAdapter(this, R.layout.simple_row, dataList);
        adapter.scanFiles(topPath,path);
        listDir.setAdapter(adapter);
    }

    public void copyFile(String src, String dest) {
        InputStream fis = null;
        OutputStream fos = null;
        try{
            fos = new FileOutputStream(dest);
            fis = new FileInputStream(src);
            byte[] b = new byte[1024];
            int off = 0;
            int len = 0;
            while((len = fis.read(b)) != -1){
                fos.write(b,off,len);
            }
            fos.flush();
            if (fis != null) fis.close();
            if (fos != null) fos.close();
        } catch(IOException ioe){
            System.out.println("There is IOException when copyFile");
        }
    }

    public void buildDirectory() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            topPath = Environment.getExternalStorageDirectory().getPath();
            dirPath = topPath + File.separator + fileName;
            currentPath = topPath;
            dirTable = new HashTable(topPath, dirPath);
            File dirFile = new File(dirPath);
            if (!dirFile.exists()) {
                dirFile.mkdir();
            } else {
                updateHashTable(dirPath,dirTable);
            }
        }
    }

    public void updateHashTable(String path, HashTable table) {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (dir.isHidden()) return false;
                File file = new File(dir.getPath() + File.separator + name);
                if (!file.isHidden()) {
                    return true;
                } else {
                    return false;
                }
            }
        };

        File[] files = new File(path).listFiles(filter);
        for (File file : files) {
            table.addFile(file);
            if (file.isDirectory()) {
                String inPath = path + File.separator + file.getName();
                HashTable subHashTable = new HashTable(topPath, inPath);
                updateHashTable(inPath, subHashTable);
                table.addSubHashTable(subHashTable, file);
            }
        }
    }

    public void jumpToServerSendLayout() {
        setContentView(R.layout.server_send_page);
        TextView server_sending = (TextView) findViewById(R.id.sending);
        Button server_stop = (Button) findViewById(R.id.stop);
        Button server_return_to_main = (Button) findViewById(R.id.main);
        isConnectingToClient = false;

        server_stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnectingToClient == true) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("You have cancelled the transmission.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {}
                            })
                            .show();
                }
            }
        });

        server_return_to_main.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnectingToClient == false) {
                    jumpToActivityMain();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("While file transfer is in progress, you may not return to the home page.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {}
                            })
                            .show();
                }
            }
        });
        Thread server_thread = new Thread(new ServerThread());
        server_thread.start();
    }

    public void jumpToClientReceiveLayout() {
        setContentView(R.layout.client_receive_page);
        TextView client_connecting = (TextView) findViewById(R.id.connecting);
        Button client_stop = (Button) findViewById(R.id.stop);
        Button client_return_to_main = (Button) findViewById(R.id.main);
        isConnectingToServer = false;

        client_stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnectingToServer == true) {
                    isConnectingToServer = false;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("You have cancelled the transmission.")
                            .setMessage("Some of the files have already been transfered.  Do you want to save them?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getApplicationContext(), "You have received some of the the files.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    File[] files = new File(dirPath).listFiles();
                                    for (File file : files) {
                                        file.delete();
                                        dirTable.remove(file);
                                        //adapter.remove();
                                        //adapter.notifyDataSetChanged();
                                    }
                                    Toast.makeText(getApplicationContext(), "You haven't received anything from the server.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                }
            }
        });

        client_return_to_main.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnectingToServer == false) {
                    jumpToActivityMain();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("While file transfer is in progress, you may not return to the home page.")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {}
                            })
                            .show();
                }
            }
        });
        new ClientAsyncTask().execute();
    }

    public class BroadcastSendThread implements Runnable {
        @Override
        public void run() {
            try {
                datagramSocket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(host_ip.getBytes(), host_ip.length(), InetAddress.getByName(getBroadcastAddress()), PORT);
                datagramSocket.send(packet);
            } catch (SocketException e) {
                System.out.println("There is a SocketException when broadcasting");
            } catch (IOException e) {
                System.out.println("There is a IOException when broadcasting");
            }
        }
    }

    public class BroadcastReceiveThread implements Runnable {
        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket dg_packet = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(dg_packet);
                server_ip = new String(dg_packet.getData()).trim();
            } catch (SocketException e) {
                System.out.println("There is a SocketException when receiving");
            } catch (IOException e) {
                System.out.println("There is a IOException when receiving");
            }
        }
    }

    public String getBroadcastAddress() {
        String broadcast_ip = "";
        int[] host_num = new int[4];
        int[] subnet_num = new int[4];
        int last = 0, order = 0;
        String subnet_mask = Formatter.formatIpAddress(wifi.getDhcpInfo().netmask);
        for (int i=0; i<host_ip.length(); i++) {
            if (host_ip.charAt(i) == '.') {
                host_num[order] = Integer.valueOf(host_ip.substring(last, i));
                order++;
                last = i + 1;
            } else if (i == host_ip.length()-1) {
                host_num[order] = Integer.valueOf(host_ip.substring(last));
            }
        }
        order = 0;
        last = 0;
        for (int i=0; i<subnet_mask.length(); i++) {
            if (subnet_mask.charAt(i) == '.') {
                subnet_num[order] = Integer.valueOf(subnet_mask.substring(last, i));
                order++;
                last = i + 1;
            } else if (i == subnet_mask.length()-1) {
                subnet_num[order] = Integer.valueOf(subnet_mask.substring(last));
            }
        }
        for (int i=0; i<4; i++) {
            int broadcast_num = ((host_num[i]/(256-subnet_num[i]))+1) * (256-subnet_num[i]) - 1;
            if (i == 0) {
                broadcast_ip = String.valueOf(broadcast_num);
            } else {
                broadcast_ip = broadcast_ip + "." + broadcast_num;
            }
        }
        return  broadcast_ip;
    }

    public class countTimeThread implements Runnable {
        ServerSocket serverSocket;

        public countTimeThread(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            int count = 0;
            while (count <= 1800 && !stopCounting) {
                count++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!stopCounting) {
                serverSocketIsStopped = true;
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class ServerThread implements Runnable {
        ServerSocket server_socket;
        Socket socket;
        int clientNum = 0;

        @Override
        public void run() {
            try {
                server_socket = new ServerSocket(PORT);

                while (!serverSocketIsStopped) {
                    stopCounting = false;
                    if (clientNum == 0) {
                        Thread count_time_thread = new Thread(new countTimeThread(server_socket));
                        count_time_thread.start();
                    }
                    try {
                        socket = server_socket.accept();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    clientNum++;
                    stopCounting = true;
                    isConnectingToClient = true;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new FileSendAsyncTask(server_socket, socket, clientNum-1).execute();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class FileSendAsyncTask extends AsyncTask<String, Integer, Integer> {
        ServerSocket serverSocket;
        Socket socket;
        File file;
        int clientIndex;
        long totalSize;

        public FileSendAsyncTask(ServerSocket serverSocket, Socket socket, int clientIndex) {
            this.serverSocket = serverSocket;
            this.socket = socket;
            this.clientIndex = clientIndex;
        }

        public void sendFile(HashTable hashTable) {
            for (int i=0; i<hashTable.size() && isConnectingToClient == true; i++) {
                for (int j=0; j<hashTable.get(i).size() && isConnectingToClient == true; j++) {
                    if (hashTable.get(i).get(j).isDirectory()) {
                        sendFile(hashTable.get(i).get(j).getHashTable());
                    } else {
                        try {
                            file = new File(hashTable.get(i).get(j).getPath());
                            byte[] bytes = new byte[(int) file.length()];
                            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                            bis.read(bytes, 0, bytes.length);
                            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                            oos.writeObject(bytes);
                            oos.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        sentSize = sentSize + hashTable.get(i).get(j).getSize();
                        publishProgress((int) (100 * (double)(sentSize) / (double)(totalSize)));
                    }
                    if (hashTable.getTablePath().equals(dirTable)) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Send the file: " + file.getName(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("File Sending ...");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setIndeterminate(false);
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(String... param) {
            sendHashTable(dirTable, socket);
            HashTable sendingHashTable = receiveHashTable(socket);
            totalSize = sendingHashTable.getTotalSize();
            sendFile(sendingHashTable);
            publishProgress(100);
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            progressDialog.setProgress(values[0]);
            if (totalSize == 0) {
                progressDialog.setMessage("");
                progressDialog.setProgress(100);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            try {
                Thread.sleep(1000);
                socket.close();
                if (isConnectingToClient == true) {
                    isConnectingToClient = false;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "All the files have been sent to: " + socket.getInetAddress(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            progressDialog.dismiss();
            Thread count_time_thread = new Thread(new countTimeThread(serverSocket));
            count_time_thread.start();
        }

    }

    public class ClientAsyncTask extends AsyncTask<String, Integer, Integer> {
        Socket socket;
        File file;
        long totalSize;

        public void receiveFile(HashTable hashTable) {
            for (int i=0; i<hashTable.size() && isConnectingToServer == true; i++) {
                for (int j=0; j<hashTable.get(i).size() && isConnectingToServer == true; j++) {
                    String str = hashTable.get(i).get(j).getPath();
                    file = new File(Environment.getExternalStorageDirectory() + File.separator + str.substring(str.indexOf(fileName)));
                    if (hashTable.get(i).get(j).isDirectory()) {
                        file.mkdir();
                        receiveFile(hashTable.get(i).get(j).getHashTable());
                    } else {
                        try {
                            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                            FileOutputStream fos = new FileOutputStream(file);
                            byte[] bytes = (byte[]) ois.readObject();
                            fos.write(bytes);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        sentSize = sentSize + hashTable.get(i).get(j).getSize();
                        publishProgress((int) (100 * (double)(sentSize) / (double)(totalSize)));
                    }
                    if ((Environment.getExternalStorageDirectory() + File.separator + hashTable.getTablePath().substring(str.indexOf(fileName))).equals(dirPath)) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, file.getName() + " has been received.", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("File Recieving ...");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setIndeterminate(false);
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(String... param) {
            try {
                socket = new Socket(server_ip, PORT);
                isConnectingToServer = true;
                HashTable serverHashTable = receiveHashTable(socket);
                dirTable.compare(serverHashTable);
                sendHashTable(dirTable.getScarceHashTable(), socket);
                totalSize = dirTable.getScarceHashTable().getTotalSize();
            } catch (IOException e) {
                e.printStackTrace();
            }
            receiveFile(dirTable.getScarceHashTable());
            publishProgress(100);
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            progressDialog.setProgress(values[0]);
            if (totalSize == 0) {
                progressDialog.setMessage("");
                progressDialog.setProgress(100);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            try {
                Thread.sleep(1000);
                socket.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            progressDialog.dismiss();
        }

    }

    public void sendHashTable(HashTable hashTable, Socket socket) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(hashTable);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public HashTable receiveHashTable(Socket socket) {
        HashTable receiveTable = null;
        try {
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            receiveTable = (HashTable) ois.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return receiveTable;
    }

    public void jumpToActivityMain() {
        setContentView(R.layout.activity_main);
        ImageView imgLogo = (ImageView) findViewById(R.id.imgLogo);
        Button btnSend = (Button) findViewById(R.id.btnSend);
        Button btnReceive = (Button) findViewById(R.id.btnReceive);
        Button btnAdd = (Button) findViewById(R.id.btnAdd);
        Button btnHash = (Button) findViewById(R.id.btnHash);

        btnSend.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread send_thread = new Thread(new BroadcastSendThread());
                send_thread.start();
                jumpToServerSendLayout();
            }
        });

        btnReceive.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread reveive_thread = new Thread(new BroadcastReceiveThread());
                reveive_thread.start();
                jumpToClientReceiveLayout();
            }
        });

        btnAdd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpDirectoryContent();
            }
        });

        btnHash.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {}
        });
    }

    public void jumpDirectoryContent() {
        setContentView(R.layout.directory_content);
        textCurrentPath = (TextView) findViewById(R.id.textCurrentPath);
        Button btnReturn = (Button) findViewById(R.id.btnReturn);
        listDir = (ListView) findViewById(R.id.listDir);

        goThroughDirectory(topPath);

        listDir.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String itemValue = (String) listDir.getItemAtPosition(position);

                if (!itemValue.contains(".")) {
                    if (itemValue.equals("Return   ")) {
                        int last = currentPath.lastIndexOf(File.separator);
                        currentPath = currentPath.substring(0, last);
                        goThroughDirectory(currentPath);
                    } else {
                        goThroughDirectory(currentPath + File.separator + itemValue);
                        currentPath = currentPath + File.separator + itemValue;
                    }
                } else {
                    String place;
                    if (position == 1) {
                        place = "first ";
                    } else if (position == 2) {
                        place = "second ";
                    } else if (position == 3) {
                        place = "third ";
                    } else {
                        place = position + "th ";
                    }
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Do you wish to copy the file?")
                            .setMessage("Do you want to copy the " + place + itemValue + "?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                String source = currentPath + File.separator + itemValue;
                                String destination = dirPath + File.separator + itemValue;
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    copyFile(source,destination);
                                    Toast.makeText(getApplicationContext(), "File successfully copied!", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {}
                            })
                            .show();
                }
            }
        });

        listDir.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final String itemValue = (String) listDir.getItemAtPosition(position);

                if (itemValue.equals("Return   ")) {
                    int last = currentPath.lastIndexOf(File.separator);
                    currentPath = currentPath.substring(0, last);
                    goThroughDirectory(currentPath);
                } else {
                    String place;
                    if (position == 1) {
                        place = "first ";
                    } else if (position == 2) {
                        place = "second ";
                    } else if (position == 3) {
                        place = "third ";
                    } else {
                        place = position + "th ";
                    }
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Do you wish to delele the file?")
                            .setMessage("Do you want to delete the " + place + itemValue + "?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (itemValue.contains(".")) {
                                        File deleted = new File(currentPath + File.separator + itemValue);
                                        deleted.delete();
                                        dirTable.removeFile(deleted);
                                        adapter.remove(itemValue);
                                        adapter.notifyDataSetChanged();
                                        Toast.makeText(getApplicationContext(), "File successfully deleted!", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {}
                            })
                            .show();
                } return false;
            }
        });

        btnReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                jumpToActivityMain();
            }
        });
    }

}