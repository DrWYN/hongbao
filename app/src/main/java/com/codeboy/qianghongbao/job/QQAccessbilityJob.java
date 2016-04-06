package com.codeboy.qianghongbao.job;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.codeboy.qianghongbao.QiangHongBaoService;

import java.util.ArrayList;
import java.util.List;

public class QQAccessbilityJob extends BaseAccessbilityJob {

    private static final String TAG = "QQAccessbilityJob";

    /** QQ的包名*/
    private static final String QQ_PACKAGENAME = "com.tencent.mobileqq";
    /** 红包消息的关键字*/
    private static final String QQ_HONGBAO_TEXT_KEY = "[QQ红包]";
    private Handler mHandler = null;

    private static final String WECHAT_OPEN_EN = "Open";
    private static final String WECHAT_OPENED_EN = "You've opened";
    private final static String QQ_DEFAULT_CLICK_OPEN = "点击拆开";
    private final static String QQ_HONG_BAO_PASSWORD = "口令红包";
    private final static String QQ_CLICK_TO_PASTE_PASSWORD = "点击输入口令";
    private boolean mLuckyMoneyReceived;
    private String lastFetchedHongbaoId = null;
    private long lastFetchedTime = 0;
    private static final int MAX_CACHE_TOLERANCE = 5000;
    private AccessibilityNodeInfo rootNodeInfo;
    private List<AccessibilityNodeInfo> mReceiveNode;

    @Override
    public void onCreateJob(QiangHongBaoService service) {
        super.onCreateJob(service);
    }

    @Override
    public void onStopJob() {

    }

    @Override
    public boolean isEnable() {
        return getConfig().isEnableWechat();
    }

    @Override
    public String getTargetPackageName() {
        return QQ_PACKAGENAME;
    }

    @Override
    public void onReceiveJob(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        if(eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            List<CharSequence> texts = event.getText();
            if(!texts.isEmpty()) {
                for(CharSequence t : texts) {
                    String text = String.valueOf(t);
                    if(text.contains(QQ_HONGBAO_TEXT_KEY)) {
                        openNotify(event);
                        break;
                    }
                }
            }
        } else if(eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 处理微信聊天界面
            openHongBao(event);
        } else if (eventType == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) {
            // 从微信主界面进入聊天界面
            openHongBao(event);
        }
    }

    /** 打开通知栏消息*/
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void openNotify(AccessibilityEvent event) {
        if(event.getParcelableData() == null || !(event.getParcelableData() instanceof Notification)) {
            return;
        }

        //以下是精华，将微信的通知栏消息打开
        Notification notification = (Notification) event.getParcelableData();
        PendingIntent pendingIntent = notification.contentIntent;

        try {
            pendingIntent.send();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void openHongBao(AccessibilityEvent event) {
        this.rootNodeInfo = event.getSource();
        Log.i(TAG,"QQ onReceiveJob rootNodeInfo : "+rootNodeInfo);
        if (rootNodeInfo == null) {
            return;
        }
        mReceiveNode = null;
        checkNodeInfo();
        Log.i(TAG, "QQ onReceiveJob mReceiveNode : " + mReceiveNode);
        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && (mReceiveNode != null)) {
            int size = mReceiveNode.size();
            if (size > 0) {
                String id = getHongbaoText(mReceiveNode.get(size - 1));
                long now = System.currentTimeMillis();
                if (this.shouldReturn(id, now - lastFetchedTime))
                    return;
                lastFetchedHongbaoId = id;
                lastFetchedTime = now;
                AccessibilityNodeInfo cellNode = mReceiveNode.get(size - 1);
                Log.i(TAG, "QQ onReceiveJob cellNode : " + cellNode);
                if (cellNode.getText().toString().equals("口令红包已拆开")) {
                    return;
                }
                if(cellNode != null) {
                    final AccessibilityNodeInfo n = cellNode;
                    long sDelayTime = getConfig().getWechatOpenDelayTime();
                    if(sDelayTime != 0) {
                        getHandler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                if (n.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {
                                    AccessibilityNodeInfo rowNode = getService().getRootInActiveWindow();
                                    if (rowNode == null) {
                                        Log.e(TAG, "noteInfo is　null");
                                        return;
                                    } else {
                                        recycle(rowNode);
                                    }
                                }
                                mLuckyMoneyReceived = false;
                            }
                        }, sDelayTime);
                    } else {
                        n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (n.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {
                            AccessibilityNodeInfo rowNode = getService().getRootInActiveWindow();
                            if (rowNode == null) {
                                Log.e(TAG, "noteInfo is　null");
                                return;
                            } else {
                                recycle(rowNode);
                            }
                        }
                        mLuckyMoneyReceived = false;
                    }
                }
                /*cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (cellNode.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {
                    AccessibilityNodeInfo rowNode = getService().getRootInActiveWindow();
                    if (rowNode == null) {
                        Log.e(TAG, "noteInfo is　null");
                        return;
                    } else {
                        recycle(rowNode);
                    }
                }
                mLuckyMoneyReceived = false;*/
            }
        }
    }

    private Handler getHandler() {
        if(mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        return mHandler;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void recycle(AccessibilityNodeInfo info) {
        Log.i(TAG, "QQ recycle info : " + info);
        if (info.getChildCount() == 0) {
   /*这个if代码的作用是：匹配“点击输入口令的节点，并点击这个节点”*/
            if(info.getText()!=null&&info.getText().toString().equals(QQ_CLICK_TO_PASTE_PASSWORD)) {
                info.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
   /*这个if代码的作用是：匹配文本编辑框后面的发送按钮，并点击发送口令*/
            if (info.getClassName().toString().equals("android.widget.Button") && info.getText().toString().equals("发送")) {
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    private void checkNodeInfo() {
        if (rootNodeInfo == null) {
            return;
        }
  /* 聊天会话窗口，遍历节点匹配“点击拆开”，“口令红包”，“点击输入口令” */
        List<AccessibilityNodeInfo> nodes1 = this.findAccessibilityNodeInfosByTexts(this.rootNodeInfo, new String[]{QQ_DEFAULT_CLICK_OPEN, QQ_HONG_BAO_PASSWORD, QQ_CLICK_TO_PASTE_PASSWORD, "发送"});
        if (!nodes1.isEmpty()) {
            String nodeId = Integer.toHexString(System.identityHashCode(this.rootNodeInfo));
            if (!nodeId.equals(lastFetchedHongbaoId)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = nodes1;
            }    return;
        }
    }

    private String getHongbaoText(AccessibilityNodeInfo node) {
   /* 获取红包上的文本 */
        String content;
        try {
            AccessibilityNodeInfo i = node.getParent().getChild(0);
            content = i.getText().toString();
        } catch (NullPointerException npe) {
            return null;
        }
        return content;
    }

    private boolean shouldReturn(String id, long duration) {
        // ID为空
        if (id == null) return true;
        // 名称和缓存不一致
        if (duration < MAX_CACHE_TOLERANCE && id.equals(lastFetchedHongbaoId)) {
            return true;
        }
        return false;
    }

    private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
        for (String text : texts) {
            if (text == null) continue;
            List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);
            if (!nodes.isEmpty()) {
                if (text.equals(WECHAT_OPEN_EN) && !nodeInfo.findAccessibilityNodeInfosByText(WECHAT_OPENED_EN).isEmpty()) {
                    continue;
                }
                return nodes;
            }
        }
        return new ArrayList<>();
    }
}
