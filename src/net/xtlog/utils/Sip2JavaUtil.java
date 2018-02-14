package net.xtlog.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *3M' SIP2标准协议的Java实现类。
 * 利用该类可快速实现基于SIP2接口的RFID业务的终端扩展。
 */
public class Sip2JavaUtil {
    //公共属性
    private String hostname;
    private int port         = 6002; //默认端口号
    private String library      = "";
    private String language     = "001"; //001= english

    //顾客信息，图书馆业务系统中通常指读者
    private String patron       = ""; //AA
    private String patronpwd    = ""; //AD

    // 授权终端的密码
    private String ac           = ""; //AC

    // 允许的最大重发次数
    private int maxretry     = 3;

    // 终端数据串的分隔符号
    private String fldTerminator = "|";
    private String msgTerminator = "\r";

    // 登录属性
    private String UIDalgorithm = "0"; //0    默认不加密
    private String PWDalgorithm = "0"; //未定义
    private String scLocation   = "";  //位置代码

    private Socket socket;

    // 序列计数器
    private int seq   = -1;

    // 重发计数器
    private int retry = 0;

    // 用于构建消息
    private String msgBuild = "";
    private boolean noFixed = false;

    private String ao = "";
    private String an = "";

    private InputStreamReader in;
    private BufferedReader bd;
    private BufferedWriter bw;

    public Sip2JavaUtil(String hostname, int port){
        this.hostname = hostname;
        this.port = port;
    }

    public void connect(){
        try {
            socket = new Socket(this.hostname, this.port);
            if(socket.isConnected()){
                System.out.println("Socket 已连接");
            }else{
                System.out.println("Socket 连接失败");
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取返回内容
     * @param message
     * @return
     */
    public  String get_message (String message) {

        while(socket == null){
            this.connect();
        }

        try{
            if(in == null)
                in = new InputStreamReader(socket.getInputStream());

            bd = new BufferedReader(in);
            bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            bw.write(message);
            bw.flush();
            String line = "";
            char terminator = 0;
            char end = (char)13;
            while (!(end == terminator)) {
                terminator = (char)bd.read();
                line += terminator;
            }
            return line;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }


    }

    /**
     * 获取读者相关信息
     * @param status
     * @param width
     * @return
     */
    public String msgSCStatus(String status, String width) {
        creNewMessage("99");
        addFixedOption(status, 1);
        addFixedOption(width, 3);
        String fs = String.format("%03.2f", Float.parseFloat("2"));
        addFixedOption(fs, 4);
        return returnMessage(true, true);
    }

    /**
     * 调用借书接口
     * @param item
     * @param nbDateDue
     * @param itmProp
     * @return
     */
    public String msgCheckout(String item, String nbDateDue,String itmProp) {
        //创建借书消息，代码号为 (11)
        creNewMessage("11");
        addFixedOption("Y", 1);
        addFixedOption("N", 1);
        addFixedOption(datestamp(null), 18);


        if (nbDateDue != null && !"".equals(nbDateDue)) {
            //设定日期
            addFixedOption(datestamp(nbDateDue), 18);
        } else {
            //发送一个空白日期，因为允许ACS使用项目计算的默认日期
            addFixedOption("", 18);
        }
        addVarOption("AO",ao, false);
        addVarOption("AA",patron, false);
        addVarOption("AB",item, false);
        addVarOption("AC","acs2", false);
        addVarOption("CH",itmProp, true);
        addVarOption("AD",patronpwd, true);
        //addVarOption("AF","acs1", false);
        //addVarOption("AG","acs1", false);
        addVarOption("BO","N", true);  /* 是否有费用 */
        addVarOption("BI","N", true); /* 是否取消 默认为否 */

        return returnMessage(true, true);
    }

    /**
     * 调用还书接口
     * @param item
     * @param itmReturnDate
     * @param itmLocation
     * @return
     */
    public String msgCheckin(String item, String itmReturnDate, String itmLocation) {
        //创建还书消息，代码号为 (11)
        if (itmLocation == null || "".equals(itmLocation)) {
            //如果没有指定位置，则假定SC的默认位置
            itmLocation = scLocation;
        }

        creNewMessage("09");
        addFixedOption("N", 1);
        addFixedOption(_datestamp(""), 18);
        addFixedOption(_datestamp(itmReturnDate), 18);
        addVarOption("AP",itmLocation,false);
        addVarOption("AO",ao,false);
        addVarOption("AB",item,false);
        addVarOption("AC",ac,false);
        addVarOption("CH","", true);
        addVarOption("BI","N", true); /* Y or N */

        return returnMessage(true, true);
    }

    /**
     * 登录终端，使用借还接口前必须调用
     * @param sipLogin
     * @param sipPassword
     * @return
     */
    public String msgLogin(String sipLogin, String sipPassword) {
        //登录消息代码为93
        creNewMessage("93");
        addFixedOption(UIDalgorithm, 1);
        addFixedOption(PWDalgorithm, 1);
        addVarOption("CN",sipLogin,false);
        addVarOption("CO",sipPassword,false);
        addVarOption("CP",scLocation, true);
        return returnMessage(true, true);

    }

    /**
     * 格式化返回的内容
     * @param res
     * @return
     */
    public Map<String, String> parseStatusResponse(String res){

        Map<String, String> resMap = new HashMap<String, String>();
        resMap.put("result", res.substring(2, 3));
        String[] resstr = res.split("\\|");
        for(String s: resstr){
            if(s.startsWith("AF")){
                resMap.put("msg", s.replace("AF", ""));
            }
            if(s.startsWith("AH")){
                resMap.put("dueDate", s.replace("AH", ""));
            }
        }
        return resMap;
    }

    private void creNewMessage(String code) {
        //重置消息构造器
        this.noFixed  = false;
        this.msgBuild = code;
    }

    private boolean addFixedOption(String value, int len) {
        //添加固定长度的选项
        if ( this.noFixed ) {
            return false;
        } else {
            String f = "%" + len + "s";
            String crcs = String.format(f, String.valueOf(value));
            msgBuild += crcs.substring(0, len);
            return true;
        }
    }

    private boolean addVarOption(String field, String value, boolean optional) {

        if (optional && "".equals(value))
        {
            System.out.println( "SIP2: Skipping optional field {$field}");
        } else {
            noFixed  = true; /* no more fixed for this message */
            msgBuild += field + value + fldTerminator;
        }
        return true;

    }

    /**
     * 日期格式化方法
     * 生成一个兼容SIP2的时间戳
     * 格式为: YYYYMMDDZZZZHHMMSS.
     * @param datedue
     * @return
     */
    private String datestamp(String datedue) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd    HHmmss");
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if(datedue != null && !"".equals(datedue)){
            try {
                Date date = format1.parse(datedue);
                return format.format(date);
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return format.format(new Date());
    }

    public String returnMessage(boolean withSeq , boolean withCrc) {
        // 完成消息并返回。 消息将保留在msgBuild中，直到调用newMessage
        if (withSeq) {
            msgBuild = msgBuild + "AY" + this.getseqnum();
        }
        if (withCrc) {
            msgBuild = msgBuild +  "AZ";
            msgBuild = msgBuild + crc(msgBuild);
        }
        msgBuild += msgTerminator;

        return msgBuild;
    }

    /**
     * 获取序列计数器
     * @return
     */
    private int getseqnum() {
        this.seq++;
        if (seq > 9 ) {
            seq = 0;
        }
        return seq;
    }

    /**
     * 循环冗余校验
     * @param buf
     * @return
     */
    private String crc(String buf) {
        int sum = 0;

        int len = buf.length();
        for (int n = 0; n < len; n++) {
            sum = sum + buf.substring(n,n+1).charAt(0);
        }

        int crc = (sum & 0xFFFF) * -1;
        String crcs = String.format("%4X", crc).toString();
        return crcs.substring(crcs.length()-4);
    }


    public String getHostname() {
        return hostname;
    }
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public String getLibrary() {
        return library;
    }
    public void setLibrary(String library) {
        this.library = library;
    }
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    public String getPatron() {
        return patron;
    }
    public void setPatron(String patron) {
        this.patron = patron;
    }
    public String getPatronpwd() {
        return patronpwd;
    }
    public void setPatronpwd(String patronpwd) {
        this.patronpwd = patronpwd;
    }
    public String getAc() {
        return ac;
    }
    public void setAc(String ac) {
        this.ac = ac;
    }
    public int getMaxretry() {
        return maxretry;
    }
    public void setMaxretry(int maxretry) {
        this.maxretry = maxretry;
    }
    public String getFldTerminator() {
        return fldTerminator;
    }
    public void setFldTerminator(String fldTerminator) {
        this.fldTerminator = fldTerminator;
    }
    public String getMsgTerminator() {
        return msgTerminator;
    }
    public void setMsgTerminator(String msgTerminator) {
        this.msgTerminator = msgTerminator;
    }

    public String getScLocation() {
        return scLocation;
    }
    public void setScLocation(String scLocation) {
        this.scLocation = scLocation;
    }

    public String getAo() {
        return ao;
    }

    public void setAo(String ao) {
        this.ao = ao;
    }

    public String getAn() {
        return an;
    }

    public void setAn(String an) {
        this.an = an;
    }

}
