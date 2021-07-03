package com.wangxiao.websocket.domain;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint(value = "/ws/asset")
@Component
public class WebSocketServer {

    @PostConstruct
    public void init() {
        System.out.println("websocket 加载");
    }
    private static Logger log = LoggerFactory.getLogger(WebSocketServer.class);
    private static final AtomicInteger OnlineCount = new AtomicInteger(0);
    private static final List<Object> userSet = new ArrayList<>();
    // concurrent包的线程安全Set，用来存放每个客户端对应的Session对象。
    private static CopyOnWriteArraySet<Session> SessionSet = new CopyOnWriteArraySet<Session>();
    private static final double EARTH_RADIUS = 6371000; // 平均半径,单位：m；不是赤道半径。赤道为6378左右

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        SessionSet.add(session);
        int cnt = OnlineCount.incrementAndGet(); // 在线数加1
        log.info("有连接加入，当前连接数为：{}", cnt);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        SessionSet.remove(session);
        int cnt = OnlineCount.decrementAndGet();
        log.info("有连接关闭，当前连接数为：{}", cnt);
        for (int i = 0; i < userSet.size(); i++) {
            Map<String,Object> item = (Map<String, Object>) userSet.get(i);
            if(session.equals((Session)item.get("session"))){
                userSet.remove(i);
            }
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message
     *            客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {

        JSONObject jsonObject = JSON.parseObject(message);
        log.info("来自客户端的消息：{}",jsonObject);
        Map<String,Object> user = new HashMap();
        if("open".equals(jsonObject.get("action"))){
            user.put("uuid",jsonObject.get("uuid"));
            user.put("lat",jsonObject.get("lat"));
            user.put("lng",jsonObject.get("lng"));
            user.put("session",session);
            userSet.add(user);
            List<String> userList = new ArrayList<String>();
            JSONObject object = new JSONObject();
            object.put("action","adduser");
            for (int i = 0; i < userSet.size(); i++) {
                Map<String,Object> item = (Map<String, Object>) userSet.get(i);
                if(getDistance(Double.parseDouble(item.get("lat").toString()),Double.parseDouble(item.get("lng").toString()),Double.parseDouble(jsonObject.get("lat").toString()),Double.parseDouble(jsonObject.get("lng").toString()))<300){
                    userList.add((String)item.get("uuid"));
                    List<String> userArray = new ArrayList<String>();
                    userArray.add((String) jsonObject.get("uuid"));
                    object.put("userlist",userArray);
                   if(!session.equals((Session)item.get("session"))) {
                       SendMessage((Session) item.get("session"), object.toJSONString());
                   }
                }
            }
            object.put("userlist",userList);
            SendMessage(session, object.toJSONString());
        }
        if("sendmessage".equals(jsonObject.get("action"))){
            JSONObject object = new JSONObject();
            object.put("action","sendmessage");
            for (int i = 0; i < userSet.size(); i++) {
                Map<String,Object> item = (Map<String, Object>) userSet.get(i);
                if(getDistance(Double.parseDouble(item.get("lat").toString()),Double.parseDouble(item.get("lng").toString()),Double.parseDouble(jsonObject.get("lat").toString()),Double.parseDouble(jsonObject.get("lng").toString()))<300){
                    object.put("uuid",jsonObject.get("uuid"));
                    object.put("message",jsonObject.get("message"));
                    SendMessage((Session)item.get("session"), object.toJSONString());
                }
            }

        }



    }

    /**
     * 出现错误
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误：{}，Session ID： {}",error.getMessage(),session.getId());
        error.printStackTrace();
    }

    /**
     * 发送消息，实践表明，每次浏览器刷新，session会发生变化。
     * @param session
     * @param message
     */
    public static void SendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(String.format("%s",message));
        } catch (IOException e) {
            log.error("发送消息出错：{}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 群发消息
     * @param message
     * @throws IOException
     */
    public static void BroadCastInfo(String message) throws IOException {
        for (Session session : SessionSet) {
            if(session.isOpen()){
                SendMessage(session, message);
            }
        }
    }

    /**
     * 指定Session发送消息
     * @param sessionId
     * @param message
     * @throws IOException
     */
    public static void SendMessage(String message,String sessionId) throws IOException {
        Session session = null;
        for (Session s : SessionSet) {
            if(s.getId().equals(sessionId)){
                session = s;
                break;
            }
        }
        if(session!=null){
            SendMessage(session, message);
        }
        else{
            log.warn("没有找到你指定ID的会话：{}",sessionId);
        }
    }

    //通过 经纬计算距离
    private double getDistance(Double lat1,Double lng1,Double lat2,Double lng2) {
        // 经纬度(角度)转弧度。弧度用作参数，以调用Math.cos和Math.sin

        double radiansAX = Math.toRadians(lng1); // A经弧度

        double radiansAY = Math.toRadians(lat1); // A纬弧度

        double radiansBX = Math.toRadians(lng2); // B经弧度

        double radiansBY = Math.toRadians(lat2); // B纬弧度

        // 公式中“cosβ1cosβ2cos(α1-α2)+sinβ1sinβ2”的部分，得到∠AOB的cos值

        double cos = Math.cos(radiansAY) * Math.cos(radiansBY) * Math.cos(radiansAX - radiansBX)

                + Math.sin(radiansAY) * Math.sin(radiansBY);

        // System.out.println("cos = " + cos); // 值域[-1,1]

        double acos = Math.acos(cos); // 反余弦值

        // System.out.println("acos = " + acos); // 值域[0,π]

        // System.out.println("∠AOB = " + Math.toDegrees(acos)); // 球心角 值域[0,180]

        return EARTH_RADIUS * acos; // 最终结果

    }

}
