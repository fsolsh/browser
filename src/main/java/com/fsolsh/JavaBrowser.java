package com.fsolsh;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class JavaBrowser extends JFrame {

    private final CefApp cefApp;
    private final CefClient client;
    private final CefBrowser browser;
    private final JTextField urlField;
    private final JButton backButton;
    private final JButton forwardButton;
    private final JButton refreshButton;
    private final JButton homeButton;
    private final JButton jsButton;

    public JavaBrowser() throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        // 创建安装目录
        File installDir = new File(System.getProperty("user.home"), "jcef-bundle");
        if (!installDir.exists() && !installDir.mkdirs()) {
            throw new IOException("Failed to create installation directory: " + installDir.getAbsolutePath());
        }

        CefAppBuilder builder = new CefAppBuilder();
        // 设置安装目录
        builder.setInstallDir(installDir);
        builder.addJcefArgs("--disable-gpu");
        builder.getCefSettings().windowless_rendering_enabled = false;
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                if (state == CefApp.CefAppState.TERMINATED) {
                    System.exit(0);
                }
            }
        });

        // 构建 CEF
        cefApp = builder.build();
        client = cefApp.createClient();

        // 创建浏览器实例
        browser = client.createBrowser("https://www.deepseek.com", false, false);
        Component browserUI = browser.getUIComponent();
        setEmptyIcon();

        // 设置消息路由器处理JavaScript到Java的调用
        CefMessageRouter messageRouter = CefMessageRouter.create();
        client.addMessageRouter(messageRouter);

        // JS call Java
        messageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
                // 处理从JavaScript发来的消息
                System.out.println("received from js client: " + request);
                // 发送响应回JavaScript
                //callback.success("java process success");
                callback.failure(0, "java process error");
                return true;
            }
        }, true);

        // 在构造函数中，创建 client 后添加以下代码
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
                SwingUtilities.invokeLater(() -> browser.loadURL(target_url));
                // 返回 true 表示已处理该请求，阻止新窗口打开
                return true;
            }
        });

        Font buttonFont = new Font("Fira Sans", Font.BOLD, 14);
        Font defaultFont = new Font("微软雅黑", Font.PLAIN, 14);
        // 创建 UI 组件
        urlField = new JTextField(50);
        urlField.setFont(defaultFont);
        backButton = new JButton("←");
        backButton.setFont(buttonFont);
        forwardButton = new JButton("→");
        forwardButton.setFont(buttonFont);
        refreshButton = new JButton("↻");
        refreshButton.setFont(buttonFont);
        homeButton = new JButton("⌂");
        homeButton.setFont(buttonFont);
        jsButton = new JButton("JSCall");
        jsButton.setFont(defaultFont);

        // 设置工具栏
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolBar.add(backButton);
        toolBar.add(forwardButton);
        toolBar.add(refreshButton);
        toolBar.add(homeButton);
        toolBar.add(jsButton);
        toolBar.add(urlField);

        // 设置布局
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(browserUI, BorderLayout.CENTER);

        // 配置事件监听器
        setupEventListeners();
        // 设置焦点
        SwingUtilities.invokeLater(urlField::requestFocusInWindow);

        // 配置窗口
        setTitle("Java Browser");
        setSize(1024, 768);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(
                        browserUI,
                        "want to close the browser?",
                        "please confirm",
                        JOptionPane.YES_NO_OPTION
                );
                if (result == JOptionPane.YES_OPTION) {
                    dispose();
                }
            }
        });
    }

    /**
     * 主函数
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                JavaBrowser browser = new JavaBrowser();
                browser.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "error creating browser: " + e.getMessage(), "error", JOptionPane.ERROR_MESSAGE);
                System.exit(-1);
            }
        });
    }

    /**
     * 去除JFrame的图标
     */
    private void setEmptyIcon() {
        BufferedImage emptyIcon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = emptyIcon.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, 16, 16);
        g2.dispose();

        // 设置透明图标
        this.setIconImage(emptyIcon);
        // 在某些平台上尝试设置窗口类型
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            setType(Window.Type.UTILITY);
        }
    }

    /**
     * 注册callJava Function
     * 注册一个window.java.callJava方法，给JS端调用
     */
    private void CefRegisterJSObject() {
        browser.executeJavaScript(
            "window.java = {" +
            "    callJava: function(data) {" +
            "        window.cefQuery({" +
            "            request: data," +
            "            onSuccess: function(response) {" +
            "                alert(response);" +
            "            }," +
            "            onFailure: function(error_code, error_message) {" +
            "                alert('Java call failed:' + error_message);" +
            "            }" +
            "        });" +
            "    }" +
            "};",
            browser.getURL(), 0);
    }

    /**
     * Java调用JS方法
     */
    private void callJavaScript(String method, String data) {
        String script = String.format("%s('%s');", method, data);
        // 执行JavaScript
        browser.executeJavaScript(script, browser.getURL(), 0);
    }

    /**
     *
     */
    private void setupEventListeners() {
        // 导航按钮事件
        backButton.addActionListener(e -> browser.goBack());
        forwardButton.addActionListener(e -> browser.goForward());
        refreshButton.addActionListener(e -> browser.reload());
        homeButton.addActionListener(e -> browser.loadURL("https://www.deepseek.com"));
        jsButton.addActionListener(e -> {
            callJavaScript("window.java.callJava", "http://www.baidu.com");
        });

        // URL 输入框事件
        urlField.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            urlField.setText(url);
            browser.loadURL(url);
        });

        // 加载状态变化监听
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                SwingUtilities.invokeLater(() -> {
                    backButton.setEnabled(canGoBack);
                    forwardButton.setEnabled(canGoForward);
                    String url = browser.getURL();
                    if (!isLoading && url.startsWith("http")) {
                        urlField.setText(browser.getURL());
                        CefRegisterJSObject();
                    }
                });
            }
            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
                // 当页面加载失败时显示错误信息
                if (!failedUrl.equals("about:blank")) {
                    SwingUtilities.invokeLater(() -> {
                        String errorMessage = String.format(
                                "<html><body><h2>页面无法访问</h2><p><b>URL:</b> %s</p><p><b>错误代码:</b> %s</p><p><b>错误信息:</b> %s</p><p>请检查网络连接或稍后重试</p></body></html>",
                                failedUrl, errorCode, errorText
                        );

                        // 显示错误页面
                        String errorPage = String.format(
                                "data:text/html;charset=utf-8,%s",
                                java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8)
                        );
                        browser.loadURL(errorPage);
                    });
                }
            }
        });
    }

    /**
     * 资源释放
     */
    @Override
    public void dispose() {
        browser.close(true);
        client.dispose();
        super.dispose();
        if (cefApp != null) {
            cefApp.dispose();
        }
        System.exit(0);
    }
}