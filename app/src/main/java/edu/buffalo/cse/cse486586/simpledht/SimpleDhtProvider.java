package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.Buffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class SimpleDhtProvider extends ContentProvider {

    //********************************** begin
    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String[] REMOTE_PORTS = new String[]{"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    static final int TIMEOUT = 1600;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final String DB_NAME = "my";
    //private static final String DB_TABLE = "keyvalue";
    private static final int DB_VERSION = 1;
    private static final String REGEX = ",,,";

    private static String selfPort;
    private static String selfHash;
//    private static String predecessor;
//    private static String successor;
    private static ArrayList<String> allNodes = new ArrayList<String>();
    HashMap<String, Socket> allSockets = new HashMap<String, Socket>();


    private SQLiteDatabase db;
    private MySQLiteOpenHelper mySQLiteOpenHelper;


    private static class MySQLiteOpenHelper extends SQLiteOpenHelper {
        public MySQLiteOpenHelper(Context context, String name,
                                  SQLiteDatabase.CursorFactory factory, int version){
            super(context, name, factory, version);
        }

        private static final String DB_CREATE =
                "create table " + DB_NAME + " (key primary key, value);" ;

        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(DB_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
            db.execSQL("drop table if exists " + DB_NAME);
            onCreate(db);
        }
    }
    ///******************************** end

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (selection.equals("@")){
            return localDelete("*All*");
        } else if (selection.equals("*")) {
            for (int i = 0; i < allNodes.size(); i++) {

                try {
                    Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.valueOf(allNodes.get(i)));
                    String strSend = "DELETE" + REGEX + "*ALL*";
                    OutputStreamWriter myWrite = new OutputStreamWriter(socket0.getOutputStream());
                    myWrite.write(strSend + "\n");
                    myWrite.flush();
                    socket0.close();

                } catch (IOException e) {
                    logPrint("[delete2]" + e.getMessage());
                }
            }
        } else {
            String responsibleNode = findResponsibleNode(selection);
            if(responsibleNode.equals(selfPort)){
                localDelete(selection);
            }else{
                try{
                    Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.valueOf(responsibleNode));
                    String strSend = "DELETE"+REGEX+selection;
                    OutputStreamWriter myWrite = new OutputStreamWriter(socket0.getOutputStream());
                    myWrite.write(strSend+"\n");
                    myWrite.flush();
                    socket0.close();
                }catch(Exception e){
                    logPrint("[delete] "+ e.getMessage());
                }
            }
        }
        return 0;
    }

    private int localDelete(String selection){
        if (selection.equals("*ALL*")) {
            db = mySQLiteOpenHelper.getWritableDatabase();
            return db.delete(DB_NAME, null, null);  //delete all

        }else{
            db = mySQLiteOpenHelper.getWritableDatabase();
            return db.delete(DB_NAME, "key='"+selection + "'", null);
        }


    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        logPrint("[insert]Inserting1....I got the values: "+ values.getAsString(KEY_FIELD) + " "+ values.getAsString(VALUE_FIELD));
        String responsibleNode = findResponsibleNode(values.getAsString(KEY_FIELD));
        String strSend = values.getAsString(KEY_FIELD) + REGEX + values.getAsString(VALUE_FIELD);

        logPrint("[insert] now the responsibleNode is "+responsibleNode);
        logPrint("[insert]Inserting....Found the responsibleNode: "+responsibleNode);


        try{
            Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.valueOf(responsibleNode));
            OutputStream outputStream = socket0.getOutputStream();
            strSend = "INSERT" + REGEX + strSend;
            outputStream.write((strSend).getBytes());
            outputStream.flush();
            outputStream.close();
//            logPrint("[insert] Finish send and wait for OK.....");

//            BufferedReader br = new BufferedReader(new InputStreamReader(socket0.getInputStream()));
//            if(br.readLine().equals("OK")){
//                logPrint("[insert] Got OK back");
//            }
            socket0.close();
//            logPrint("Finish Inserting!");
        }catch (Exception e){
            logPrint(e.getMessage());
        }

        return null;
    }

    private void localInsert(ContentValues values){
        db = mySQLiteOpenHelper.getWritableDatabase();
        try{
            db.insertWithOnConflict(DB_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e){
            Log.e(TAG, "Error in qb local_inserting");
        }
        Log.v("localInsert", values.toString());
    }

    @Override
    public boolean onCreate(){
        //create 数据库
        Context context = getContext();
        mySQLiteOpenHelper = new MySQLiteOpenHelper(context, DB_NAME, null, DB_VERSION);
//        db = mySQLiteOpenHelper.getWritableDatabase();

        //Calculate the port number that this AVD listens on.
        TelephonyManager tel = (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        selfPort = String.valueOf((Integer.parseInt(portStr) * 2));
        logPrint(selfPort);


        try{
            selfHash = genHash( String.valueOf(Integer.valueOf(selfPort)/2));
//            selfHash = genHash(selfPort);
            logPrint(selfHash);
        }catch (NoSuchAlgorithmException e){
            logPrint(e.getMessage());
        }

        // new a ServerTask()
        try{
            logPrint("Begin to get into serversocket");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            new SendJoinTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        }catch (IOException e){
            Log.e(TAG, "Can't create a ServerSocket");
        }

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        if(selection.equals("@")){
            return localQuery(null);
        }else if(selection.equals("*")){
            MatrixCursor myCursor = new MatrixCursor(new String[]{KEY_FIELD, VALUE_FIELD});
            for (int i = 0; i < allNodes.size(); i++) {

                try {
                    Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.valueOf(allNodes.get(i)));
                    String strSend = "QUERY"+REGEX+"*ALL*";
                    OutputStreamWriter myWrite = new OutputStreamWriter(socket0.getOutputStream());
                    myWrite.write(strSend+"\n");
                    myWrite.flush();


                    BufferedReader br = new BufferedReader(new InputStreamReader(socket0.getInputStream()));
                    String[] queryResponses = br.readLine().split(REGEX);
                    for (int j = 1; j < queryResponses.length; j+=2) {
                        myCursor.addRow(new String[]{queryResponses[j], queryResponses[j+1]});
                    }
                    socket0.close();

                } catch (IOException e){
                    logPrint("[query2]"+e.getMessage());
                }
            }
            return myCursor;

        }else{
            MatrixCursor myCursor = new MatrixCursor(new String[]{KEY_FIELD, VALUE_FIELD});
            String responsibleNode = findResponsibleNode(selection);

            logPrint("[query] selection is "+selection+ " and responsibleNode is "+responsibleNode);
            Socket socket0;
            String strSend = "";
            try {
                socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.valueOf(responsibleNode));
                logPrint("[query] start a socket and connected!");
                strSend = "QUERY" + REGEX + selection;
                OutputStreamWriter myWrite = new OutputStreamWriter(socket0.getOutputStream());
                myWrite.write(strSend+"\n");
                myWrite.flush();
//                myWrite.close();

                logPrint("[query] Finish sending query command: "+ strSend + " , and wait feedback.....");
                BufferedReader br = new BufferedReader(new InputStreamReader(socket0.getInputStream()));
                String tmp = br.readLine();
                br.close();
                logPrint("[query] I got feedback: "+tmp);
                String[] queryResponses = tmp.split(REGEX);
                for (int j = 1; j < queryResponses.length; j+=2) {
                    myCursor.addRow(new String[]{queryResponses[j], queryResponses[j+1]});
                }

                socket0.close();

            } catch (IOException e){
                logPrint("[query2]"+e.getMessage()+",,,"+e.getStackTrace());
            } catch (Exception e1){
                logPrint("[query3]"+ e1.getMessage()+",,,"+ e1.getStackTrace());
            }
            return myCursor;

        }
    }

    public Cursor localQuery(String selection) {
        ///**************** begin

        db = mySQLiteOpenHelper.getWritableDatabase();
        String sql;
        if(selection == null){
            sql = "select * from " + DB_NAME;
        }else{
            sql = "select * from " + DB_NAME + " where " + "key=\"" + selection +"\"";
        }
        Cursor cursor = db.rawQuery(sql, null);

        ///**************** end

        if(selection != null){
            logPrint("query: "+selection);
        }else{
            logPrint("[localQuery] query everything");
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    private void logPrint(String mm){
        Log.e(TAG, mm);
    }

    private ArrayList<String> sortByHashValue(ArrayList<String> inputList){
        // Sorting
        Collections.sort(inputList, new Comparator<String>() {
            @Override
            public int compare(String lhsOriginal, String rhsOriginal)
            {
                String lhs = String.valueOf(Integer.valueOf(lhsOriginal)/2);
                String rhs = String.valueOf(Integer.valueOf(rhsOriginal)/2);
                int flag;
                try{
                    flag = genHash(lhs).compareTo(genHash(rhs));
                    return flag;
                }catch (Exception e){
                    logPrint(e.getMessage());
                }
                return -1;
            }
        });
        return inputList;
    }

    private String findResponsibleNode(String keyStr){

        String keyStrHash;
        try {
            keyStrHash = genHash(keyStr);
        }catch (NoSuchAlgorithmException e){
            logPrint(e.getMessage());
            return null;
        }

        for (int i = 0; i < allNodes.size(); i++) {
            String currentNodeHash;
            try{
                currentNodeHash = genHash( String.valueOf(Integer.valueOf(allNodes.get(i))/2));
            }catch (NoSuchAlgorithmException e){
                logPrint(e.getMessage());
                return null;
            }
            if(keyStrHash.compareTo(currentNodeHash) <= 0){
                return allNodes.get(i);
            }
        }
        return allNodes.get(0);

    }

    /*
    private Socket startConnection(String port){
        while (true){
            logPrint("[startConnection] ...");
            try{
                Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.valueOf(port));
                return socket0;
            }catch (Exception e){
                logPrint("[onCreate] Got an exception when tring to connect to 11108: "+e.getMessage());
                try {
                    Thread.sleep(50);
                }catch (InterruptedException e1){
                    logPrint(e1.getMessage());
                }
            }
        }
    }*/
    public class SendJoinTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            allNodes.add(selfPort);
            if(!selfPort.equals("11108")){
                while (true){
                    logPrint("[onCreate] try to connect to 11108");
                    try {
                        Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.valueOf("11108"));

                        String strToSend = "JOIN" + REGEX + String.valueOf(selfPort);
                        OutputStream outputStream = socket0.getOutputStream();
                        outputStream.write((strToSend).getBytes());
                        outputStream.flush();
                        outputStream.close();
                        socket0.close();

                    } catch (Exception e){
                        logPrint("[onCreate] Got an exception when tring to connect to 11108: "+e.getMessage());
                        try {
                            Thread.sleep(200);
                        }catch (InterruptedException e1){
                            logPrint(e1.getMessage());
                        }
                    }
                }
            }
            return null;
        }
    }



    public class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            /**************************************
            if(selfPort.equals("11108")){
                allNodes.add("11108");
            } else {
                while (true){
                    logPrint("[onCreate] try to connect to 11108");
                    try {
                        Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.valueOf("11108"));

                        String strToSend = "JOIN" + REGEX + String.valueOf(selfPort);
                        OutputStream outputStream = socket0.getOutputStream();
                        outputStream.write((strToSend).getBytes());
                        outputStream.flush();
                        outputStream.close();
                        socket0.close();
                        break;

                    } catch (Exception e){
                        logPrint("[onCreate] Got an exception when tring to connect to 11108: "+e.getMessage());
                        try {
                            Thread.sleep(200);
                        }catch (InterruptedException e1){
                            logPrint(e1.getMessage());
                        }
                    }
                }
            }
            ///end 连接11108
             ***********************************/


            ServerSocket serverSocket = sockets[0];
//            ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
            ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
            while (true) {
                try {
                    Socket socket_sv = serverSocket.accept();
                    logPrint("I accpted a new socket");
//                    cachedThreadPool.execute(new AsServer(socket_sv));
                    singleThreadExecutor.execute(new AsServer(socket_sv));
//                    BufferedReader br = new BufferedReader(new InputStreamReader(socket_sv.getInputStream()));
//                    String msg = br.readLine();


                } catch (Exception e) {
                    Log.e(TAG, "error bufferedReader" + e.getMessage());
                }
            }
//            return null;
        }

        class AsServer implements Runnable{
            private Socket socket_accepted;

            AsServer(Socket sckt){
                this.socket_accepted = sckt;
            }

            public void run(){
//                Scanner scanner;
                String msg;
                try{
                    logPrint("[AsServer::run]");
//                    scanner = new Scanner(socket_accepted.getInputStream());
//                    while (!scanner.hasNext()){logPrint("scanner");}
//                    msg = scanner.nextLine();
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket_accepted.getInputStream()));
                    msg = br.readLine();

                    logPrint("And I got a msg: "+msg);
                    dealwithCommand(socket_accepted, msg);
                }catch (Exception e){
                    logPrint(e.getMessage());
                }
            }
            private void dealwithCommand(Socket socket, String cmd){
                String[] tokens = cmd.split(REGEX,2);

                if(tokens[0].equals("JOIN")){
                    onJOIN(tokens[1]);
                }else if(tokens[0].equals("INSERT")){
                    onINSERT(tokens[1]);
                }else if(tokens[0].equals("QUERY")) {
                    onQUERY(socket, tokens[1]);
                }else if(tokens[0].equals("JOINUPDATE")){
                    onJOINUPDATE(tokens[1]);
                }else if(tokens[0].equals("DELETE")){
                    onDELETE(tokens[1]);
                }
                else{
                    logPrint("[dealwithCommand] This commander is not supported!");
                }
            }

            private void onJOIN(String content){
                //only avd 5554 (11108) will call this


                //构造发送的字符串包含所有的11108已知的机器。
                allNodes.add(content);
                allNodes = sortByHashValue(allNodes);

                String strToSend = "";
                for (int i = 0; i < allNodes.size(); i++) {
                    strToSend += allNodes.get(i) + REGEX;
                }

                //multicast joinupdate
                for(String eachNode:allNodes){
                    if (eachNode.equals("11108"))
                        continue;
                    try{
                        Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.valueOf(eachNode));

                        String sts = "JOINUPDATE" + REGEX + strToSend;
                        OutputStream outputStream = socket0.getOutputStream();
                        outputStream.write((sts).getBytes());
                        outputStream.flush();
                        outputStream.close();

                        socket0.close();

                    }catch (Exception e){
                        logPrint(e.getMessage());
                    }
                }
            }

            private void onJOINUPDATE(String content){
                //all avds will call this except 11108
                //1, 加入节点，2， 转移数据
                String[] tokens = content.split(REGEX);
                if (allNodes.isEmpty()){
                    logPrint("[onJOINUPDATE]allNodes is empty");
                    Collections.addAll(allNodes, tokens);
                }else{
                    logPrint("[onJOINUPDATE]allNodes is not empty");
                    //不是空的情况下，要看看插入的是不是自己的上游。如果是，需要发送和他分享数据。
                    ArrayList<String> newAllNodes = new ArrayList<String>();
                    Collections.addAll(newAllNodes, tokens);

                    String newPredecessor = isThereNewPredecessor(allNodes, newAllNodes);
                    if(newPredecessor != null){
                        //准备发送自己的一部分数据给新节点，然后删除本库里的
                        Cursor myCursor = localQuery(null); //查询全部

                        String strSend = "";
                        myCursor.moveToFirst();
                        while (!myCursor.isAfterLast()){
                            //构造发送字符串
                            strSend += myCursor.getString(0) + REGEX + myCursor.getString(1) + REGEX;
                            //删除将要发送的条目
                            localDelete(myCursor.getString(0));
                            myCursor.moveToNext();
                        }

                        if(!strSend.isEmpty()){//如果结果不是空，就发送
                            try{
                                Socket socket0 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.valueOf(newPredecessor));
                                OutputStream outputStream = socket0.getOutputStream();
                                strSend = "INSERT" + REGEX + strSend;
                                outputStream.write((strSend).getBytes());
                                outputStream.flush();
                                outputStream.close();

                                socket0.close();
                            }catch (Exception e){
                                logPrint(e.getMessage());
                            }
                        }
                    }
                    allNodes = newAllNodes;
                }
            }

            private void onINSERT(String content){
                logPrint("[Server:onINSERT] try to insert content: " + content);
                String[] tokens = content.split(REGEX);
                //构造 contentValues 并插入。
                for (int i = 0; i < tokens.length; i += 2) {
                    ContentValues t = new ContentValues();
                    t.put(KEY_FIELD, tokens[i]);
                    t.put(VALUE_FIELD, tokens[i+1]);
                    localInsert(t);
                }
//
//                try{
//                    OutputStream outputStream = socket.getOutputStream();
//                    String strSend = "OK";
//                    outputStream.write((strSend).getBytes());
//                    outputStream.flush();
//                    outputStream.close();
//                    socket.close();
//                }catch (Exception e){
//                    logPrint(e.getMessage());
//                }
            }

            private void onQUERY(Socket socket, String content){
                //接收 <哪里发来的 query>，<query 什么>，→ 然后socket发回去。（找不到的话，发 null）
                logPrint("Now we are in onQUERY");
                String[] tokens = content.split(REGEX);
                String queryKey = tokens[0];
                Cursor retCursor;
                if (queryKey.equals("*ALL*")){
                    retCursor = localQuery(null);
                }else{
                    retCursor = localQuery(queryKey);
                }

                //构造发送字符串
                String strSend = "";
                retCursor.moveToFirst();
                while (!retCursor.isAfterLast()){
                    strSend += retCursor.getString(0) + REGEX + retCursor.getString(1) + REGEX;
                    retCursor.moveToNext();
                }
                //整理 retCursor 并发送
                try{
                    OutputStreamWriter myWrite = new OutputStreamWriter(socket.getOutputStream());
                    strSend = "QUERYRESPONSE" + REGEX + strSend;
                    logPrint("[onQUERY] sending msg: "+strSend);
                    myWrite.write(strSend+"\n");
                    myWrite.flush();
                    socket.close();
                }catch (Exception e){
                    logPrint(e.getMessage());
                }

            }
            private void onDELETE(String content){
                localDelete(content);
            }

            ////////////////////////////////////////////////////////////////////////
            ////////////////// asistant functions //////////////////////////////////
            ////////////////////////////////////////////////////////////////////////
            private String isThereNewPredecessor(ArrayList<String> oldOne, ArrayList<String> newOne){
                String oldPred = findMyPredecessor(oldOne, selfPort);
                String newPred = findMyPredecessor(newOne, selfPort);

                if(newPred.equals(oldPred)){
                    return null;
                }
                else{
                    return newPred;
                }
            }

            private String findMyPredecessor(ArrayList<String> llist, String pport){
                if(llist.indexOf(pport) == 0)
                    return llist.get(llist.size()-1);
                else
                    return llist.get(llist.indexOf(pport)-1);
            }
        }
    }
}
