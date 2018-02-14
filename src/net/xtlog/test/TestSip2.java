package net.xtlog.test;

import net.xtlog.utils.Sip2JavaUtil;

public class TestSip2 {

    public static void main(String[] args) {

        Sip2JavaUtil sip2 = new Sip2JavaUtil("0.0.0.0", 2006);

        //设定读者的证号和密码
        sip2.setPatron("000");
        sip2.setPatronpwd("00000000000");
        //设置终端授权码
        sip2.setAc("acs");
        //连接远程Socket端口
        sip2.connect();
        //登录终端
        String msg1 = sip2.msgLogin("acs", "acs");
        System.out.println(msg1);
        //借阅指定条码的图书
        String msg2 = sip2.msgCheckout("000000", null, "");
        System.out.println(msg2);

    }
}